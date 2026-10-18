# job-posting-agent (planned, not started)

A second HR module: HR gives a free-text prompt describing a role, the
agent drafts a structured job posting (title, hard requirements vs
weighted/preferred criteria, education), HR reviews and manually posts it
to LinkedIn and the company website, then the agent monitors an email
inbox for applications, parses resumes, and scores candidates against the
posting's criteria -- shortlisting for HR to review, never auto-rejecting
or auto-inviting without approval.

No voice interface for this module. See the main conversation (or the
project memory) for the full scoping discussion, including why LinkedIn/
website posting stays manual (no LinkedIn Talent Solutions API access
assumed) and the compliance reasoning behind keeping a human in the loop
(NYC Local Law 144 / EEOC / EU AI Act considerations for automated
hiring-decision tools).
