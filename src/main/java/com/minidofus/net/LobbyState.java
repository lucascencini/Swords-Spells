package com.minidofus.net;

import com.minidofus.model.FighterClass;
import com.minidofus.model.Team;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.MapTheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * État du salon, partagé par le serveur (qui le modifie) et les clients (qui l'affichent).
 * Six places : 0 à 2 pour l'équipe rouge, 3 à 5 pour la bleue ; seules les {@code teamSize} premières de
 * chaque équipe sont utilisées. Une place sans joueur est jouée par l'ordinateur.
 */
public final class LobbyState {

    public static final int SLOTS = 6;
    public static final int[] TIMER_CHOICES = {0, 30, 60, 90};

    /** Joueur connu du salon. */
    public record Player(int id, String name, boolean connected) { }

    public MapTheme map;            // null = au hasard
    public int teamSize = 2;
    public Difficulty difficulty = Difficulty.NORMAL;
    public int turnSeconds = 60;    // 0 = pas de chrono
    public final String[] slotClass = {"warrior", "archer", "mage", "mage", "warrior", "archer"};
    public final int[] slotOwner = {-1, -1, -1, -1, -1, -1};
    public final List<Player> players = new ArrayList<>();
    public int chefId = -1;
    public boolean inMatch;

    public static Team teamOf(int slot) {
        return slot < 3 ? Team.RED : Team.BLUE;
    }

    public static int slotOf(Team team, int index) {
        return (team == Team.RED ? 0 : 3) + index;
    }

    public boolean isUsed(int slot) {
        return slot >= 0 && slot < SLOTS && slot % 3 < teamSize;
    }

    public Player player(int id) {
        for (Player p : players) if (p.id() == id) return p;
        return null;
    }

    public String ownerName(int slot) {
        Player p = player(slotOwner[slot]);
        return p == null ? null : p.name();
    }

    public List<FighterClass> classes(Team team) {
        List<FighterClass> list = new ArrayList<>();
        for (int i = 0; i < teamSize; i++) list.add(FighterCatalog.byId(slotClass[slotOf(team, i)]));
        return list;
    }

    // ------------------------------------------------------------------ texte

    /** Encodage sur un seul argument de message (champs séparés par « ; », listes par « , », joueurs par « | »). */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append(map == null ? "random" : map.name()).append(';')
                .append(teamSize).append(';')
                .append(difficulty.name()).append(';')
                .append(turnSeconds).append(';')
                .append(String.join(",", slotClass)).append(';');
        for (int i = 0; i < SLOTS; i++) sb.append(i > 0 ? "," : "").append(slotOwner[i]);
        sb.append(';').append(chefId).append(';').append(inMatch ? 1 : 0).append(';');
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (i > 0) sb.append('|');
            sb.append(p.id()).append(':').append(p.connected() ? 1 : 0).append(':').append(clean(p.name()));
        }
        return sb.toString();
    }

    public static LobbyState decode(String text) {
        LobbyState s = new LobbyState();
        String[] f = text.split(";", -1);
        s.map = "random".equals(f[0]) ? null : MapTheme.valueOf(f[0]);
        s.teamSize = Integer.parseInt(f[1]);
        s.difficulty = Difficulty.valueOf(f[2]);
        s.turnSeconds = Integer.parseInt(f[3]);
        String[] classes = f[4].split(",");
        System.arraycopy(classes, 0, s.slotClass, 0, SLOTS);
        String[] owners = f[5].split(",");
        for (int i = 0; i < SLOTS; i++) s.slotOwner[i] = Integer.parseInt(owners[i]);
        s.chefId = Integer.parseInt(f[6]);
        s.inMatch = "1".equals(f[7]);
        if (f.length > 8 && !f[8].isEmpty()) {
            for (String p : f[8].split("\\|")) {
                String[] q = p.split(":", 3);
                s.players.add(new Player(Integer.parseInt(q[0]), q[2], "1".equals(q[1])));
            }
        }
        return s;
    }

    /** Les pseudos ne peuvent pas contenir les séparateurs de l'encodage. */
    public static String clean(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            if (c == ';' || c == '|' || c == ':' || c == ',' || Character.isISOControl(c)) continue;
            sb.append(c);
        }
        String s = sb.toString().trim();
        return s.length() > Protocol.MAX_NAME ? s.substring(0, Protocol.MAX_NAME) : s;
    }

    public LobbyState copy() {
        return decode(encode());
    }

    @Override
    public String toString() {
        return "Lobby" + Arrays.toString(slotOwner) + players;
    }
}
