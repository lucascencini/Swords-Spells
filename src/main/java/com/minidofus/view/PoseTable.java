package com.minidofus.view;

import com.minidofus.engine.Assets;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Images de chaque classe selon l'orientation, lues dans {@code poses.properties} :
 * une pose de repos, un cycle de marche facultatif et une échelle d'affichage.
 */
public final class PoseTable {

    /** Échelle par défaut des sprites (poses d'origine du pack). */
    public static final double DEFAULT_SCALE = 0.95;

    private record ClassPoses(Map<Facing, BufferedImage> idle, Map<Facing, List<BufferedImage>> walk, double scale) { }

    private final Map<String, ClassPoses> poses = new HashMap<>();
    private final Assets assets;
    private final Properties config;

    public PoseTable(Assets assets) {
        this.assets = assets;
        this.config = assets.properties("poses.properties");
    }

    public BufferedImage image(String classId, Facing facing) {
        return get(classId).idle().get(facing);
    }

    /** Images du cycle de marche (vide si la classe n'en a pas). */
    public List<BufferedImage> walk(String classId, Facing facing) {
        return get(classId).walk().get(facing);
    }

    public boolean hasWalk(String classId) {
        return !get(classId).walk().get(Facing.SE).isEmpty();
    }

    public double scale(String classId) {
        return get(classId).scale();
    }

    private ClassPoses get(String classId) {
        return poses.computeIfAbsent(classId, this::load);
    }

    private ClassPoses load(String classId) {
        Map<Facing, BufferedImage> idle = new EnumMap<>(Facing.class);
        Map<Facing, List<BufferedImage>> walk = new EnumMap<>(Facing.class);
        for (Facing f : Facing.values()) {
            idle.put(f, resolve(classId, config.getProperty(classId + "." + f.name(), "1")));
            List<BufferedImage> frames = new ArrayList<>();
            String cycle = config.getProperty(classId + "." + f.name() + ".walk", "").trim();
            if (!cycle.isEmpty()) for (String token : cycle.split(",")) frames.add(resolve(classId, token));
            walk.put(f, List.copyOf(frames));
        }
        double scale = Double.parseDouble(config.getProperty(classId + ".scale", String.valueOf(DEFAULT_SCALE)).trim());
        return new ClassPoses(idle, walk, scale);
    }

    /**
     * {@code 3} → characters/&lt;classe&gt;_3, {@code 3m} → idem en miroir,
     * {@code se_idle} → characters/&lt;classe&gt;_se_idle, {@code flip:se_idle} → idem en miroir.
     */
    private BufferedImage resolve(String classId, String token) {
        String t = token.trim();
        boolean mirror = false;
        if (t.startsWith("flip:")) {
            mirror = true;
            t = t.substring(5).trim();
        } else if (t.matches("\\d+m")) {
            mirror = true;
            t = t.substring(0, t.length() - 1);
        }
        String path = "characters/" + classId + "_" + t;
        return mirror ? assets.mirrored(path) : assets.image(path);
    }
}
