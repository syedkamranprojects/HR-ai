package dev.voiceagent.brain.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Hands the conversation off to a human HR rep. A real deployment would file
 * a ticket in whatever system HR uses; this logs it and returns a reference
 * number, which is enough to exercise the escalation flow end to end.
 */
public final class EscalationTool {

    private static final Logger log = LoggerFactory.getLogger(EscalationTool.class);

    @Tool("Escalate to a human HR representative. Use this when the employee explicitly asks to talk "
            + "to a person, or when searchCompanyPolicies has nothing relevant and the employee still "
            + "needs an answer. Returns a reference number to read back to the employee.")
    public String escalateToHuman(@P("brief reason for the escalation") String reason) {
        String referenceNumber = "HR-" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
        log.info("escalation {} : {}", referenceNumber, reason);
        return "Escalated to HR. Reference number: " + referenceNumber;
    }
}
