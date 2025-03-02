package org.camunda.bpm.getstarted.loanapproval;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.getstarted.model.CamundaInstance;
import org.camunda.bpm.getstarted.model.CamundaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.camunda.bpm.getstarted.utils.SpringContext;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class IntegrationTask implements JavaDelegate {
    private static final Logger logger
            = LoggerFactory.getLogger(IntegrationTask.class);
    private static final Random random = new Random();
    private static final AtomicInteger atomicInteger = new AtomicInteger(-1);

    private JdbcTemplate jdbcTemplate;

    @Override
    public void execute(DelegateExecution delegateExecution) throws Exception {

        // Get the JdbcTemplate bean from the Spring context
        JdbcTemplate jdbcTemplate = SpringContext.getBean(JdbcTemplate.class);

        String currentActivityName = delegateExecution.getCurrentActivityName();
        logger.info("Calling {} task...", currentActivityName);

        if ("Initial".equals(currentActivityName)) {
            logger.info("Initial task.");
            delegateExecution.setVariable("StartTime",System.currentTimeMillis());
            delegateExecution.setVariable("CAMUNDA_INSTANCE",
                    new CamundaInstance(CamundaStatus.CREATED, 0, delegateExecution.getBusinessKey()));

            // Save a row in the Loan_Audit table using JdbcTemplate and retrieve the generated ID
            String sql = "INSERT INTO Loan_Audit (name, description, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"}); // Specify the column name for the generated key
                ps.setString(1, "ID_Execution_" + atomicInteger.incrementAndGet());
                ps.setString(2, "This is the initial task in the process.");
                return ps;
            }, keyHolder);

            // Retrieve the generated ID
            Number generatedId = keyHolder.getKey();
            if (generatedId != null) {
                logger.info("Generated ID: {}", generatedId.longValue());
                delegateExecution.setVariable("generatedId", generatedId.longValue());
            } else {
                logger.warn("No ID was generated for the inserted row.");
            }

            logger.info("Saved a row in Loan_Audit table for Initial task.");
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

            // Update the Loan_Audit table for the row where name matches the name variable
            Object generatedId = delegateExecution.getVariable("generatedId");
            logger.info("Updating Loan_Audit table for id: {} ...", generatedId);

            String updateSql = "UPDATE Loan_Audit SET description = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            jdbcTemplate.update(updateSql, "Finalized task with total time: " + totalTime + " ms", generatedId);
            logger.info("Updated Loan_Audit table for Id: {}", generatedId);
        }

        logger.info("Called {} task.", currentActivityName);
    }
}
