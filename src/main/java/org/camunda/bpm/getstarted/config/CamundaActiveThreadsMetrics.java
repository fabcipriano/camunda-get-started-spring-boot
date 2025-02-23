package org.camunda.bpm.getstarted.config;

import io.prometheus.client.Gauge;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.impl.jobexecutor.ThreadPoolJobExecutor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class CamundaActiveThreadsMetrics {

    // Define a Prometheus Gauge metric
    private static final Gauge activeThreadsGauge = Gauge.build()
            .name("camunda_job_executor_active_threads")
            .help("Number of active threads in the Camunda Job Executor thread pool")
            .register();

    //private final ThreadPoolJobExecutor jobExecutor;

    public CamundaActiveThreadsMetrics() {
        // Access the default ProcessEngine and its JobExecutor
        ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
//        this.jobExecutor = (ThreadPoolJobExecutor) processEngine.getProcessEngineConfiguration().getJobExecutor();
    }

    // Update the metric value periodically
    @Scheduled(fixedRate = 5000) // Update every 5 seconds
    public void updateActiveThreadsMetric() {
//        int activeThreads = jobExecutor.getThreadPoolExecutor().getActiveCount();
//        activeThreadsGauge.set(activeThreads);
    }
}