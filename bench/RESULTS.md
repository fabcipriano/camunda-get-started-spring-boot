# Results: job acquisition in a Camunda cluster

Verification of [Scaling Camunda BPM in Cluster: Job Acquisition](https://camunda.com/blog/2015/09/scaling-camunda-bpm-in-cluster-job/) (Camunda blog, 2015)
on Camunda 7.8 / Spring Boot 1.5 / PostgreSQL 13. Plan and hypotheses: [PLAN.md](../PLAN.md). Runs on 2026-10-18.

## Verdict

| | Article's claim | Our result | Verdict |
|---|---|---|---|
| **H1** | Acquiring larger chunks (48 vs 3 jobs) is much faster | 48 jobs per acquisition drained 50,000 jobs **4.7× faster** (19.6 s vs 92.9 s) | **Confirmed** |
| **H2** | Backoff reduces failed locks but gives only a slight benefit | Backoff cut failed locks by **62–71%**, but made the drain **66–88% slower** | **Half confirmed**: fewer conflicts, but throughput got worse, not slightly better |
| **H3** | Adding nodes stops helping; the DB saturates | 1→3 nodes: **2.2×** faster. 3→6 nodes: **no gain**. DB used at most 9 of 24 cores | **Partly confirmed**: scaling stops, but not because of DB CPU (see caveats) |
| **S7** | *(not in the article)* `FOR UPDATE SKIP LOCKED` + 48 per acquisition | **0 failed locks** (from 65–81%), 37–44% faster than backoff, +36% peak rate at 6 nodes, 5–7% faster end to end | **Recommended**: never slower than S2, removes contention |

The short version: **use large acquisition batches, don't turn on backoff unless the database is the
bottleneck, and on PostgreSQL add `SKIP LOCKED` acquisition (S7).** In this setup, backoff lets one node win most acquisitions while the others wait,
so a 3-node cluster performs like about one node.

**Follow-up: `FOR UPDATE SKIP LOCKED` (S7/S8).** A small opt-in plugin switches acquisition to
`SELECT … FOR UPDATE SKIP LOCKED` on PostgreSQL. It removed **all** failed locks (from 65–81%), cut acquisition cycles by
45–80%, beat every backoff setting by 37–44%, and lifted the 6-node peak rate by 36%. End to end it was only 5–7% faster
here, because the nodes' threads (3 nodes) and the shared host (6 nodes) became the limit. It complements large batches
rather than replacing them. See [S7/S8](#s7s8-for-update-skip-locked-acquisition-follow-up).

## Setup

| | Article (2015) | This run |
|---|---|---|
| Camunda | 7.4.0-alpha1 | 7.8.0 (Spring Boot starter 2.3.0) |
| Database | PostgreSQL 9.4 on 2 cores | PostgreSQL 13, no CPU limit (24-core host) |
| Nodes | 3 (and up to 5) | 3 for S1–S5; 1, 3 and 6 for S6 |
| Workload | 50,000 instances, one async job each, 0–160 ms of work | Same (`benchmark.bpmn`, `BenchmarkDelegate`) |
| Executor threads | not stated | 90 core / 105 max per node |
| Other | — | acquire-by-priority on, history NONE, 768 MB heap per node, all containers on one host (WSL2) |

Each run starts from an empty database. All 50,000 jobs are created first, then every node's
job executor is started at the same moment, and the time until the job table is empty is measured.
Each configuration ran 3 times, interleaved (S1 S2 S3 S4 S5, ×3) so drift on the machine doesn't favour one scenario.
Values below are medians of the 3 runs, with min–max where shown.

## S1–S5 and S7: batch size, backoff and SKIP LOCKED (3 nodes)

| Scenario | maxJobs | Backoff (init/max/factor) | Drain time, s | Jobs/s | Failed locks | Failed share | Acquisition cycles | DB CPU, cores (avg / max) |
|---|---|---|---|---|---|---|---|---|
| S1 | 3 | off | 92.9 (91.9–95.5) | 538 | 81,379 | 62% | 43,871 | 2.7 / 3.3 |
| **S2** | **48** | **off** | **19.6 (19.6–24.4)** | **2,546** | 90,976 | 65% | 3,390 | 5.0 / 6.8 |
| S3 | 48 | 50/250/2 | 36.8 (26.5–36.9) | 1,359 | 27,312 | 35% | 2,889 | 2.5 / 7.2 |
| S4 | 48 | 70/350/2 | 36.8 (34.6–39.4) | 1,359 | 26,688 | 35% | 3,024 | 2.5 / 7.4 |
| S5 | 48 | 90/450/2 | 32.6 (29.5–36.8) | 1,534 | 34,400 | 41% | 2,975 | 2.6 / 5.8 |
| **S7** | **48 + SKIP LOCKED** | **off** | **20.6 (18.4–21.1)** | **2,432** | **0** | **0%** | **1,891** | 6.2 / 7.9 |

S7 is the follow-up described [below](#s7s8-for-update-skip-locked-acquisition-follow-up): S2's settings plus
`FOR UPDATE SKIP LOCKED` acquisition. It was run after S1–S5, not interleaved with them.

![Throughput by scenario](results/charts/throughput.svg)

![Failed lock attempts by scenario](results/charts/failed-ratio.svg)

![Remaining jobs over time](results/charts/drain.svg)

*S3 and S4 overlap almost exactly in the chart above (both medians 36.8 s), and so do S2 and S7 (19.6 s and 20.6 s).*

### H1: larger batches

S2 acquires up to 48 jobs per cycle instead of 3, and needs **13× fewer acquisition cycles**
(3,390 vs 43,871) for the same 50,000 jobs. The number of failed locks hardly changes
(about 81k vs 91k), but each cycle now delivers 16× more work, so the drain is 4.7× faster. Database CPU
doubles because the database is doing more useful work per second, not because of extra conflicts.

### H2: backoff

Backoff works as designed at the lock level: failed locks drop from about 91k to 27–34k, and the failed
share from 65% to 35–41%. But every backoff scenario is **slower** than S2. The per-node numbers show why:

| Scenario (median run) | Jobs executed per node | Acquisition cycles per node |
|---|---|---|
| S2 (no backoff) | 20,096 / 14,016 / 15,888 | 1,238 / 1,025 / 1,072 |
| S3 | 38,064 / 8,192 / 3,744 | 2,400 / 343 / 146 |
| S4 | 38,096 / 7,152 / 4,752 | 2,503 / 272 / 148 |
| S5 | 15,248 / 33,408 / 1,344 | 699 / 2,186 / 49 |

With backoff, the node that loses a few acquisitions backs off, so it loses the next race too, and
ends up waiting at maximum backoff while one node does 67–82% of all the work. That node then
runs at its own limit: from about 15 s into each backoff run, the job table drains at a steady
**~1,090 jobs/s**, which is exactly one node's thread capacity (90 threads ÷ ~82 ms per job).
A single node with no contention at all (S6, 1 node) reached 1,059 jobs/s. The two backoff runs where the work was
spread more evenly were also the fastest ones (top node 53% → 26.5 s, 58% → 29.5 s).

Why the article saw a small gain instead: its database had only 2 cores and reached 100% CPU (with 5 nodes),
so anything that reduced database load helped. Here the database never used more than about 7 of 24 cores,
so saving database work bought nothing, while idling two of three nodes cost a lot of capacity.

## S6: node scaling (48 per acquisition, no backoff)

| Nodes | Drain time, s | Jobs/s | Speed-up vs 1 node | Failed locks | Failed share | DB CPU, cores (avg / max) | All containers, cores (avg / max) |
|---|---|---|---|---|---|---|---|
| 1 | 47.2 (46.1–49.2) | 1,059 | 1.0× | 0 | 0% | 1.8 / 2.0 | 2.6 / 3.9 |
| 3 | 21.6 (20.4–21.7) | 2,320 | 2.2× | 90,880 | 65% | 4.6 / 7.0 | 8.6 / 17.3 |
| 6 | 22.5 (19.0–24.8) | 2,218 | 2.1× | 207,952 | 81% | 7.0 / 9.0 | 14.6 / 22.9 |

![S6 throughput by node count](results/charts/scaling-throughput.svg)

![S6 failed lock attempts by node count](results/charts/scaling-failed-ratio.svg)

![S6 database CPU by node count](results/charts/scaling-db-cpu.svg)

### H3: where scaling stops

Going from 1 to 3 nodes gives 2.2×, not 3×. Going from 3 to 6 gives nothing: the 6-node runs
(19.0–24.8 s) overlap the 3-node runs (20.4–21.7 s). The work is spread evenly across the 6 nodes
(6,032–13,152 jobs each), so this isn't the backoff problem. Instead, **81% of all lock attempts fail**:
every node runs the same acquisition query for the first 48 acquirable jobs, so they largely ask for
the same rows, and most lock attempts fail. Six nodes do 5,423 acquisition cycles in total, 60% more than three nodes
(3,416), to finish the same 50,000 jobs.

Unlike the article, the database CPU is **not** saturated: Postgres averaged 7 cores and peaked at
9 of 24. The limit here is contention on the same job rows, not database capacity.

## S7/S8: `FOR UPDATE SKIP LOCKED` acquisition (follow-up)

Design notes, side-effect analysis and upgrade checklist: [docs/skip-locked-job-acquisition.md](../docs/skip-locked-job-acquisition.md).

### How Camunda 7.8 acquires jobs

Camunda 7.8 doesn't lock jobs pessimistically. Acquisition (`AcquireJobsCmd` + `selectNextJobsToExecute` in `Job.xml`) is optimistic:

1. `SELECT RES.* FROM ACT_RU_JOB RES WHERE <acquirable> ORDER BY … LIMIT 48` takes no row locks, so every node gets the same head of the queue.
2. Each job's lock owner and expiry are set in memory.
3. At commit, `UPDATE ACT_RU_JOB … WHERE ID_ = ? AND REV_ = ?` runs per job. Updates that hit 0 rows are optimistic-locking
   failures, and an `OptimisticLockingListener` drops those jobs from the batch. These are the "failed locks" above.

`SKIP LOCKED` takes a very short pessimistic lock to avoid those conflicts. Each acquisition locks the rows it selects,
and concurrent acquisitions on other nodes skip those rows and take the next free ones.

### Implementation (opt-in, no engine fork)

- `src/main/resources/mappings/SkipLockedJobAcquisition.xml`: a copy of `selectNextJobsToExecute` from 7.8.0 with
  `FOR UPDATE OF RES SKIP LOCKED` appended. PostgreSQL accepts it because the query has no joins, `DISTINCT` or aggregates.
- `SkipLockedJobAcquisitionPlugin`: a `ProcessEnginePlugin` that registers the mapper in MyBatis after the engine's
  SqlSessionFactory is built, then maps `selectNextJobsToExecute` to the new statement through
  `DbSqlSessionFactory`'s statement mappings. Camunda uses the same mechanism for its own `*_postgres` statements.
- Enabled with `JOB_EXECUTOR_SKIP_LOCKED=true`, and only on PostgreSQL (logs a warning and does nothing on other databases).

Why it's safe:
- Acquisition runs in its own transaction (`getCommandExecutorTxRequired()`), so the row locks last only for the SELECT
  plus the lock-owner UPDATEs, a few milliseconds.
- `SKIP LOCKED` never waits, so it can't block or deadlock job execution.
- Under PostgreSQL's default READ COMMITTED isolation, `FOR UPDATE` re-checks the WHERE clause on the latest row version.
  A job that another node locked and committed a moment earlier is skipped, and the LIMIT still fills from the next rows.
- Exclusive jobs of the same process instance can still be acquired by two nodes at once (the `NOT EXISTS` check sees
  a snapshot). This race exists in the original query too, and is still caught by optimistic locking when the jobs execute.

Verified in the runs below: **0 failed locks** in every multi-node run, and every job executed exactly once
(acquired = successful = 50,000).

### Results (48 per acquisition, no backoff; S6 = default optimistic, S7 = SKIP LOCKED)

| Nodes | Drain time, s: S6 → S7 | Peak drain rate, jobs/s: S6 → S7 | Acquisition cycles: S6 → S7 | Failed locks: S6 → S7 | DB CPU avg, cores: S6 → S7 |
|---|---|---|---|---|---|
| 1 | 47.2 → 48.0 (no change) | 1,108 → 1,109 | 4,140 → 3,109 | 0 → 0 | 1.8 → 2.0 |
| 3 | 21.6 → 20.6 (−5%) | 3,199 → 3,208 | 3,416 → 1,891 (−45%) | 90,880 → **0** | 4.6 → 6.2 |
| 6 | 22.5 → 20.9 (−7%) | 3,138 → **4,279 (+36%)** | 5,423 → 1,104 (−80%) | 207,952 → **0** | 7.0 → 6.3 |

![Drain time: optimistic vs SKIP LOCKED](results/charts/skip-locked-drain.svg)

![Peak drain rate: optimistic vs SKIP LOCKED](results/charts/skip-locked-peak.svg)

And SKIP LOCKED with Camunda's default batch size (S8, 3 nodes, 3 per acquisition):

| Scenario (3 nodes) | Jobs per acquisition | Acquisition | Drain time, s |
|---|---|---|---|
| S1 | 3 | optimistic | 92.9 |
| **S8** | 3 | **SKIP LOCKED** | **57.4 (56.9–58.6)** |
| S2 | 48 | optimistic | 19.6 |
| **S7** | 48 | **SKIP LOCKED** | **20.6 (18.4–21.1)** |

### Verdict on SKIP LOCKED

- **It removes contention completely:** 0 failed locks at 3 and 6 nodes (from 65% and 81%), and 45–80% fewer
  acquisition cycles. It costs nothing on a single node.
- **It's better than backoff:** at 3 nodes, S7 (20.6 s) is 37–44% faster than every backoff scenario (32.6–36.8 s), and the
  work is evenly spread (15.6k–18.5k jobs per node instead of one node doing 67–82%).
- **It doesn't replace large batches, it complements them.** With 3 per acquisition it's 38% faster than S1, but still
  2.8× slower than 48 per acquisition. Each node has one acquisition thread, and fetching 3 jobs per round trip caps it
  at about 300 jobs/s however cleanly the locks work.
- **The end-to-end gain here is small (5–7%)** because the bottleneck moves elsewhere:
  - **3 nodes:** both S6 and S7 peak at about 3,200 jobs/s, which is the 3 nodes' combined thread limit (3 × ~1,090).
    Removing contention can't go past that.
  - **6 nodes:** SKIP LOCKED lifts the peak rate by 36% (to about 4,300 jobs/s), where optimistic locking was stuck at the
    3-node level. But the shared 24-core host is saturated (all containers averaged 19 cores, peaked at 23), so 6 JVMs
    ramp up slowly, and 50,000 jobs run out before they reach full speed. Run-to-run spread is also tighter
    (18.2–21.0 s vs 19.0–24.8 s).
- **Database CPU was 35% higher at 3 nodes** (4.6 → 6.2 cores, average) but 10% lower at 6 nodes. Row locks add a write to
  each selected tuple, but failed optimistic UPDATEs go away. Neither came close to saturating the database here.

The benefit should grow where contention is the real limit: more nodes, nodes on separate machines, or more threads
per node. That's the next test to confirm it. S6 and S7 were run about 45 minutes apart on the same machine
(not interleaved), and the 3-node difference is within run-to-run noise.

## Caveats

- **Everything ran on one 24-core machine.** With 6 nodes, all containers together peaked at about
  23 of 24 cores (about 15 on average), so the host itself was close to its limit at the peaks. The 6-node result is
  partly a host limit, not only lock contention. Running the nodes on separate machines would separate the two.
- **Thread-bound nodes.** At 90 threads and about 82 ms per job, one node tops out at about 1,090 jobs/s. This
  makes losing a node to backoff very expensive, and is probably the biggest difference from the
  article's setup. Fewer threads per node, or a CPU-limited database (`DB_CPUS=2`), would move the
  bottleneck towards what the article measured. Both are one environment variable away in `bench/run.sh`.
- **Acquire-by-priority was on.** The article didn't use priorities. All benchmark jobs have priority 0,
  but the acquisition query still sorts by priority; the effect of that wasn't measured.
- **S2 had one slow run** (24.4 s vs 19.6 s twice) and S3 one fast run (26.5 s vs ~37 s twice).
  Medians are reported, so a single outlier doesn't move them, but 3 runs is a small sample.
- **Measurement resolution:** drain time is polled every second, and `docker stats` samples about
  every 1–2 s. With drains of 19–95 s, that's 1–5% uncertainty on the duration.

## Reproduce

```bash
bench/run-matrix.sh 3 S1 S2 S3 S4 S5                               # about 25 min
for n in 1 3 6; do NODES=$n SKIP_BUILD=1 bench/run.sh S6 1; done   # one round of S6
for n in 1 3 6; do NODES=$n SKIP_BUILD=1 bench/run.sh S7 1; done   # same with SKIP LOCKED
bench/run.sh S8 1                                                  # SKIP LOCKED, 3 per acquisition
python3 bench/analyze.py > bench/results/tables.md                 # tables + charts/*.svg
```

Raw data per run is in `bench/results/<scenario>-n<nodes>-r<run>-<timestamp>/`
(config, progress, per-node metrics, docker stats), with one row per run in `bench/results/summary.csv`.
While a run is in progress, the Grafana dashboard at http://localhost:13000/d/camunda-bench shows the
article's acquired vs failed chart live.
