package dev.voiceagent.brain.http;

import dev.voiceagent.brain.sessions.EmployeeSession;
import dev.voiceagent.brain.sessions.SessionStore;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.util.Map;

/**
 * Looked up by glue/ at session start, given the LiveKit room name, to find
 * out which (if any) pre-configured employee this conversation is for.
 */
public final class EmployeeInfoHandler implements Handler {

    private final SessionStore sessionStore;

    public EmployeeInfoHandler(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public void handle(Context ctx) {
        String room = ctx.queryParam("room");
        EmployeeSession session = room == null ? null : sessionStore.findByRoom(room);
        if (session == null) {
            ctx.status(404).json(Map.of("found", false));
            return;
        }
        ctx.json(Map.of(
                "found", true,
                "name", session.name(),
                "age", session.age() == null ? "" : session.age(),
                "experience", session.experience() == null ? "" : session.experience()
        ));
    }
}
