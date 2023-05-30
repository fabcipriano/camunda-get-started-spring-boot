package org.camunda.bpm.getstarted.loanapproval;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

public class VerifyTask implements JavaDelegate {
    private static final Logger logger
            = LoggerFactory.getLogger(VerifyTask.class);
    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {

        logger.info("Calling...");
        Thread.sleep(5000);
        Map<String, Object> variables = delegateExecution.getVariables();
        Object moneyRequested = variables.get("MoneyRequested");
        Object scale = variables.get("Scale");

        if ((moneyRequested instanceof Long) && (scale instanceof Integer)) {
            BigDecimal money = new BigDecimal((Long) moneyRequested).movePointLeft((Integer)scale);
            logger.info("Converted to {}", money);
            int total = money.intValue();
            for (int i = 0; i < money.intValue(); i++) {
                total--;
                delegateExecution.setVariable("Ticket-"+i, total);
            }
            logger.info("variables set");
        }
        logger.info("Called VerifyTask");

    }
}
