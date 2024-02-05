package org.camunda.bpm.getstarted.loanapproval;

import org.slf4j.LoggerFactory;

public class AnatelAudit extends NormalAudit {

    public AnatelAudit() {
        logger = LoggerFactory.getLogger(AnatelAudit.class);
    }
}
