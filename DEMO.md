# Kanini live demo

This branch adds **Cursor project hooks** only. No Fineract application code is
changed. Hooks are local, deterministic controls: they run on the operator's
machine from `.cursor/hooks.json`, read a JSON payload on stdin, and must
print valid JSON on stdout.

Trusted workspaces load project hooks automatically. Confirm they are enabled
in Cursor Settings → Hooks, then reload the window after pulling this branch.

## View the audit log

Every `beforeSubmitPrompt`, `beforeShellExecution`, `afterFileEdit`, and
`stop` event appends one JSON line to `~/kanini-audit.jsonl`:

```sh
tail -f ~/kanini-audit.jsonl | jq
```

Useful one-liners:

```sh
# Last 20 events
tail -n 20 ~/kanini-audit.jsonl | jq

# Prompt submissions only
jq 'select(.event == "beforeSubmitPrompt")' ~/kanini-audit.jsonl
```

The log is under the operator home directory, not the repo. Prompt, command,
and path values are truncated to 240 characters. The file can still contain
prompt text — use fictional data only during the demo.

## Test prompt that gets blocked

Paste this in Agent or Chat (fictional SSN only — never use a real number):

```
Look up the loan for client SSN 123-45-6789 and post a waiver.
```

Expected: the `beforeSubmitPrompt` guard returns `continue: false` and Cursor
shows a user-facing message that the prompt looks like it contains a US Social
Security number. Nothing is sent to the model.

A 10–16 digit account/card value is also blocked (spaces or hyphens allowed):

```
Disburse loan account 4111 1111 1111 1111 to the client savings account.
```

A clean prompt such as `Show the repayment schedule for loan 1` is allowed and
still written to the audit log.

## Suggested live issues

Do **not** implement these before the demo. Each is a Plan Mode → Agent build →
Bugbot review loop of about ten minutes. Keep money math in `Money` /
`BigDecimal`. Run `./gradlew spotlessApply` on touched modules before handing
to Bugbot.

### 1. Mask loan account numbers on retrieve

**Why it demos well:** a regulated back-office client should not echo a full
account number on every `GET /loans/{id}` payload.

**Files:**

- `fineract-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/data/LoanAccountData.java` — `accountNo` is a plain `String` today
- `fineract-provider/src/main/java/org/apache/fineract/portfolio/loanaccount/service/LoanReadPlatformServiceImpl.java` — `LoanMapper` selects `l.account_no as accountNo` and `retrieveOne(Long loanId)` returns that DTO
- `fineract-provider/src/main/java/org/apache/fineract/portfolio/loanaccount/api/LoansApiResource.java` — serializes `LoanAccountData` (`accountNo` is already in `LOAN_DATA_PARAMETERS`)

**Approach:** add a small mask helper (keep the last four characters, replace
the rest with `*`) and expose it as a new JSON field such as `accountNoMasked`.
Do **not** overwrite `accountNo` and do **not** change the stored value. That
keeps existing clients and tests green.

**Tests already in this area:**

- Unit pattern: `fineract-loan/src/test/java/org/apache/fineract/portfolio/loanaccount/data/LoanTransactionDataTest.java` (no `LoanAccountData` test exists yet — add one)
- Live API: `.cursor/skills/verify-fineract/` — `fineract seed-loan` then `fineract api GET loans/<id>`
- Integration: any retrieve in `integration-tests` that asserts `accountNo` (for example `WorkingCapitalLoanApplicationCRUDTest`) must still see the raw number if you add a new field

Cheap proof: `./gradlew :fineract-loan:test --tests '...LoanAccountDataTest'`.

### 2. Reject non-alphanumeric savings account numbers

**Why it demos well:** create/update already checks length, not format, so a
one-line validator change is visible on `POST /savingsaccounts`.

**Files:**

- `fineract-savings/src/main/java/org/apache/fineract/portfolio/savings/data/SavingsAccountDataValidator.java` — `validateForSubmit` / update currently do `accountNo.notBlank().notExceedingLengthOf(20)` only
- `fineract-core/src/main/java/org/apache/fineract/infrastructure/core/data/DataValidatorBuilder.java` — `matchesRegularExpression(...)`
- `fineract-provider/src/main/java/org/apache/fineract/portfolio/savings/api/SavingsAccountsApiResource.java` — `submitApplication` is the HTTP entry

**Approach:** after the existing length check on both submit and modify,
`.matchesRegularExpression("[A-Za-z0-9]+")` (or a tighter product rule). Leave
the 20-character cap in place.

**Tests already in this area:**

- No `SavingsAccountDataValidator` unit test exists. Copy the style of `fineract-provider/src/test/java/org/apache/fineract/portfolio/client/data/ClientDataValidatorTest.java` (real `FromJsonHelper`, assert `PlatformApiDataValidationException` parameter names)
- Integration: `integration-tests/src/test/java/org/apache/fineract/integrationtests/SavingsAccountsTest.java` and `SavingsAccountsExternalIdTest.java` cover create/retrieve; add one rejected-body case or a module unit test rather than the full suite

Cheap proof: `./gradlew :fineract-savings:test --tests '...SavingsAccountDataValidatorTest'`.

### 3. Require an international prefix on client mobile numbers

**Why it demos well:** input validation the audience already understands (PHI /
PII adjacent), with tests that already exist.

**Files:**

- `fineract-core/src/main/java/org/apache/fineract/infrastructure/core/validator/PhoneNumberValidationService.java` — default regex is `^\+?[0-9]{7,15}$` (leading `+` is optional)
- `fineract-provider/src/main/java/org/apache/fineract/portfolio/client/data/ClientDataValidator.java` — create/update call `matchesRegularExpression(phoneNumberValidationService.getRegex())`

**Approach:** require a country code, for example `^\+[1-9][0-9]{6,14}$`, via
the existing `fineract.phone.regex` property or a tighter default. Keep
`ignoreIfNull()` so omitted `mobileNo` still succeeds.

**Tests already in this area:**

- `fineract-core/src/test/java/org/apache/fineract/infrastructure/core/validator/PhoneNumberValidationServiceTest.java`
- `fineract-provider/src/test/java/org/apache/fineract/portfolio/client/data/ClientDataValidatorTest.java` — already accepts `+919876543210` / `+14155552671` and rejects `phone123`

Cheap proof:

```sh
./gradlew :fineract-core:test --tests 'org.apache.fineract.infrastructure.core.validator.PhoneNumberValidationServiceTest'
./gradlew :fineract-provider:test --tests 'org.apache.fineract.portfolio.client.data.ClientDataValidatorTest'
```

## Hook contract (for the presenter)

| Event | Documented stdin fields | Documented stdout |
| --- | --- | --- |
| `beforeSubmitPrompt` | `prompt`, `conversation_id`, `generation_id`, `model`, `hook_event_name` | Allow: `{"continue": true}`. Deny: `{"continue": false, "user_message": "..."}` |
| `beforeShellExecution` | `command`, `cwd`, plus the common ids | `{"permission": "allow"}` (this repo does not deny shell) |
| `afterFileEdit` | `file_path`, `edits` | `{}` |
| `stop` | `status` | `{}` |

`user_message` is shown only when `continue` is `false`. The audit hook is
`failClosed: false` (a log failure must not stop the demo). The DLP hook is
`failClosed: true` (crash, timeout, or invalid JSON blocks submission).
Scripts themselves always emit valid JSON and exit 0.

Project hook `command` paths are relative to the repository root and run from
there. See [Cursor hooks](https://cursor.com/docs/hooks).
