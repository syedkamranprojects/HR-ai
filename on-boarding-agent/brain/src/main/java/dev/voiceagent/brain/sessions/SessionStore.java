package dev.voiceagent.brain.sessions;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of admin-configured employee sessions. Demo-scale only --
 * restarting the brain service loses any unopened sessions. A real
 * deployment would back this with a database.
 */
public final class SessionStore {

    private final Map<String, EmployeeSession> byId = new ConcurrentHashMap<>();
    private final Map<String, EmployeeSession> byRoom = new ConcurrentHashMap<>();

    public EmployeeSession create(String name, String age, String experience) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        EmployeeSession session = EmployeeSession.create(id, name, age, experience);
        byId.put(id, session);
        byRoom.put(session.room(), session);
        return session;
    }

    public EmployeeSession findById(String id) {
        return byId.get(id);
    }

    public EmployeeSession findByRoom(String room) {
        return byRoom.get(room);
    }

    public Collection<EmployeeSession> list() {
        return List.copyOf(byId.values());
    }
}
