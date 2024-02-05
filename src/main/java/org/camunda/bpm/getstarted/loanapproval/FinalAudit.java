package org.camunda.bpm.getstarted.loanapproval;

import org.slf4j.LoggerFactory;

public class FinalAudit extends NormalAudit {

    public FinalAudit() {
        logger = LoggerFactory.getLogger(FinalAudit.class);
    }
}
