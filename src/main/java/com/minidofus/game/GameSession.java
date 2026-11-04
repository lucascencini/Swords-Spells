package com.minidofus.game;

import com.minidofus.engine.Assets;
import com.minidofus.engine.SoundPlayer;
import com.minidofus.model.Battle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.Spell;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.Arena;
import com.minidofus.model.map.MapGenerator;
import com.minidofus.model.map.MapTheme;
import com.minidofus.ui.Hud;
import com.minidofus.ui.HudModel;
import com.minidofus.view.BattleScene;
import com.minidofus.view.Facing;
import com.minidofus.view.FighterView;
import com.minidofus.view.Highlights;
import com.minidofus.view.PoseTable;
import com.minidofus.view.anim.AnimationQueue;

import java.util.List;
import java.util.Random;

/** Une partie : la carte, le combat, sa représentation et le contrôleur de tour. */
public final class GameSession implements HudModel {

    private final MapTheme theme;
    private final long seed;
    private final Battle battle;
    private final BattleScene scene;
    private final AnimationQueue animations = new AnimationQueue();
    private final TurnController controller;
    private final SoundPlayer sound;
    private final List<FighterView> views;

    public GameSession(MapTheme theme, long seed, Assets assets, PoseTable poses, SoundPlayer sound, Hud hud) {
        this.theme = theme;
        this.seed = seed;
        this.sound = sound;
        Arena arena = MapGenerator.generate(theme, seed);
        Fighter warrior = new Fighter(FighterCatalog.WARRIOR, arena.spawnA());
        Fighter mage = new Fighter(FighterCatalog.MAGE, arena.spawnB());
        this.views = List.of(new FighterView(warrior, Facing.SE), new FighterView(mage, Facing.NW));
        this.battle = new Battle(arena.board(), List.of(warrior, mage), new Random());
        this.scene = new BattleScene(assets, poses, arena, views);
        this.controller = new TurnController(battle);
        battle.addListener(new BattlePresenter(scene, animations, sound, assets, hud, theme.displayName()));
        battle.start();
    }

    public BattleScene scene() { return scene; }
    Battle battle() { return battle; }
    public Highlights highlights() { return controller.highlights(); }
    public MapTheme theme() { return theme; }

    public void update(double dt) {
        animations.update(dt);
        scene.update(dt);
        controller.refresh(isBusy());
    }

    // ------------------------------------------------------------------ commandes du joueur

    public void hover(Cell cell) {
        controller.setHover(cell);
    }

    public void click(Cell cell) {
        if (!isBusy()) controller.click(cell);
    }

    public void selectMove() {
        if (!isBusy()) controller.selectMove();
    }

    public void selectSpell(int index) {
        if (!isBusy()) controller.selectSpell(index);
    }

    public void cancel() {
        controller.cancel();
    }

    public void endTurn() {
        if (!isBusy() && !battle.isOver()) controller.endTurn();
    }

    public int spellCount() {
        return battle.active().fighterClass().spells().size();
    }

    // ------------------------------------------------------------------ HudModel

    @Override public List<FighterView> fighterViews() { return views; }
    @Override public Fighter active() { return battle.active(); }
    @Override public int turn() { return battle.turn(); }
    @Override public boolean isBusy() { return animations.isBusy(); }
    @Override public boolean isOver() { return battle.isOver(); }
    @Override public Fighter winner() { return battle.winner(); }
    @Override public Spell selectedSpell() { return controller.selected(); }
    @Override public String mapName() { return theme.displayName(); }
    @Override public long seed() { return seed; }
    @Override public boolean isMuted() { return sound.isMuted(); }
    @Override public String hint() { return controller.hint(isBusy()); }
}
