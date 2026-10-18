package dev.voiceagent.brain.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.voiceagent.brain.sessions.EmployeeSession;
import dev.voiceagent.brain.sessions.SessionStore;
import io.javalin.http.Context;

import java.util.Map;

/**
 * Admin API: create and list pre-configured employee onboarding sessions.
 * No authentication -- this is demo-scale, matching the rest of the
 * project; a real deployment would put this behind HR-only auth.
 */
public final class AdminSessionsHandler {

    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;

    public AdminSessionsHandler(SessionStore sessionStore, ObjectMapper objectMapper) {
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
    }

    public void create(Context ctx) throws Exception {
        Request request = objectMapper.readValue(ctx.body(), Request.class);
        if (request.name() == null || request.name().isBlank()) {
            ctx.status(400).json(Map.of("error", "name is required"));
            return;
        }
        EmployeeSession session = sessionStore.create(request.name(), request.age(), request.experience());
        ctx.json(session);
    }

    public void list(Context ctx) {
        ctx.json(sessionStore.list());
    }

    public void get(Context ctx) {
        EmployeeSession session = sessionStore.findById(ctx.pathParam("id"));
        if (session == null) {
            ctx.status(404).json(Map.of("error", "not found"));
            return;
        }
        ctx.json(session);
    }

    private record Request(String name, String age, String experience) {
    }
}
