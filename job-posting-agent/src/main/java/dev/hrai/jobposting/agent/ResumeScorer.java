package dev.hrai.jobposting.agent;

import dev.hrai.jobposting.model.JobPosting;
import dev.hrai.jobposting.model.ResumeScore;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Scores a resume against a job posting's criteria. Purely advisory: this
 * never shortlists, rejects, or invites anyone -- it gives HR a structured
 * starting point, and HR reviews every candidate themselves. See README for
 * why (NYC Local Law 144 / EEOC / EU AI Act considerations for automated
 * hiring-decision tools).
 */
public final class ResumeScorer {

    private static final String SYSTEM_PROMPT = """
            You compare a candidate's resume against a job posting's
            requirements and produce an advisory match assessment for HR.
            You never make a hiring decision -- HR reviews every candidate
            regardless of score, so phrase your recommendation as guidance,
            never as a verdict (never say "reject" or "disqualify"; instead
            describe what's missing or unclear and let HR decide).

            Base the score and matches only on job-relevant skills,
            experience, and qualifications that the resume provides actual
            evidence for -- do not guess at anything not stated. Ignore the
            candidate's name, age, gender, photo, nationality, or any other
            characteristic not relevant to the job's requirements; if the
            resume contains such information, do not let it affect the
            score in any way. If a must-have requirement isn't clearly
            addressed in the resume, list it as missing rather than assuming
            the candidate has it.
            """;

    private final ChatModel model;

    public ResumeScorer(String baseUrl, String apiKey, String modelName) {
        this.model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    public ResumeScore score(JobPosting posting, String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            throw new IllegalArgumentException("resumeText must not be blank");
        }
        Scorer scorer = AiServices.create(Scorer.class, model);
        return scorer.score(
                posting.title(),
                String.join("; ", posting.mustHaveRequirements()),
                String.join("; ", posting.preferredCriteria()),
                String.join("; ", posting.educationRequirements()),
                resumeText
        );
    }

    private interface Scorer {
        @SystemMessage(SYSTEM_PROMPT)
        @UserMessage("""
                Job title: {{title}}
                Must-have requirements: {{mustHave}}
                Preferred criteria: {{preferred}}
                Education requirements: {{education}}

                Resume:
                {{resume}}
                """)
        ResumeScore score(
                @V("title") String title,
                @V("mustHave") String mustHave,
                @V("preferred") String preferred,
                @V("education") String education,
                @V("resume") String resume
        );
    }
}
