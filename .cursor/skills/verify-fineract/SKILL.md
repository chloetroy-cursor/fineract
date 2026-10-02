---
name: verify-fineract
description: Start Apache Fineract locally and prove loan behavior through its REST API, the way a back-office client of the API sees it. Use when reproducing a reported loan, balance, or transaction bug, before and after a fix, or before running an integration test.
---

# Verify Fineract

Fineract's user-facing surface is its REST API at `https://localhost:8443/fineract-provider/api/v1`. Every proof goes through that API, never through the database or internal classes.

All commands go through `.cursor/skills/verify-fineract/scripts/fineract`, run from anywhere in the repo. Below it is called `fineract`.

## Launch

1. Build the image from the current checkout: `fineract build` (about 5 minutes cold, faster warm). The server only contains code that was in the tree at build time, so rebuild after every code change you want to verify.
2. Start Postgres and Fineract: `fineract up`. It returns once `actuator/health` reports `UP`, usually in 1 to 2 minutes. Ports 5432 and 8443 must be free.

## Doctor

Run `fineract doctor` first, and again whenever a result looks wrong. It prints:

- `health`: must contain `"status":"UP"`.
- `server`: the git commit baked into the running image (`-dirty` means it was built with uncommitted changes).
- `head`: your checkout. If it differs from `server`, or says there are uncommitted changes, rebuild before trusting a result.
- Container state for `db` and `fineract`.

Only drive the `fineract-verify` compose project this script started. Do not touch another Fineract instance on the machine.

## Drive

- `fineract api METHOD PATH [JSON]` calls the API as `mifos` / `password` on tenant `default`. `PATH` is relative to `/api/v1`, for example `fineract api GET loans/1`.
- `fineract seed-loan` creates a client, a flat-interest product, and a disbursed loan, then prints `{"clientId","productId","loanId","evidence"}`. The loan is 12,000 over 12 monthly repayments at 1% flat per month, disbursed 01 January 2026: 1,440 interest in total, 120 per installment.
- Every request body that has dates needs `"dateFormat":"dd MMMM yyyy"` and `"locale":"en"`.

The feature map in `features/` has the exact recipe and expected end state for each feature. Read `features/README.md` before driving anything.

## Evidence

- Write proof files under `$EVIDENCE_DIR` (defaults to `/tmp/fineract-verify/<timestamp>`), never inside the repo. `seed-loan` already saves the loan as seeded.
- A proof includes the request, the response, and a second read-only view of the result. For loans, compare `summary` on `GET loans/<id>` with the totals from `GET loans/<id>?associations=repaymentSchedule`. The two must agree.
- Assert against literal expected numbers worked out from the loan terms, not against whatever the server returns.
- For a fix, capture the same recipe on the broken build and on the fixed build.

## Cleanup

`fineract down` stops both containers and deletes the database volume. Evidence in `$EVIDENCE_DIR` survives cleanup.

## Running integration tests against this instance

After `fineract up`, the integration suite reaches the same server: `./gradlew :integration-tests:test --tests SomeIntegrationTest`. Run single classes only.
