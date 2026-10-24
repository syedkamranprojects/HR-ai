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
`POST /v1/draft-posting` takes a message history (initial role description,
then optional follow-up refinement turns -- "make it stricter", "drop the
Docker requirement") and returns a structured draft (title, must-have
requirements, preferred criteria, education, employment type, location,
application email) as JSON; the client resends the full history each call,
same pattern as `on-boarding-agent/brain`'s chat endpoint. The application
email is always HR-supplied, never model-generated, so it's never a guess.

UI: a React (Vite) page styled to match `on-boarding-agent/ui`'s layout and
branding (same color tokens, card/topbar CSS, Centegy logo), as a real chat
conversation -- HR's instructions as bubbles, each reply rendered as a
structured posting card inline in the thread, with a "copy as text" action
for pasting into LinkedIn or the careers page. HR always posts it manually;
nothing here publishes on its own. The conversation only persists for the
current browser session (no backend storage yet).

**Milestone 2 (resume scoring) is done.** `POST /v1/score-resume` takes a
multipart upload -- a resume (PDF via Apache PDFBox, or plain text) and the
`posting` JSON to score it against (exactly what `/v1/draft-posting`
returns; there's no backend storage of postings, so the client sends the
one it has) -- and returns a `ResumeScore`: a 0-100 fit score, which
must-have/preferred criteria matched or are missing, a short rationale, and
an advisory recommendation. This is purely advisory: the model is
instructed to score only on job-relevant skills/experience actually
evidenced in the resume (never on name, age, gender, or anything else it
might reveal) and to never phrase anything as a final decision -- HR
reviews every candidate regardless of score. In the UI, once a posting
exists, HR can upload one or more resumes and see each as a score card.

Not yet built: email inbox monitoring (planned: Gmail API) to actually
receive applications.

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
  -d '{"messages":[{"role":"user","content":"We need a mid-level backend engineer who knows Java and Spring Boot..."}],"applicationEmail":"careers@centegytechnologies.com"}'

curl http://localhost:8081/v1/score-resume \
  -F "resume=@/path/to/resume.pdf" \
  --form-string "posting=<paste the JobPosting JSON from above>"
```

See [`.env.example`](.env.example) for backend configuration.
