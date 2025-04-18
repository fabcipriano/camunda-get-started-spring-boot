package org.camunda.bpm.getstarted.model;

public class CreateLoan {
    String name;
    long moneyRequested;
    int scale;
    int priority;

    public CreateLoan() {
    }

    public CreateLoan(String name, long moneyRequested, int scale, int priority) {
        this.name = name;
        this.moneyRequested = moneyRequested;
        this.scale = scale;
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getMoneyRequested() {
        return moneyRequested;
    }

    public void setMoneyRequested(long moneyRequested) {
        this.moneyRequested = moneyRequested;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
