package com.minidofus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Combattant en cours de partie : état mutable (position, PV, PA, PM, statuts, relances, statistiques). */
public final class Fighter {

    private final FighterClass fighterClass;
    private final Team team;
    private final int slot;
    private Cell position;
    private int hp;
    private int pa;
    private int pm;
    private int initiative;
    private final List<Status> statuses = new ArrayList<>();
    private final Map<Spell, Integer> cooldowns = new HashMap<>();
    private FighterStats stats = new FighterStats();

    /**
     * @param slot position dans l'équipe (0, 1, 2), sert à distinguer deux combattants de même classe
     */
    public Fighter(FighterClass fighterClass, Team team, int slot, Cell start) {
        this.fighterClass = fighterClass;
        this.team = team;
        this.slot = slot;
        this.position = start;
        this.hp = fighterClass.maxHp();
        this.pa = fighterClass.maxPa();
        this.pm = fighterClass.maxPm();
        this.initiative = fighterClass.initiative();
    }

    /** Identifiant de la classe (sert aux sprites et portraits). */
    public String classId() { return fighterClass.id(); }
    public String name() { return fighterClass.displayName(); }
    public FighterClass fighterClass() { return fighterClass; }
    public Team team() { return team; }
    public int slot() { return slot; }
    public Cell position() { return position; }
    public int hp() { return hp; }
    public int maxHp() { return fighterClass.maxHp(); }
    public int pa() { return pa; }
    public int pm() { return pm; }
    public int initiative() { return initiative; }
    public boolean isAlive() { return hp > 0; }
    public FighterStats stats() { return stats; }
    public List<Status> statuses() { return Collections.unmodifiableList(statuses); }

    public boolean isEnemyOf(Fighter other) {
        return other != null && other.team != team;
    }

    public boolean has(StatusKind kind) {
        return status(kind) != null;
    }

    public Status status(StatusKind kind) {
        for (Status s : statuses) if (s.kind() == kind) return s;
        return null;
    }

    public boolean hasShield() { return has(StatusKind.SHIELD); }
    public boolean isSilenced() { return has(StatusKind.SILENCE); }

    /** Tours restants avant de pouvoir relancer ce sort (0 = disponible). */
    public int cooldown(Spell spell) {
        return cooldowns.getOrDefault(spell, 0);
    }

    // ------------------------------------------------------------------ mutations (réservées au modèle)

    void setInitiative(int value) { this.initiative = value; }
    void moveTo(Cell cell) { this.position = cell; }
    void spendPa(int amount) { this.pa -= amount; }
    void spendPm(int amount) { this.pm -= amount; }
    void startCooldown(Spell spell) { if (spell.cooldown() > 0) cooldowns.put(spell, spell.cooldown()); }

    /** Applique les dégâts (réduits par le bouclier, 1 minimum) et renvoie les dégâts infligés. */
    int takeDamage(int raw) {
        int dealt = raw;
        Status shield = status(StatusKind.SHIELD);
        if (shield != null) dealt = Math.max(1, (int) Math.floor(raw * (100 - shield.value()) / 100.0));
        dealt = Math.min(dealt, hp);
        hp -= dealt;
        return dealt;
    }

    /** Soigne sans dépasser les PV max et renvoie les PV réellement rendus. */
    int heal(int amount) {
        int healed = Math.min(amount, maxHp() - hp);
        hp += healed;
        return healed;
    }

    /** Pose un statut ; un statut du même type est remplacé (pas de cumul). */
    void addStatus(Status status) {
        statuses.removeIf(s -> s.kind() == status.kind());
        statuses.add(status);
    }

    /** Début de tour : PA/PM restaurés, relances décomptées, statuts « de début de tour » vieillis. */
    List<StatusKind> startTurn() {
        pa = fighterClass.maxPa();
        pm = fighterClass.maxPm();
        cooldowns.replaceAll((s, v) -> v - 1);
        cooldowns.values().removeIf(v -> v <= 0);
        return age(false);
    }

    /** Fin de tour : statuts « de fin de tour » vieillis. */
    List<StatusKind> endTurn() {
        return age(true);
    }

    private List<StatusKind> age(boolean atTurnEnd) {
        List<StatusKind> expired = new ArrayList<>();
        for (Status s : statuses) {
            if (s.kind().expiresAtTurnEnd() == atTurnEnd) {
                s.decrement();
                if (s.turnsLeft() <= 0) expired.add(s.kind());
            }
        }
        statuses.removeIf(s -> s.turnsLeft() <= 0);
        return expired;
    }

    /** Copie profonde pour les simulations de l'IA (les sources de statuts sont remappées ensuite). */
    Fighter copy() {
        Fighter f = new Fighter(fighterClass, team, slot, position);
        f.hp = hp;
        f.pa = pa;
        f.pm = pm;
        f.initiative = initiative;
        f.cooldowns.putAll(cooldowns);
        f.stats = stats.copy();
        return f;
    }

    void copyStatusesFrom(Fighter original, Map<Fighter, Fighter> mapping) {
        for (Status s : original.statuses) statuses.add(s.copyWithSource(mapping.get(s.source())));
    }

    @Override
    public String toString() {
        return name() + "(" + team + slot + ")";
    }
}
