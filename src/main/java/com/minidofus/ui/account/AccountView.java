package com.minidofus.ui.account;

import com.minidofus.model.FighterClass;
import com.minidofus.model.progression.CharacterBuild;
import com.minidofus.model.progression.Progression;
import com.minidofus.model.progression.TalentValidator;
import com.minidofus.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Compte et personnages tels que le serveur les a envoyés (message ACCOUNT) : affichage seulement. */
public record AccountView(String name, List<Hero> characters) {

    /** Personnage vu par le client. */
    public record Hero(String id, String name, String classId, int level, int xp, Map<String, Integer> talents,
                       List<String> spells, int wins, int losses) {

        public CharacterBuild build() {
            return new CharacterBuild(name, classId, level, talents, spells);
        }

        public FighterClass fighter() {
            return build().toFighterClass();
        }

        public int xpToNext() {
            return Progression.xpToNext(level);
        }

        public int freePoints() {
            return Progression.talentPoints(level) - TalentValidator.spent(classId, talents);
        }
    }

    @SuppressWarnings("unchecked")
    public static AccountView parse(String json) {
        Map<String, Object> root = Json.object(json);
        List<Hero> heroes = new ArrayList<>();
        for (Object o : (List<Object>) root.getOrDefault("characters", List.of())) {
            Map<String, Object> c = (Map<String, Object>) o;
            Map<String, Integer> talents = new TreeMap<>();
            if (c.get("talents") instanceof Map<?, ?> t) {
                for (Map.Entry<?, ?> e : t.entrySet()) talents.put(String.valueOf(e.getKey()), Json.integer(e.getValue(), 0));
            }
            List<String> spells = new ArrayList<>();
            if (c.get("spells") instanceof List<?> l) for (Object s : l) spells.add(String.valueOf(s));
            heroes.add(new Hero(Json.string(c.get("id"), ""), Json.string(c.get("name"), ""), Json.string(c.get("classId"), ""),
                    Json.integer(c.get("level"), 1), Json.integer(c.get("xp"), 0), talents, spells,
                    Json.integer(c.get("wins"), 0), Json.integer(c.get("losses"), 0)));
        }
        return new AccountView(Json.string(root.get("name"), ""), List.copyOf(heroes));
    }

    public Hero hero(String id) {
        for (Hero h : characters) if (h.id().equals(id)) return h;
        return null;
    }
}
