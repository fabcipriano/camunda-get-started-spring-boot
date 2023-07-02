package org.camunda.bpm.getstarted.model;

public class ReportCamundaInstance {
    long totalInstances;
    long instanceCreated;
    long instancesRunning;
    long instancesFinished;
    long avarageExecutionTimeOfFinishedInstances;
    long maxExecutionTime;

    public ReportCamundaInstance() {
    }

    public ReportCamundaInstance(long totalInstances, long instanceCreated, long instancesRunning, long instancesFinished,
                                 long avarageExecutionTimeOfFinishedInstances, long maxExecutionTime) {
        this.totalInstances = totalInstances;
        this.instanceCreated = instanceCreated;
        this.instancesRunning = instancesRunning;
        this.instancesFinished = instancesFinished;
        this.avarageExecutionTimeOfFinishedInstances = avarageExecutionTimeOfFinishedInstances;
        this.maxExecutionTime = maxExecutionTime;
    }

    public long getTotalInstances() {
        return totalInstances;
    }

    public void setTotalInstances(long totalInstances) {
        this.totalInstances = totalInstances;
    }

    public long getInstanceCreated() {
        return instanceCreated;
    }

    public void setInstanceCreated(long instanceCreated) {
        this.instanceCreated = instanceCreated;
    }

    public long getInstancesRunning() {
        return instancesRunning;
    }

    public void setInstancesRunning(long instancesRunning) {
        this.instancesRunning = instancesRunning;
    }

    public long getInstancesFinished() {
        return instancesFinished;
    }

    public void setInstancesFinished(long instancesFinished) {
        this.instancesFinished = instancesFinished;
    }

    public long getAvarageExecutionTimeOfFinishedInstances() {
        return avarageExecutionTimeOfFinishedInstances;
    }

    public void setAvarageExecutionTimeOfFinishedInstances(long avarageExecutionTimeOfFinishedInstances) {
        this.avarageExecutionTimeOfFinishedInstances = avarageExecutionTimeOfFinishedInstances;
    }

    public long getMaxExecutionTime() {
        return maxExecutionTime;
    }

    public void setMaxExecutionTime(long maxExecutionTime) {
        this.maxExecutionTime = maxExecutionTime;
    }
}
