# Repayment

A back-office user posts a repayment. It is allocated across the due installments, and the outstanding balance drops by the amount paid.

## Sub-features

- `repay-record` records the repayment as a loan transaction.
- `repay-allocate` allocates the payment to interest and principal on the due installment.
- `repay-balance` lowers total outstanding by the amount paid.

## How to get to it (user POV)

- `POST loans/<id>/transactions?command=repayment` with a date and an amount.

## Driving it with the fineract script

Preconditions:

- A fresh loan from `fineract seed-loan` (total outstanding `13440.0`).

- **Repay.** Run `fineract api POST "loans/<loanId>/transactions?command=repayment" '{"transactionDate":"01 February 2026","transactionAmount":1120,"dateFormat":"dd MMMM yyyy","locale":"en"}'`. The response has a `resourceId`.
- **Check summary.** Run `fineract api GET loans/<loanId>`. `summary.interestPaid` is `120.0`, `summary.principalPaid` is `1000.0`, and `summary.totalOutstanding` is `12320.0`.
- **Cross-check.** Run `fineract api GET "loans/<loanId>?associations=repaymentSchedule"`. Installment 1 is fully paid (`complete` is `true`). The sum of `totalOutstandingForPeriod` across installments is `12320.0`.

## Gotchas

- The default `mifos-standard-strategy` pays penalties, then fees, then interest, then principal on each installment.
- A repayment dated before disbursement is rejected.
