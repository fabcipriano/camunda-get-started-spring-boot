package org.camunda.bpm.getstarted.model;

public record ReportCamundaInstance(long totalInstances,
                                    long instanceCreated,
                                    long instancesRunning,
                                    long instancesFinished,
                                    long avarageExecutionTimeOfFinishedInstances,
                                    long maxExecutionTime) {
}
