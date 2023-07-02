package org.camunda.bpm.getstarted.model;

public class CamundaResult {
    String status;
    String camundaInstance;
    String businessKey;

    public CamundaResult() {
    }

    public CamundaResult(String status, String camundaInstance, String businessKey) {
        this.status = status;
        this.camundaInstance = camundaInstance;
        this.businessKey = businessKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCamundaInstance() {
        return camundaInstance;
    }

    public void setCamundaInstance(String camundaInstance) {
        this.camundaInstance = camundaInstance;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }
}
