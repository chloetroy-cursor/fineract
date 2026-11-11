# LNFEECALC — business rules

End-of-day batch that assesses a late fee on one overdue loan installment.
Currency is always two decimal places. Amounts are non-negative.

## Inputs

| Field | Meaning |
|-------|---------|
| `LN-PRINCIPAL-OUTSTANDING` | Remaining principal on the installment, in major currency units |
| `LN-DAYS-OVERDUE` | Calendar days past the installment due date |
| `LN-PRODUCT-CODE` | Product key used to look up control parameters |
| `LN-CURRENCY-CODE` | ISO currency (informational; math is currency-agnostic) |

## Control parameters (per product)

| Field | Meaning | Demo defaults (`EVERYDAY`) |
|-------|---------|----------------------------|
| `CTL-GRACE-DAYS` | No fee while overdue days ≤ grace | `5` |
| `CTL-TIER1-MAX-DAYS` | End of tier 1 (days past grace) | `30` |
| `CTL-TIER2-MAX-DAYS` | End of tier 2 (days past grace) | `60` |
| `CTL-TIER1-RATE-BP` | Daily rate in basis points of principal (tier 1) | `5` (= 0.05%/day) |
| `CTL-TIER2-RATE-BP` | Daily rate in basis points (tier 2) | `8` |
| `CTL-TIER3-RATE-BP` | Daily rate in basis points (tier 3) | `10` |
| `CTL-CAP-PCT` | Max fee as percent of principal | `5` |
| `CTL-MIN-FEE` | Minimum fee once any fee applies | `1.00` |

Products shipped in the control card:

- `EVERYDAY` — defaults above (retail everyday banking)
- `CONSUMO` — grace `3`, tier rates `6/9/12`, cap `7%`, min `2.00`
- `HIPOTEC` — grace `15`, tier rates `2/3/4`, cap `2%`, min `5.00`

Unknown product codes → status `99`, fee `0`.

## Calculation

1. If principal is `0` → status `02`, fee `0`.
2. If days overdue ≤ grace → status `01`, fee `0`.
3. Let `billable = days overdue − grace`.
4. Split `billable` across tiers (days past grace):
   - Tier 1: days in `1 .. CTL-TIER1-MAX-DAYS`
   - Tier 2: days in `CTL-TIER1-MAX-DAYS+1 .. CTL-TIER2-MAX-DAYS`
   - Tier 3: days above `CTL-TIER2-MAX-DAYS`
5. For each tier: `tierFee = principal × rateBp / 10000 × tierDays`.
6. `rawFee = sum(tierFee)`.
7. `capped = min(rawFee, principal × CTL-CAP-PCT / 100)`.
8. Round half-up to 2 decimals.
9. If rounded fee `> 0` and `< CTL-MIN-FEE`, raise to `CTL-MIN-FEE`.
10. Status `00`, fee = result.

## Outputs

| Field | Meaning |
|-------|---------|
| `LN-LATE-FEE-AMT` | Assessed fee |
| `LN-BILLABLE-DAYS` | Days past grace used in the calc (`0` when no fee) |
| `LN-STATUS-CODE` | `00` ok · `01` in grace · `02` zero principal · `99` bad product |
| `LN-STATUS-MSG` | Short English message |

## Non-goals

- No interest accrual, no GL posting, no multi-currency FX.
- No compounding of the late fee itself.
- No holiday calendars (days are plain calendar days).
