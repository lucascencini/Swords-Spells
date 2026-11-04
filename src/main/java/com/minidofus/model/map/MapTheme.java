package com.minidofus.model.map;

import java.util.List;

import static com.minidofus.model.map.DecorKind.CRATE_1;
import static com.minidofus.model.map.DecorKind.CRATE_2;
import static com.minidofus.model.map.DecorKind.FALLEN_LOG;
import static com.minidofus.model.map.DecorKind.ROCK_CLUSTER_1;
import static com.minidofus.model.map.DecorKind.ROCK_CLUSTER_2;

/** Ambiances de carte : mêmes valeurs que les 3 préréglages du prototype. */
public enum MapTheme {

    FOREST("Clairière forestière", 10, 22, 36, Feature.NONE,
            List.of(ROCK_CLUSTER_1, ROCK_CLUSTER_2, FALLEN_LOG)),
    RUINS("Ruines anciennes", 16, 8, 20, Feature.STONE_PATH,
            List.of(ROCK_CLUSTER_1, ROCK_CLUSTER_2, CRATE_1, CRATE_2)),
    LAKE("Bord de lac", 8, 28, 28, Feature.LAKE,
            List.of(ROCK_CLUSTER_1, ROCK_CLUSTER_2, FALLEN_LOG));

    /** Particularité du sol propre à l'ambiance. */
    public enum Feature { NONE, STONE_PATH, LAKE }

    private final String displayName;
    private final int obstacles;
    private final int flowers;
    private final int trees;
    private final Feature feature;
    private final List<DecorKind> obstacleKinds;

    MapTheme(String displayName, int obstacles, int flowers, int trees, Feature feature, List<DecorKind> obstacleKinds) {
        this.displayName = displayName;
        this.obstacles = obstacles;
        this.flowers = flowers;
        this.trees = trees;
        this.feature = feature;
        this.obstacleKinds = obstacleKinds;
    }

    public String displayName() { return displayName; }
    public int obstacles() { return obstacles; }
    public int flowers() { return flowers; }
    public int trees() { return trees; }
    public Feature feature() { return feature; }
    public List<DecorKind> obstacleKinds() { return obstacleKinds; }
}
