package com.minidofus.model.progression;

import com.minidofus.model.FighterClass;
import com.minidofus.model.Spell;
import com.minidofus.model.SpellTuning;
import com.minidofus.model.StatusKind;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.catalog.SpellBook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ce qu'un personnage apporte au combat : nom, classe, niveau, talents (rang par talent) et sorts équipés.
 * C'est une valeur échangée telle quelle entre le serveur et les clients ({@link #encode()}) : tous construisent
 * à partir d'elle exactement le même combattant ({@link #toFighterClass}), ce qui garde le combat en ligne
 * déterministe.
 */
public record CharacterBuild(String name, String classId, int level, Map<String, Integer> talents, List<String> spells) {

    public CharacterBuild {
        talents = new TreeMap<>(talents);
        talents.values().removeIf(v -> v <= 0);
        talents = java.util.Collections.unmodifiableMap(talents);
        spells = List.copyOf(spells);
    }

    /** Combattant de base de la classe, niveau 1, sans talent (partie rapide, invité). */
    public static CharacterBuild basic(String classId) {
        return new CharacterBuild("", classId, 1, Map.of(), List.of());
    }

    /** Nom de personnage sûr : lettres, chiffres, espaces, tirets ; 16 caractères au plus. */
    public static String cleanName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : (raw == null ? "" : raw.trim()).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'') sb.append(c);
        }
        String s = sb.toString().trim().replaceAll(" +", " ");
        return s.length() > 16 ? s.substring(0, 16).trim() : s;
    }

    // ------------------------------------------------------------------ échange

    /** « nom;classe;niveau;talent=rang,…;sort,… » (le nom ne contient ni « ; » ni « , » ni « | »). */
    public String encode() {
        StringBuilder t = new StringBuilder();
        for (Map.Entry<String, Integer> e : talents.entrySet()) {
            if (t.length() > 0) t.append(',');
            t.append(e.getKey()).append('=').append(e.getValue());
        }
        return cleanName(name) + ";" + classId + ";" + level + ";" + t + ";" + String.join(",", spells);
    }

    public static CharacterBuild decode(String s) {
        if (!s.contains(";")) return basic(s);                         // ancien format : identifiant de classe seul
        String[] p = s.split(";", -1);
        Map<String, Integer> talents = new HashMap<>();
        if (p.length > 3 && !p[3].isEmpty()) {
            for (String kv : p[3].split(",")) {
                String[] x = kv.split("=");
                if (x.length == 2) talents.put(x[0], Integer.parseInt(x[1]));
            }
        }
        List<String> spells = p.length > 4 && !p[4].isEmpty() ? List.of(p[4].split(",")) : List.of();
        return new CharacterBuild(p[0], p[1], Integer.parseInt(p[2]), talents, spells);
    }

    // ------------------------------------------------------------------ combat

    /** Combattant au niveau du personnage. */
    public FighterClass toFighterClass() {
        return toFighterClass(0);
    }

    /**
     * Combattant prêt pour le combat. {@code levelOverride} > 0 : niveau imposé (niveaux égalisés en ligne) ; les
     * talents restent ceux du personnage. Statistiques finales = base de classe × niveau × talents.
     */
    public FighterClass toFighterClass(int levelOverride) {
        FighterClass base = FighterCatalog.byId(classId);
        int lvl = Math.max(1, Math.min(Progression.MAX_LEVEL, levelOverride > 0 ? levelOverride : level));
        TalentCatalog.Tree tree = TalentCatalog.of(classId);
        int hpPct = 0;
        int powerPct = 0;
        int resistPct = 0;
        int initiative = 0;
        Map<String, SpellTuning> tunings = new LinkedHashMap<>();
        for (SpellBook.Entry e : SpellBook.of(classId)) tunings.put(e.id(), new SpellTuning());
        for (Map.Entry<String, Integer> te : talents.entrySet()) {
            TalentDef t = tree.talent(te.getKey());
            if (t == null) continue;
            int rank = Math.min(te.getValue(), t.maxRank());
            for (TalentEffect fx : t.effects()) {
                int v = fx.value() * rank;
                switch (fx.type()) {
                    case "hp_pct" -> hpPct += v;
                    case "power_pct" -> powerPct += v;
                    case "resist_pct" -> resistPct += v;
                    case "initiative" -> initiative += v;
                    default -> {
                        for (SpellBook.Entry e : SpellBook.of(classId)) {
                            if (!targets(fx.target(), e)) continue;
                            SpellTuning tu = tunings.get(e.id());
                            switch (fx.type()) {
                                case "spell_power" -> tu.addPower(v);
                                case "spell_range" -> tu.addRange(v);
                                case "spell_cost" -> tu.addCost(v);
                                case "spell_cooldown" -> tu.addCooldown(v);
                                case "spell_param" -> tu.addParam(fx.key(), v);
                                case "spell_onhit" -> tu.addOnHit(StatusKind.valueOf(fx.status()), fx.turns(), v);
                                default -> { }
                            }
                        }
                    }
                }
            }
        }
        // sorts : ceux appris au niveau réel du personnage (l'égalisation ne fait pas apprendre de sorts)
        List<String> chosen = spells.isEmpty() ? SpellBook.defaultLoadout(classId, level) : spells;
        List<Spell> list = new ArrayList<>();
        int normal = 0;
        boolean ultimate = false;
        for (String id : chosen) {
            SpellBook.Entry e = SpellBook.entry(classId, id);
            if (e == null || e.level() > level) continue;                                  // pas encore appris
            if (e.ultimate()) {
                if (ultimate) continue;
                ultimate = true;
            } else if (++normal > SpellBook.MAX_EQUIPPED) {
                continue;
            }
            if (list.stream().anyMatch(s -> s.id().equals(id))) continue;
            list.add(e.spell().tuned(tunings.get(id)));
        }
        if (list.isEmpty()) list.addAll(base.spells());
        int hp = (int) Math.round(base.maxHp() * (1 + Progression.HP_PER_LEVEL * (lvl - 1)) * (1 + hpPct / 100.0));
        double power = (1 + Progression.POWER_PER_LEVEL * (lvl - 1)) * (1 + powerPct / 100.0);
        double resist = Math.min(Progression.MAX_RESIST, resistPct / 100.0);
        String display = name == null || name.isBlank() ? base.displayName() : name;
        return new FighterClass(classId, display, base.displayName(), lvl, hp, base.maxPa(), base.maxPm(),
                base.initiative() + initiative, base.preferredRange(), list, power, resist);
    }

    private static boolean targets(String target, SpellBook.Entry e) {
        if (target == null) return false;
        if (target.startsWith("tag:")) return e.tags().contains(target.substring(4));
        return target.equals(e.id());
    }
}
