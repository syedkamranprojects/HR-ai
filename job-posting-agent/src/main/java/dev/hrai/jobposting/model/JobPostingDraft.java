package dev.hrai.jobposting.model;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

/**
 * The part of a job posting the model generates from HR's free-text prompt.
 * Deliberately excludes contact details like an application email -- those
 * are exact, HR-supplied facts, not something to let the model guess at.
 * See {@link JobPosting} for the full posting HR actually reviews.
 */
public record JobPostingDraft(
        @Description("the job title, e.g. 'Senior Backend Engineer'")
        String title,

        @Description("a 2-4 sentence overview of the role and the team it sits in")
        String summary,

        @Description("hard requirements a candidate must meet to be considered at all")
        List<String> mustHaveRequirements,

        @Description("preferred but not mandatory qualifications; a strong candidate may lack some of these")
        List<String> preferredCriteria,

        @Description("education requirements, e.g. 'Bachelor's in Computer Science or equivalent experience'; empty list if none specified")
        List<String> educationRequirements,

        @Description("e.g. 'Full-time', 'Part-time', 'Contract'; best guess if not stated in the prompt")
        String employmentType,

        @Description("e.g. 'Remote', 'Hybrid - Lahore', 'On-site - Lahore'; best guess if not stated in the prompt")
        String location
) {
}
