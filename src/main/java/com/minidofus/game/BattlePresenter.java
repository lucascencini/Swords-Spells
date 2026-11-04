package com.minidofus.game;

import com.minidofus.engine.Assets;
import com.minidofus.engine.Easing;
import com.minidofus.engine.Sfx;
import com.minidofus.engine.SoundPlayer;
import com.minidofus.model.BattleEvent;
import com.minidofus.model.CastStyle;
import com.minidofus.model.Cell;
import com.minidofus.model.Fighter;
import com.minidofus.model.MoveStyle;
import com.minidofus.ui.Hud;
import com.minidofus.ui.UiTheme;
import com.minidofus.view.BattleScene;
import com.minidofus.view.FighterView;
import com.minidofus.view.Iso;
import com.minidofus.view.anim.Animation;
import com.minidofus.view.anim.AnimationQueue;
import com.minidofus.view.fx.Burst;
import com.minidofus.view.fx.FloatingText;
import com.minidofus.view.fx.Projectile;
import com.minidofus.view.fx.RingPulse;
import com.minidofus.view.fx.Slash;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.minidofus.view.anim.Animations.call;
import static com.minidofus.view.anim.Animations.defer;
import static com.minidofus.view.anim.Animations.delay;
import static com.minidofus.view.anim.Animations.sequence;
import static com.minidofus.view.anim.Animations.tween;

/**
 * Traduit chaque {@link BattleEvent} en animation, sons et effets, et les met en file d'attente.
 * C'est le seul endroit qui décide « à quoi ressemble » une action : le modèle, lui, ne sait rien
 * de l'affichage.
 */
final class BattlePresenter implements Consumer<BattleEvent> {

    private static final double WALK_STEP = 0.17;
    private static final double DASH_STEP = 0.075;

    private final BattleScene scene;
    private final AnimationQueue queue;
    private final SoundPlayer sound;
    private final Assets assets;
    private final Hud hud;
    private final String mapName;

    BattlePresenter(BattleScene scene, AnimationQueue queue, SoundPlayer sound, Assets assets, Hud hud, String mapName) {
        this.scene = scene;
        this.queue = queue;
        this.sound = sound;
        this.assets = assets;
        this.hud = hud;
        this.mapName = mapName;
    }

    @Override
    public void accept(BattleEvent event) {
        queue.add(animationFor(event));
    }

    private Animation animationFor(BattleEvent event) {
        if (event instanceof BattleEvent.TurnStarted e) return turnStarted(e);
        if (event instanceof BattleEvent.Moved e) return moved(e);
        if (event instanceof BattleEvent.SpellCast e) return spellCast(e);
        if (event instanceof BattleEvent.Damaged e) return damaged(e);
        if (event instanceof BattleEvent.Healed e) return healed(e);
        if (event instanceof BattleEvent.ShieldChanged e) return shieldChanged(e);
        if (event instanceof BattleEvent.BattleEnded e) return battleEnded(e);
        return call(() -> { });
    }

    // ------------------------------------------------------------------ événements

    private Animation turnStarted(BattleEvent.TurnStarted e) {
        return call(() -> {
            Fighter f = e.fighter();
            Point2D.Double p = view(f).worldFeet();
            scene.effects().add(new RingPulse(p.x, p.y, UiTheme.teamColor(f.id()), 0.7));
            Color c = UiTheme.teamTextColor(f.id());
            if (e.turn() == 1) {
                hud.showBanner(mapName, "Le " + f.name() + " commence !", UiTheme.GOLD_LIGHT, 2.2);
            } else {
                hud.showBanner("Tour " + ((e.turn() + 1) / 2) + "  —  " + f.name(), null, c, 1.1);
            }
            Sfx.TURN.play(sound);
        });
    }

    private Animation moved(BattleEvent.Moved e) {
        return defer(() -> {
            FighterView v = view(e.fighter());
            boolean dash = e.style() == MoveStyle.DASH;
            double step = dash ? DASH_STEP : WALK_STEP;
            double hopHeight = dash ? 3 : 7;
            List<Animation> steps = new ArrayList<>();
            Cell[] from = {new Cell((int) Math.round(v.gx()), (int) Math.round(v.gy()))};
            if (!dash) steps.add(call(() -> Sfx.MOVE.play(sound)));
            for (Cell to : e.path()) {
                Cell start = from[0];
                steps.add(call(() -> v.face(start, to)));
                steps.add(tween(step, t -> {
                    double k = dash ? Easing.inOutQuad(t) : t;
                    v.setGridPosition(Easing.lerp(start.x(), to.x(), k), Easing.lerp(start.y(), to.y(), k),
                            Math.sin(t * Math.PI) * hopHeight);
                }));
                from[0] = to;
            }
            steps.add(call(() -> v.snapTo(e.path().get(e.path().size() - 1))));
            return sequence(steps.toArray(Animation[]::new));
        });
    }

    private Animation spellCast(BattleEvent.SpellCast e) {
        FighterView caster = view(e.caster());
        Cell target = e.target();
        Animation face = call(() -> {
            Cell from = e.caster().position().equals(target) ? null : new Cell((int) Math.round(caster.gx()), (int) Math.round(caster.gy()));
            if (from != null) caster.face(from, target);
        });
        CastStyle style = e.spell().style();
        return switch (style) {
            case MELEE -> sequence(face, call(() -> {
                Sfx.MELEE.play(sound);
                caster.lunge();
            }), delay(0.12));
            case ARCANE_BOLT -> sequence(face, call(() -> Sfx.PROJECTILE.play(sound)),
                    projectile(caster, target, "fx/arcane", 0.34, 26, 0.5, false),
                    call(() -> burst(target, "fx/arcane", 0.4, 0.9, 0.35, 0)));
            case FIREBALL -> sequence(face, call(() -> Sfx.FIREBALL.play(sound)),
                    projectile(caster, target, "fx/fire_projectile", 0.42, 34, 0.6, true),
                    call(() -> {
                        Sfx.FIREBALL_IMPACT.play(sound);
                        for (Cell c : e.impact()) {
                            boolean center = c.equals(target);
                            burst(c, "fx/fire_explosion", center ? 0.45 : 0.3, center ? 1.35 : 0.95, 0.45, center ? 0 : 0.06);
                        }
                    }), delay(0.15));
            case DASH -> sequence(face, call(() -> Sfx.CHARGE.play(sound)));
            case SHIELD -> sequence(call(() -> {
                Sfx.BUFF.play(sound);
                Point2D.Double p = caster.worldFeet();
                scene.effects().add(new RingPulse(p.x, p.y, UiTheme.SHIELD, 0.6));
            }), delay(0.25));
            case HEAL -> sequence(call(() -> {
                Sfx.BUFF.play(sound);
                Point2D.Double p = caster.worldFeet();
                scene.effects().add(new RingPulse(p.x, p.y, UiTheme.HEAL, 0.6));
            }), delay(0.15));
        };
    }

    private Animation damaged(BattleEvent.Damaged e) {
        return sequence(call(() -> {
            FighterView v = view(e.target());
            Point2D.Double feet = v.worldFeet();
            if (e.style() == CastStyle.MELEE || e.style() == CastStyle.DASH) {
                FighterView src = view(e.source());
                scene.effects().add(new Slash(feet.x, feet.y, src.gx() > v.gx() || src.gy() < v.gy()));
            }
            if (e.style() == CastStyle.DASH) Sfx.CHARGE_IMPACT.play(sound);
            Sfx.HIT.play(sound, 0.05);
            v.flashDamage();
            v.setShownHp(e.hpAfter());
            Point2D.Double head = v.worldHead();
            scene.effects().add(new FloatingText("-" + e.amount(), head.x, head.y,
                    e.heavy() ? UiTheme.DAMAGE_HEAVY : UiTheme.DAMAGE, e.heavy() ? 30 : 24));
        }), delay(0.3));
    }

    private Animation healed(BattleEvent.Healed e) {
        return sequence(call(() -> {
            FighterView v = view(e.target());
            v.flashHeal();
            v.setShownHp(e.hpAfter());
            Point2D.Double head = v.worldHead();
            scene.effects().add(new FloatingText("+" + e.amount(), head.x, head.y, UiTheme.HEAL, 24));
        }), delay(0.3));
    }

    private Animation shieldChanged(BattleEvent.ShieldChanged e) {
        return call(() -> {
            FighterView v = view(e.fighter());
            v.setShielded(e.active());
            Point2D.Double head = v.worldHead();
            scene.effects().add(new FloatingText(e.active() ? "Bouclier -50 %" : "Bouclier dissipé",
                    head.x, head.y - 4, UiTheme.SHIELD, 16));
        });
    }

    private Animation battleEnded(BattleEvent.BattleEnded e) {
        return sequence(delay(0.6), call(() -> {
            Sfx.VICTORY.play(sound);
            Sfx.VICTORY_COINS.play(sound, 0.25);
        }));
    }

    // ------------------------------------------------------------------ outils

    private Animation projectile(FighterView caster, Cell target, String sprite, double seconds, double arc,
                                 double scale, boolean orient) {
        return defer(() -> {
            Point2D.Double from = caster.worldFeet();
            double tx = Iso.worldX(target.x(), target.y());
            double ty = Iso.worldY(target.x(), target.y()) - 22;
            Projectile p = new Projectile(assets.image(sprite), from.x, from.y - 34, tx, ty, arc, scale, orient);
            scene.effects().add(p);
            return tween(seconds, p::setProgress);
        });
    }

    private void burst(Cell cell, String sprite, double from, double to, double seconds, double delaySeconds) {
        double x = Iso.worldX(cell.x(), cell.y());
        double y = Iso.worldY(cell.x(), cell.y()) - 16;
        scene.effects().add(new Burst(assets.image(sprite), x, y, from, to, seconds, delaySeconds));
    }

    private FighterView view(Fighter f) {
        return scene.view(f);
    }
}
