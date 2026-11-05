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
import com.minidofus.model.StatusKind;
import com.minidofus.ui.Hud;
import com.minidofus.ui.UiTheme;
import com.minidofus.view.BattleScene;
import com.minidofus.view.FighterView;
import com.minidofus.view.anim.Animation;
import com.minidofus.view.anim.AnimationQueue;
import com.minidofus.view.fx.Arrow;
import com.minidofus.view.fx.Burst;
import com.minidofus.view.fx.BeeSwarm;
import com.minidofus.view.fx.FlyingFrames;
import com.minidofus.view.fx.FrameEffect;
import com.minidofus.view.fx.FloatingText;
import com.minidofus.view.fx.Projectile;
import com.minidofus.view.fx.RingPulse;
import com.minidofus.view.fx.Slash;
import com.minidofus.view.fx.SoulWisp;
import com.minidofus.view.fx.Tether;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.minidofus.view.anim.Animations.call;
import static com.minidofus.view.anim.Animations.defer;
import static com.minidofus.view.anim.Animations.delay;
import static com.minidofus.view.anim.Animations.parallel;
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
    private static final double SLIDE_STEP = 0.09;

    private final BattleScene scene;
    private final AnimationQueue queue;
    private final SoundPlayer sound;
    private final Assets assets;
    private final Hud hud;
    private final String mapName;
    private final Consumer<Point2D.Double> focus;
    private final Function<Fighter, String> subtitleOf;
    private boolean introShown;

    BattlePresenter(BattleScene scene, AnimationQueue queue, SoundPlayer sound, Assets assets, Hud hud,
                    String mapName, Function<Fighter, String> subtitleOf, Consumer<Point2D.Double> focus) {
        this.subtitleOf = subtitleOf;
        this.mapName = mapName;
        this.focus = focus;
        this.scene = scene;
        this.queue = queue;
        this.sound = sound;
        this.assets = assets;
        this.hud = hud;
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
        if (event instanceof BattleEvent.StatusChanged e) return statusChanged(e);
        if (event instanceof BattleEvent.Died e) return died(e);
        if (event instanceof BattleEvent.ObstacleDamaged e) return obstacleDamaged(e);
        if (event instanceof BattleEvent.ObstacleDestroyed e) return obstacleDestroyed(e);
        if (event instanceof BattleEvent.TrapPlaced e) return call(() -> scene.addTrap(e.trap()));
        if (event instanceof BattleEvent.TrapRemoved e) return call(() -> scene.removeTrap(e.trap()));
        if (event instanceof BattleEvent.TrapTriggered e) return trapTriggered(e);
        if (event instanceof BattleEvent.BattleEnded e) return battleEnded(e);
        return call(() -> { });
    }

    // ------------------------------------------------------------------ tours et déplacements

    private Animation turnStarted(BattleEvent.TurnStarted e) {
        return sequence(call(() -> {
            Fighter f = e.fighter();
            Point2D.Double p = view(f).worldFeet();
            focus.accept(new Point2D.Double(p.x, p.y - 30));
            scene.effects().add(new RingPulse(p.x, p.y, UiTheme.teamColor(f.team()), 0.7));
            String subtitle = subtitleOf.apply(f);
            if (!introShown) {
                introShown = true;
                hud.showIntro(mapName, f.name() + " (" + f.team().displayName() + ") ouvre le combat");
            }
            hud.showTurn(f, "Manche " + e.round() + "  ·  " + subtitle);
            Sfx.TURN.play(sound);
        }), delay(0.25));
    }

    private Animation moved(BattleEvent.Moved e) {
        return defer(() -> {
            FighterView v = view(e.fighter());
            Cell start = v.cell();
            Cell end = e.path().get(e.path().size() - 1);
            return switch (e.style()) {
                case TELEPORT -> teleportAnimation(v, start, end);
                case LEAP -> leapAnimation(v, start, end);
                default -> stepAnimation(v, start, e.path(), e.style());
            };
        });
    }

    private Animation stepAnimation(FighterView v, Cell origin, List<Cell> path, MoveStyle style) {
        boolean slide = style == MoveStyle.PUSHED || style == MoveStyle.PULLED;
        double step = style == MoveStyle.WALK ? WALK_STEP : slide ? SLIDE_STEP : DASH_STEP;
        double hopHeight = style == MoveStyle.WALK ? 7 : style == MoveStyle.DASH ? 3 : 0;
        List<Animation> steps = new ArrayList<>();
        boolean walk = style == MoveStyle.WALK || style == MoveStyle.DASH;
        if (style == MoveStyle.WALK) steps.add(call(() -> Sfx.MOVE.play(sound)));
        if (walk) steps.add(call(() -> v.setWalking(true)));
        Cell from = origin;
        for (Cell to : path) {
            Cell a = from;
            if (!slide) steps.add(call(() -> v.face(a, to)));
            steps.add(tween(step, t -> {
                double k = style == MoveStyle.WALK ? t : Easing.inOutQuad(t);
                v.setGridPosition(Easing.lerp(a.x(), to.x(), k), Easing.lerp(a.y(), to.y(), k), Math.sin(t * Math.PI) * hopHeight);
            }));
            from = to;
        }
        Cell last = path.get(path.size() - 1);
        steps.add(call(() -> {
            v.snapTo(last);
            v.setWalking(false);
        }));
        return sequence(steps.toArray(Animation[]::new));
    }

    private Animation teleportAnimation(FighterView v, Cell from, Cell to) {
        return sequence(
                call(() -> {
                    Sfx.TELEPORT.play(sound);
                    burst(from, "fx/arcane", 0.3, 0.8, 0.35, 0);
                }),
                tween(0.18, t -> v.setFade(1 - t)),
                call(() -> {
                    v.face(from, to);
                    v.snapTo(to);
                    burst(to, "fx/arcane", 0.3, 0.8, 0.35, 0);
                }),
                tween(0.18, v::setFade));
    }

    private Animation leapAnimation(FighterView v, Cell from, Cell to) {
        return sequence(
                call(() -> {
                    v.face(from, to);
                    Sfx.MOVE.play(sound);
                }),
                tween(0.36, t -> v.setGridPosition(Easing.lerp(from.x(), to.x(), t), Easing.lerp(from.y(), to.y(), t),
                        Math.sin(t * Math.PI) * 34)),
                call(() -> {
                    v.snapTo(to);
                    Point2D.Double p = v.worldFeet();
                    scene.effects().add(new RingPulse(p.x, p.y, new Color(230, 220, 190), 0.4));
                }));
    }

    // ------------------------------------------------------------------ sorts

    private Animation spellCast(BattleEvent.SpellCast e) {
        FighterView caster = view(e.caster());
        Cell target = e.target();
        Animation face = call(() -> {
            Cell from = caster.cell();
            if (!from.equals(target)) caster.face(from, target);
        });
        Color aura = auraColor(e.spell().style());
        if (aura != null) face = sequence(face, call(() -> caster.cast(aura)), delay(0.12));
        return switch (e.spell().style()) {
            case MELEE, STUN_STRIKE, PUSH_STRIKE -> sequence(face, call(() -> {
                Sfx.MELEE.play(sound);
                caster.lunge();
            }), delay(0.12));
            case DASH -> sequence(face, call(() -> Sfx.CHARGE.play(sound)));
            case GRAPPLE -> sequence(face, call(() -> Sfx.GRAPPLE.play(sound)), tether(caster, target));
            case ARCANE_BOLT -> sequence(face, call(() -> Sfx.PROJECTILE.play(sound)),
                    projectile(caster, target, assets.image("fx/arcane"), 0.34, 26, 0.5, false),
                    call(() -> burst(target, "fx/arcane", 0.4, 0.9, 0.35, 0)));
            case POISON -> sequence(face, call(() -> Sfx.POISON.play(sound)),
                    projectile(caster, target, assets.tinted(assets.image("fx/arcane"), "poison", UiTheme.POISON), 0.38, 30, 0.45, false),
                    call(() -> scene.effects().add(new Burst(assets.tinted(assets.image("fx/arcane"), "poison", UiTheme.POISON),
                            scene.projection().x(target.x(), target.y()), scene.projection().y(target.x(), target.y()) - 16, 0.4, 0.9, 0.4, 0))));
            case FIREBALL -> sequence(face, call(() -> Sfx.FIREBALL.play(sound)),
                    projectile(caster, target, assets.image("fx/fire_projectile"), 0.42, 34, 0.6, true),
                    call(() -> {
                        Sfx.FIREBALL_IMPACT.play(sound);
                        for (Cell c : e.impact()) {
                            boolean center = c.equals(target);
                            burst(c, "fx/fire_explosion", center ? 0.45 : 0.3, center ? 1.35 : 0.95, 0.45, center ? 0 : 0.06);
                        }
                    }), delay(0.15));
            case ARROW, RECOIL_ARROW -> sequence(face, call(() -> Sfx.ARROW.play(sound)), arrow(caster, target, 0.3, 18));
            case ARROW_RAIN -> sequence(face, call(() -> Sfx.ARROW.play(sound)), arrowRain(e.impact()), delay(0.1));
            case TRAP -> sequence(face, call(() -> {
                Sfx.TRAP.play(sound);
                Point2D.Double p = cellPoint(target);
                scene.effects().add(new RingPulse(p.x, p.y + 16, UiTheme.teamColor(e.caster().team()), 0.5));
            }), delay(0.15));
            case SHIELD -> sequence(call(() -> {
                Sfx.BUFF.play(sound);
                Point2D.Double p = caster.worldFeet();
                scene.effects().add(new RingPulse(p.x, p.y, UiTheme.SHIELD, 0.6));
            }), delay(0.25));
            case HEAL -> sequence(face, call(() -> {
                Sfx.BUFF.play(sound);
                Point2D.Double p = cellPoint(target);
                scene.effects().add(new RingPulse(p.x, p.y + 16, UiTheme.HEAL, 0.6));
            }), delay(0.15));
            case CROW -> sequence(face, call(() -> Sfx.CROW.play(sound)), crow(caster, target), call(() -> {
                Point2D.Double p = cellPoint(target);
                scene.effects().add(new RingPulse(p.x, p.y + 16, new Color(0x4A4060), 0.35));
            }));
            case BEES -> sequence(face, call(() -> Sfx.BEES.play(sound)), beeSwarm(caster, target));
            case TORNADO -> sequence(face, tornado(target));
            case LIGHTNING -> sequence(face, delay(0.08), lightning(target));
            case AQUA_VITA -> sequence(face, aquaVita(target));
            case TELEPORT, LEAP -> face;
            case COLLISION, TRAP_TRIGGER, POISON_TICK, BEES_TICK -> call(() -> { });
        };
    }

    /** Couleur de l'aura d'incantation, ou {@code null} pour les attaques physiques (élan à la place). */
    private static Color auraColor(CastStyle style) {
        return switch (style) {
            case ARCANE_BOLT, TELEPORT -> new Color(0x8FA8FF);
            case FIREBALL -> new Color(0xFF8A3D);
            case HEAL -> UiTheme.HEAL;
            case POISON -> UiTheme.POISON;
            case SHIELD -> UiTheme.SHIELD;
            case ARROW_RAIN, TRAP -> UiTheme.GOLD_LIGHT;
            case CROW -> new Color(0x9A8CC8);
            case BEES -> UiTheme.BEES;
            case TORNADO -> new Color(0x9BD48A);
            case LIGHTNING -> new Color(0xFFE680);
            case AQUA_VITA -> new Color(0x3FD0B0);
            default -> null;
        };
    }

    private Animation damaged(BattleEvent.Damaged e) {
        return sequence(call(() -> {
            FighterView v = view(e.target());
            Point2D.Double feet = v.worldFeet();
            switch (e.style()) {
                case MELEE, STUN_STRIKE, PUSH_STRIKE, DASH -> {
                    FighterView src = view(e.source());
                    scene.effects().add(new Slash(feet.x, feet.y, src.gx() > v.gx() || src.gy() < v.gy()));
                    if (e.style() == CastStyle.DASH) Sfx.CHARGE_IMPACT.play(sound);
                }
                case COLLISION -> Sfx.COLLISION.play(sound);
                default -> { }
            }
            Sfx.HIT.play(sound, 0.05);
            v.flashDamage();
            v.setShownHp(e.hpAfter());
            Point2D.Double head = v.worldHead();
            Color color = e.style() == CastStyle.POISON_TICK ? UiTheme.POISON
                    : e.style() == CastStyle.BEES_TICK ? UiTheme.BEES
                    : e.heavy() ? UiTheme.DAMAGE_HEAVY : UiTheme.DAMAGE;
            String text = "-" + e.amount() + (e.style() == CastStyle.COLLISION ? " choc" : "");
            scene.effects().add(new FloatingText(text, head.x, head.y, color, e.heavy() ? 30 : 24));
        }), delay(0.3));
    }

    private Animation healed(BattleEvent.Healed e) {
        return sequence(call(() -> {
            FighterView v = view(e.target());
            v.flashHeal();
            v.setShownHp(e.hpAfter());
            Point2D.Double head = v.worldHead();
            scene.effects().add(new FloatingText(e.amount() > 0 ? "+" + e.amount() : "PV au maximum", head.x, head.y,
                    UiTheme.HEAL, e.amount() > 0 ? 24 : 15));
        }), delay(0.3));
    }

    private Animation statusChanged(BattleEvent.StatusChanged e) {
        return call(() -> {
            FighterView v = view(e.fighter());
            v.setStatus(e.kind(), e.active());
            String text = switch (e.kind()) {
                case SHIELD -> e.active() ? "Bouclier -50 %" : "Bouclier dissipé";
                case POISON -> e.active() ? "Empoisonné" : null;
                case BEES -> e.active() ? "Nuée d'abeilles" : null;
                case SILENCE -> e.active() ? "Silence !" : null;
                case SILENCE_IMMUNITY -> null;
            };
            if (text == null) return;
            Color color = switch (e.kind()) {
                case POISON -> UiTheme.POISON;
                case BEES -> UiTheme.BEES;
                case SILENCE -> UiTheme.SILENCE;
                default -> UiTheme.SHIELD;
            };
            Point2D.Double head = v.worldHead();
            scene.effects().add(new FloatingText(text, head.x, head.y - 4, color, 16));
        });
    }

    private Animation died(BattleEvent.Died e) {
        return defer(() -> {
            FighterView v = view(e.fighter());
            return sequence(call(() -> {
                Sfx.DEATH.play(sound);
                for (StatusKind k : StatusKind.values()) v.setStatus(k, false);
                Point2D.Double p = v.worldFeet();
                scene.effects().add(new SoulWisp(p.x, p.y, UiTheme.teamColor(e.fighter().team())));
                scene.effects().add(new FloatingText("K.O.", p.x, p.y - 70, UiTheme.teamTextColor(e.fighter().team()), 20));
            }), tween(0.9, v::setDeathProgress));
        });
    }

    private Animation obstacleDamaged(BattleEvent.ObstacleDamaged e) {
        return call(() -> {
            scene.shakeObstacle(e.cell());
            if (e.style() != CastStyle.FIREBALL && e.style() != CastStyle.ARROW_RAIN) Sfx.TRAP.play(sound);
            Point2D.Double p = cellPoint(e.cell());
            scene.effects().add(new FloatingText("-" + e.amount(), p.x, p.y - 10, new Color(0xD8C8A8), 18));
        });
    }

    private Animation obstacleDestroyed(BattleEvent.ObstacleDestroyed e) {
        return sequence(delay(0.1), call(() -> {
            Sfx.BREAK.play(sound);
            scene.removeObstacle(e.cell());
            burst(e.cell(), "fx/fire_explosion", 0.25, 0.7, 0.35, 0);
            Point2D.Double p = cellPoint(e.cell());
            scene.effects().add(new FloatingText("Détruit", p.x, p.y - 4, new Color(0xD8C8A8), 15));
        }), delay(0.15));
    }

    private Animation trapTriggered(BattleEvent.TrapTriggered e) {
        return call(() -> {
            scene.removeTrap(e.trap());
            Sfx.TRAP.play(sound);
            burst(e.trap().cell(), "fx/fire_explosion", 0.25, 0.75, 0.35, 0);
        });
    }

    private Animation battleEnded(BattleEvent.BattleEnded e) {
        return sequence(delay(0.6), call(() -> {
            Sfx.VICTORY.play(sound);
            Sfx.VICTORY_COINS.play(sound, 0.25);
        }));
    }

    // ------------------------------------------------------------------ outils

    private Animation projectile(FighterView caster, Cell target, java.awt.image.BufferedImage sprite, double seconds,
                                 double arc, double scale, boolean orient) {
        return defer(() -> {
            Point2D.Double from = caster.worldFeet();
            Point2D.Double to = cellPoint(target);
            Projectile p = new Projectile(sprite, from.x, from.y - 34, to.x, to.y - 6, arc, scale, orient);
            scene.effects().add(p);
            return tween(seconds, p::setProgress);
        });
    }

    // ------------------------------------------------------------------ sorts du Druide

    private List<java.awt.image.BufferedImage> frames(String prefix, int count) {
        List<java.awt.image.BufferedImage> out = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) out.add(assets.image("fx/" + prefix + "_" + i));
        return out;
    }

    /** Le corbeau s'envole du lanceur et pique sur la cible. */
    private Animation crow(FighterView caster, Cell target) {
        return defer(() -> {
            Point2D.Double from = caster.worldFeet();
            Point2D.Double to = cellPoint(target);
            FlyingFrames bird = new FlyingFrames(frames("crow", 4), from.x, from.y - 46, to.x, to.y - 10, 46, 30, 12);
            scene.effects().add(bird);
            return tween(0.5, bird::setProgress);
        });
    }

    /** La nuée file vers la cible (elle reste ensuite autour d'elle, dessinée par sa vue). */
    private Animation beeSwarm(FighterView caster, Cell target) {
        return defer(() -> {
            Point2D.Double from = caster.worldFeet();
            Point2D.Double to = cellPoint(target);
            BeeSwarm swarm = new BeeSwarm(frames("bee", 2), from.x, from.y - 40, to.x, to.y - 14);
            scene.effects().add(swarm);
            return tween(0.6, swarm::setProgress);
        });
    }

    /** Tornade au centre de la zone : elle tourbillonne, les dégâts tombent pendant qu'elle se dissipe. */
    private Animation tornado(Cell center) {
        return sequence(call(() -> {
            Sfx.WIND.play(sound);
            Point2D.Double p = cellPoint(center);
            double ground = p.y + 20;
            scene.effects().add(new FrameEffect(frames("tornado", 10), p.x, ground + 12, 150, 14, 1.25, true, 0, 8)
                    .fades(0.15, 0.25));
            scene.effects().add(new RingPulse(p.x, ground, new Color(0xB8D8A0), 0.7));
        }), delay(0.85));
    }

    /** L'éclair grandit en trois images puis frappe : tonnerre et onde au sol. */
    private Animation lightning(Cell target) {
        return sequence(call(() -> {
            Point2D.Double p = cellPoint(target);
            double ground = p.y + 20;
            scene.effects().add(new FrameEffect(frames("lightning", 3), p.x, ground + 6, 230, 16, 0.5, true, 2, 3)
                    .fades(0, 0.18));
        }), delay(0.12), call(() -> {
            Sfx.THUNDER.play(sound);
            Point2D.Double p = cellPoint(target);
            scene.effects().add(new RingPulse(p.x, p.y + 20, new Color(0xFFE680), 0.45));
            scene.effects().add(new Burst(assets.tinted(assets.image("fx/arcane"), "lightning", new Color(0xFFF2B0)),
                    p.x, p.y + 10, 0.35, 1.1, 0.3, 0));
        }), delay(0.12));
    }

    /** Une aqua vita apparaît au-dessus de la tête de la cible, tourne sur elle-même, puis se déverse en soin. */
    private Animation aquaVita(Cell target) {
        return sequence(call(() -> {
            Sfx.CHIME.play(sound);
            Point2D.Double p = cellPoint(target);
            scene.effects().add(new FrameEffect(frames("potion", 21), p.x, p.y - 66, 44, 20, 1.15, false, 0, 21)
                    .rise(14).fades(0.18, 0.3));
        }), delay(0.85), call(() -> {
            Point2D.Double p = cellPoint(target);
            scene.effects().add(new RingPulse(p.x, p.y + 20, new Color(0x3FD0B0), 0.6));
            scene.effects().add(new Burst(assets.tinted(assets.image("fx/arcane"), "aquavita", new Color(0x7FF0D0)),
                    p.x, p.y - 20, 0.3, 0.9, 0.4, 0));
        }), delay(0.1));
    }

    private Animation arrow(FighterView caster, Cell target, double seconds, double arc) {
        return defer(() -> {
            Point2D.Double from = caster.worldFeet();
            Point2D.Double to = cellPoint(target);
            Arrow a = new Arrow(from.x, from.y - 36, to.x, to.y - 8, arc, UiTheme.teamColor(caster.fighter().team()));
            scene.effects().add(a);
            return tween(seconds, a::setProgress);
        });
    }

    /** Une flèche tombe du ciel sur chaque case de la zone, légèrement décalées dans le temps. */
    private Animation arrowRain(List<Cell> cells) {
        List<Animation> drops = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            double wait = i * 0.035;
            drops.add(sequence(delay(wait), defer(() -> {
                Point2D.Double to = cellPoint(c);
                Arrow a = new Arrow(to.x - 40, to.y - 230, to.x, to.y + 6, 0, UiTheme.GOLD);
                scene.effects().add(a);
                return tween(0.3, a::setProgress);
            })));
        }
        return parallel(drops.toArray(Animation[]::new));
    }

    private Animation tether(FighterView caster, Cell target) {
        return defer(() -> {
            Point2D.Double from = caster.worldFeet();
            Point2D.Double to = cellPoint(target);
            Tether t = new Tether(from.x, from.y - 30, to.x, to.y - 4);
            scene.effects().add(t);
            return tween(0.3, t::setProgress);
        });
    }

    /** Centre d'une case, à hauteur de poitrine d'un combattant. */
    private Point2D.Double cellPoint(Cell c) {
        return new Point2D.Double(scene.projection().x(c.x(), c.y()), scene.projection().y(c.x(), c.y()) - 20);
    }

    private void burst(Cell cell, String sprite, double from, double to, double seconds, double delaySeconds) {
        Point2D.Double p = cellPoint(cell);
        scene.effects().add(new Burst(assets.image(sprite), p.x, p.y + 4, from, to, seconds, delaySeconds));
    }

    private FighterView view(Fighter f) {
        return scene.view(f);
    }
}
