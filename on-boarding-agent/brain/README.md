# brain — LangChain4j agent service

The actual agent: conversation logic, the onboarding-overview flow, the
`searchCompanyPolicies` RAG tool (Qdrant-backed), and the escalation-to-HR
tool. Exposes an OpenAI-compatible streaming `/v1/chat/completions`
endpoint so the `glue/` LiveKit agent can use it as a drop-in custom LLM.

## Build & run

```bash
mvn -f brain/pom.xml package
java -jar brain/target/brain.jar
```

`GET /health` → `ok` once it's running. Everything else is scaffolding —
see Milestone 3 onward in the project plan.
