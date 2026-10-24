# job-posting-agent

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

## Status

**Milestone 1 (job posting drafter) is done.** Java + LangChain4j, same
stack and Groq provider as [`on-boarding-agent/brain`](../on-boarding-agent/brain/),
for consistency. Given a free-text role description, `POST /v1/draft-posting`
returns a structured draft (title, must-have requirements, preferred
criteria, education, employment type, location) as JSON -- HR reviews and
edits it before posting anywhere; nothing here publishes on its own.

Not yet built: email inbox monitoring (planned: Gmail API) and resume
parsing/scoring against a posting's criteria.

## Running it locally

```
mvn package
GROQ_API_KEY=... java -jar target/job-posting-agent.jar
```

Then:

```
curl http://localhost:8081/v1/draft-posting \
  -H "Content-Type: application/json" \
  -d '{"prompt":"We need a mid-level backend engineer who knows Java and Spring Boot..."}'
```

See [`.env.example`](.env.example) for configuration.
