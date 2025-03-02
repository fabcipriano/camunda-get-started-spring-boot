package org.camunda.bpm.getstarted.config;

import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.ThreadPoolJobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

@Configuration
public class CamundaPrometheusMetricsConfig {
    private static final Logger logger
            = LoggerFactory.getLogger(CamundaPrometheusMetricsConfig.class);

    private static final Gauge activeThreadsGauge = Gauge.build()
            .name("camunda_job_executor_active_threads")
            .help("Number of active threads in the Camunda Job Executor thread pool")
            .register();

    private static final Gauge activeJobExecutoThreadsGauge = Gauge.build()
            .name("camunda_process_engine_job_executor_active_threads")
            .help("Number of active threads found in process engine in the Camunda Job Executor thread pool")
            .register();

    private static final Gauge activeJobsGauge = Gauge.build()
            .name("camunda_active_jobs")
            .help("Number of active jobs in the Camunda Job Executor.")
            .register();

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;

    private ManagementService managementService;
    private ThreadPoolJobExecutor jobThreadPoolExecutor;

    @PostConstruct
    public void init() throws IOException {
        logger.info("Start Prometheus HTTP server metrics ");
        HTTPServer server = new HTTPServer(8081);

        // Register JVM default metrics
        DefaultExports.initialize();

        logger.info("Monitor Camunda active jobs");
        managementService = processEngine.getManagementService();

        logger.info("Got managementService ... processEngineConfiguration: {}", processEngineConfiguration);
        if (processEngineConfiguration instanceof ProcessEngineConfigurationImpl ) {
            JobExecutor jobExecutor = ((ProcessEngineConfigurationImpl) processEngineConfiguration).getJobExecutor();

            logger.info("Verify if jobExecutor is ThreadPoolJobExecutor ... jobExecutor: {} ", jobExecutor);
            if ( jobExecutor instanceof ThreadPoolJobExecutor) {
                jobThreadPoolExecutor = (ThreadPoolJobExecutor) jobExecutor;
                logger.info("Got threadPoolJobExecutor.");
            }
        }

        logger.info("PostConstruct success !!!");
    }

    @Scheduled(fixedRate = 5000)
    public void updateActiveJobsCount() {
        long activeJobsCount = managementService.createJobQuery().active().count();
        activeJobsGauge.set(activeJobsCount);
        logger.info("=====> updateActiveJobsCount - activeJobsCount: {}", activeJobsCount);
    }

    @Scheduled(fixedRate = 4000)
    public void updateActiveThreadProcessEngine() {
        int activeThreads = jobThreadPoolExecutor.getThreadPoolExecutor().getActiveCount();

        activeJobExecutoThreadsGauge.set(activeThreads);

        logger.info("=====> updateActiveThreadProcessEngine - activeThreads: {}", activeThreads);
    }

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
        logger.info("=====> activeTHREADS: {}", activeThreads);
    }

}