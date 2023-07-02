package org.camunda.bpm.getstarted.loanapproval;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.getstarted.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class CamundaController {
    private static final String template = "Hello, %s!";
    private static final Logger logger
            = LoggerFactory.getLogger(CamundaController.class);

    @Autowired
    private RuntimeService runtimeService;


//    @GetMapping("/report")
//    public ReportCamundaInstance report() {
//        Map<String, CamundaInstance> instanceMap = IntegrationTask.CAMUNDA_INSTANCE_MAP;
//        int totalInstances = instanceMap.size();
//        long avarageExecutionTimeOfFinishedInstances = 0;
//
//        long instancesCreated = instanceMap.values().stream().filter(i -> i.getStatus() == CamundaStatus.CREATED).count();
//        long instancesRunning = instanceMap.values().stream().filter(i -> i.getStatus() == CamundaStatus.RUNNING).count();
//
//        long instancesFinished = instanceMap.values().stream().filter(i -> i.getStatus() == CamundaStatus.FINISHED).count();
//
//        long sum = instanceMap.values().stream().filter(i -> i.getStatus() == CamundaStatus.FINISHED)
//                .mapToLong(CamundaInstance::getTotalTime).sum();
//
//        if (instancesFinished > 0) {
//            avarageExecutionTimeOfFinishedInstances = sum / instancesFinished;
//        }
//
//        OptionalLong maxExecutionTime = instanceMap.values().stream().filter(i -> i.getStatus() == CamundaStatus.FINISHED)
//                .mapToLong(CamundaInstance::getTotalTime).max();
//
//        return new ReportCamundaInstance(totalInstances, instancesCreated,
//                instancesRunning, instancesFinished, avarageExecutionTimeOfFinishedInstances,
//                maxExecutionTime.orElse(0));
//    }

    @PostMapping(value = "/createCamundaInstance",
            consumes = {"application/json"},
            produces = {"application/json"})
    public CamundaResult create(@RequestBody CreateLoan loan) throws InterruptedException {
        Map<String, Object> variables = new HashMap<>();

        variables.put("Name", loan.getName());
        variables.put("MoneyRequested", loan.getMoneyRequested());
        variables.put("Scale", loan.getScale());
        logger.info("Calling create ...");

        ProcessInstance processInstance =
                runtimeService.startProcessInstanceByKey(
                        "loanApproval",
                        "LOAN-" + UUID.randomUUID(),
                        variables);
        logger.info("Called create");

        return new CamundaResult("CREATED", processInstance.getId(), processInstance.getBusinessKey());
    }
}
