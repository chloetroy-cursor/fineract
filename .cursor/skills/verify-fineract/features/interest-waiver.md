# Interest waiver

A back-office user waives part of a client's interest. The waived amount is recorded, and both the interest outstanding and the total outstanding drop by exactly that amount.

## Sub-features

- `waive-record` records the waiver as a loan transaction.
- `waive-summary` lowers interest and total outstanding in the loan summary.
- `waive-schedule` lowers interest outstanding on the affected installment.

## How to get to it (user POV)

- `POST loans/<id>/transactions?command=waiveinterest` with a date and an amount.

## Driving it with the fineract script

Preconditions:

- A fresh loan from `fineract seed-loan` (total outstanding `13440.0`).

- **Waive.** Run `fineract api POST "loans/<loanId>/transactions?command=waiveinterest" '{"transactionDate":"01 February 2026","transactionAmount":120,"dateFormat":"dd MMMM yyyy","locale":"en"}'`. The response has a `resourceId` for the new transaction.
- **Check summary.** Run `fineract api GET loans/<loanId>`. `summary.interestWaived` is `120.0`, `summary.interestOutstanding` is `1320.0`, and `summary.totalOutstanding` is `13320.0`.
- **Cross-check.** Run `fineract api GET "loans/<loanId>?associations=repaymentSchedule"`. Installment 1 shows `interestWaived` `120.0` and `interestOutstanding` `0.0`. The sum of `totalOutstandingForPeriod` across installments is `13320.0`, the same as the summary.

## Gotchas

- The waiver date must be on or after disbursement (01 January 2026) and not in the future.
- `interestWaived` alone is not proof. The outstanding totals must also drop.
