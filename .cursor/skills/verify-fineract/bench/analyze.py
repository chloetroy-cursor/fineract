#!/usr/bin/env python3
"""Summarise overhead.sh results.

    analyze.py runs.jsonl [--jvm bench] [--csv runs.csv] [--adoc] [--summary]

For each workload it prints the median and spread of each variant, then the overhead of each After variant
against Before. Overhead is positive when After is worse: lower throughput, higher latency, more CPU.

A cycle is one server start of every variant. Runs in one JVM share its JIT and GC state, so the statistics
treat the start, not the run, as the unit:

* 95% interval: bootstrap over cycles. Cycles are drawn with replacement and the overhead is recomputed from
  the runs of the drawn cycles.
* Per cycle: the overhead inside one cycle, After's runs against Before's runs of the same cycle. "Cycles worse"
  counts the cycles in which After was the worse one.
* A/A: the same overhead computed between the odd and the even cycles of Before, where nothing changed.
* Verdict: a difference is outside the noise only when the interval excludes zero and the overhead is larger
  than the A/A difference.
"""
import argparse
import csv
import json
import random
import statistics
import sys

VARIANTS = ["before", "after", "after-json"]
METRICS = [
    # key, label, unit, digits, higher is better
    ("rps", "Throughput", "req/s", 1, True),
    ("p50", "p50", "ms", 2, False),
    ("p97_5", "p97.5", "ms", 2, False),
    ("p99", "p99", "ms", 2, False),
    ("cpu", "Server CPU", "ms/req", 2, False),
]
RESAMPLES = 10000


def load(path, jvm):
    rows = []
    for line in open(path):
        r = json.loads(line)
        if r["jvm"] != jvm:
            continue
        if r["problems"] or r["non2xx"] or r["errors"] or r["timeouts"]:
            sys.exit(f"run with failed requests: {line[:300]}")
        rows.append({
            "jvm": r["jvm"], "cycle": r["cycle"], "position": r["position"], "variant": r["variant"],
            "phase": r["phase"], "workload": r["workload"], "run": r["run"], "start": r["start"],
            "seconds": r["elapsedSeconds"], "responses2xx": r["responses2xx"], "non2xx": r["non2xx"],
            "errors": r["errors"], "timeouts": r["timeouts"],
            "rps": r["requestsPerSecond"], "p50": r["latencyMs"]["p50"], "p97_5": r["latencyMs"]["p97_5"],
            "p99": r["latencyMs"]["p99"], "cpu": round(r["serverCpuUsec"] / 1000 / r["responses2xx"], 4),
            "dbCpu": round(r["dbCpuUsec"] / 1000 / r["responses2xx"], 4), "logLines": r["serverLogLines"],
            "repaymentsSentToCheckedLoans": r["repaymentCheck"]["sent"],
            "repaymentsFoundOnCheckedLoans": r["repaymentCheck"]["found"],
        })
    return rows


def quartiles(values):
    q1, _, q3 = statistics.quantiles(values, n=4, method="inclusive")
    return q1, q3


def overhead(before, after, higher_is_better):
    b, a = statistics.median(before), statistics.median(after)
    return (b - a) / b * 100 if higher_is_better else (a - b) / b * 100


def bootstrap(cycles, variant, key, higher_is_better, rng):
    numbers = sorted(cycles)
    samples = []
    for _ in range(RESAMPLES):
        drawn = [rng.choice(numbers) for _ in numbers]
        before = [v for c in drawn for v in cycles[c]["before"][key]]
        after = [v for c in drawn for v in cycles[c][variant][key]]
        samples.append(overhead(before, after, higher_is_better))
    samples.sort()
    return samples[int(0.025 * RESAMPLES)], samples[int(0.975 * RESAMPLES) - 1]


def summarise(measured, workload, rng):
    runs = [r for r in measured if r["workload"] == workload]
    cycles = {}
    for r in runs:
        cell = cycles.setdefault(r["cycle"], {v: {m[0]: [] for m in METRICS} for v in VARIANTS})
        for key, *_ in METRICS:
            cell[r["variant"]][key].append(r[key])
    cycles = {c: cell for c, cell in cycles.items() if all(cell[v]["rps"] for v in VARIANTS)}
    runs = [r for r in runs if r["cycle"] in cycles]
    values = {v: {m[0]: [x for c in sorted(cycles) for x in cycles[c][v][m[0]]] for m in METRICS} for v in VARIANTS}

    levels, overheads = [], []
    for key, label, unit, digits, higher in METRICS:
        for variant in VARIANTS:
            v = values[variant][key]
            q1, q3 = quartiles(v)
            starts = [statistics.median(cycles[c][variant][key]) for c in sorted(cycles)]
            levels.append({
                "metric": f"{label} ({unit})", "variant": variant, "digits": digits, "median": statistics.median(v),
                "q1": q1, "q3": q3, "min": min(v), "max": max(v),
                "runSpread": (q3 - q1) / 2 / statistics.median(v) * 100,
                "startSpread": (max(starts) - min(starts)) / 2 / statistics.median(v) * 100,
            })
        before = values["before"][key]
        odd = [x for c in cycles if c % 2 == 1 for x in cycles[c]["before"][key]]
        even = [x for c in cycles if c % 2 == 0 for x in cycles[c]["before"][key]]
        aa = abs(overhead(odd, even, higher)) if odd and even else float("nan")
        for variant in VARIANTS[1:]:
            point = overhead(before, values[variant][key], higher)
            low, high = bootstrap(cycles, variant, key, higher, rng)
            per_start = [overhead(cycles[c]["before"][key], cycles[c][variant][key], higher) for c in sorted(cycles)]
            outside = (low > 0 or high < 0) and abs(point) > aa
            overheads.append({
                "metric": f"{label} ({unit})", "variant": variant, "overhead": point, "low": low, "high": high,
                "startMin": min(per_start), "startMax": max(per_start), "aa": aa,
                "worse": sum(1 for x in per_start if x > 0), "starts": len(per_start),
                "verdict": ("slower" if point > 0 else "faster") if outside else "inside the noise",
            })
    log_lines = {v: [r["logLines"] for r in runs if r["variant"] == v] for v in VARIANTS}
    return {
        "workload": workload, "starts": len(cycles), "runs": len(values["before"]["rps"]),
        "responses": {v: sum(r["responses2xx"] for r in runs if r["variant"] == v) for v in VARIANTS},
        "levels": levels, "overheads": overheads,
        "logLines": {v: (statistics.median(x), max(x)) for v, x in log_lines.items()},
    }


def level_cells(level):
    d = level["digits"]
    return [level["metric"], level["variant"], f"{level['median']:.{d}f}", f"{level['q1']:.{d}f} .. {level['q3']:.{d}f}",
            f"{level['min']:.{d}f} .. {level['max']:.{d}f}", f"±{level['runSpread']:.1f}%", f"±{level['startSpread']:.1f}%"]


def overhead_cells(o):
    return [o["metric"], o["variant"], f"{o['overhead']:+.1f}%", f"{o['low']:+.1f}% .. {o['high']:+.1f}%",
            f"{o['startMin']:+.1f}% .. {o['startMax']:+.1f}%", f"{o['worse']} of {o['starts']}", f"{o['aa']:.1f}%",
            o["verdict"]]


LEVEL_HEADER = ["Metric", "Variant", "Median", "Quartiles", "Min .. max", "Run spread", "Start spread"]
OVERHEAD_HEADER = ["Metric", "Variant", "Overhead", "95% interval", "Per cycle, min .. max", "Cycles worse", "A/A", "Verdict"]


def print_text(header, rows):
    widths = [max(len(str(row[i])) for row in [header] + rows) for i in range(len(header))]
    for row in [header] + rows:
        print("  " + "  ".join(str(cell).ljust(width) for cell, width in zip(row, widths)).rstrip())


def print_adoc(header, rows, cols):
    print(f'[cols="{cols}",options="header"]')
    print("|===")
    print("".join(f"|{cell} " for cell in header).rstrip())
    for row in rows:
        if any("\n" in cell for cell in row):
            print()
            print("\n".join(f"|{cell}" for cell in row))
        else:
            print("".join(f"|{cell} " for cell in row).rstrip())
    print("|===")


def print_summary(summaries):
    """One AsciiDoc table: a row for each workload and After variant, a cell for each metric."""
    names = {"read": "Loan read", "repay": "Repayment", "after": "After, JSON logs off", "after-json": "After, JSON logs on"}
    header = ["Workload", "Variant"] + [label for _, label, *_ in METRICS]
    rows = []
    for s in summaries:
        for variant in VARIANTS[1:]:
            cells = [names[s["workload"]], names[variant]]
            for o in s["overheads"]:
                if o["variant"] == variant:
                    mark = "" if o["verdict"] == "inside the noise" else f" *({o['verdict']})*"
                    cells.append(f"{o['overhead']:+.1f}% +\n{o['low']:+.1f} .. {o['high']:+.1f}{mark}")
            rows.append(cells)
        before = [level for level in s["levels"] if level["variant"] == "before"]
        aa = [o["aa"] for o in s["overheads"] if o["variant"] == "after"]
        rows.append([names[s["workload"]], "Noise: Before against itself"]
                    + [f"A/A {a:.1f}% +\nstarts ±{level['startSpread']:.1f}%" for a, level in zip(aa, before)])
    print_adoc(header, rows, "2,3,3,3,3,3,3")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("runs")
    parser.add_argument("--jvm", default="bench")
    parser.add_argument("--csv")
    parser.add_argument("--adoc", action="store_true")
    parser.add_argument("--summary", action="store_true")
    args = parser.parse_args()

    rows = load(args.runs, args.jvm)
    if args.csv:
        with open(args.csv, "w", newline="") as out:
            writer = csv.DictWriter(out, fieldnames=list(rows[0]), lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)

    rng = random.Random(20261112)
    measured = [r for r in rows if r["phase"] == "measure"]
    summaries = [summarise(measured, workload, rng) for workload in ("read", "repay")]
    if args.summary:
        print_summary(summaries)
        return
    for s in summaries:
        workload = s["workload"]
        responses = ", ".join(f"{v} {n}" for v, n in s["responses"].items())
        log_lines = ", ".join(f"{v} {median:.0f} (max {top})" for v, (median, top) in s["logLines"].items())
        print(f"\n{workload}, jvm={args.jvm}: {s['starts']} starts and {s['runs']} measured runs of each variant")
        print(f"2xx responses in measured runs, no other status: {responses}")
        print(f"server log lines during one measured run, median: {log_lines}\n")
        levels = [level_cells(level) for level in s["levels"]]
        overheads = [overhead_cells(o) for o in s["overheads"]]
        if args.adoc:
            print_adoc(LEVEL_HEADER, levels, "3,2,2,3,3,2,2")
            print()
            print_adoc(OVERHEAD_HEADER, overheads, "3,2,2,3,3,2,1,3")
        else:
            print_text(LEVEL_HEADER, levels)
            print()
            print_text(OVERHEAD_HEADER, overheads)


if __name__ == "__main__":
    main()
