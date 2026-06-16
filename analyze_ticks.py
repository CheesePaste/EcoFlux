# -*- coding: utf-8 -*-
import csv
from collections import defaultdict
from pathlib import Path

LOG_DIR = Path(__file__).resolve().parent / "run" / "logs"
CSV_VIS_ON  = LOG_DIR / "ecoflux-ticks_visual.csv"
CSV_VIS_OFF = LOG_DIR / "ecoflux-ticks_withoutvisual.csv"
CSV_NEW     = LOG_DIR / "ecoflux-ticks.csv"
TOP_N = 25

# -- Helpers --
def ms(ns):
    return ns / 1_000_000.0

def short_label(label):
    parts = label.split(".")
    if label.startswith("vis.") and len(parts) >= 2:
        return "vis." + parts[1]
    if label.startswith("net.") and len(parts) >= 2:
        return "net." + parts[1]
    if len(parts) >= 2:
        return parts[0] + ".*"
    return label

def load_csv(path):
    label_stats = defaultdict(lambda: {"count": 0, "sum": 0, "max": 0, "max_row": None})
    tick_sums = defaultdict(lambda: {"total_ns": 0, "label_sums": defaultdict(int), "span_count": 0})
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if len(row) < 3: continue
            try:
                nanos = int(row[2])
            except ValueError:
                continue
            label = row[1]
            extra = row[3] if len(row) > 3 else ""
            s = label_stats[label]
            s["count"] += 1; s["sum"] += nanos
            if nanos > s["max"]: s["max"] = nanos; s["max_row"] = (row[0], label, nanos, extra)
            t = tick_sums[row[0]]
            t["total_ns"] += nanos; t["label_sums"][label] += nanos; t["span_count"] += 1
    grand = sum(s["sum"] for _, s in label_stats.items())
    return label_stats, tick_sums, grand

def print_section(title):
    print("\n" + "=" * 105)
    print(title)
    print("=" * 105)

def print_label_table(stats, grand, top_n=TOP_N):
    rows = sorted(stats.items(), key=lambda x: x[1]["max"], reverse=True)
    print(f"{'label':<30} {'count':>8} {'max_ms':>8} {'avg_ms':>8} {'sum_ms':>11} {'%total':>8}")
    print("-" * 105)
    for label, s in rows[:top_n]:
        pct = s["sum"] / grand * 100 if grand else 0
        print(f"{label:<30} {s['count']:>8} {ms(s['max']):>8.2f} {ms(s['sum']/s['count']):>8.2f} {ms(s['sum']):>11.1f} {pct:>7.1f}%")

def print_tick_dist(name, tick_sums):
    totals = sorted([t["total_ns"] for t in tick_sums.values()], reverse=True)
    if not totals: return
    n = len(totals); total = sum(totals)
    print(f"  {name}: ticks={n}  avg={ms(total/n):.1f}ms  P50={ms(totals[max(0,n//2)]):.1f}ms  P95={ms(totals[max(0,int(n*0.95))]):.1f}ms  worst={ms(totals[0]):.1f}ms")

# -- Load --
files = {}
for label, path in [("VIS=ON", CSV_VIS_ON), ("VIS=OFF", CSV_VIS_OFF), ("NEW", CSV_NEW)]:
    if path.exists():
        files[label] = load_csv(path)
        print(f"Loaded {label}: {path.name}  ({path.stat().st_size//1024//1024}MB)")

if not files:
    print("No CSV files found.")
    exit(1)

# -- 1. Side-by-side per-label comparison (grouped) --
print_section("GROUPED COMPARISON: VIS=ON  vs  VIS=OFF  vs  NEW (markDirty fix)")

all_keys = set()
group_sums = defaultdict(lambda: {"on": 0, "off": 0, "new": 0, "count_on": 0, "count_off": 0, "count_new": 0})
for name, (stats, _, _) in files.items():
    for label, s in stats.items():
        key = short_label(label)
        all_keys.add(key)
        if name == "VIS=ON":
            group_sums[key]["on"] += s["sum"]; group_sums[key]["count_on"] += s["count"]
        elif name == "VIS=OFF":
            group_sums[key]["off"] += s["sum"]; group_sums[key]["count_off"] += s["count"]
        elif name == "NEW":
            group_sums[key]["new"] += s["sum"]; group_sums[key]["count_new"] += s["count"]

sorted_groups = sorted(group_sums.items(), key=lambda x: x[1]["on"], reverse=True)
print(f"{'group':<30} {'ON_ms':>9} {'OFF_ms':>9} {'NEW_ms':>9} {'ON-OFF':>8} {'NEW-OFF':>8} {'ON cnt':>6} {'NEW cnt':>6}")
print("-" * 105)
for key, d in sorted_groups:
    if d["on"] == 0 and d["off"] == 0 and d["new"] == 0: continue
    def fmt(v): return f"{ms(v):.1f}" if v else "-"
    d1 = d["on"] - d["off"]; d2 = d["new"] - d["off"]
    print(f"{key:<30} {fmt(d['on']):>9} {fmt(d['off']):>9} {fmt(d['new']):>9} {fmt(d1):>8} {fmt(d2):>8} {d['count_on']:>6} {d['count_new']:>6}")

# -- 2. Key delta metrics --
print_section("DELTA SUMMARY (net effect of markDirty fix)")

if "VIS=ON" in files and "NEW" in files:
    gt_on = files["VIS=ON"][2]
    gt_off = files["VIS=OFF"][2] if "VIS=OFF" in files else 0
    gt_new = files["NEW"][2]
    tc_on = len(files["VIS=ON"][1])
    tc_off = len(files["VIS=OFF"][1]) if "VIS=OFF" in files else 0
    tc_new = len(files["NEW"][1])

    vis_sync_on = group_sums.get("vis.sync", {}).get("on", 0)
    vis_sync_new = group_sums.get("vis.sync", {}).get("new", 0)
    vis_world_on = group_sums.get("vis.world", {}).get("on", 0)
    vis_world_new = group_sums.get("vis.world", {}).get("new", 0)
    vis_runtime_on = group_sums.get("vis.runtime", {}).get("on", 0)
    vis_runtime_new = group_sums.get("vis.runtime", {}).get("new", 0)

    print(f"  {'Metric':<30} {'VIS=ON':>12} {'VIS=OFF':>12} {'NEW':>12}")
    print(f"  {'-'*30} {'-'*12} {'-'*12} {'-'*12}")

    on_avg = ms(gt_on / max(1, tc_on))
    off_avg = ms(gt_off / max(1, tc_off)) if "VIS=OFF" in files else 0
    new_avg = ms(gt_new / max(1, tc_new))

    print(f"  {'tick avg (ms)':<30} {on_avg:>12.2f} {off_avg:>12.2f} {new_avg:>12.2f}")
    off_total_str = f"{ms(gt_off):.1f}" if "VIS=OFF" in files else "-"
    print(f"  {'total (ms)':<30} {ms(gt_on):>12.1f} {off_total_str:>12} {ms(gt_new):>12.1f}")
    print(f"  {'vis.sync sum (ms)':<30} {ms(vis_sync_on):>12.1f} {'-':>12} {ms(vis_sync_new):>12.1f}")
    print(f"  {'vis.world sum (ms)':<30} {ms(vis_world_on):>12.1f} {'-':>12} {ms(vis_world_new):>12.1f}")
    print(f"  {'vis.runtime sum (ms)':<30} {ms(vis_runtime_on):>12.1f} {'-':>12} {ms(vis_runtime_new):>12.1f}")

# -- 3. Per-label detail for NEW --
if "NEW" in files:
    print_section("NEW (markDirty fix) -- per-label details")
    print_label_table(files["NEW"][0], files["NEW"][2])

    print_section("NEW -- tick cost distribution")
    print_tick_dist("NEW", files["NEW"][1])

# -- 4. Per-label detail for VIS=ON (if only 1 file present) --
if "NEW" not in files and "VIS=ON" in files:
    print_section("VIS=ON -- per-label details")
    print_label_table(files["VIS=ON"][0], files["VIS=ON"][2])

# -- 5. All three distributions --
print_section("TICK COST DISTRIBUTION: all three")
for name, (stats, tick_sums, grand) in files.items():
    print_tick_dist(name, tick_sums)

print("\nDone.")
