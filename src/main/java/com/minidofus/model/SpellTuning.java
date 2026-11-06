package com.minidofus.model;

import com.minidofus.model.spell.AbstractSpell.OnHit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Améliorations cumulées d'un sort (venant des talents) : elles s'additionnent, puis {@link Spell#tuned} produit
 * une copie modifiée du sort. Règles de cumul explicites : bonus additifs entre eux, multiplicatif sur le sort.
 */
public final class SpellTuning {

    private int powerPct;
    private int range;
    private int cost;
    private int cooldown;
    private boolean ignoresLineOfSight;
    private final List<OnHit> onHit = new ArrayList<>();
    private final Map<String, Integer> params = new TreeMap<>();

    public SpellTuning addPower(int pct) { powerPct += pct; return this; }
    public SpellTuning addRange(int r) { range += r; return this; }
    public SpellTuning addCost(int c) { cost += c; return this; }
    public SpellTuning addCooldown(int c) { cooldown += c; return this; }
    public SpellTuning noLineOfSight() { ignoresLineOfSight = true; return this; }
    public SpellTuning addOnHit(StatusKind kind, int turns, int value) { onHit.add(new OnHit(kind, turns, value)); return this; }
    public SpellTuning addParam(String key, int value) { params.merge(key, value, Integer::sum); return this; }

    public int powerPct() { return powerPct; }
    public int range() { return range; }
    public int cost() { return cost; }
    public int cooldown() { return cooldown; }
    public boolean ignoresLineOfSight() { return ignoresLineOfSight; }
    public List<OnHit> onHit() { return onHit; }
    public Map<String, Integer> params() { return params; }

    public boolean isEmpty() {
        return powerPct == 0 && range == 0 && cost == 0 && cooldown == 0 && !ignoresLineOfSight && onHit.isEmpty()
                && params.isEmpty();
    }
}
