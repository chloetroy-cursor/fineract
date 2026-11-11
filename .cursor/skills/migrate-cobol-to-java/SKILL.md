---
name: migrate-cobol-to-java
description: >-
  Migrate the demo COBOL late-fee batch (legacy/cobol) into fineract-loan
  Java: read the COBOL and SPEC, lock fixtures as characterization tests,
  port the rules, prove parity, open a PR against addepar-demo. Use when
  asked to migrate LNFEECALC, port COBOL late fees, or run the Banco
  Guayaquil / COBOL modernization demo.
---

# Migrate COBOL → Java (LNFEECALC)

Port the synthetic late-fee batch under `legacy/cobol/` into
`fineract-loan` as pure Java domain logic, with golden fixtures as the
characterization suite.

This skill is the demo loop for legacy modernization. Do **not** invent
a second fee formula. The COBOL + `SPEC.md` + `fixtures/cases.jsonl` are
the contract.

## Inputs (fixed for this repo)

| Item | Path |
|------|------|
| Program | `legacy/cobol/programs/LNFEECALC.cbl` |
| Copybooks | `legacy/cobol/copybooks/LNFEEREC.cpy`, `LNFEECTL.cpy` |
| Rules | `legacy/cobol/SPEC.md` |
| Fixtures | `legacy/cobol/fixtures/cases.jsonl` |
| Oracle (regen only) | `legacy/cobol/scripts/lnfeecalc_oracle.py` |
| Landing zone | `fineract-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/domain/legacy/` |
| Tests | `fineract-loan/src/test/java/org/apache/fineract/portfolio/loanaccount/domain/legacy/` |
| Base branch | `addepar-demo` |

## Working style

1. **Read before writing.** Open the COBOL, both copybooks, and `SPEC.md`. Summarize the rules in 5 bullets before any Java.
2. **Fixtures first.** Characterization tests load `cases.jsonl`. They must fail (class missing) then pass (port done). Do not hard-code expected numbers in the test source — read the JSONL.
3. **Pure domain only.** No Spring, no JPA, no REST in this unit. Money math uses `BigDecimal` with `RoundingMode.HALF_UP`, scale 2. Never `double`.
4. **Parity is the gate.** `./gradlew :fineract-loan:test --tests '...LateFeeCalculatorTest'` green is the definition of done for the unit.
5. **Leave the COBOL in place.** Migration adds Java; it does not delete `legacy/cobol/`. A later unit can mark the batch retired.

## Steps

### 1. Orient

- Confirm you are on a branch off `addepar-demo`.
- Read `legacy/cobol/README.md` and `SPEC.md`.
- Skim `LNFEECALC.cbl` PROCEDURE DIVISION: grace → tiers → cap → round → min.
- Print the fixture summary: `python3 legacy/cobol/scripts/lnfeecalc_oracle.py` (no `--write` unless fixtures are stale and the user asked to regenerate).

### 2. Characterization tests (before the port)

Create:

`fineract-loan/src/test/java/org/apache/fineract/portfolio/loanaccount/domain/legacy/LateFeeCalculatorTest.java`

Requirements:

- Load every line of `legacy/cobol/fixtures/cases.jsonl` (resolve the path from the module root / repo root; do not copy the JSON into `src/test/resources` unless path resolution is painful — copying is acceptable if you keep a comment pointing at the canonical file).
- For each case, call `LateFeeCalculator.assess(principal, daysOverdue, productCode)` and assert `lateFeeAmt`, `billableDays`, `statusCode`.
- Use parameterized tests (`@ParameterizedTest` / `@MethodSource`) so the demo shows one test per fixture id.
- Apache license header on the new file.
- Run the test once against a missing class to confirm it fails for the right reason.

### 3. Port

Create:

- `.../legacy/LateFeeCalculator.java` — static or small immutable service with `assess(...)`.
- `.../legacy/LateFeeAssessment.java` — result type (`lateFeeAmt`, `billableDays`, `statusCode`, `statusMsg`).
- `.../legacy/LateFeeProductCatalog.java` — the three products from `LOAD-PRODUCT-TABLE` (`EVERYDAY`, `CONSUMO`, `HIPOTEC`).

Rules to preserve exactly:

- Status codes `00` / `01` / `02` / `99` and their messages from the COBOL.
- Tier split on **days past grace**, not raw overdue days.
- Cap as percent of principal, then half-up to cents, then minimum fee.
- Product codes are matched trimmed; COBOL stores `CONSUMO` / `HIPOTEC` space-padded to 8.

Keep money in `BigDecimal`. Intermediate tier fees may use higher scale; final amount is scale 2, `HALF_UP`.

### 4. Prove parity

```bash
./gradlew :fineract-loan:test --tests 'org.apache.fineract.portfolio.loanaccount.domain.legacy.LateFeeCalculatorTest'
./gradlew :fineract-loan:spotlessApply
```

Every fixture must pass. If one fails, fix the Java — do not edit fixtures to match the port.

### 5. Optional wiring (only if asked)

A thin call site is enough for the demo narrative:

- Document in the PR that Everyday Banking can call `LateFeeCalculator` from a delinquency job later.
- Do **not** invent a new REST endpoint or Liquibase changelog unless the user asks. The unit is the calculator.

### 6. Ship

- Commit on a `cursor/...` or `demo/...` branch.
- Open a PR against `addepar-demo` in `chloetroy-cursor/fineract`.
- PR body: what was ported, how parity is proven (`cases.jsonl` + test command), and that `legacy/cobol/` remains as the source batch.

## Demo prompts (ready to paste)

**Cloud agent / overnight (full unit):**

```text
Migrate legacy/cobol LNFEECALC into fineract-loan Java.

Follow .cursor/skills/migrate-cobol-to-java/SKILL.md exactly.

- Read the COBOL, copybooks, and SPEC.md.
- Add LateFeeCalculator + characterization tests that load
  legacy/cobol/fixtures/cases.jsonl.
- Prove parity with:
  ./gradlew :fineract-loan:test --tests '*LateFeeCalculatorTest'
  ./gradlew :fineract-loan:spotlessApply
- Open a PR against addepar-demo.
- Do not delete legacy/cobol. Do not add REST or DB changes.
```

**Live on the call (short):**

```text
Port LNFEECALC late-fee rules into
org.apache.fineract.portfolio.loanaccount.domain.legacy.LateFeeCalculator.
Use legacy/cobol/fixtures/cases.jsonl as characterization tests.
Stop when the test class is green.
```

## Guardrails

- Do not change `SPEC.md` or fixtures to make a bad port pass.
- Do not use `double` / `float` for money.
- Do not put the calculator in `fineract-provider` — it belongs in `fineract-loan`.
- Do not run the full integration suite.
- Do not target `apache/fineract` upstream. PRs go to `addepar-demo` on this fork.
- Spanish product names in the control table are intentional demo flavor; keep them.
