package org.camunda.bpm.getstarted.config;

import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.spring.components.jobexecutor.SpringJobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Single place where the job executor is configured. Values come from the
 * job-executor block in application.yaml (overridable by JOB_EXECUTOR_* env vars).
 */
@Configuration
public class CustomJobExecutorConfig {
    private static final Logger logger
            = LoggerFactory.getLogger(CustomJobExecutorConfig.class);

    @Value("${job-executor.core-pool-size}")
    private int corePoolSize;
    @Value("${job-executor.max-pool-size}")
    private int maxPoolSize;
    @Value("${job-executor.queue-capacity}")
    private int queueCapacity;
    @Value("${job-executor.keep-alive-seconds}")
    private int keepAliveSeconds;

    @Value("${job-executor.max-jobs-per-acquisition}")
    private int maxJobsPerAcquisition;
    @Value("${job-executor.wait-time-in-millis}")
    private int waitTimeInMillis;
    @Value("${job-executor.max-wait}")
    private long maxWait;
    @Value("${job-executor.wait-increase-factor}")
    private float waitIncreaseFactor;
    @Value("${job-executor.backoff-time-in-millis}")
    private int backoffTimeInMillis;
    @Value("${job-executor.max-backoff}")
    private long maxBackoff;
    @Value("${job-executor.backoff-decrease-threshold}")
    private int backoffDecreaseThreshold;
    @Value("${job-executor.lock-time-in-millis}")
    private int lockTimeInMillis;

    @Bean
    public JobExecutor customJobExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();

        threadPoolTaskExecutor.setQueueCapacity(queueCapacity);
        threadPoolTaskExecutor.setCorePoolSize(corePoolSize);
        threadPoolTaskExecutor.setMaxPoolSize(maxPoolSize);
        threadPoolTaskExecutor.setKeepAliveSeconds(keepAliveSeconds);
        threadPoolTaskExecutor.initialize();
        logger.info("Job executor thread pool: corePoolSize={}, maxPoolSize={}, queueCapacity={}, keepAliveSeconds={}",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);

        SpringJobExecutor springJobExecutor = new SpringJobExecutor();
        springJobExecutor.setTaskExecutor(threadPoolTaskExecutor);

        springJobExecutor.setMaxJobsPerAcquisition(maxJobsPerAcquisition);
        springJobExecutor.setWaitTimeInMillis(waitTimeInMillis);
        springJobExecutor.setMaxWait(maxWait);
        springJobExecutor.setWaitIncreaseFactor(waitIncreaseFactor);
        springJobExecutor.setBackoffTimeInMillis(backoffTimeInMillis);
        springJobExecutor.setMaxBackoff(maxBackoff);
        springJobExecutor.setBackoffDecreaseThreshold(backoffDecreaseThreshold);
        springJobExecutor.setLockTimeInMillis(lockTimeInMillis);
        logger.info("Job acquisition: maxJobsPerAcquisition={}, waitTimeInMillis={}, maxWait={}, waitIncreaseFactor={}, "
                        + "backoffTimeInMillis={}, maxBackoff={}, backoffDecreaseThreshold={}, lockTimeInMillis={}",
                maxJobsPerAcquisition, waitTimeInMillis, maxWait, waitIncreaseFactor,
                backoffTimeInMillis, maxBackoff, backoffDecreaseThreshold, lockTimeInMillis);

        return springJobExecutor;
    }
}
