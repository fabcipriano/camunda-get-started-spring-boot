package org.camunda.bpm.getstarted.loanapproval;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class NormalAudit implements JavaDelegate {
    protected Logger logger = LoggerFactory.getLogger(NormalAudit.class);
    private static final Random random = new Random();

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        String currentActivityName = delegateExecution.getCurrentActivityName();
        logger.info("Calling {} task...", currentActivityName);

        int i = random.nextInt(2001);
        logger.info("Random task {} with number {}", currentActivityName, i);
        Thread.sleep(100 + i);

        Object auditItem = delegateExecution.getVariable("auditItem");
        logger.info("Called {} task with audit Item: {} .", currentActivityName, auditItem);

    }
}
