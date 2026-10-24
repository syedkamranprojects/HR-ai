package dev.hrai.jobposting.model;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

/**
 * An advisory comparison of one resume against a job posting's criteria.
 * This never decides anything -- HR reviews every candidate; the agent
 * never auto-rejects or auto-invites (see README for the compliance
 * reasoning: NYC Local Law 144 / EEOC / EU AI Act).
 */
public record ResumeScore(
        @Description("the candidate's name as it appears on the resume; 'Unknown' if it can't be determined")
        String candidateName,

        @Description("the candidate's email address, copied exactly as it appears in the resume; null if none is present -- never invent or guess one")
        String candidateEmail,

        @Description("0-100: how well the resume's skills and experience match the posting's requirements. Based only on job-relevant qualifications -- never on name, age, gender, or other characteristics the resume might reveal")
        int fitScore,

        @Description("must-have requirements from the posting that the resume provides clear evidence for")
        List<String> matchedMustHave,

        @Description("must-have requirements from the posting with no clear evidence in the resume")
        List<String> missingMustHave,

        @Description("preferred criteria from the posting that the resume provides clear evidence for")
        List<String> matchedPreferred,

        @Description("preferred criteria from the posting with no clear evidence in the resume")
        List<String> missingPreferred,

        @Description("2-3 sentences explaining the score, citing specific resume content against specific requirements")
        String summary,

        @Description("an advisory note for HR, e.g. 'Recommend shortlisting for review' or 'Several must-have requirements are unclear from this resume -- recommend a closer look'. Never phrased as a final decision -- HR always reviews every candidate")
        String recommendation
) {
}
