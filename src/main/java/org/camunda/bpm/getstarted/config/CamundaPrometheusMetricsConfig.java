package org.camunda.bpm.getstarted.config;

import com.zaxxer.hikari.HikariDataSource;
import io.prometheus.client.Gauge;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.spring.components.jobexecutor.SpringJobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

@Configuration
public class CamundaPrometheusMetricsConfig {
    private static final Logger logger
            = LoggerFactory.getLogger(CamundaPrometheusMetricsConfig.class);

    private static final Gauge activeJobExecutoThreadsGauge = Gauge.build()
            .name("camunda_job_executor_active_threads")
            .help("Number of active threads found in process engine in the Camunda Job Executor thread pool")
            .register();

    private static final Gauge activeJobsGauge = Gauge.build()
            .name("camunda_active_jobs")
            .help("Number of active jobs in the Camunda Job Executor.")
            .register();

    private static final Gauge activeQueueSizeGauge = Gauge.build()
            .name("camunda_job_executor_number_of_tasks_in_queue")
            .help("Number of tasks in the job executor queue waiting to be executed in memory")
            .register();

    // HikariCP metrics
    private static final Gauge hikariActiveConnectionsGauge = Gauge.build()
            .name("hikari_active_connections")
            .help("Number of active connections in the HikariCP DataSource.")
            .register();

    private static final Gauge hikariIdleConnectionsGauge = Gauge.build()
            .name("hikari_idle_connections")
            .help("Number of idle connections in the HikariCP DataSource.")
            .register();

    private static final Gauge hikariTotalConnectionsGauge = Gauge.build()
            .name("hikari_total_connections")
            .help("Total number of connections in the HikariCP DataSource.")
            .register();

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;

    @Autowired
    private DataSource dataSource;

    private ManagementService managementService;
    private ThreadPoolTaskExecutor springTaskExecutor;

    @PostConstruct
    public void init() throws IOException {

        logger.info("Monitor Camunda active jobs");
        managementService = processEngine.getManagementService();

        logger.info("Got managementService ... processEngineConfiguration: {}", processEngineConfiguration);
        if (processEngineConfiguration instanceof ProcessEngineConfigurationImpl ) {
            JobExecutor jobExecutor = ((ProcessEngineConfigurationImpl) processEngineConfiguration).getJobExecutor();

            logger.info("-----> processEngineConfiguration.isJobExecutorAcquireByPriority: {}",
                    processEngineConfiguration.isJobExecutorAcquireByPriority());
            logger.info("-----> processEngineConfiguration.isJobExecutorAcquireByDueDate(): {}",
                    processEngineConfiguration.isJobExecutorAcquireByDueDate());
            logger.info("-----> processEngineConfiguration.isJobExecutorPreferTimerJobs(): {}",
                    processEngineConfiguration.isJobExecutorPreferTimerJobs());

            if (!processEngineConfiguration.isJobExecutorAcquireByPriority()) {
                processEngineConfiguration.setJobExecutorAcquireByPriority(true);
                logger.info("-----> SETTING TRUE JobExecutorAcquireByPriority: {}",
                        processEngineConfiguration.isJobExecutorAcquireByPriority());
            }

            logger.info("Verify if jobExecutor is ThreadPoolJobExecutor ... jobExecutor: {} ", jobExecutor);
            if ( jobExecutor instanceof SpringJobExecutor) {
                SpringJobExecutor jobThreadPoolExecutor = (SpringJobExecutor) jobExecutor;
                TaskExecutor taskExecutor = jobThreadPoolExecutor.getTaskExecutor();
                logger.info("Got SpringJobExecutor. taskExecutor: {}", taskExecutor);

                if (taskExecutor instanceof ThreadPoolTaskExecutor) {
                    springTaskExecutor = (ThreadPoolTaskExecutor)taskExecutor;
                    logger.info("Got ThreadPoolTaskExecutor. springTaskExecutor: {}", springTaskExecutor);

                    logger.info("-----> springTaskExecutor.getCorePoolSize(): {}", springTaskExecutor.getCorePoolSize());
                    logger.info("-----> springTaskExecutor.getPoolSize(): {}", springTaskExecutor.getPoolSize());
                    logger.info("-----> springTaskExecutor.getMaxPoolSize(): {}", springTaskExecutor.getMaxPoolSize());
                    logger.info("-----> springTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity(): {}",
                            springTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity());


                    logger.info("-----> jobThreadPoolExecutor.getBackoffTimeInMillis(): {}", jobThreadPoolExecutor.getBackoffTimeInMillis());
                    logger.info("-----> jobThreadPoolExecutor.getLockTimeInMillis(): {}", jobThreadPoolExecutor.getLockTimeInMillis());
                    logger.info("-----> jobThreadPoolExecutor.getMaxBackoff(): {}", jobThreadPoolExecutor.getMaxBackoff());
                    logger.info("-----> jobThreadPoolExecutor.getMaxWait(): {}", jobThreadPoolExecutor.getMaxWait());
                    logger.info("-----> jobThreadPoolExecutor.getMaxJobsPerAcquisition(): {}", jobThreadPoolExecutor.getMaxJobsPerAcquisition());
                }
            }
        }

        logger.info("PostConstruct success !!!");
    }

    @Scheduled(fixedRate = 5000)
    public void updateActiveJobsCount() {
        long activeJobsCount = managementService.createJobQuery().active().count();
        activeJobsGauge.set(activeJobsCount);
    }

    @Scheduled(fixedRate = 4000)
    public void updateActiveQueueSizeGauge() {
        int queueSize = springTaskExecutor.getThreadPoolExecutor().getQueue().size();

        activeQueueSizeGauge.set(queueSize);
    }

    @Scheduled(fixedRate = 2000)
    public void updateActiveThreadProcessEngine() {
        int activeCount = springTaskExecutor.getActiveCount();

        activeJobExecutoThreadsGauge.set(activeCount);

    }

    @Scheduled(fixedRate = 15000)
    public void updateHikariMetrics() {
        if (dataSource instanceof org.apache.tomcat.jdbc.pool.DataSource) {
            org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource = (org.apache.tomcat.jdbc.pool.DataSource)dataSource;
            hikariActiveConnectionsGauge.set(tomcatDataSource.getActive());
            hikariIdleConnectionsGauge.set(tomcatDataSource.getIdle());
            hikariTotalConnectionsGauge.set(tomcatDataSource.getMaxActive());

        } else {
            logger.warn("DataSource is not an instance of HikariDataSource");
        }
    }

}