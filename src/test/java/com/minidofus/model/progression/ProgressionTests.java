package com.minidofus.model.progression;

import com.minidofus.Check;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Spell;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.SpellBook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Niveaux, expérience, talents (prérequis, hybrides, points), sorts débloqués et construction du combattant. */
public final class ProgressionTests {

    private ProgressionTests() { }

    public static void run() {
        Check.suite("Progression : niveaux et expérience");
        Check.equal(0, Progression.talentPoints(1), "niveau 1 : aucun point de talent");
        Check.equal(4, Progression.talentPoints(5), "niveau 5 : 4 points");
        Check.equal(49, Progression.talentPoints(50), "niveau 50 : 49 points");
        Check.that(Progression.xpToNext(10) > Progression.xpToNext(2), "la courbe d'expérience monte");
        Check.equal(0, Progression.xpToNext(50), "niveau maximum : plus d'expérience à gagner");
        int[] up = Progression.addXp(1, 0, Progression.xpToNext(1) + Progression.xpToNext(2) + 5);
        Check.that(up[0] == 3 && up[1] == 5 && up[2] == 2, "deux niveaux d'un coup, reste conservé");
        Check.that(Progression.reward(5, 5, true, Difficulty.NORMAL) > Progression.reward(5, 5, false, Difficulty.NORMAL),
                "la victoire rapporte plus");
        Check.that(Progression.reward(5, 5, true, Difficulty.HARD) > Progression.reward(5, 5, true, Difficulty.EASY),
                "le niveau difficile rapporte plus");
        Check.that(Progression.reward(5, 9, true, Difficulty.NORMAL) > Progression.reward(5, 5, true, Difficulty.NORMAL),
                "battre plus fort rapporte plus");

        Check.suite("Progression : talents du Mage");
        TalentCatalog.Tree tree = TalentCatalog.of("mage");
        Check.that(tree.talents().size() >= 20, "au moins 20 talents pour le Mage pilote (" + tree.talents().size() + ")");
        Check.equal(3, tree.branches().size(), "3 branches : Pyromancien, Cryomancien, Arcaniste");
        boolean parentsKnown = true;
        for (TalentDef t : tree.talents()) for (String p : t.parents()) parentsKnown &= tree.talent(p) != null;
        Check.that(parentsKnown, "tous les talents parents existent");
        Check.equal(null, TalentValidator.validate("mage", 4, Map.of("mage_pyro_flames", 3)), "3 points au niveau 4");
        Check.that(TalentValidator.validate("mage", 3, Map.of("mage_pyro_flames", 3)) != null, "pas assez de points au niveau 3");
        Check.that(TalentValidator.validate("mage", 50, Map.of("mage_pyro_flames", 4)) != null, "rang maximum respecté");
        Check.that(TalentValidator.validate("mage", 50, Map.of("inconnu", 1)) != null, "talent inconnu refusé");
        Check.that(TalentValidator.validate("mage", 2, Map.of("mage_pyro_reach", 1)) != null, "niveau requis (3) vérifié");
        // Combustion : 5 points en Pyromancien et « Flammes vives » requis
        Map<String, Integer> t = new HashMap<>(Map.of("mage_pyro_combustion", 1, "mage_pyro_focus", 2, "mage_pyro_reach", 1));
        Check.that(TalentValidator.validate("mage", 20, t) != null, "talent parent manquant refusé");
        t.put("mage_pyro_flames", 1);
        Check.that(TalentValidator.validate("mage", 20, t) != null, "4 points en Pyromancien ne suffisent pas (5 requis)");
        t.put("mage_pyro_flames", 2);
        Check.equal(null, TalentValidator.validate("mage", 20, t), "5 points et parent : Combustion accessible");
        // hybride : 8 + 8
        Map<String, Integer> h = new HashMap<>(Map.of("mage_pyro_flames", 3, "mage_pyro_focus", 2, "mage_pyro_reach", 1,
                "mage_pyro_combustion", 2, "mage_hyb_thermal", 1));
        Check.that(TalentValidator.validate("mage", 50, h) != null, "hybride : points en Cryomancien manquants");
        h.putAll(Map.of("mage_cryo_bite", 3, "mage_cryo_lasting", 1, "mage_cryo_granite", 2, "mage_cryo_deep", 1, "mage_cryo_rampart", 1));
        Check.equal(null, TalentValidator.validate("mage", 50, h), "hybride : 8 + 8 points, accessible");

        Check.suite("Progression : sorts et construction du combattant");
        List<String> lvl1 = SpellBook.defaultLoadout("mage", 1);
        Check.equal(5, lvl1.size(), "Mage niveau 1 : 5 sorts");
        List<String> lvl30 = SpellBook.defaultLoadout("mage", 30);
        Check.equal(SpellBook.MAX_EQUIPPED + 1, lvl30.size(), "niveau 30 : 8 sorts + l'ultime");
        Check.that(lvl30.contains("meteor"), "Météore (ultime) débloqué au niveau 25");
        FighterClass base = new CharacterBuild("Merlin", "mage", 1, Map.of(), List.of()).toFighterClass();
        FighterClass high = new CharacterBuild("Merlin", "mage", 21, Map.of(), List.of()).toFighterClass();
        Check.equal(60, base.maxHp(), "niveau 1 : PV de la classe");
        Check.that(high.maxHp() > base.maxHp() && high.power() > base.power(), "niveau 21 : plus de PV et de puissance");
        Check.equal(base.maxPa(), high.maxPa(), "les PA ne montent pas avec le niveau");
        Check.equal("Merlin", base.displayName(), "nom du personnage");
        FighterClass tuned = new CharacterBuild("M", "mage", 10, Map.of("mage_pyro_reach", 1, "mage_pyro_flames", 3), List.of()).toFighterClass();
        Spell fb = tuned.spells().stream().filter(s -> s.id().equals("fireball")).findFirst().orElseThrow();
        Spell fb0 = base.spells().stream().filter(s -> s.id().equals("fireball")).findFirst().orElseThrow();
        Check.equal(fb0.maxRange() + 1, fb.maxRange(), "talent : Boule de feu +1 portée");
        Check.that(!fb.effectSummary().equals(fb0.effectSummary()), "talent : dégâts de la Boule de feu augmentés (" + fb.effectSummary() + ")");
        Check.equal(6, fb0.maxRange(), "le sort du catalogue n'est pas modifié");
        CharacterBuild b = new CharacterBuild("Ana Lys", "mage", 12, Map.of("mage_arc_sharp", 2), List.of("arcane", "ray", "teleport"));
        CharacterBuild back = CharacterBuild.decode(b.encode());
        Check.equal(b, back, "un personnage s'échange sans perte (réseau)");
        Check.equal(3, back.toFighterClass().spells().size(), "seuls les sorts équipés sont emportés");
        Check.equal(3, new CharacterBuild("x", "mage", 2, Map.of(), List.of("arcane", "ray", "teleport", "fireball")).toFighterClass()
                .spells().size(), "un sort pas encore appris (Rayon : niveau 10) est ignoré");
        Check.equal(20, CharacterBuild.basic("mage").toFighterClass(20).level(), "niveaux égalisés : niveau imposé");
    }
}
