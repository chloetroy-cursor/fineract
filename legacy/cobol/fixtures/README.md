# Golden fixtures for LNFEECALC

Each line of `cases.jsonl` is one characterization case:

```json
{
  "id": "tier2-mid",
  "input": {
    "principalOutstanding": "10000.00",
    "daysOverdue": 50,
    "productCode": "EVERYDAY",
    "currencyCode": "USD"
  },
  "expected": {
    "lateFeeAmt": "270.00",
    "billableDays": 45,
    "statusCode": "00",
    "statusMsg": "FEE ASSESSED"
  }
}
```

These numbers are the source of truth for the migration. Regenerate only
via the oracle (never by hand):

```bash
python3 legacy/cobol/scripts/lnfeecalc_oracle.py --write
```

A Java port is done when a unit test loads this file and every case
passes against the new class.
