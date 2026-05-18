package com.example.dam.common.domain;

import java.util.Objects;

/**
 * The party responsible for a change. Humans, system components and AI agents are distinguished by
 * reserved prefixes so that, for example, an AI agent can never act as an approver.
 */
public record Actor(String id) {

    public static final String SYSTEM_PREFIX = "system:";
    public static final String AI_PREFIX = "ai:";
    private static final String ANONYMOUS_ID = "anonymous";
    private static final int MAX_LENGTH = 200;

    public Actor {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || id.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Actor id must be 1.." + MAX_LENGTH + " characters");
        }
    }

    public static Actor human(String id) {
        String trimmed = Objects.requireNonNull(id, "id").trim();
        if (trimmed.startsWith(SYSTEM_PREFIX) || trimmed.startsWith(AI_PREFIX)) {
            throw new IllegalArgumentException("Human actor ids must not use reserved prefixes: " + trimmed);
        }
        return new Actor(trimmed);
    }

    public static Actor anonymous() {
        return new Actor(ANONYMOUS_ID);
    }

    public static Actor system(String component) {
        return new Actor(SYSTEM_PREFIX + component);
    }

    public static Actor ai(String agentName) {
        return new Actor(AI_PREFIX + agentName);
    }

    public boolean isHuman() {
        return !id.startsWith(SYSTEM_PREFIX) && !id.startsWith(AI_PREFIX);
    }

    public boolean isIdentifiedHuman() {
        return isHuman() && !ANONYMOUS_ID.equals(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
