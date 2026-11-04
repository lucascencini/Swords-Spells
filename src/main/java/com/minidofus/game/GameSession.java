package com.minidofus.game;

import com.minidofus.engine.Assets;
import com.minidofus.engine.SoundPlayer;
import com.minidofus.model.Battle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Spell;
import com.minidofus.model.Team;
import com.minidofus.model.ai.AiAction;
import com.minidofus.model.ai.AiPlayer;
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.MapGenerator;
import com.minidofus.ui.Hud;
import com.minidofus.ui.HudModel;
import com.minidofus.view.BattleScene;
import com.minidofus.view.Facing;
import com.minidofus.view.FighterView;
import com.minidofus.view.Highlights;
import com.minidofus.view.PoseTable;
import com.minidofus.view.anim.AnimationQueue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Une partie : la carte, le combat, sa représentation, le contrôleur du joueur humain et, le cas
 * échéant, l'ordinateur qui joue une équipe.
 */
public final class GameSession implements HudModel {

    /** Pause avant chaque action de l'ordinateur, pour que le joueur puisse suivre. */
    private static final double AI_THINK_SECONDS = 0.35;
    private static final int AI_MAX_ACTIONS_PER_TURN = 14;

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

    public GameSession(MatchSetup setup, Assets assets, PoseTable poses, SoundPlayer sound, Hud hud) {
        this.setup = setup;
        this.sound = sound;
        Arena arena = MapGenerator.generate(setup.theme(), setup.seed());
        List<Fighter> fighters = new ArrayList<>();
        for (Team team : Team.values()) {
            List<FighterClass> classes = setup.team(team);
            for (int i = 0; i < classes.size(); i++) {
                Fighter f = new Fighter(classes.get(i), team, i, arena.spawns(team).get(i));
                fighters.add(f);
                views.add(new FighterView(f, team == Team.RED ? Facing.SE : Facing.NW));
            }
            if (setup.ai(team) != null) ai.put(team, new AiPlayer(setup.ai(team), new Random(setup.seed() * 31 + team.ordinal())));
        }
        this.battle = new Battle(arena.board(), fighters, new Random());
        this.scene = new BattleScene(assets, poses, arena, views);
        this.controller = new TurnController(battle);
        battle.addListener(new BattlePresenter(scene, animations, sound, assets, hud, setup.theme().displayName()));
        battle.start();
    }

    public BattleScene scene() { return scene; }
    public Highlights highlights() { return controller.highlights(); }
    public MatchSetup setup() { return setup; }
    Battle battle() { return battle; }

    public int spellCount() {
        return battle.active().fighterClass().spells().size();
    }

    public void update(double dt) {
        animations.update(dt);
        scene.update(dt);
        controller.update(dt);
        if (!animations.isBusy() && !battle.isOver() && isAiTurn()) runAi(dt);
        controller.refresh(isBusy() || isAiTurn());
    }

    private boolean isAiTurn() {
        return !battle.isOver() && ai.containsKey(battle.active().team());
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

    // ------------------------------------------------------------------ commandes du joueur humain

    private boolean acceptsInput() {
        return !isBusy() && !isAiTurn();
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
    @Override public boolean isHumanTurn() { return !isAiTurn(); }
    @Override public boolean isOver() { return battle.isOver(); }
    @Override public Team winner() { return battle.winner(); }
    @Override public Team soloHumanTeam() { return setup.soloHumanTeam(); }
    @Override public Spell selectedSpell() { return controller.selected(); }
    @Override public String unavailableReason(Spell spell) { return battle.unavailableReason(spell); }
    @Override public String mapName() { return setup.theme().displayName(); }
    @Override public long seed() { return setup.seed(); }
    @Override public boolean isMuted() { return sound.isMuted(); }
    @Override public String hint() { return isAiTurn() ? "" : controller.hint(); }
}
