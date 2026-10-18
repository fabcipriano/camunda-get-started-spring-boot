package org.camunda.bpm.getstarted.benchmark;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates a call to an external service, like the article: sleeps a random 0..maxDelayMs.
 */
@Component("benchmarkDelegate")
public class BenchmarkDelegate implements JavaDelegate {

    @Value("${benchmark.max-delay-ms}")
    private int maxDelayMs;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Thread.sleep(ThreadLocalRandom.current().nextInt(maxDelayMs + 1));
    }
}
