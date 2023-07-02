package org.camunda.bpm.getstarted.model;

public class Greetings {
    long id;
    String content;

    public Greetings() {
    }

    public Greetings(long id, String content) {
        this.id = id;
        this.content = content;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
