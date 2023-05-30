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

    public static final Map<String, CamundaInstance> CAMUNDA_INSTANCE_MAP = new HashMap<>();

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {
        String currentActivityName = delegateExecution.getCurrentActivityName();
        logger.info("Calling {} task...", currentActivityName);

        if ("Initial".equals(currentActivityName)) {
            logger.info("Initial task.");
            CAMUNDA_INSTANCE_MAP.put(delegateExecution.getProcessInstanceId(),
                    new CamundaInstance(CamundaStatus.CREATED, 0, delegateExecution.getBusinessKey()));
            delegateExecution.setVariable("StartTime",System.currentTimeMillis());
            logger.info("Called {} task. INITIALIZED ..  resturn", currentActivityName);
            return;
        }

        if ("Create Loan".equals(currentActivityName)) {
            logger.info("Create loan task.");
            CamundaInstance camundaInstance = CAMUNDA_INSTANCE_MAP.get(delegateExecution.getProcessInstanceId());
            camundaInstance.setStatus(CamundaStatus.RUNNING);
        }

        delegateExecution.setVariable("StatusLoan-" + currentActivityName,"CREATED");
        int i = random.nextInt(4001);
        logger.info("Random task {} with number {}", currentActivityName, i);
        Thread.sleep(6000 + i);

        if ("Finalize".equals(currentActivityName)) {
            logger.info("Final task.");
            Object startTime = delegateExecution.getVariable("StartTime");
            long totalTime  = System.currentTimeMillis() - (long)startTime;
            CamundaInstance camundaInstance = CAMUNDA_INSTANCE_MAP.get(delegateExecution.getProcessInstanceId());
            camundaInstance.setTotalTime(totalTime);
            camundaInstance.setStatus(CamundaStatus.FINISHED);
            logger.info(" =====> Instance {} executed in {} ms", delegateExecution.getProcessInstanceId(), totalTime);
        }

        logger.info("Called {} task.", currentActivityName);
    }
}
