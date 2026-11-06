package com.minidofus.model;

import com.minidofus.model.ai.AiAction;
import com.minidofus.model.ai.AiPlayer;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.MapGenerator;
import com.minidofus.model.map.MapTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulation d'équilibrage : l'ordinateur (niveau Difficile des deux côtés) joue des combats entre classes,
 * sur toutes les cartes, en alternant les côtés. Affiche pour chaque classe son taux de victoire en 1 contre 1
 * (par adversaire et au total) et en 2 contre 2 (équipes tirées dans toutes les compositions), avec la durée
 * moyenne des combats et les dégâts infligés.
 *
 * <pre>java -cp build/classes:build/test com.minidofus.model.BalanceSim [parties par affiche] [fichier csv]</pre>
 */
public final class BalanceSim {

    private BalanceSim() { }

    private record Result(Team winner, int rounds, Map<String, Integer> damageByClass, Map<String, Integer> deathsByClass) { }

    public static void main(String[] args) throws Exception {
        int perPairing = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        List<FighterClass> all = FighterCatalog.ALL;
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
        long t0 = System.nanoTime();
        AtomicLong seeds = new AtomicLong(1000);

        // ---- 1 contre 1 : chaque paire, chaque carte, chaque côté
        Map<String, int[]> duel = new ConcurrentHashMap<>();           // "A>B" -> [victoires de A, parties]
        Map<String, double[]> perClass = new ConcurrentHashMap<>();    // classe -> [victoires, parties, dégâts, rounds]
        List<Future<?>> jobs = new ArrayList<>();
        for (FighterClass a : all) {
            for (FighterClass b : all) {
                if (a == b) continue;
                for (MapTheme theme : MapTheme.values()) {
                    for (int i = 0; i < perPairing; i++) {
                        long seed = seeds.incrementAndGet();
                        boolean aRed = i % 2 == 0;
                        jobs.add(pool.submit(() -> {
                            Result r = play(theme, seed, List.of(aRed ? a : b), List.of(aRed ? b : a));
                            boolean aWon = r.winner() == (aRed ? Team.RED : Team.BLUE);
                            duel.computeIfAbsent(a.id() + ">" + b.id(), k -> new int[2]);
                            synchronized (duel) {
                                int[] d = duel.get(a.id() + ">" + b.id());
                                if (aWon) d[0]++;
                                d[1]++;
                                double[] c = perClass.computeIfAbsent("1v1 " + a.id(), k -> new double[4]);
                                if (aWon) c[0]++;
                                c[1]++;
                                c[2] += r.damageByClass().getOrDefault(a.id(), 0);
                                c[3] += r.rounds();
                            }
                        }));
                    }
                }
            }
        }
        for (Future<?> f : jobs) f.get();

        // ---- 2 contre 2 : toutes les compositions (avec répétition) contre toutes les autres
        List<List<FighterClass>> teams = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) for (int j = i; j < all.size(); j++) teams.add(List.of(all.get(i), all.get(j)));
        Map<String, double[]> team2 = new ConcurrentHashMap<>();
        jobs.clear();
        int per2 = Math.max(1, perPairing / 5);
        for (List<FighterClass> red : teams) {
            for (List<FighterClass> blue : teams) {
                if (red == blue) continue;
                for (MapTheme theme : MapTheme.values()) {
                    for (int i = 0; i < per2; i++) {
                        long seed = seeds.incrementAndGet();
                        jobs.add(pool.submit(() -> {
                            Result r = play(theme, seed, red, blue);
                            synchronized (team2) {
                                for (Team t : Team.values()) {
                                    List<FighterClass> mine = t == Team.RED ? red : blue;
                                    for (FighterClass c : mine) {
                                        double[] v = team2.computeIfAbsent(c.id(), k -> new double[3]);
                                        if (r.winner() == t) v[0]++;
                                        v[1]++;
                                    }
                                }
                                for (Map.Entry<String, Integer> e : r.deathsByClass().entrySet()) {
                                    team2.computeIfAbsent(e.getKey(), k -> new double[3])[2] += e.getValue();
                                }
                            }
                        }));
                    }
                }
            }
        }
        for (Future<?> f : jobs) f.get();
        pool.shutdown();

        // ---- rapport
        System.out.println("\n=== 1 contre 1 (Difficile contre Difficile, " + perPairing + " parties par affiche et par carte) ===");
        System.out.printf(Locale.ROOT, "%-9s", "");
        for (FighterClass b : all) System.out.printf(Locale.ROOT, "%10s", "vs " + b.id());
        System.out.printf(Locale.ROOT, "%10s%12s%10s%n", "TOTAL", "dégâts/part.", "manches");
        for (FighterClass a : all) {
            System.out.printf(Locale.ROOT, "%-9s", a.id());
            for (FighterClass b : all) {
                if (a == b) {
                    System.out.printf(Locale.ROOT, "%10s", "—");
                    continue;
                }
                int[] d = duel.get(a.id() + ">" + b.id());
                System.out.printf(Locale.ROOT, "%9.0f%%", 100.0 * d[0] / d[1]);
            }
            double[] c = perClass.get("1v1 " + a.id());
            System.out.printf(Locale.ROOT, "%9.0f%%%12.0f%10.1f%n", 100 * c[0] / c[1], c[2] / c[1], c[3] / c[1]);
        }
        System.out.println("\n=== 2 contre 2 (toutes les compositions, " + per2 + " parties par affiche et par carte) ===");
        Map<String, double[]> sorted = new TreeMap<>(team2);
        for (Map.Entry<String, double[]> e : sorted.entrySet()) {
            double[] v = e.getValue();
            System.out.printf(Locale.ROOT, "%-9s victoires %5.1f %%   morts par partie %.2f   (%d présences)%n",
                    e.getKey(), 100 * v[0] / v[1], v[2] / v[1], (int) v[1]);
        }
        System.out.printf(Locale.ROOT, "%n%d parties en %.0f s%n", seeds.get() - 1000, (System.nanoTime() - t0) / 1e9);
    }

    private static Result play(MapTheme theme, long seed, List<FighterClass> red, List<FighterClass> blue) {
        Arena arena = MapGenerator.generate(theme, seed);
        List<Fighter> fighters = new ArrayList<>();
        for (int i = 0; i < red.size(); i++) fighters.add(new Fighter(red.get(i), Team.RED, i, arena.redSpawns().get(i)));
        for (int i = 0; i < blue.size(); i++) fighters.add(new Fighter(blue.get(i), Team.BLUE, i, arena.blueSpawns().get(i)));
        Battle b = new Battle(arena.board(), fighters, new java.util.Random(seed));
        b.start();
        AiPlayer redAi = new AiPlayer(Difficulty.HARD, new java.util.Random(seed * 31));
        AiPlayer blueAi = new AiPlayer(Difficulty.HARD, new java.util.Random(seed * 17));
        int actions = 0;
        while (!b.isOver() && b.round() <= 40) {
            AiPlayer ai = b.active().team() == Team.RED ? redAi : blueAi;
            AiAction a = ai.decide(b);
            if (a instanceof AiAction.EndTurn || actions >= 14 || !b.apply(a.toCommand(b))) {
                if (!b.isOver()) b.endTurn();
                actions = 0;
            } else {
                actions++;
            }
        }
        Map<String, Integer> dmg = new TreeMap<>();
        Map<String, Integer> deaths = new TreeMap<>();
        for (Fighter f : b.fighters()) {
            dmg.merge(f.classId(), f.stats().damageDealt(), Integer::sum);
            if (!f.isAlive()) deaths.merge(f.classId(), 1, Integer::sum);
        }
        // nul (limite de manches) : on départage aux PV restants en proportion
        Team winner = b.winner();
        if (winner == null) {
            double red0 = 0;
            double blue0 = 0;
            for (Fighter f : b.fighters()) {
                if (f.team() == Team.RED) red0 += f.hp() / (double) f.maxHp(); else blue0 += f.hp() / (double) f.maxHp();
            }
            winner = red0 >= blue0 ? Team.RED : Team.BLUE;
        }
        return new Result(winner, b.round(), dmg, deaths);
    }
}
