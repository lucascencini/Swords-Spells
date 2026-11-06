package com.minidofus.model;

/** Statut actif sur un combattant. {@code source} est le lanceur (pour attribuer les dégâts de poison). */
public final class Status {

    private final StatusKind kind;
    private final int value;
    private final Fighter source;
    private int turnsLeft;

    public Status(StatusKind kind, int turnsLeft, int value, Fighter source) {
        this.kind = kind;
        this.turnsLeft = turnsLeft;
        this.value = value;
        this.source = source;
    }

    public StatusKind kind() { return kind; }
    public int turnsLeft() { return turnsLeft; }
    public int value() { return value; }
    public Fighter source() { return source; }

    void decrement() { turnsLeft--; }

    Status copyWithSource(Fighter newSource) {
        return new Status(kind, turnsLeft, value, newSource);
    }
}
