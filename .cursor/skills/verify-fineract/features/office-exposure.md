# Office exposure

A risk or finance user asks how much is outstanding on active loans in an office, including all of its child offices, per currency.

## Sub-features

- `exposure-totals` returns active loan count, principal, interest, and total outstanding per currency.
- `exposure-rollup` includes loans of clients in child offices (matched on `m_office.hierarchy`).
- `exposure-latency` stays roughly flat as the number of loans grows.

## How to get to it (user POV)

- `GET offices/<officeId>/exposure`. It does not exist on `addepar-demo`. It exists on the `demo/office-exposure-slow` and `demo/office-exposure-fast` branches.

## Driving it with the fineract script

Preconditions:

- A server built from a branch that has the endpoint. Pre-built images: `fineract:exposure-slow` and `fineract:exposure-fast`. Switch with `FINERACT_IMAGE=fineract:exposure-fast fineract restart`; the database is kept.
- Loans from `fineract seed-loan` or `fineract seed-loans N`. Both use the same terms, so each active loan adds 12,000 principal, 1,440 interest, and 13,440 total in USD.

Steps:

- **Totals.** Run `fineract api GET offices/1/exposure`. With N seeded loans and nothing else on the tenant, the `USD` entry has `activeLoanCount` N, `principalOutstanding` N x 12000, `interestOutstanding` N x 1440, `totalOutstanding` N x 13440.
- **Cross-check.** Run `fineract api GET loans/<loanId>` on any seeded loan. `summary.totalOutstanding` is `13440.0`. The office total is that value times the loan count.
- **Latency.** Run `time fineract api GET offices/1/exposure > /dev/null` with about 5 loans and again with about 1,000 loans. Also read `http_server_requests_seconds` for `uri="/v1/offices/{officeId}/exposure"` on `/fineract-provider/actuator/prometheus`.
- **SQL count.** `FINERACT_STATEMENT_LOGGING_ENABLED=true fineract restart`, call the endpoint once, then count `SELECT` lines in `docker logs fineract-verify-fineract-1`. Turn it off again before measuring latency.

## Gotchas

- Fineract uses EclipseLink, not Hibernate. Hibernate logging properties do nothing; use `FINERACT_STATEMENT_LOGGING_ENABLED`.
- A new `@Cacheable` cache is a no-op here. `CacheConfig` only backs `configByName` and the external-event cache, and `TransactionBoundCacheManager` clears caches when every transaction begins and ends.
- Repayments, waivers, and write-offs change the totals. Use fresh loans when asserting literal numbers.
