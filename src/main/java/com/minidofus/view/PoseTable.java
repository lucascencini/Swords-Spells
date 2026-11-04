package com.minidofus.view;

import com.minidofus.engine.Assets;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Associe chaque orientation d'une classe à une image de pose (lu dans {@code poses.properties}). */
public final class PoseTable {

    private final Map<String, Map<Facing, BufferedImage>> poses = new HashMap<>();
    private final Assets assets;
    private final Properties config;

    public PoseTable(Assets assets) {
        this.assets = assets;
        this.config = assets.properties("poses.properties");
    }

    public BufferedImage image(String classId, Facing facing) {
        return poses.computeIfAbsent(classId, this::load).get(facing);
    }

    private Map<Facing, BufferedImage> load(String classId) {
        Map<Facing, BufferedImage> map = new EnumMap<>(Facing.class);
        for (Facing f : Facing.values()) {
            String value = config.getProperty(classId + "." + f.name(), "1").trim();
            boolean mirror = value.endsWith("m");
            String pose = mirror ? value.substring(0, value.length() - 1) : value;
            String path = "characters/" + classId + "_" + pose;
            map.put(f, mirror ? assets.mirrored(path) : assets.image(path));
        }
        return map;
    }
}
