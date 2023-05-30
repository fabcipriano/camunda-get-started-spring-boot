package org.camunda.bpm.getstarted.model;

public class CamundaInstance {
    private CamundaStatus status;
    private long totalTime;
    private String businessKey;

    public CamundaInstance(CamundaStatus status, long totalTime, String businessKey) {
        this.status = status;
        this.totalTime = totalTime;
        this.businessKey = businessKey;
    }

    public CamundaStatus getStatus() {
        return status;
    }

    public void setStatus(CamundaStatus status) {
        this.status = status;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(long totalTime) {
        this.totalTime = totalTime;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }
}
