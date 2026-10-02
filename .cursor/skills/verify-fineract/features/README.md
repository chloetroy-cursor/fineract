# Fineract verification map

Recipes for proving loan behavior through the REST API. Each feature file says how an API client reaches the feature, the exact calls, and the end state that proves it works.

## Baseline

- `fineract doctor` reports `UP` and a `server` commit that matches the checkout.
- Start each recipe from a fresh loan made with `fineract seed-loan`. Do not reuse a loan another recipe already changed.
- The seeded loan: principal 12,000, 12 monthly installments from 01 February 2026, 1% flat interest per month, 120 interest per installment, 1,440 interest in total.

## Proof rules

- Capture each request and its response.
- Confirm every money result two ways: the loan `summary` and the totals of the repayment schedule (`?associations=repaymentSchedule`). A mismatch is a defect, even if one view looks right.
- Compare against literal numbers derived from the loan terms.

## Features

- [Loan onboarding](./loan-onboarding.md): create a client, a product, and a loan, then approve and disburse it.
- [Interest waiver](./interest-waiver.md): waive interest on an active loan and see the balance drop.
- [Repayment](./repayment.md): post a repayment and see it allocated and the balance reduced.
