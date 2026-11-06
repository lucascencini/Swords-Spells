package com.minidofus.model;

/** Camp d'un combattant. */
public enum Team {
    RED("Rouge"),
    BLUE("Bleue");

    private final String displayName;

    Team(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public Team opposite() {
        return this == RED ? BLUE : RED;
    }
}
