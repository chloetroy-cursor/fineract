# Loan onboarding

A back-office user creates a client, applies for a loan on a product, approves it, and disburses it. Afterward the loan is active and its full balance is outstanding.

## Sub-features

- `onboard-client` creates an active client.
- `onboard-product` creates a flat-interest loan product.
- `onboard-apply` submits a loan application.
- `onboard-approve` approves the application.
- `onboard-disburse` disburses the principal.

## How to get to it (user POV)

- `POST clients`, `POST loanproducts`, `POST loans`, then `POST loans/<id>?command=approve` and `POST loans/<id>?command=disburse`.
- All five steps in one go: `fineract seed-loan`.

## Driving it with the fineract script

Preconditions:

- `fineract doctor` reports `UP`.

- **Onboard.** Run `fineract seed-loan`. It prints JSON with `loanId` and the path of the saved loan.
- **Check status.** Run `fineract api GET loans/<loanId>`. `status.value` is `Active`.
- **Check balance.** In the same response, `summary.principalOutstanding` is `12000.0`, `summary.interestOutstanding` is `1440.0`, and `summary.totalOutstanding` is `13440.0`.
- **Cross-check.** Run `fineract api GET "loans/<loanId>?associations=repaymentSchedule"`. The schedule has 12 installments. The sum of `totalOutstandingForPeriod` across them is `13440.0`.

## Gotchas

- Product `shortName` must be unique and at most 4 characters. `seed-loan` randomizes it, so run it as many times as you like.
- Dates before the client's activation date (01 January 2026) are rejected.
