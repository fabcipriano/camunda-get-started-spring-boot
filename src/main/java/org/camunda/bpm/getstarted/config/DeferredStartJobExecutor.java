package org.camunda.bpm.getstarted.config;

import org.camunda.bpm.engine.spring.components.jobexecutor.SpringJobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SpringJobExecutor that can ignore the start() issued by the Camunda starter at boot,
 * so a benchmark can fill the job table first and then start all nodes together via {@link #release()}.
 */
public class DeferredStartJobExecutor extends SpringJobExecutor {
    private static final Logger logger
            = LoggerFactory.getLogger(DeferredStartJobExecutor.class);

    private volatile boolean released;

    public DeferredStartJobExecutor(boolean startOnBoot) {
        this.released = startOnBoot;
    }

    @Override
    public synchronized void start() {
        if (!released) {
            logger.info("Job executor start deferred until release() (job-executor.start-on-boot=false)");
            return;
        }
        super.start();
    }

    public synchronized void release() {
        released = true;
        start();
    }
}
