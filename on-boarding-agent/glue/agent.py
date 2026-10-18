"""LiveKit real-time glue layer -- Milestone 3: wire in the Java brain.

Owns the room/STT/TTS/VAD/turn-detection session only. The LLM is the Java
brain service (../brain), reached as an OpenAI-compatible endpoint --
openai.LLM with a custom base_url needs no special integration code here.
All conversation logic, persona, RAG, and tools live in the Java service.
"""

import logging
import os

import aiohttp
from dotenv import load_dotenv
from livekit.agents import Agent, AgentSession, ErrorEvent, JobContext, JobProcess, TurnHandlingOptions, WorkerOptions, cli, room_io
from livekit.agents.voice.events import LLMError
from livekit.plugins import cartesia, deepgram, openai, silero

load_dotenv()

logger = logging.getLogger("glue")

BRAIN_BASE_URL = os.environ.get("BRAIN_BASE_URL", "http://localhost:8080/v1")
# Separate from BRAIN_BASE_URL (which carries the /v1 chat-completions
# prefix) -- used for the admin-configured employee-info lookup.
BRAIN_ROOT_URL = os.environ.get("BRAIN_ROOT_URL", "http://localhost:8080")

# Deepgram's nova-3 supports Urdu only as a dedicated language, not through
# its "multi" code-switching mode (which covers English/Hindi/Spanish/etc
# but not Urdu) -- so this is a single fixed STT language, not auto-detect.
# Set DEEPGRAM_LANGUAGE=ur for an employee who'll speak Urdu.
DEEPGRAM_LANGUAGE = os.environ.get("DEEPGRAM_LANGUAGE", "en-US")

FALLBACK_MESSAGE = (
    "Sorry, I'm having trouble connecting right now. Please try again in a "
    "moment, or let me know if you'd like me to connect you with HR directly."
)


class HrOnboardingAssistant(Agent):
    def __init__(self) -> None:
        # The real persona/system prompt lives in the Java brain service;
        # this is just a placeholder since Agent requires non-empty
        # instructions.
        super().__init__(instructions="Defer entirely to the configured LLM.")


def prewarm(proc: JobProcess) -> None:
    proc.userdata["vad"] = silero.VAD.load()


async def fetch_employee_info(room_name: str) -> dict | None:
    """Looks up the admin-configured employee profile for this room, if any.

    Rooms created ad-hoc (e.g. the "Start Conversation" button with no
    admin-generated link) have no matching profile -- that's a normal case,
    not an error, so a 404 just means "no personalization for this room."
    """
    url = f"{BRAIN_ROOT_URL}/api/employee-info"
    try:
        async with aiohttp.ClientSession() as http:
            async with http.get(url, params={"room": room_name}, timeout=aiohttp.ClientTimeout(total=5)) as resp:
                if resp.status != 200:
                    return None
                data = await resp.json()
                return data if data.get("found") else None
    except Exception:
        logger.exception("employee-info lookup failed")
        return None


async def entrypoint(ctx: JobContext) -> None:
    await ctx.connect()

    session = AgentSession(
        vad=ctx.proc.userdata["vad"],
        stt=deepgram.STT(model="nova-3", language=DEEPGRAM_LANGUAGE),
        llm=openai.LLM(
            model="brain",
            base_url=BRAIN_BASE_URL,
            api_key="not-needed",
        ),
        tts=cartesia.TTS(),
        # Milestone 8: tuned for an onboarding conversation's pace -- quick
        # enough to feel responsive, but with enough max delay that someone
        # pausing mid-thought isn't cut off.
        turn_handling=TurnHandlingOptions(
            turn_detection="vad",
            endpointing={"mode": "fixed", "min_delay": 0.4, "max_delay": 3.0},
        ),
    )

    def on_error(event: ErrorEvent) -> None:
        # The LLM plugin already retries transient failures internally
        # (recoverable=True); only speak up once it's truly given up, so we
        # don't interrupt the user over a blip that resolved itself.
        if isinstance(event.error, LLMError) and not event.error.recoverable:
            logger.error("unrecoverable LLM error: %s", event.error.error)
            session.say(FALLBACK_MESSAGE)  # sync call, starts speaking immediately

    session.on("error", on_error)

    await session.start(
        agent=HrOnboardingAssistant(),
        room=ctx.room,
        # Lets the web UI's chat box feed text turns in alongside voice, and
        # publishes transcriptions of both sides of the conversation so the
        # UI can render a live transcript.
        room_input_options=room_io.RoomInputOptions(text_enabled=True),
        room_output_options=room_io.RoomOutputOptions(transcription_enabled=True),
    )

    employee = await fetch_employee_info(ctx.room.name)
    if employee:
        logger.info("personalized session for %s", employee.get("name"))
        greeting_instructions = (
            "This is the very start of the conversation. The employee's name "
            f"is {employee['name']}. "
            + (f"Their role/experience: {employee['experience']}. " if employee.get("experience") else "")
            + "Welcome them to their first day BY NAME, briefly introduce "
            "yourself, and give a short overview of what you can help with. "
            "Then invite them to ask anything."
        )
    else:
        greeting_instructions = (
            "This is the very start of the conversation. Welcome the new "
            "employee to their first day, briefly introduce yourself, and "
            "give a short overview of what you can help with. Then invite "
            "them to ask anything."
        )

    try:
        await session.generate_reply(instructions=greeting_instructions)
    except Exception:
        logger.exception("initial greeting failed")
        await session.say(FALLBACK_MESSAGE)


if __name__ == "__main__":
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint, prewarm_fnc=prewarm))
