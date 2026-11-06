package com.minidofus.game;

import com.minidofus.engine.Assets;
import com.minidofus.engine.SoundPlayer;
import com.minidofus.model.Battle;
import com.minidofus.model.Cell;
import com.minidofus.model.Command;
import com.minidofus.model.Fighter;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Spell;
import com.minidofus.model.Team;
import com.minidofus.model.ai.AiAction;
import com.minidofus.model.ai.AiPlayer;
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.MapGenerator;
import com.minidofus.net.MatchInfo;
import com.minidofus.net.MatchReplica;
import com.minidofus.net.Message;
import com.minidofus.ui.Hud;
import com.minidofus.ui.HudModel;
import com.minidofus.view.BattleScene;
import com.minidofus.view.Facing;
import com.minidofus.view.FighterView;
import com.minidofus.view.Highlights;
import com.minidofus.view.PoseTable;
import com.minidofus.view.anim.AnimationQueue;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Une partie : la carte, le combat, sa représentation et le contrôleur du joueur. Deux modes :
 * <ul>
 *   <li>local (un PC) : les équipes humaines jouent à tour de rôle, l'ordinateur joue les autres ;</li>
 *   <li>en ligne : le combat n'avance qu'avec les commandes diffusées par le serveur ; le joueur
 *       n'agit que pendant le tour de ses propres combattants.</li>
 * </ul>
 */
public final class GameSession implements HudModel {

    /** Pause avant chaque action de l'ordinateur, pour que le joueur puisse suivre. */
    private static final double AI_THINK_SECONDS = 0.35;
    private static final int AI_MAX_ACTIONS_PER_TURN = 14;

    /** Lien avec le serveur pour une partie en ligne. */
    public interface OnlineLink {
        int localPlayerId();

        String playerName(int playerId);

        /** Envoie la commande du combattant actif ; {@code index} = rang attendu de la commande. */
        void send(int index, Command command);

        /** Demande au serveur de renvoyer tout le combat (désynchronisation détectée). */
        void resync();
    }

    private final MatchSetup setup;
    private final Battle battle;
    private final BattleScene scene;
    private final AnimationQueue animations = new AnimationQueue();
    private final TurnController controller;
    private final SoundPlayer sound;
    private final List<FighterView> views = new ArrayList<>();
    private final Map<Team, AiPlayer> ai = new EnumMap<>(Team.class);
    private double aiTimer;
    private int aiActions;
    private Fighter aiTurnOwner;
    private double battleSeconds;

    // en ligne
    private final OnlineLink online;
    private final MatchInfo info;
    private final MatchReplica replica;
    private boolean synced;
    private int pending = -1;          // rang de la commande envoyée en attente de diffusion
    private double timerLeft = -1;     // secondes restantes au combattant actif (-1 = pas de chrono)
    private List<String> rewardLines = List.of();
    private String backLabel = "Retour au salon (Échap)";

    /** Partie locale. */
    public GameSession(MatchSetup setup, Assets assets, PoseTable poses, SoundPlayer sound, Hud hud,
                       Consumer<Point2D.Double> focus) {
        this(setup, null, null, assets, poses, sound, hud, focus);
    }

    /** Partie en ligne, créée à partir du message MATCH du serveur. */
    public GameSession(MatchInfo info, OnlineLink link, Assets assets, PoseTable poses, SoundPlayer sound, Hud hud,
                       Consumer<Point2D.Double> focus) {
        this(new MatchSetup(info.theme(), info.seed(),
                        info.red().stream().map(b -> b.toFighterClass(info.levelOverride())).toList(),
                        info.blue().stream().map(b -> b.toFighterClass(info.levelOverride())).toList(), null, null), info, link,
                assets, poses, sound, hud, focus);
    }

    private GameSession(MatchSetup setup, MatchInfo info, OnlineLink link, Assets assets, PoseTable poses,
                        SoundPlayer sound, Hud hud, Consumer<Point2D.Double> focus) {
        this.setup = setup;
        this.sound = sound;
        this.online = link;
        this.info = info;
        Arena arena = MapGenerator.generate(setup.theme(), setup.seed());
        List<Fighter> fighters = new ArrayList<>();
        // même ordre de création que GameServer.createBattle : rouges puis bleus
        for (Team team : Team.values()) {
            List<FighterClass> classes = setup.team(team);
            for (int i = 0; i < classes.size(); i++) {
                Fighter f = new Fighter(classes.get(i), team, i, arena.spawns(team).get(i));
                fighters.add(f);
                views.add(new FighterView(f, team == Team.RED ? Facing.SE : Facing.NW));
            }
            if (link == null && setup.ai(team) != null) {
                ai.put(team, new AiPlayer(setup.ai(team), new Random(setup.seed() * 31 + team.ordinal())));
            }
        }
        // en ligne, la graine fixe le hasard du combat : il est alors identique chez tous les joueurs
        this.battle = new Battle(arena.board(), fighters, link == null ? new Random() : new Random(setup.seed()));
        this.scene = new BattleScene(assets, poses, arena, views);
        this.controller = new TurnController(battle, link == null ? null : new NetworkExecutor());
        battle.addListener(new BattlePresenter(scene, animations, sound, assets, hud, setup.theme().displayName(),
                this::turnSubtitle, focus));
        battle.start();
        this.replica = link == null ? null : new MatchReplica(info, battle);
    }

    public BattleScene scene() { return scene; }
    public Highlights highlights() { return controller.highlights(); }
    public MatchSetup setup() { return setup; }
    Battle battle() { return battle; }
    public boolean isOnline() { return online != null; }

    /** Expérience gagnée, affichée sur l'écran de fin (combat en ligne avec un compte). */
    public void setRewards(List<String> lines) {
        rewardLines = List.copyOf(lines);
    }

    public void setBackLabel(String label) {
        backLabel = label;
    }

    @Override
    public List<String> rewardLines() {
        return rewardLines;
    }

    @Override
    public String backLabel() {
        return backLabel;
    }

    public int spellCount() {
        return battle.active().fighterClass().spells().size();
    }

    public void update(double dt) {
        if (!battle.isOver()) battleSeconds += dt;
        if (timerLeft > 0) timerLeft = Math.max(0, timerLeft - dt);
        animations.update(dt);
        scene.update(dt);
        controller.update(dt);
        if (!animations.isBusy() && !battle.isOver() && isAiTurn()) runAi(dt);
        controller.refresh(isBusy() || !isHumanTurn());
    }

    private boolean isAiTurn() {
        return online == null && !battle.isOver() && ai.containsKey(battle.active().team());
    }

    private void runAi(double dt) {
        if (battle.active() != aiTurnOwner) {
            aiTurnOwner = battle.active();
            aiActions = 0;
            aiTimer = 0;
        }
        aiTimer += dt;
        if (aiTimer < AI_THINK_SECONDS) return;
        aiTimer = 0;
        AiAction action = ai.get(battle.active().team()).decide(battle);
        boolean done = action instanceof AiAction.EndTurn || ++aiActions > AI_MAX_ACTIONS_PER_TURN;
        if (!done && action instanceof AiAction.Move m) done = !battle.move(m.target());
        else if (!done && action instanceof AiAction.Cast c) done = !battle.cast(c.spell(), c.target());
        if (done && !battle.isOver() && battle.active() == aiTurnOwner) battle.endTurn();
    }

    // ------------------------------------------------------------------ en ligne

    /** Applique une commande diffusée par le serveur (message CMD). */
    public void receive(Message cmd) {
        MatchReplica.Result r = replica.apply(cmd);
        if (r == MatchReplica.Result.DESYNC) {
            synced = false;
            pending = -1;
            online.resync();
            return;
        }
        if (r == MatchReplica.Result.APPLIED && pending >= 0 && replica.applied() > pending) pending = -1;
        if (!synced) fastForward();
    }

    /** Fin du rattrapage : le combat est à jour, le joueur peut agir. */
    public void markSynced() {
        fastForward();
        synced = true;
    }

    /** Le serveur a refusé l'action envoyée : on rend la main au joueur. */
    public void rejected() {
        pending = -1;
    }

    public void setTimer(int seconds) {
        timerLeft = seconds <= 0 ? -1 : seconds;
    }

    /** Joue instantanément, sans son, les animations en attente (rattrapage après connexion). */
    private void fastForward() {
        boolean wasSuppressed = sound.isSuppressed();
        sound.setSuppressed(true);
        for (int i = 0; i < 2000 && animations.isBusy(); i++) {
            animations.update(0.25);
            scene.update(0.25);
        }
        scene.update(5);
        sound.setSuppressed(wasSuppressed);
    }

    private boolean isLocalFighter(Fighter f) {
        if (online == null) return !ai.containsKey(f.team());
        int controller = info.controllers()[battle.fighters().indexOf(f)];
        return controller == online.localPlayerId();
    }

    private String controllerName(Fighter f) {
        int id = info.controllers()[battle.fighters().indexOf(f)];
        String name = id < 0 ? null : online.playerName(id);
        return name == null ? "Ordinateur" : name;
    }

    /** Sous-titre de la bannière de début de tour. */
    private String turnSubtitle(Fighter f) {
        if (online != null) return isLocalFighter(f) ? "À vous de jouer !" : "Tour de " + controllerName(f);
        boolean computer = setup.ai(f.team()) != null;
        if (setup.soloHumanTeam() != null) return computer ? "L'ordinateur réfléchit…" : "À vous de jouer !";
        return computer ? "Équipe " + f.team().displayName() + " · ordinateur" : "Équipe " + f.team().displayName();
    }

    /** Exécutant réseau : vérifie l'action localement puis l'envoie au serveur. */
    private final class NetworkExecutor implements TurnController.Executor {
        @Override
        public boolean move(Cell cell) {
            int cost = battle.reach().costTo(cell);
            if (cost <= 0 || cost > battle.active().pm()) return false;
            sendCommand(new Command.Move(cell.x(), cell.y()));
            return true;
        }

        @Override
        public boolean cast(Spell spell, Cell cell) {
            if (!battle.canCast(spell, cell)) return false;
            sendCommand(battle.toCommand(spell, cell));
            return true;
        }

        @Override
        public void endTurn() {
            sendCommand(new Command.EndTurn());
        }

        private void sendCommand(Command c) {
            pending = replica.applied();
            online.send(pending, c);
        }
    }

    // ------------------------------------------------------------------ commandes du joueur

    private boolean acceptsInput() {
        return !isBusy() && isHumanTurn();
    }

    public void hover(Cell cell) {
        controller.setHover(cell);
    }

    public void click(Cell cell) {
        if (acceptsInput()) controller.click(cell);
    }

    public void selectMove() {
        if (acceptsInput()) controller.selectMove();
    }

    public void selectSpell(int index) {
        if (acceptsInput()) controller.selectSpell(index);
    }

    public void cancel() {
        controller.cancel();
    }

    public void endTurn() {
        if (acceptsInput() && !battle.isOver()) controller.endTurn();
    }

    // ------------------------------------------------------------------ HudModel

    @Override public List<FighterView> fighterViews() { return views; }
    @Override public Fighter active() { return battle.active(); }
    @Override public List<Fighter> turnOrder() { return battle.turnOrder(); }
    @Override public int round() { return battle.round(); }
    @Override public boolean isBusy() { return animations.isBusy(); }

    @Override
    public boolean isHumanTurn() {
        if (battle.isOver()) return true;
        if (online == null) return !isAiTurn();
        return synced && pending < 0 && isLocalFighter(battle.active());
    }

    @Override public boolean isOver() { return battle.isOver(); }
    @Override public Team winner() { return battle.winner(); }

    @Override
    public Team soloHumanTeam() {
        if (online == null) return setup.soloHumanTeam();
        Set<Team> mine = new HashSet<>();
        for (Fighter f : battle.fighters()) if (isLocalFighter(f)) mine.add(f.team());
        return mine.size() == 1 ? mine.iterator().next() : null;
    }

    @Override public Spell selectedSpell() { return controller.selected(); }
    @Override public String unavailableReason(Spell spell) { return battle.unavailableReason(spell); }
    @Override public String mapName() { return setup.theme().displayName(); }
    @Override public long seed() { return setup.seed(); }
    @Override public boolean isMuted() { return sound.isMuted(); }
    @Override public double battleSeconds() { return battleSeconds; }
    @Override public boolean online() { return online != null; }
    @Override public int secondsLeft() { return timerLeft < 0 ? -1 : (int) Math.ceil(timerLeft); }

    @Override
    public String controllerLabel(Fighter f) {
        return online == null ? null : controllerName(f);
    }

    @Override
    public String waitingLabel() {
        if (online == null) return "L'ordinateur joue…";
        if (!synced) return "Synchronisation…";
        if (pending >= 0) return "Envoi au serveur…";
        return "Tour de " + controllerName(battle.active()) + "…";
    }

    @Override
    public String hint() {
        if (!isHumanTurn()) return "";
        return controller.hint();
    }
}
