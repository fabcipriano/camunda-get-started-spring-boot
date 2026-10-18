# PLAN: Verify "Scaling Camunda BPM in Cluster: Job Acquisition" (2015)

Source: https://camunda.com/blog/2015/09/scaling-camunda-bpm-in-cluster-job/

## Goal
Reproduce the article's experiment on our stack (Camunda 7.8, Spring Boot 1.5,
PostgreSQL 13, Docker) and check whether its conclusions still hold:
1. Acquiring larger chunks (maxJobsPerAcquisition 48 vs 3) improves throughput.
2. Exponential backoff reduces acquisition conflicts but gives only a slight
   throughput benefit.
3. With N nodes, the DB becomes the bottleneck (~100% CPU at 5 nodes in the article).

## Hypotheses / success criteria
- H1: maxJobs=48 finishes 50k jobs significantly faster than maxJobs=3.
- H2: With backoff, OptimisticLockingException count drops ≥50%, but total
  duration changes by <10%.
- H3: Throughput stops scaling beyond K nodes; DB CPU saturates.

## Test design (match the article)
- Workload: 50,000 instances of a NEW minimal process `benchmark.bpmn`:
  start → serviceTask(asyncBefore, sleeps random 0–160 ms) → end.
  (Don't use loanApproval: 6 async steps would muddy the comparison.)
- Load the jobs BEFORE starting the job executors (pre-fill, then measure drain time),
  so we measure job execution, not HTTP ingestion.
- history-level NONE (already set), clean DB between runs.
- Nodes: 1, 3, 6 (compose files exist).
- Each scenario run 3× and report the median.

## Scenario matrix
| ID | Nodes | maxJobsPerAcquisition | backoff (init/max/factor) |
|----|-------|-----------------------|---------------------------|
| S1 | 3     | 3                     | off                       |
| S2 | 3     | 48                    | off                       |
| S3 | 3     | 48                    | 50/250/2                  |
| S4 | 3     | 48                    | 70/350/2                  |
| S5 | 3     | 48                    | 90/450/2                  |
| S6 | 1,3,6 | best of above         | best of above             |

"Backoff off" = `JOB_EXECUTOR_BACKOFF_TIME_IN_MILLIS=0` and `JOB_EXECUTOR_MAX_BACKOFF=0`.

## Metrics to collect
- Total drain time (first job start → ACT_RU_JOB empty)
- Jobs/sec over time (Prometheus)
- Acquisition attempts, acquired jobs, failed locks
  (Camunda metrics: job-acquisition-attempt, job-acquired-success,
  job-acquired-failure, job-locked-exclusive, job-successful, job-failed)
- OptimisticLockingException count in logs
- Postgres CPU (docker stats / cadvisor) and active connections

## Tasks
### Phase 0 – Prep
- [x] Note: CustomJobExecutorConfig overrides the pool settings in application.yaml
      (90/105 threads). Decide on a single source of truth and make it env-configurable.
      → Single source of truth is now the `job-executor:` block in application.yaml,
        applied only by `CustomJobExecutorConfig`. The dead `camunda.bpm.job-execution`
        pool block was removed, and the hardcoded overrides in
        `CamundaPrometheusMetricsConfig` were removed (it only logs now).
- [x] Expose maxJobsPerAcquisition, waitTimeInMillis, backoffTimeInMillis,
      maxBackoff, waitIncreaseFactor via env vars (set on the JobExecutor bean).
      → All `JOB_EXECUTOR_*` env vars, see application.yaml. Defaults = previous
        effective values, so behaviour without env vars is unchanged.
- [x] Check connection pool size × nodes ≤ Postgres max_connections (950).
      → Found that the Hikari settings were never applied (see Decisions log).
        Now Hikari with `DB_POOL_MAX_SIZE` (default 110): 6 nodes × 110 = 660 ≤ 950.
- [x] Build passes (`mvn package`) and the app starts in Docker with the new config.

### Phase 1 – Benchmark process
- [x] Add benchmark.bpmn + BenchmarkDelegate (Thread.sleep(random 0..160)).
      → `benchmarkDelegate` Spring bean; max delay via `BENCHMARK_MAX_DELAY_MS` (default 160).
- [x] Add POST /benchmark/fill?count=50000 (bulk-create instances, executor paused).
      → `&batchSize=500` = instances per transaction. 5,000 instances ≈ 4.2 s locally.
- [x] Add POST /benchmark/start (activate job executor) or start nodes after filling.
      → Boot nodes with `JOB_EXECUTOR_START_ON_BOOT=false`, fill on one node,
        then POST /benchmark/start on every node. GET /benchmark/status returns
        remainingJobs, executor state and the active acquisition settings.
- [x] Disable the loanApproval instance started on every boot
      (`WebappExampleProcessApplication.processPostDeploy`) during benchmarks.
      → `START_SAMPLE_INSTANCE=false`.
- [x] Smoke test (1 node, local jar, maxJobs=48, no backoff): executor stayed idle
      after boot, 10,000 jobs drained in 10.3 s (~970 jobs/s), 0 failed acquisitions,
      0 rejected, 0 errors.

### Phase 2 – Measurement tooling
- [ ] Script `bench/run.sh <scenario>`: reset DB → up N nodes → fill → start →
      poll ACT_RU_JOB count → record duration → dump metrics to bench/results/<id>.csv
- [ ] Grafana panel: acquired vs failed acquisitions (like the article's blue/orange chart)

### Phase 3 – Execute
- [ ] S1 … S6 (3 runs each)

### Phase 4 – Analysis
- [ ] Results table + charts in bench/RESULTS.md
- [ ] Verdict for H1/H2/H3 and differences from the article

## Decisions log
- 2026-10-18: Use dedicated benchmark process instead of loanApproval (reason: isolate variable).
- 2026-10-18: The job executor was configured in three places that disagreed:
  `camunda.bpm.job-execution` in application.yaml (50/80 threads; ignored because
  the custom JobExecutor bean replaces the starter's default, and starter 2.3.0
  doesn't know keep-alive-seconds/queue-capacity at all), `CustomJobExecutorConfig`
  (90/105 threads, effective) and `CamundaPrometheusMetricsConfig.init()` (silently
  set maxJobsPerAcquisition=3, waitTime=1000, backoff 10/100/2 after startup).
  Consolidated into `job-executor:` in application.yaml.
- 2026-10-18: The app used the Tomcat JDBC pool (maxActive 100 by default), not Hikari:
  Spring Boot 1.5 prefers tomcat-jdbc when both are on the classpath, so all
  `spring.datasource.hikari.*` settings were ignored and the "hikari_*" metrics
  read the Tomcat pool. Set `spring.datasource.type` to Hikari explicitly and
  updated the metrics to read the Hikari pool MXBean.
- 2026-10-18: Default DB pool max = 110 (≥ 105 executor threads + acquisition/HTTP),
  so executor threads don't starve waiting for connections and skew results.
  Env vars use `${ENV:default}` placeholders rather than Boot 1.5 relaxed binding,
  which is unreliable for dashed names.
- 2026-10-18: Executor start is deferred with `DeferredStartJobExecutor` (ignores the
  starter's start() until /benchmark/start), not `camunda.bpm.job-execution.enabled=false`.
  With enabled=false the starter silently falls back to DefaultJobExecutor, so our
  JobExecutor bean and all JOB_EXECUTOR_* settings are lost (verified: maxJobs showed 3).
- 2026-10-18: The benchmark process has no job priority and no variables, so each instance
  is exactly one job-table row, like the article.

## Results
(filled in during Phase 3)

## Open questions
- Throughput ceiling per node is thread-bound: 90 threads / 80 ms average work ≈ 1,125 jobs/s,
  and the smoke test got ~970. To make acquisition (not thread count) the bottleneck as in the
  article, consider fewer threads per node (e.g. `JOB_EXECUTOR_CORE_POOL_SIZE`). Decide before Phase 3.
- Is ACT_RU_JOB polling accurate enough, or use the job-successful metric delta?
- Should the DB be CPU-limited (`cpus: 2`) to mimic the article's 2-core machine?
- Is `job-executor-acquire-by-priority: true` (currently on) part of the test?
  The article didn't use priorities; consider turning it off for S1–S6.
