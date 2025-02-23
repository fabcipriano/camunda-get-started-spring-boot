package org.camunda.bpm.getstarted.config;

import io.prometheus.client.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

@Configuration
@EnableScheduling
public class CamundaActiveThreadsMetrics {
    private static final Logger logger
            = LoggerFactory.getLogger(CamundaActiveThreadsMetrics.class);
    // Define a Prometheus Gauge metric
    private static final Gauge activeThreadsGauge = Gauge.build()
            .name("camunda_job_executor_active_threads")
            .help("Number of active threads in the Camunda Job Executor thread pool")
            .register();

    //private final ThreadPoolJobExecutor jobExecutor;


    // Update the metric value periodically
    @Scheduled(fixedRate = 10000)
    public void updateActiveThreadsMetric() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        // Get all thread information without stack traces
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);

        // Initialize the counter for threads with the prefix "camundaTaskExecutor"
        int activeThreads = 0;

        // Iterate through all threads and count those with the specified prefix
        for (ThreadInfo threadInfo : threadInfos) {
            if (threadInfo != null
                    && threadInfo.getThreadName().startsWith("camundaTaskExecutor")
                    && threadInfo.getThreadState() != Thread.State.WAITING ) {
                activeThreads++;
            }
        }
        activeThreadsGauge.set(activeThreads);
        logger.info("=====> activeTHREADS: " + activeThreads);
    }
}