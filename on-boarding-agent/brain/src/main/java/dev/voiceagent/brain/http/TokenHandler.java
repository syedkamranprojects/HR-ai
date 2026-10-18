package dev.voiceagent.brain.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.voiceagent.brain.sessions.EmployeeSession;
import dev.voiceagent.brain.sessions.SessionStore;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;

import java.util.Map;
import java.util.UUID;

/**
 * Mints a LiveKit access token for the web UI, so an employee's browser can
 * join a room directly without going through LiveKit's own dev Playground.
 * With a {@code session} query param (from an admin-generated link), joins
 * that pre-configured employee's deterministic room; otherwise a fresh
 * ad-hoc room/identity is generated per request. The registered glue/
 * worker auto-dispatches to any new room either way.
 */
public final class TokenHandler implements Handler {

    private final String apiKey;
    private final String apiSecret;
    private final String livekitUrl;
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;

    public TokenHandler(
            String apiKey, String apiSecret, String livekitUrl, SessionStore sessionStore, ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.livekitUrl = livekitUrl;
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        String sessionId = ctx.queryParam("session");
        EmployeeSession employeeSession = sessionId == null ? null : sessionStore.findById(sessionId);

        String room = employeeSession != null
                ? employeeSession.room()
                : ctx.queryParam("room") != null ? ctx.queryParam("room") : "onboarding-" + shortId();
        String identity = employeeSession != null
                ? "employee-" + employeeSession.id()
                : ctx.queryParam("identity") != null ? ctx.queryParam("identity") : "employee-" + shortId();

        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(identity);
        token.addGrants(new RoomJoin(true), new RoomName(room), new CanPublish(true), new CanSubscribe(true));

        ctx.contentType("application/json");
        ctx.result(objectMapper.writeValueAsString(Map.of(
                "token", token.toJwt(),
                "url", livekitUrl,
                "room", room,
                "identity", identity
        )));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
