#!/usr/bin/env python3
"""Seed many disbursed loans with the same terms as `fineract seed-loan`.

Each loan is 12,000 over 12 monthly repayments at 1% flat per month, disbursed 01 January 2026,
so N loans add exactly N x 12,000 principal and N x 1,440 interest to the office's exposure.
"""
import argparse
import base64
import json
import os
import random
import ssl
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE_URL = os.environ.get("FINERACT_URL", "https://localhost:8443/fineract-provider") + "/api/v1/"
AUTH = "Basic " + base64.b64encode(b"mifos:password").decode()
CTX = ssl._create_unverified_context()
DATES = {"dateFormat": "dd MMMM yyyy", "locale": "en"}
TERMS = {
    "principal": 12000, "numberOfRepayments": 12, "repaymentEvery": 1, "repaymentFrequencyType": 2,
    "interestRatePerPeriod": 1, "amortizationType": 1, "interestType": 1, "interestCalculationPeriodType": 1,
    "transactionProcessingStrategyCode": "mifos-standard-strategy",
}


def api(method, path, body=None):
    req = urllib.request.Request(BASE_URL + path, method=method, data=json.dumps(body).encode() if body else None)
    req.add_header("Content-Type", "application/json")
    req.add_header("Fineract-Platform-TenantId", "default")
    req.add_header("Authorization", AUTH)
    with urllib.request.urlopen(req, context=CTX, timeout=120) as resp:
        return json.loads(resp.read())


def create_product():
    suffix = random.randint(100, 999)
    return api("POST", "loanproducts", {
        **TERMS, "name": f"Demo Bulk {suffix}-{int(time.time())}", "shortName": f"B{suffix}", "currencyCode": "USD",
        "digitsAfterDecimal": 2, "inMultiplesOf": 0, "interestRateFrequencyType": 2, "accountingRule": 1,
        "daysInYearType": 1, "daysInMonthType": 1, "isInterestRecalculationEnabled": False, "locale": "en",
    })["resourceId"]


def create_client(office_id, index):
    return api("POST", "clients", {
        **DATES, "officeId": office_id, "legalFormId": 1, "firstname": "Bulk", "lastname": f"Client {index}",
        "active": True, "activationDate": "01 January 2026",
    })["clientId"]


def create_loan(client_id, product_id):
    loan_id = api("POST", "loans", {
        **TERMS, **DATES, "clientId": client_id, "productId": product_id, "loanTermFrequency": 12,
        "loanTermFrequencyType": 2, "expectedDisbursementDate": "01 January 2026",
        "submittedOnDate": "01 January 2026", "loanType": "individual",
    })["loanId"]
    api("POST", f"loans/{loan_id}?command=approve", {**DATES, "approvedOnDate": "01 January 2026"})
    api("POST", f"loans/{loan_id}?command=disburse",
        {**DATES, "actualDisbursementDate": "01 January 2026", "transactionAmount": 12000})
    return loan_id


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("count", type=int)
    parser.add_argument("--office-id", type=int, default=1)
    parser.add_argument("--loans-per-client", type=int, default=20)
    parser.add_argument("--workers", type=int, default=8)
    args = parser.parse_args()

    started = time.time()
    product_id = create_product()
    client_count = -(-args.count // args.loans_per_client)
    with ThreadPoolExecutor(args.workers) as pool:
        clients = list(pool.map(lambda i: create_client(args.office_id, i), range(client_count)))
        futures = [pool.submit(create_loan, clients[i % client_count], product_id) for i in range(args.count)]
        loan_ids, failures = [], 0
        for done, future in enumerate(as_completed(futures), 1):
            try:
                loan_ids.append(future.result())
            except Exception as error:  # keep seeding; report at the end
                failures += 1
                print(f"loan failed: {error}", file=sys.stderr)
            if done % 100 == 0:
                print(f"{done}/{args.count} loans", file=sys.stderr)

    print(json.dumps({
        "officeId": args.office_id, "productId": product_id, "clients": len(clients), "loans": len(loan_ids),
        "failures": failures, "minLoanId": min(loan_ids, default=None), "maxLoanId": max(loan_ids, default=None),
        "seconds": round(time.time() - started, 1),
    }))
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
