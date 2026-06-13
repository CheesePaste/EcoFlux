import csv
import sys
from collections import defaultdict

CSV = r"D:\Projects\MCMOD\Succession\run\logs\ecoflux-ticks.csv"

# Single pass: aggregate per-label stats + track worst per label
label_stats = defaultdict(lambda: {"count": 0, "sum": 0, "max": 0, "max_row": None})

with open(CSV, newline="", encoding="utf-8") as f:
    reader = csv.reader(f)
    header = next(reader)  # skip header
    for row in reader:
        if len(row) < 3:
            continue
        game_time, label, nanos_str = row[0], row[1], row[2]
        try:
            nanos = int(nanos_str)
        except ValueError:
            continue
        extra = row[3] if len(row) > 3 else ""

        s = label_stats[label]
        s["count"] += 1
        s["sum"] += nanos
        if nanos > s["max"]:
            s["max"] = nanos
            s["max_row"] = (game_time, label, nanos, extra)

# Group by prefix (e.g. "pipeline.prune" → "pipeline")
def short_label(label):
    parts = label.split(".")
    if len(parts) >= 2:
        return parts[0] + ".*"
    return label

# Sort by max desc, print top 50
all_entries = sorted(label_stats.items(), key=lambda x: x[1]["max"], reverse=True)

print("=" * 90)
print("TOP 50 WORST SINGLE-SAMPLE BY LABEL (max nanos)")
print("=" * 90)
print(f"{'label':<35} {'count':>8} {'max_ms':>8} {'avg_ms':>8} {'sum_ms':>10}")
print("-" * 90)

for label, s in all_entries[:50]:
    avg_ns = s["sum"] / s["count"]
    print(f"{label:<35} {s['count']:>8} {s['max']/1e6:>8.2f} {avg_ns/1e6:>8.2f} {s['sum']/1e6:>10.1f}")

# Print the 20 absolute worst single samples
print()
print("=" * 90)
print("TOP 20 ABSOLUTE WORST SINGLE SAMPLES")
print("=" * 90)
all_samples = []
for label, s in label_stats.items():
    if s["max_row"]:
        all_samples.append(s["max_row"])
all_samples.sort(key=lambda x: int(x[2]), reverse=True)
for i, (gt, label, nanos, extra) in enumerate(all_samples[:20]):
    print(f"{i+1}. gameTime={gt}  label={label}  {int(nanos)/1e6:.2f}ms  extra={extra}")

# Print worst per group
print()
print("=" * 90)
print("WORST SAMPLE PER GROUP")
print("=" * 90)
groups = defaultdict(list)
for label, s in label_stats.items():
    groups[short_label(label)].append((s["max"], s["max_row"]))
for g in sorted(groups.keys()):
    ent = max(groups[g], key=lambda x: x[0])
    print(f"  {g}: {ent[0]/1e6:.2f}ms  (count={label_stats[ent[1][1]]['count']})")
