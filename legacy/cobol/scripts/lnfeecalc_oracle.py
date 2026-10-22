#!/usr/bin/env python3
"""Oracle for LNFEECALC.

Implements legacy/cobol/SPEC.md exactly. Regenerates
legacy/cobol/fixtures/cases.jsonl. Characterization tests and the Java
port must match these numbers — do not invent a second set of rules.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

PRODUCTS = {
    "EVERYDAY": {
        "grace": 5,
        "t1_max": 30,
        "t2_max": 60,
        "t1_bp": 5,
        "t2_bp": 8,
        "t3_bp": 10,
        "cap_pct": 5,
        "min_fee": Decimal("1.00"),
    },
    "CONSUMO": {
        "grace": 3,
        "t1_max": 30,
        "t2_max": 60,
        "t1_bp": 6,
        "t2_bp": 9,
        "t3_bp": 12,
        "cap_pct": 7,
        "min_fee": Decimal("2.00"),
    },
    "HIPOTEC": {
        "grace": 15,
        "t1_max": 30,
        "t2_max": 60,
        "t1_bp": 2,
        "t2_bp": 3,
        "t3_bp": 4,
        "cap_pct": 2,
        "min_fee": Decimal("5.00"),
    },
}


@dataclass(frozen=True)
class Result:
    late_fee: Decimal
    billable_days: int
    status_code: str
    status_msg: str


def assess(principal: Decimal, days_overdue: int, product: str) -> Result:
    key = product.strip()
    ctl = PRODUCTS.get(key)
    if ctl is None:
        return Result(Decimal("0.00"), 0, "99", "UNKNOWN PRODUCT CODE")
    if principal == 0:
        return Result(Decimal("0.00"), 0, "02", "ZERO PRINCIPAL - NO FEE")
    if days_overdue <= ctl["grace"]:
        return Result(Decimal("0.00"), 0, "01", "WITHIN GRACE PERIOD")

    billable = days_overdue - ctl["grace"]
    remaining = billable
    t1 = min(remaining, ctl["t1_max"])
    remaining -= t1
    t2_span = ctl["t2_max"] - ctl["t1_max"]
    t2 = min(remaining, t2_span) if remaining > 0 else 0
    remaining -= t2
    t3 = remaining if remaining > 0 else 0

    raw = (
        principal * Decimal(ctl["t1_bp"]) / Decimal(10000) * t1
        + principal * Decimal(ctl["t2_bp"]) / Decimal(10000) * t2
        + principal * Decimal(ctl["t3_bp"]) / Decimal(10000) * t3
    )
    cap = principal * Decimal(ctl["cap_pct"]) / Decimal(100)
    capped = min(raw, cap)
    rounded = capped.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    if rounded > 0 and rounded < ctl["min_fee"]:
        rounded = ctl["min_fee"]
    return Result(rounded, billable, "00", "FEE ASSESSED")


# Cases chosen for a live demo: grace, each tier, cap, min-fee, zero
# principal, bad product, and one case per product.
CASES = [
    ("grace-boundary", Decimal("10000.00"), 5, "EVERYDAY", "USD"),
    ("grace-plus-one", Decimal("10000.00"), 6, "EVERYDAY", "USD"),
    ("tier1-only", Decimal("10000.00"), 20, "EVERYDAY", "USD"),
    ("tier1-end", Decimal("10000.00"), 35, "EVERYDAY", "USD"),
    ("tier2-mid", Decimal("10000.00"), 50, "EVERYDAY", "USD"),
    ("tier3-deep", Decimal("10000.00"), 100, "EVERYDAY", "USD"),
    ("cap-hit", Decimal("1000.00"), 200, "EVERYDAY", "USD"),
    ("min-fee", Decimal("50.00"), 6, "EVERYDAY", "USD"),
    ("zero-principal", Decimal("0.00"), 40, "EVERYDAY", "USD"),
    ("bad-product", Decimal("10000.00"), 40, "UNKNOWN", "USD"),
    ("consumo-tier2", Decimal("5000.00"), 45, "CONSUMO", "USD"),
    ("hipotec-grace", Decimal("200000.00"), 10, "HIPOTEC", "USD"),
    ("hipotec-tier1", Decimal("200000.00"), 25, "HIPOTEC", "USD"),
    ("half-up-round", Decimal("1234.56"), 12, "EVERYDAY", "USD"),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--write",
        action="store_true",
        help="Rewrite fixtures/cases.jsonl",
    )
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    out = root / "fixtures" / "cases.jsonl"
    lines: list[str] = []
    for case_id, principal, days, product, currency in CASES:
        result = assess(principal, days, product)
        record = {
            "id": case_id,
            "input": {
                "principalOutstanding": f"{principal:.2f}",
                "daysOverdue": days,
                "productCode": product,
                "currencyCode": currency,
            },
            "expected": {
                "lateFeeAmt": f"{result.late_fee:.2f}",
                "billableDays": result.billable_days,
                "statusCode": result.status_code,
                "statusMsg": result.status_msg,
            },
        }
        lines.append(json.dumps(record, separators=(",", ":")))
        print(
            f"{case_id:16} fee={result.late_fee:>10} "
            f"days={result.billable_days:>3} status={result.status_code}"
        )

    if args.write:
        out.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"wrote {out}")


if __name__ == "__main__":
    main()
