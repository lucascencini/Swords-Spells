package com.minidofus.model.progression;

import java.util.Map;

/**
 * Validation d'une répartition de talents (exécutée par le serveur, qui fait autorité, et par l'écran de talents) :
 * talents connus de la classe, rangs, points disponibles au niveau, niveau requis, points investis dans les
 * branches (hors ce talent) et talents parents.
 */
public final class TalentValidator {

    private TalentValidator() { }

    /** Raison du refus, ou {@code null} si la répartition est valide. */
    public static String validate(String classId, int level, Map<String, Integer> talents) {
        TalentCatalog.Tree tree = TalentCatalog.of(classId);
        int spent = 0;
        for (Map.Entry<String, Integer> e : talents.entrySet()) {
            int rank = e.getValue();
            if (rank == 0) continue;
            TalentDef t = tree.talent(e.getKey());
            if (t == null) return "Talent inconnu : " + e.getKey();
            if (rank < 0 || rank > t.maxRank()) return t.name() + " : rang " + rank + " impossible (max " + t.maxRank() + ")";
            spent += rank * t.cost();
            String why = unmet(t, level, talents);
            if (why != null) return t.name() + " : " + why;
        }
        int available = Progression.talentPoints(level);
        if (spent > available) return "Points de talent insuffisants (" + spent + " / " + available + ")";
        return null;
    }

    /** Prérequis non remplis d'un talent, ou {@code null}. */
    public static String unmet(TalentDef t, int level, Map<String, Integer> talents) {
        if (level < t.level()) return "niveau " + t.level() + " requis";
        for (Map.Entry<String, Integer> bp : t.branchPoints().entrySet()) {
            int have = branchPoints(t.classId(), bp.getKey(), talents, t.id());
            if (have < bp.getValue()) return bp.getValue() + " points en " + branchName(t.classId(), bp.getKey()) + " requis (" + have + ")";
        }
        for (String p : t.parents()) {
            if (talents.getOrDefault(p, 0) <= 0) {
                TalentDef parent = TalentCatalog.of(t.classId()).talent(p);
                return "nécessite « " + (parent == null ? p : parent.name()) + " »";
            }
        }
        return null;
    }

    /** Points investis dans une branche, sans compter le talent {@code except}. */
    public static int branchPoints(String classId, String branch, Map<String, Integer> talents, String except) {
        int sum = 0;
        for (TalentDef t : TalentCatalog.of(classId).talents()) {
            if (t.branch().equals(branch) && !t.id().equals(except)) sum += talents.getOrDefault(t.id(), 0) * t.cost();
        }
        return sum;
    }

    public static int spent(String classId, Map<String, Integer> talents) {
        int sum = 0;
        for (TalentDef t : TalentCatalog.of(classId).talents()) sum += talents.getOrDefault(t.id(), 0) * t.cost();
        return sum;
    }

    private static String branchName(String classId, String branch) {
        for (TalentCatalog.Branch b : TalentCatalog.of(classId).branches()) if (b.id().equals(branch)) return b.name();
        return branch;
    }
}
