package org.camunda.bpm.getstarted.config;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Configuration
public class PrometheusMetricsConfig {
    private static final Logger logger
            = LoggerFactory.getLogger(PrometheusMetricsConfig.class);
    @Autowired
    private ProcessEngine processEngine;

    private static final Counter taskCounter = Counter.build()
            .name("camunda_task_executed_total")
            .help("Total number of tasks executed by Camunda.")
            .register();

    private static final Gauge activeJobsGauge = Gauge.build()
            .name("camunda_active_jobs")
            .help("Number of active jobs in the Camunda Job Executor.")
            .register();

    @PostConstruct
    public void init() throws IOException {
        logger.info("Start Prometheus HTTP server metrics ");
        HTTPServer server = new HTTPServer(8081);

        // Register JVM default metrics
        DefaultExports.initialize();

        logger.info("Monitor Camunda active jobs");
        ManagementService managementService = processEngine.getManagementService();

        logger.info("Simulate monitoring active jobs");
        new Thread(() -> {
            while (true) {
                try {
                    // Query the number of active jobs
                    long activeJobsCount = managementService.createJobQuery().active().count();
                    activeJobsGauge.set(activeJobsCount);
                    logger.info("=====> activeJobsCount: " + activeJobsCount);
                    Thread.sleep(5000); // Update every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    public static void incrementTaskCounter() {
        taskCounter.inc();
    }
}