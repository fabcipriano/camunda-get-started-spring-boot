package org.camunda.bpm.getstarted.loanapproval;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DepositTask implements JavaDelegate {
    private static final Logger logger
            = LoggerFactory.getLogger(DepositTask.class);
    private static final Random random = new Random();

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        logger.info("Calling DepositTask...");

        List<String> items = Arrays.asList("foo", "bar",
                "initial", "loan",  "verify", "approve", "deposit", "audit", "receita federal",
                "COAF", "fim");

        Thread.sleep(1000);

        boolean b = random.nextBoolean();
        if (b) {
            delegateExecution.setVariable("isAnatel", "TRUE");
            delegateExecution.setVariable("anatelAuditItemsList", items);
            logger.info("DepositTask anatel");
        } else {
            delegateExecution.setVariable("isAnatel", "FALSE");
            delegateExecution.setVariable("auditItemsList", items);
            logger.info("DepositTask normal");
        }

        logger.info("Called DepositTask");

    }
}
