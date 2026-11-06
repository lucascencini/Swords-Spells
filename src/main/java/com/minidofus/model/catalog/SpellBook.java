package com.minidofus.model.catalog;

import com.minidofus.model.CastStyle;
import com.minidofus.model.FighterClass;
import com.minidofus.model.IntRange;
import com.minidofus.model.MoveStyle;
import com.minidofus.model.Spell;
import com.minidofus.model.SpellTuning;
import com.minidofus.model.StatusKind;
import com.minidofus.model.spell.AbstractSpell.Base;
import com.minidofus.model.spell.AreaStrikeSpell;
import com.minidofus.model.spell.LineSpell;
import com.minidofus.model.spell.NovaSpell;
import com.minidofus.model.spell.OverloadSpell;
import com.minidofus.model.spell.StrikeSpell;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Grimoire de chaque classe : tous ses sorts, le niveau qui les débloque, le sort ultime, et des étiquettes
 * (feu, glace, arcane…) que visent les talents. Les sorts de base de la classe ({@link FighterCatalog}) sont
 * connus dès le niveau 1.
 */
public final class SpellBook {

    /** Sorts équipés au maximum (hors ultime). */
    public static final int MAX_EQUIPPED = 8;

    public record Entry(Spell spell, int level, boolean ultimate, Set<String> tags) {
        public String id() { return spell.id(); }
    }

    private SpellBook() { }

    private static final List<Entry> MAGE = List.of(
            new Entry(spell(FighterCatalog.MAGE, "arcane"), 1, false, Set.of("arcane")),
            new Entry(spell(FighterCatalog.MAGE, "fireball"), 1, false, Set.of("fire")),
            new Entry(spell(FighterCatalog.MAGE, "icezone"), 1, false, Set.of("ice")),
            new Entry(spell(FighterCatalog.MAGE, "teleport"), 1, false, Set.of("arcane")),
            new Entry(spell(FighterCatalog.MAGE, "stoneshield"), 1, false, Set.of("ice", "protection")),
            new Entry(new StrikeSpell(Base.of("frostbolt", "Trait de givre", 3, 1, 6), new IntRange(7, 10), CastStyle.FROST_BOLT)
                    .tuned(new SpellTuning().addOnHit(StatusKind.CHILL, 1, 1)), 3, false, Set.of("ice")),
            new Entry(new StrikeSpell(Base.of("prison", "Prison de glace", 4, 1, 5).cooldown(3), new IntRange(4, 6), CastStyle.ICE_PRISON)
                    .tuned(new SpellTuning().addOnHit(StatusKind.ROOT, 1, 0)), 6, false, Set.of("ice")),
            new Entry(new LineSpell(Base.of("ray", "Rayon arcanique", 4, 1, 5).cooldown(1), new IntRange(10, 14), CastStyle.ARCANE_RAY),
                    10, false, Set.of("arcane")),
            new Entry(new OverloadSpell(Base.of("overload", "Surcharge", 2, 0, 0).cooldown(3), 30), 14, false, Set.of("arcane")),
            new Entry(new NovaSpell(Base.of("nova", "Nova élémentaire", 3, 0, 0).cooldown(2), new IntRange(9, 13), CastStyle.NOVA)
                    .tuned(new SpellTuning().addOnHit(StatusKind.CHILL, 1, 1)), 18, false, Set.of("fire", "ice")),
            new Entry(new AreaStrikeSpell(Base.of("meteor", "Météore", 6, 3, 7).cooldown(6), new IntRange(20, 28),
                    AreaStrikeSpell.Shape.LARGE, CastStyle.METEOR), 25, true, Set.of("fire")));

    /** Tous les sorts de la classe (base et à débloquer), dans l'ordre du grimoire. */
    public static List<Entry> of(String classId) {
        if ("mage".equals(classId)) return MAGE;
        FighterClass c = FighterCatalog.byId(classId);
        List<Entry> out = new ArrayList<>();
        for (Spell s : c.spells()) out.add(new Entry(s, 1, false, Set.of()));
        return out;
    }

    public static Entry entry(String classId, String spellId) {
        for (Entry e : of(classId)) if (e.id().equals(spellId)) return e;
        return null;
    }

    /** Sorts équipés par défaut à ce niveau : les sorts débloqués dans l'ordre, 8 au plus, plus l'ultime. */
    public static List<String> defaultLoadout(String classId, int level) {
        List<String> out = new ArrayList<>();
        int normal = 0;
        for (Entry e : of(classId)) {
            if (e.level() > level) continue;
            if (e.ultimate()) out.add(e.id());
            else if (normal++ < MAX_EQUIPPED) out.add(e.id());
        }
        return out;
    }

    private static Spell spell(FighterClass c, String id) {
        for (Spell s : c.spells()) if (s.id().equals(id)) return s;
        throw new IllegalArgumentException(id);
    }
}
