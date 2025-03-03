package org.camunda.bpm.getstarted.controller;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.getstarted.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class CamundaController {
    private static final String template = "Hello, %s!";
    private static final Logger logger
            = LoggerFactory.getLogger(CamundaController.class);

    @Autowired
    private RuntimeService runtimeService;

    public CamundaController() {
        logger.info("VAAAAAAAAAAAAAaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaiiiiiiiiiiiiiiiiiiii ........... 4");
    }

    @GetMapping("/echo")
    public String echo() {
        logger.info("Echo");

        return "Echo";
    }

    @PostMapping(value = "/createCamundaInstance",
            consumes = {"application/json"},
            produces = {"application/json"})
    public CamundaResult create(@RequestBody CreateLoan loan) throws InterruptedException {
        Map<String, Object> variables = new HashMap<>();

        variables.put("Name", loan.getName());
        variables.put("MoneyRequested", loan.getMoneyRequested());
        variables.put("Scale", loan.getScale());
        variables.put("LOAN_PRIOR", loan.getPriority());
        logger.info("Calling create ... PRIOR: {}", loan.getPriority());

        ProcessInstance processInstance =
                runtimeService.startProcessInstanceByKey(
                        "loanApproval",
                        "LOAN-" + UUID.randomUUID(),
                        variables);
        logger.info("Called create");

        return new CamundaResult("CREATED", processInstance.getId(), processInstance.getBusinessKey());
    }
}
