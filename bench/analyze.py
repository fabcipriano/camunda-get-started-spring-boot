#!/usr/bin/env python3
"""Summarise bench/results/summary.csv: prints Markdown tables and writes SVG charts
to bench/results/charts/. Standard library only.

    python3 bench/analyze.py > bench/results/tables.md
"""
import csv
import os
import statistics
from collections import defaultdict

BENCH_DIR = os.path.dirname(os.path.abspath(__file__))
RESULTS = os.path.join(BENCH_DIR, "results")
CHARTS = os.path.join(RESULTS, "charts")

# Categorical slots 1-5 (light, dark); fixed order, colour follows the scenario
SLOTS = [("#2a78d6", "#3987e5"), ("#eb6834", "#d95926"), ("#1baf7a", "#199e70"),
         ("#eda100", "#c98500"), ("#e87ba4", "#d55181")]

LIGHT = {"surface": "#fcfcfb", "ink": "#0b0b0b", "ink2": "#52514e", "muted": "#898781", "grid": "#e1e0d9", "axis": "#c3c2b7"}
DARK = {"surface": "#1a1a19", "ink": "#ffffff", "ink2": "#c3c2b7", "muted": "#898781", "grid": "#2c2c2a", "axis": "#383835"}


def theme_rules(t, slot):
    rules = [f".bg{{fill:{t['surface']}}}", f"text{{fill:{t['ink2']}}}", f".title,.val{{fill:{t['ink']}}}",
             f".tick{{fill:{t['muted']}}}", f".grid{{stroke:{t['grid']}}}", f".axis{{stroke:{t['axis']}}}",
             f".whisker{{stroke:{t['ink2']}}}", f".ring{{stroke:{t['surface']}}}"]
    rules += [f".f{i + 1}{{fill:{c[slot]}}} .k{i + 1}{{stroke:{c[slot]}}}" for i, c in enumerate(SLOTS)]
    return "\n  ".join(rules)


def slot_vars():
    # Plain classes + hex (no CSS variables): renders the same in browsers, GitHub and rasterizers
    return f"""<style>
  text {{ font-family: system-ui, -apple-system, "Segoe UI", sans-serif; font-size: 12px; }}
  .title {{ font-size: 15px; font-weight: 600; }}
  .tick {{ font-size: 11px; font-variant-numeric: tabular-nums; }}
  .val {{ font-weight: 600; }}
  .grid, .axis {{ stroke-width: 1; }}
  .whisker {{ stroke-width: 1.5; }}
  {theme_rules(LIGHT, 0)}
  @media (prefers-color-scheme: dark) {{
  {theme_rules(DARK, 1)}
  }}
</style>"""


def nice_max(v):
    """Smallest 'round' number >= v, so the axis ends just above the data."""
    for mag in range(-2, 9):
        for step in (1, 1.5, 2, 2.5, 3, 4, 5, 6, 8):
            top = step * 10 ** mag
            if top >= v:
                return top
    return v


def ticks(top, n=None):
    """Tick values 0..top; picks an interval count that lands every tick on a round value."""
    if n is None:
        def nice(step):
            while step >= 10:
                step /= 10
            while step < 1:
                step *= 10
            return any(abs(step - c) < 1e-9 for c in (1, 2, 2.5, 5))
        n = next((k for k in (5, 4, 3, 6, 8) if nice(top / k)), 4)
    return [top * i / n for i in range(n + 1)]


def fmt(v):
    if v >= 10000:
        return f"{v / 1000:.0f}k"
    if v >= 1000:
        return f"{v / 1000:.1f}k".replace(".0k", "k")
    if v != int(v):
        return f"{v:.2f}".rstrip("0").rstrip(".")
    return f"{int(v)}"


def svg_open(w, h, title, sub):
    return [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img" aria-label="{title}">',
            slot_vars(),
            f'<rect class="bg" width="{w}" height="{h}" rx="8"/>',
            f'<text class="title" x="20" y="28">{title}</text>',
            f'<text class="sub" x="20" y="47">{sub}</text>']


def column_chart(path, title, sub, cats, med, lo, hi, value_fmt, color_slot=1, tick_fmt=fmt):
    """Single-series columns (median) with min-max whiskers across runs."""
    w, h = 640, 340
    left, right, top, bottom = 56, 20, 72, 44
    pw, ph = w - left - right, h - top - bottom
    ymax = nice_max(max(hi) * 1.08)
    y = lambda v: top + ph - v / ymax * ph
    out = svg_open(w, h, title, sub)
    for t in ticks(ymax):
        out.append(f'<line class="grid" x1="{left}" x2="{w - right}" y1="{y(t):.1f}" y2="{y(t):.1f}"/>')
        out.append(f'<text class="tick" x="{left - 8}" y="{y(t) + 4:.1f}" text-anchor="end">{tick_fmt(t)}</text>')
    band = pw / len(cats)
    bw = min(24, band * 0.5)
    for i, c in enumerate(cats):
        cx = left + band * (i + 0.5)
        x0, yt, yb = cx - bw / 2, y(med[i]), y(0)
        r = min(4, (yb - yt) / 2)
        # 4px rounded data end, square at the baseline
        out.append(f'<path d="M{x0:.1f},{yb:.1f} V{yt + r:.1f} Q{x0:.1f},{yt:.1f} {x0 + r:.1f},{yt:.1f} '
                   f'H{x0 + bw - r:.1f} Q{x0 + bw:.1f},{yt:.1f} {x0 + bw:.1f},{yt + r:.1f} V{yb:.1f} Z" '
                   f'class="f{color_slot}"><title>{c}: {value_fmt(med[i])} (runs {value_fmt(lo[i])}–{value_fmt(hi[i])})</title></path>')
        if hi[i] > lo[i]:
            out.append(f'<line class="whisker" x1="{cx:.1f}" x2="{cx:.1f}" y1="{y(lo[i]):.1f}" y2="{y(hi[i]):.1f}"/>')
            for v in (lo[i], hi[i]):
                out.append(f'<line class="whisker" x1="{cx - 5:.1f}" x2="{cx + 5:.1f}" y1="{y(v):.1f}" y2="{y(v):.1f}"/>')
        out.append(f'<text class="val" x="{cx:.1f}" y="{min(yt, y(hi[i])) - 8:.1f}" text-anchor="middle">{value_fmt(med[i])}</text>')
        out.append(f'<text x="{cx:.1f}" y="{h - bottom + 20}" text-anchor="middle">{c}</text>')
    out.append(f'<line class="axis" x1="{left}" x2="{w - right}" y1="{y(0):.1f}" y2="{y(0):.1f}"/>')
    out.append("</svg>")
    open(path, "w").write("\n".join(out))


def line_chart(path, title, sub, series, xlabel):
    """series: list of (name, [(x, y), ...]) in slot order. Legend always shown."""
    w, h = 640, 360
    left, right, top, bottom = 56, 20, 96, 44
    pw, ph = w - left - right, h - top - bottom
    xmax = nice_max(max(x for _, pts in series for x, _ in pts))
    ymax = nice_max(max(v for _, pts in series for _, v in pts))
    X = lambda v: left + v / xmax * pw
    Y = lambda v: top + ph - v / ymax * ph
    out = svg_open(w, h, title, sub)
    # Legend row under the subtitle
    lx = 20
    for i, (name, _) in enumerate(series):
        out.append(f'<line x1="{lx}" x2="{lx + 16}" y1="68" y2="68" class="k{i + 1}" stroke-width="2" stroke-linecap="round"/>')
        out.append(f'<text x="{lx + 22}" y="72">{name}</text>')
        lx += 22 + 7.2 * len(name) + 18
    for t in ticks(ymax):
        out.append(f'<line class="grid" x1="{left}" x2="{w - right}" y1="{Y(t):.1f}" y2="{Y(t):.1f}"/>')
        out.append(f'<text class="tick" x="{left - 8}" y="{Y(t) + 4:.1f}" text-anchor="end">{fmt(t)}</text>')
    for t in ticks(xmax):
        out.append(f'<text class="tick" x="{X(t):.1f}" y="{h - bottom + 18}" text-anchor="middle">{fmt(t)}</text>')
    out.append(f'<text class="tick" x="{w - right}" y="{h - 8}" text-anchor="end">{xlabel}</text>')
    out.append(f'<line class="axis" x1="{left}" x2="{w - right}" y1="{Y(0):.1f}" y2="{Y(0):.1f}"/>')
    for i, (name, pts) in enumerate(series):
        d = " ".join(f"{'M' if j == 0 else 'L'}{X(x):.1f},{Y(v):.1f}" for j, (x, v) in enumerate(pts))
        out.append(f'<path d="{d}" fill="none" class="k{i + 1}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"><title>{name}</title></path>')
        ex, ey = pts[-1]
        out.append(f'<circle cx="{X(ex):.1f}" cy="{Y(ey):.1f}" r="4" class="f{i + 1} ring" stroke-width="2"><title>{name}: done at {ex:.1f} s</title></circle>')
    out.append("</svg>")
    open(path, "w").write("\n".join(out))


def load():
    rows = list(csv.DictReader(open(os.path.join(RESULTS, "summary.csv"))))
    groups = defaultdict(list)
    for r in rows:
        groups[(r["scenario"], int(r["nodes"]))].append(r)
    return groups


def stats(rs, key, conv=float):
    vals = [conv(r[key]) for r in rs]
    return statistics.median(vals), min(vals), max(vals)


def median_run(rs):
    return sorted(rs, key=lambda r: int(r["drain_ms"]))[len(rs) // 2]


def progress(run):
    path = os.path.join(RESULTS, run["results_dir"], "progress.csv")
    return [(int(r["elapsed_ms"]) / 1000, int(r["remaining_jobs"])) for r in csv.DictReader(open(path))]


def table(groups, keys):
    print("| Scenario | Nodes | maxJobs | Backoff | Runs | Drain s (median, min–max) | Jobs/s | Failed locks | Failed ratio | Acq. cycles | DB CPU avg / max (cores) |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for k in keys:
        rs = groups[k]
        d, dlo, dhi = stats(rs, "drain_ms")
        jps = stats(rs, "jobs_per_s")[0]
        fl = stats(rs, "acquired_failed")[0]
        fr = stats(rs, "failed_ratio")[0]
        at = stats(rs, "acquisition_attempts")[0]
        ca = stats(rs, "db_cpu_avg_pct")[0] / 100
        cm = stats(rs, "db_cpu_max_pct")[0] / 100
        r0 = rs[0]
        print(f"| {k[0]} | {k[1]} | {r0['max_jobs']} | {r0['backoff']} | {len(rs)} | {d / 1000:.1f} ({dlo / 1000:.1f}–{dhi / 1000:.1f}) "
              f"| {jps:.0f} | {fl:,.0f} | {fr:.0%} | {at:,.0f} | {ca:.1f} / {cm:.1f} |")
    print()


def per_node_table(groups, keys):
    """Work split across nodes in the median run (shows if one node starves the others)."""
    print("| Scenario | Nodes | Jobs executed per node (median run) | Acquisition cycles per node |")
    print("|---|---|---|---|")
    for k in keys:
        run = median_run(groups[k])
        nodes = list(csv.DictReader(open(os.path.join(RESULTS, run["results_dir"], "nodes.csv"))))
        print(f"| {k[0]} | {k[1]} | {' / '.join(f'{int(n['successful']):,}' for n in nodes)} "
              f"| {' / '.join(f'{int(n['acquisition_attempts']):,}' for n in nodes)} |")
    print()


def main():
    os.makedirs(CHARTS, exist_ok=True)
    groups = load()
    matrix = sorted(k for k in groups if k[0] != "S6")
    scaling = sorted(k for k in groups if k[0] == "S6")

    if matrix:
        cats = [k[0] for k in matrix]
        jps = [stats(groups[k], "jobs_per_s") for k in matrix]
        column_chart(os.path.join(CHARTS, "throughput.svg"), "Throughput by scenario (3 nodes)",
                     "Jobs per second to drain 50,000 jobs · median of runs, whiskers = min–max",
                     cats, [j[0] for j in jps], [j[1] for j in jps], [j[2] for j in jps], lambda v: f"{v:.0f}")
        fr = [stats(groups[k], "failed_ratio") for k in matrix]
        column_chart(os.path.join(CHARTS, "failed-ratio.svg"), "Failed lock attempts by scenario (3 nodes)",
                     "Share of lock attempts lost to another node · median of runs, whiskers = min–max",
                     cats, [f[0] * 100 for f in fr], [f[1] * 100 for f in fr], [f[2] * 100 for f in fr],
                     lambda v: f"{v:.0f}%", tick_fmt=lambda v: f"{v:.0f}%")
        line_chart(os.path.join(CHARTS, "drain.svg"), "Remaining jobs over time (3 nodes)",
                   "Median run per scenario · lower and further left is faster",
                   [(f"{k[0]}", progress(median_run(groups[k]))) for k in matrix], "seconds since executors started")
        print("### S1–S5 (3 nodes)\n")
        table(groups, matrix)
        per_node_table(groups, matrix)

    if scaling:
        cats = [f"{k[1]} node{'s' if k[1] > 1 else ''}" for k in scaling]
        jps = [stats(groups[k], "jobs_per_s") for k in scaling]
        column_chart(os.path.join(CHARTS, "scaling-throughput.svg"), "S6 throughput by node count",
                     "Jobs per second to drain 50,000 jobs · median of runs, whiskers = min–max",
                     cats, [j[0] for j in jps], [j[1] for j in jps], [j[2] for j in jps], lambda v: f"{v:.0f}")
        fr = [stats(groups[k], "failed_ratio") for k in scaling]
        column_chart(os.path.join(CHARTS, "scaling-failed-ratio.svg"), "S6 failed lock attempts by node count",
                     "Share of lock attempts lost to another node · median of runs, whiskers = min–max",
                     cats, [f[0] * 100 for f in fr], [f[1] * 100 for f in fr], [f[2] * 100 for f in fr],
                     lambda v: f"{v:.0f}%", tick_fmt=lambda v: f"{v:.0f}%")
        cpu = [stats(groups[k], "db_cpu_avg_pct") for k in scaling]
        column_chart(os.path.join(CHARTS, "scaling-db-cpu.svg"), "S6 database CPU by node count",
                     "Average Postgres CPU while draining, in cores · median of runs, whiskers = min–max",
                     cats, [c[0] / 100 for c in cpu], [c[1] / 100 for c in cpu], [c[2] / 100 for c in cpu],
                     lambda v: f"{v:.1f}")
        print("### S6 node scaling\n")
        table(groups, scaling)
        per_node_table(groups, scaling)


if __name__ == "__main__":
    main()
