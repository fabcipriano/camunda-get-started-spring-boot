package org.camunda.bpm.getstarted.config;

import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.spring.components.jobexecutor.SpringJobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CustomJobExecutorConfig {
    private static final Logger logger
            = LoggerFactory.getLogger(CustomJobExecutorConfig.class);


    @Bean
    public JobExecutor customJobExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();

        logger.info("Creating MY threadPoolTaskExecutor: {} ...", threadPoolTaskExecutor);

        threadPoolTaskExecutor.setQueueCapacity(66);
        threadPoolTaskExecutor.setCorePoolSize(55);
        threadPoolTaskExecutor.setMaxPoolSize(88);
        threadPoolTaskExecutor.setKeepAliveSeconds(121);
        logger.info("Created MY threadPoolTaskExecutor: {}", threadPoolTaskExecutor);

        SpringJobExecutor springJobExecutor = new SpringJobExecutor();

        threadPoolTaskExecutor.initialize();
        logger.info("threadPoolTaskExecutor initialized!");
        springJobExecutor.setTaskExecutor(threadPoolTaskExecutor);

        logger.info("Created springJobExecutor: {} ...", springJobExecutor);
        return springJobExecutor;
    }
}