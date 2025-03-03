/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.getstarted;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.camunda.bpm.spring.boot.starter.event.PostDeployEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.prometheus.client.spring.boot.EnablePrometheusEndpoint;
import io.prometheus.client.spring.boot.EnableSpringBootMetricsCollector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
@EnableProcessApplication
@EnableScheduling
// Add a Prometheus metrics enpoint to the route `/prometheus`. `/metrics` is already taken by Actuator.
@EnablePrometheusEndpoint
// Pull all metrics from Actuator and expose them as Prometheus metrics. Need to disable security feature in properties file.
@EnableSpringBootMetricsCollector
public class WebappExampleProcessApplication {

  @Autowired
  private RuntimeService runtimeService;

  private static final Logger logger
          = LoggerFactory.getLogger(WebappExampleProcessApplication.class);

  public static void main(String... args) {
    logger.info("Starting vai ... ");
    SpringApplication.run(WebappExampleProcessApplication.class, args);
    logger.info("Started !!!");
  }

  @EventListener
  public void processPostDeploy(PostDeployEvent event) {
    Map<String, Object> variables = new HashMap<>();

    variables.put("Name", "Teste 1 2 3");
    variables.put("MoneyRequested", 25000);
    variables.put("Scale", 2);
    variables.put("LOAN_PRIOR", 1);

    runtimeService.startProcessInstanceByKey(
            "loanApproval",
            "LOAN-" + UUID.randomUUID(),
            variables);
    logger.info("INTERNAL Called create ...");
  }

}