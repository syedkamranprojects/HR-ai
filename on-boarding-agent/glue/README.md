# glue — LiveKit real-time layer

Thin LiveKit Agent (Python, official `livekit-agents` SDK). Owns the
real-time session only: STT (Deepgram), TTS (Cartesia), VAD, and turn
detection. Holds no conversation logic — its LLM is configured to call the
`brain/` Java service's OpenAI-compatible endpoint.

## Setup

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env   # then fill in real keys
```

## Run

Not wired up yet — see Milestone 2 in the project plan.

```bash
python agent.py dev
```
