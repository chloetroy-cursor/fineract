# Legacy COBOL estate (demo)

Synthetic core-banking batch that calculates **loan late fees** for overdue
installments. It exists so Cursor demos can show a COBOL → Java migration
against a real banking domain (Apache Fineract), not against a toy calculator.

This is **not** from Banco Guayaquil, Apache Fineract, or any customer. It is
hand-authored demo source that mirrors the shape of a typical mainframe fee
batch: copybooks, a CALC program, control cards, and golden fixtures.

## Layout

| Path | Role |
|------|------|
| `copybooks/LNFEEREC.cpy` | Loan fee work-area (input + output) |
| `copybooks/LNFEECTL.cpy` | Product control parameters (grace, tiers, cap) |
| `programs/LNFEECALC.cbl` | Late-fee calculator |
| `SPEC.md` | Plain-English rules the COBOL implements |
| `fixtures/cases.jsonl` | Golden input → expected output pairs |
| `scripts/lnfeecalc_oracle.py` | Regenerates fixtures from `SPEC.md` |

## Demo intent

Target landing zone after migration:

`fineract-loan` → `org.apache.fineract.portfolio.loanaccount.domain.legacy`

Use the Cursor skill `.cursor/skills/migrate-cobol-to-java/` to run the loop:

characterize from fixtures → port to Java → prove parity → open a PR.

## Compiling (optional)

GnuCOBOL can compile the program if you want a live binary. Fixtures do **not**
require it; the golden file is the source of truth for characterization.

```bash
# optional
cobc -x -I legacy/cobol/copybooks -o /tmp/lnfeecalc legacy/cobol/programs/LNFEECALC.cbl
```
