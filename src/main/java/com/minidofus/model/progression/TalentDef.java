package com.minidofus.model.progression;

import java.util.List;
import java.util.Map;

/**
 * Talent d'une classe : branche, palier, rangs, coût par rang et prérequis (niveau, points investis dans des
 * branches, talents parents). Ses effets sont génériques (voir {@link TalentEffect}) : le moteur de combat
 * n'a aucune condition propre à un talent.
 */
public record TalentDef(String id, String name, String classId, String branch, int tier, int maxRank, int cost,
                        int level, Map<String, Integer> branchPoints, List<String> parents, boolean hybrid,
                        String description, List<TalentEffect> effects) { }
