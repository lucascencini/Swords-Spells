package com.minidofus.net;

import com.minidofus.Check;
import com.minidofus.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Comptes et progression côté serveur : création, connexion, personnages, talents validés par le serveur,
 * combat solo jusqu'au bout avec expérience enregistrée, persistance après redémarrage, salon avec personnages
 * et niveaux égalisés.
 */
public final class AccountTests {

    private AccountTests() { }

    public static void run() throws Exception {
        Path data = Files.createTempDirectory("spells-accounts");
        Check.suite("Comptes : création et connexion");
        GameServer server = new GameServer(0, "", data, s -> { });
        int port = server.start();
        NetTests.Bot lucas = NetTests.Bot.account(port, "Lucas", "secret1", true);
        List<NetTests.Bot> one = new ArrayList<>(List.of(lucas));
        Check.that(NetTests.await(one, () -> lucas.accountJson != null, 4000), "compte créé, données reçues");
        NetTests.Bot copy = NetTests.Bot.account(port, "lucas", "autre", true);
        Check.that(NetTests.await(List.of(copy), () -> copy.rejected != null, 4000) && copy.rejected.contains("existe"),
                "un nom de compte déjà pris est refusé (sans tenir compte des majuscules)");
        NetTests.Bot thief = NetTests.Bot.account(port, "Zoé", "x", false);
        Check.that(NetTests.await(List.of(thief), () -> thief.rejected != null, 4000), "connexion à un compte inexistant refusée");
        NetTests.Bot guest = new NetTests.Bot(port, "Lucas", "");
        Check.that(NetTests.await(List.of(guest), () -> guest.rejected != null, 4000), "un invité ne peut pas prendre le nom d'un compte");

        Check.suite("Comptes : personnages et talents");
        lucas.send(Protocol.CHAR_NEW, "Merlin", "mage");
        lucas.send(Protocol.CHAR_NEW, "Merlin", "warrior");
        lucas.send(Protocol.CHAR_NEW, "Brok", "warrior");
        lucas.send(Protocol.CHAR_NEW, "  ", "druid");
        Check.that(NetTests.await(one, () -> characters(lucas).size() == 2 && lucas.notices.size() >= 2, 4000),
                "2 personnages créés, nom en double et nom vide refusés");
        String merlin = idOf(lucas, "Merlin");
        String brok = idOf(lucas, "Brok");
        lucas.send(Protocol.CHAR_SAVE, merlin, "mage_pyro_flames=1", "");
        Check.that(NetTests.await(one, () -> lucas.notices.stream().anyMatch(n -> n.contains("Points")), 4000),
                "le serveur refuse un talent sans point disponible (niveau 1)");
        lucas.send(Protocol.CHAR_SAVE, merlin, "", "arcane,ray");
        Check.that(NetTests.await(one, () -> lucas.notices.stream().anyMatch(n -> n.contains("niveau 10")), 4000),
                "le serveur refuse un sort pas encore appris");
        lucas.send(Protocol.CHAR_SAVE, merlin, "", "fireball,arcane,teleport");
        Check.that(NetTests.await(one, () -> spells(lucas, merlin).size() == 3, 4000), "sorts équipés enregistrés");

        Check.suite("Comptes : combat solo et expérience");
        lucas.autoplay = true;
        lucas.send(Protocol.SOLO, 2, "FOREST", "EASY", merlin + "," + brok);
        Check.that(NetTests.await(one, () -> lucas.match != null && lucas.synced, 5000), "combat solo lancé sur le serveur");
        Check.equal("Merlin", lucas.match.battle().fighters().get(0).name(), "le personnage combat sous son nom");
        boolean ended = NetTests.await(one, () -> !lucas.rewards.isEmpty(), 120_000);
        Check.that(ended, "le combat solo se termine et l'expérience arrive");
        Check.equal(0, lucas.desyncs, "aucune désynchronisation");
        List<Object> reward = ended ? (List<Object>) Json.parse(lucas.rewards.get(0)) : List.of();
        Check.equal(2, reward.size(), "les deux personnages gagnent de l'expérience");
        int xpTotal = xpOf(lucas, merlin) + 100 * (levelOf(lucas, merlin) - 1);
        Check.that(NetTests.await(one, () -> xpOf(lucas, merlin) > 0 || levelOf(lucas, merlin) > 1, 4000) && xpTotal > 0,
                "expérience enregistrée sur le personnage");
        lucas.autoplay = false;
        lucas.send(Protocol.SOLO_QUIT);
        int levelBefore = levelOf(lucas, merlin);
        int xpBefore = xpOf(lucas, merlin);
        lucas.match = null;
        lucas.send(Protocol.SOLO, 1, "random", "HARD", merlin);
        Check.that(NetTests.await(one, () -> lucas.match != null, 5000), "second combat solo");
        lucas.send(Protocol.SOLO_QUIT);
        Thread.sleep(300);
        lucas.pump();
        Check.that(levelOf(lucas, merlin) == levelBefore && xpOf(lucas, merlin) == xpBefore, "un combat abandonné ne rapporte rien");
        lucas.net.close();
        server.close();

        Check.suite("Comptes : persistance après redémarrage");
        GameServer again = new GameServer(0, "", data, s -> { });
        int port2 = again.start();
        NetTests.Bot back = NetTests.Bot.account(port2, "Lucas", "secret1", false);
        List<NetTests.Bot> b = new ArrayList<>(List.of(back));
        Check.that(NetTests.await(b, () -> back.accountJson != null, 4000), "reconnexion au compte après redémarrage");
        Check.that(levelOf(back, idOf(back, "Merlin")) == levelBefore && xpOf(back, idOf(back, "Merlin")) == xpBefore,
                "niveau et expérience conservés");
        Check.equal(3, spells(back, idOf(back, "Merlin")).size(), "sorts équipés conservés");
        NetTests.Bot wrong = NetTests.Bot.account(port2, "Lucas", "mauvais", false);
        Check.that(NetTests.await(List.of(wrong), () -> wrong.rejected != null, 4000) && wrong.rejected.contains("incorrect"),
                "mauvais mot de passe refusé");
        boolean noPlainPassword = true;
        try (var files = Files.walk(data)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) noPlainPassword &= !Files.readString(p).contains("secret1");
        }
        Check.that(noPlainPassword, "le mot de passe n'apparaît jamais dans la base");

        Check.suite("Comptes : salon avec personnages et niveaux égalisés");
        NetTests.Bot ana = NetTests.Bot.account(port2, "Ana", "pw", true);
        b.add(ana);
        NetTests.await(b, () -> ana.accountJson != null && back.lobby != null && back.lobby.players.size() == 2, 4000);
        NetTests.Bot chef = back.lobby.chefId == back.net.playerId() ? back : ana;
        chef.send(Protocol.SETTINGS, "FOREST", 1, "NORMAL", 0, "EQUAL");
        back.send(Protocol.CLAIM, 0);
        NetTests.await(b, () -> back.lobby.slotOwner[0] >= 0, 4000);
        back.send(Protocol.CLASS, 0, "char:" + idOf(back, "Merlin"));
        Check.that(NetTests.await(b, () -> back.lobby.slotLabel[0].startsWith("Merlin"), 4000), "personnage posé sur une place du salon");
        chef.send(Protocol.START);
        Check.that(NetTests.await(b, () -> ana.match != null && ana.synced, 5000), "combat du salon lancé");
        Check.equal(levelBefore, ana.match.battle().fighters().get(1).fighterClass().level(),
                "niveaux égalisés : l'ordinateur en face est au niveau de Merlin");
        for (NetTests.Bot x : b) x.net.close();
        again.close();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> characters(NetTests.Bot bot) {
        bot.pump();
        if (bot.accountJson == null) return List.of();
        return (List<Map<String, Object>>) Json.object(bot.accountJson).get("characters");
    }

    private static Map<String, Object> character(NetTests.Bot bot, String id) {
        for (Map<String, Object> c : characters(bot)) if (id.equals(c.get("id"))) return c;
        return Map.of();
    }

    private static String idOf(NetTests.Bot bot, String name) {
        for (Map<String, Object> c : characters(bot)) if (name.equals(c.get("name"))) return (String) c.get("id");
        return "?";
    }

    private static int levelOf(NetTests.Bot bot, String id) {
        return Json.integer(character(bot, id).get("level"), 0);
    }

    private static int xpOf(NetTests.Bot bot, String id) {
        return Json.integer(character(bot, id).get("xp"), 0);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> spells(NetTests.Bot bot, String id) {
        Object s = character(bot, id).get("spells");
        return s instanceof List<?> l ? (List<Object>) l : List.of();
    }
}
