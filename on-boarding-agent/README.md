# Voice Agent — HR Onboarding Assistant

A voice AI agent for Centegy Technologies that welcomes a new employee,
walks them through a brief company overview, and answers policy/benefits
questions by retrieving real answers from company documents (RAG). If a
question needs a human, it escalates and hands back a reference number.

Modeled on a Python/LiveKit/LangChain tutorial, rebuilt with **LangChain4j**
(Java) for the conversational agent, **LiveKit** for real-time voice, and
**Qdrant** for the knowledge base.

## Why three sub-projects

LiveKit's **Agents framework** — the layer that joins a room, streams
mic audio through STT → LLM → TTS, runs VAD and turn detection — only has
official SDKs in Python and Node.js. There is no Java/JVM SDK that can act
as a real-time LiveKit participant. Java's LiveKit SDK *is* fully supported
for server-side work though: room admin, and signing access tokens — which
is exactly what the web UI needs to let an employee join a room.

So the project is split in three:

- **[`glue/`](glue/)** — a thin LiveKit Agent written in Python, using the
  official `livekit-agents` SDK. It owns the real-time session: STT
  (Deepgram, with Urdu support via `DEEPGRAM_LANGUAGE=ur`), TTS (Cartesia),
  VAD, and tuned turn detection. It holds no business logic — its only
  "LLM" is an HTTP call to `brain/`.
- **[`brain/`](brain/)** — a Java service (LangChain4j + Javalin) that *is*
  the agent: conversation logic, the onboarding-overview flow, the
  `searchCompanyPolicies` RAG tool (Qdrant + local Ollama embeddings), and
  the `escalateToHuman` tool. It exposes an OpenAI-compatible streaming
  `/v1/chat/completions` endpoint, so `glue/` can point LiveKit's built-in
  OpenAI-compatible LLM plugin straight at it with no custom integration
  code on the Python side. It also mints LiveKit access tokens for the web
  UI, using LiveKit's Java server SDK.
- **[`ui/`](ui/)** — a React (Vite) web app, branded for Centegy
  Technologies, where an employee actually talks to the assistant: voice or
  typed chat, a live transcript of both sides of the conversation, mic
  mute, and end-call control, built on `@livekit/components-react`.
  Replaces needing to use LiveKit's own dev Playground. Also has an
  `/admin` page where HR pre-configures a session for a new employee (name,
  age, experience) and gets a link to share — opening it personalizes the
  greeting with the employee's name, however HR or the employee starts it.

```
ui/ (React) --token--> brain/ (token mint)
   |
   v
LiveKit room  <-->  glue/ (Python, livekit-agents)  <-- HTTP/SSE -->  brain/ (Java, LangChain4j)
                      STT · VAD · turn-detect · TTS                    conversation · RAG (Qdrant) · tools
```

## Status

All ten milestones through the custom web UI are done and verified live:
scaffolding, LiveKit connection, the Java brain wired in as the LLM, the
proactive welcome flow, RAG, speech-only output filtering, the escalation
flow, tuned turn detection, error recovery, and the branded web UI. On top
of that, the UI now also has typed chat, a live conversation transcript,
and an admin flow for personalizing a session with the employee's name
ahead of time. Final end-to-end testing and a production-concerns
write-up are the one remaining milestone.

The knowledge base currently holds real Centegy Technologies facts (from
centegytechnologies.com) for company overview, but placeholder content for
HR policy specifics (PTO, benefits, code of conduct) pending the real
documents.

## Running it locally

Three processes, in order:

1. **Qdrant** (Docker): `docker run -d --name qdrant-voiceagent -p 6333:6333 -p 6334:6334 qdrant/qdrant`
2. **Ollama**, with `nomic-embed-text` pulled, for embeddings
3. **`brain/`**: `mvn -f brain/pom.xml package && java -jar brain/target/brain.jar`
   (needs `GROQ_API_KEY`, `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` in its environment)
4. **`glue/`**: `python agent.py dev` (see [glue/.env.example](glue/.env.example))
5. **`ui/`**: `npm install && npm run dev`, then open the printed localhost URL

## Sub-projects

- [glue/README.md](glue/README.md)
- [brain/README.md](brain/README.md)
- [ui/](ui/) (React + Vite; see `.env.example` for `VITE_BRAIN_URL`)
