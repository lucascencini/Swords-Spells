package com.minidofus.model.progression;

import com.minidofus.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Talents de chaque classe, lus dans {@code resources/content/talents/<classe>.json}. */
public final class TalentCatalog {

    public record Branch(String id, String name, String theme) { }

    public record Tree(String classId, List<Branch> branches, List<TalentDef> talents) {
        public TalentDef talent(String id) {
            for (TalentDef t : talents) if (t.id().equals(id)) return t;
            return null;
        }
    }

    private static final Map<String, Tree> CACHE = new LinkedHashMap<>();

    private TalentCatalog() { }

    /** Arbre de la classe (vide si la classe n'a pas encore de talents). */
    public static synchronized Tree of(String classId) {
        return CACHE.computeIfAbsent(classId, TalentCatalog::load);
    }

    @SuppressWarnings("unchecked")
    private static Tree load(String classId) {
        try (InputStream in = TalentCatalog.class.getResourceAsStream("/content/talents/" + classId + ".json")) {
            if (in == null) return new Tree(classId, List.of(), List.of());
            Map<String, Object> root = Json.object(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<Branch> branches = new ArrayList<>();
            for (Object o : (List<Object>) root.get("branches")) {
                Map<String, Object> b = (Map<String, Object>) o;
                branches.add(new Branch(Json.string(b.get("id"), ""), Json.string(b.get("name"), ""), Json.string(b.get("theme"), "")));
            }
            List<TalentDef> talents = new ArrayList<>();
            for (Object o : (List<Object>) root.get("talents")) {
                Map<String, Object> t = (Map<String, Object>) o;
                Map<String, Integer> bp = new LinkedHashMap<>();
                if (t.get("branchPoints") instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) bp.put(String.valueOf(e.getKey()), Json.integer(e.getValue(), 0));
                }
                List<String> parents = new ArrayList<>();
                if (t.get("parents") instanceof List<?> l) for (Object p : l) parents.add(String.valueOf(p));
                List<TalentEffect> effects = new ArrayList<>();
                for (Object eo : (List<Object>) t.get("effects")) {
                    Map<String, Object> e = (Map<String, Object>) eo;
                    effects.add(new TalentEffect(Json.string(e.get("type"), ""), Json.string(e.get("target"), null),
                            Json.string(e.get("key"), null), Json.string(e.get("status"), null),
                            Json.integer(e.get("turns"), 1), Json.integer(e.get("value"), 0)));
                }
                talents.add(new TalentDef(Json.string(t.get("id"), ""), Json.string(t.get("name"), ""), classId,
                        Json.string(t.get("branch"), ""), Json.integer(t.get("tier"), 1), Json.integer(t.get("maxRank"), 1),
                        Json.integer(t.get("cost"), 1), Json.integer(t.get("level"), 1), Collections.unmodifiableMap(bp),
                        List.copyOf(parents), Boolean.TRUE.equals(t.get("hybrid")), Json.string(t.get("description"), ""),
                        List.copyOf(effects)));
            }
            return new Tree(classId, List.copyOf(branches), List.copyOf(talents));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Talents illisibles pour " + classId + " : " + e.getMessage(), e);
        }
    }
}
