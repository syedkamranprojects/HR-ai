# HR-ai

HR automation for Centegy Technologies, built as a set of independent agents
sharing one repository.

## Modules

- **[`on-boarding-agent/`](on-boarding-agent/)** — a voice/chat AI agent that
  welcomes a new employee, walks them through a company overview, and
  answers policy/benefits questions by retrieving real answers from company
  documents (RAG), escalating to a human when it can't help. Built with
  LiveKit (real-time voice), LangChain4j (Java, conversational logic), and a
  React web UI. All core milestones are done and verified live; see its own
  README for architecture, status, and how to run it.

- **[`job-posting-agent/`](job-posting-agent/)** — HR gives a free-text
  prompt describing a role, the agent drafts a structured job posting, HR
  reviews and posts it manually, and the agent will later score incoming
  applications against the posting's criteria — always shortlisting for
  human review, never auto-rejecting or auto-inviting. The drafter (Java +
  LangChain4j, same stack as on-boarding-agent) is done; email monitoring
  and resume scoring are not yet built. See its own README for the full
  scoping notes, including the compliance reasoning behind keeping a human
  in the loop.

## Repo layout

```
HR-ai/
├── on-boarding-agent/   # voice onboarding assistant (active)
│   ├── glue/            # Python LiveKit Agent (real-time STT/TTS/VAD)
│   ├── brain/           # Java conversational agent (LangChain4j + RAG)
│   └── ui/               # React web UI
└── job-posting-agent/   # job posting + applicant screening (planned)
```

Each module is self-contained with its own README, dependencies, and run
instructions — start there for details.
