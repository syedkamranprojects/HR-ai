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

**Milestone 1 (job posting drafter, backend + UI) is done.** Backend: Java +
LangChain4j, same stack and Groq provider as
[`on-boarding-agent/brain`](../on-boarding-agent/brain/), for consistency.
Given a free-text role description, `POST /v1/draft-posting` returns a
structured draft (title, must-have requirements, preferred criteria,
education, employment type, location) as JSON. UI: a React (Vite) page,
styled to match `on-boarding-agent/ui`'s layout and branding (same color
tokens, card/topbar CSS, Centegy logo), where HR types a role description
and gets back a reviewable draft with a "copy as text" action for pasting
into LinkedIn or the careers page -- HR always posts it manually; nothing
here publishes on its own. Drafts only persist for the current browser
session (no backend storage yet).

Not yet built: email inbox monitoring (planned: Gmail API) and resume
parsing/scoring against a posting's criteria.

## Running it locally

Two processes:

1. **Backend**:
   ```
   mvn package
   GROQ_API_KEY=... java -jar target/job-posting-agent.jar
   ```
2. **UI** (see [`ui/.env.example`](ui/.env.example) for `VITE_JOBS_URL`):
   ```
   cd ui && npm install && npm run dev
   ```
   then open the printed localhost URL (defaults to `http://localhost:5174`).

Or test the backend directly:

```
curl http://localhost:8081/v1/draft-posting \
  -H "Content-Type: application/json" \
  -d '{"prompt":"We need a mid-level backend engineer who knows Java and Spring Boot..."}'
```

See [`.env.example`](.env.example) for backend configuration.
