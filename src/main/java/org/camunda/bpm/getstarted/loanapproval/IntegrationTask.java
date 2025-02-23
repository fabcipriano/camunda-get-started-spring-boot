package org.camunda.bpm.getstarted.loanapproval;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.getstarted.model.CamundaInstance;
import org.camunda.bpm.getstarted.model.CamundaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class IntegrationTask implements JavaDelegate {
    private static final Logger logger
            = LoggerFactory.getLogger(IntegrationTask.class);
    private static final Random random = new Random();

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        String currentActivityName = delegateExecution.getCurrentActivityName();
        logger.info("Calling {} task...", currentActivityName);

        if ("Initial".equals(currentActivityName)) {
            logger.info("Initial task.");
            delegateExecution.setVariable("StartTime",System.currentTimeMillis());
            delegateExecution.setVariable("CAMUNDA_INSTANCE",
                    new CamundaInstance(CamundaStatus.CREATED, 0, delegateExecution.getBusinessKey()));
            logger.info("Called {} task. INITIALIZED ..  return", currentActivityName);
            return;
        }

        if ("Create Loan".equals(currentActivityName)) {
            logger.info("Create loan task.");
            CamundaInstance camundaInstance = (CamundaInstance) delegateExecution.getVariable("CAMUNDA_INSTANCE");
            if (camundaInstance != null ) {
                camundaInstance.setStatus(CamundaStatus.RUNNING);
                delegateExecution.setVariable("CAMUNDA_INSTANCE", camundaInstance);
                logger.info("Create loan task variable saved.");
            } else {
                logger.warn("Create loan task SKIP !!!!!!");
            }
        }

        delegateExecution.setVariable("StatusLoan-" + currentActivityName,"CREATED");
        int i = random.nextInt(1001);
        logger.info("Random task {} with number {}", currentActivityName, i);
        Thread.sleep(600 + i);

        if ("Finalize".equals(currentActivityName)) {
            logger.info("Final task.");
            Object startTime = delegateExecution.getVariable("StartTime");
            long totalTime  = System.currentTimeMillis() - (long)startTime;
            CamundaInstance camundaInstance = (CamundaInstance) delegateExecution.getVariable("CAMUNDA_INSTANCE");
            if (camundaInstance != null) {
                camundaInstance.setTotalTime(totalTime);
                camundaInstance.setStatus(CamundaStatus.FINISHED);

                delegateExecution.setVariable("CAMUNDA_INSTANCE", camundaInstance);
                logger.info(" =====> Instance {} executed AND saved in {} ms", delegateExecution.getProcessInstanceId(), totalTime);
            } else {
                logger.warn(" =====> Instance BYPASS executed AND saved in {} ms", totalTime);
            }
        }

        logger.info("Called {} task.", currentActivityName);
    }
}
