package dev.voiceagent.brain.sessions;

/**
 * A pre-configured onboarding session an admin sets up ahead of an
 * employee's first day. {@code room} is deterministic from {@code id} so
 * reopening the same link (by HR or the employee) lands in the same room.
 */
public record EmployeeSession(String id, String name, String age, String experience, String room) {

    public static EmployeeSession create(String id, String name, String age, String experience) {
        return new EmployeeSession(id, name, age, experience, "onboarding-" + id);
    }
}
