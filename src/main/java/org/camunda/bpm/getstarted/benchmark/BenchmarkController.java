package org.camunda.bpm.getstarted.benchmark;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.getstarted.config.DeferredStartJobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Drives the job acquisition benchmark (see PLAN.md):
 * start every node with JOB_EXECUTOR_START_ON_BOOT=false, POST /benchmark/fill on one node,
 * then POST /benchmark/start on every node and poll GET /benchmark/status until remainingJobs is 0.
 */
@RestController
@RequestMapping("/benchmark")
public class BenchmarkController {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkController.class);

    static final String PROCESS_KEY = "benchmark";

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private ManagementService managementService;

    @Autowired
    private ProcessEngineConfigurationImpl processEngineConfiguration;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PostMapping("/fill")
    public Map<String, Object> fill(@RequestParam(defaultValue = "50000") int count,
                                    @RequestParam(defaultValue = "500") int batchSize) {
        long start = System.currentTimeMillis();
        int created = 0;
        while (created < count) {
            int batch = Math.min(batchSize, count - created);
            // One transaction per batch: much faster than one transaction per instance
            transactionTemplate.execute(status -> {
                for (int i = 0; i < batch; i++) {
                    runtimeService.startProcessInstanceByKey(PROCESS_KEY);
                }
                return null;
            });
            created += batch;
        }
        long durationMs = System.currentTimeMillis() - start;
        logger.info("Benchmark fill: created {} instances in {} ms", created, durationMs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("durationMs", durationMs);
        return result;
    }

    @PostMapping("/start")
    public Map<String, Object> startJobExecutor() {
        JobExecutor jobExecutor = processEngineConfiguration.getJobExecutor();
        if (!jobExecutor.isActive()) {
            if (jobExecutor instanceof DeferredStartJobExecutor) {
                ((DeferredStartJobExecutor) jobExecutor).release();
            } else {
                jobExecutor.start();
            }
            logger.info("Benchmark: job executor started");
        }
        return status();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        JobExecutor jobExecutor = processEngineConfiguration.getJobExecutor();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("node", jobExecutor.getLockOwner());
        result.put("jobExecutorActive", jobExecutor.isActive());
        result.put("remainingJobs", managementService.createJobQuery().processDefinitionKey(PROCESS_KEY).count());
        result.put("remainingInstances", runtimeService.createProcessInstanceQuery().processDefinitionKey(PROCESS_KEY).count());
        result.put("maxJobsPerAcquisition", jobExecutor.getMaxJobsPerAcquisition());
        result.put("backoffTimeInMillis", jobExecutor.getBackoffTimeInMillis());
        result.put("maxBackoff", jobExecutor.getMaxBackoff());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
}
