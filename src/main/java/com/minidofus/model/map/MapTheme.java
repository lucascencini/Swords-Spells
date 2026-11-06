package com.minidofus.model.map;

import java.util.List;

import static com.minidofus.model.map.DecorKind.*;

/** Ambiances de carte : biome, densité du décor, particularité du terrain et éléments utilisés. */
public enum MapTheme {

    FOREST("Clairière forestière", Biome.MEADOW, 10, 22, 36, Feature.NONE,
            List.of(ROCK_CLUSTER_1, ROCK_CLUSTER_2, FALLEN_LOG),
            List.of(TREE_OAK_1, TREE_OAK_2, TREE_OAK_3, TREE_DECIDUOUS, TREE_PINE_1, TREE_PINE_2, TREE_PINE_3, BUSH),
            List.of(FLOWERS_PURPLE, FLOWERS_RED, FLOWERS_WHITE, FLOWERS_YELLOW, FLOWERS_BLUE)),
    RUINS("Ruines anciennes", Biome.MEADOW, 16, 8, 20, Feature.STONE_PATH,
            List.of(ROCK_CLUSTER_1, ROCK_CLUSTER_2, CRATE_1, CRATE_2),
            List.of(TREE_OAK_1, TREE_OAK_2, TREE_DECIDUOUS, TREE_PINE_1, TREE_PINE_3, BUSH),
            List.of(FLOWERS_WHITE, FLOWERS_YELLOW, DEBRIS_1)),
    LAKE("Bord de lac", Biome.MEADOW, 8, 28, 28, Feature.LAKE,
            List.of(ROCK_CLUSTER_1, ROCK_CLUSTER_2, FALLEN_LOG),
            List.of(TREE_OAK_1, TREE_OAK_2, TREE_OAK_3, TREE_DECIDUOUS, TREE_PINE_2, BUSH),
            List.of(FLOWERS_PURPLE, FLOWERS_RED, FLOWERS_WHITE, FLOWERS_YELLOW, FLOWERS_BLUE)),
    SNOW("Col enneigé", Biome.SNOW, 10, 0, 30, Feature.FROZEN_LAKE,
            List.of(SNOW_ROCK_1, SNOW_ROCK_2, CRATE_1),
            List.of(SNOW_PINE_1, SNOW_PINE_2, SNOW_PINE_3, SNOW_BUSH),
            List.of()),
    DESERT("Désert brûlant", Biome.DESERT, 11, 0, 22, Feature.OASIS,
            List.of(DESERT_ROCK_1, DESERT_ROCK_2, CACTUS_OBSTACLE, CRATE_2),
            List.of(CACTUS_1, CACTUS_2, CACTUS_3, DRY_BUSH),
            List.of()),
    DUNGEON("Crypte oubliée", Biome.DUNGEON, 14, 10, 22, Feature.NONE,
            List.of(PILLAR, MONUMENT, CRATE_1, CRATE_2),
            List.of(TORCH, TORCH, PILLAR_DECOR, MONUMENT_DECOR),
            List.of(DEBRIS_1, DEBRIS_2));

    /** Particularité du sol propre à l'ambiance. */
    public enum Feature { NONE, STONE_PATH, LAKE, FROZEN_LAKE, OASIS }

    private final String displayName;
    private final Biome biome;
    private final int obstacles;
    private final int flowers;
    private final int trees;
    private final Feature feature;
    private final List<DecorKind> obstacleKinds;
    private final List<DecorKind> perimeterKinds;
    private final List<DecorKind> flowerKinds;

    MapTheme(String displayName, Biome biome, int obstacles, int flowers, int trees, Feature feature,
             List<DecorKind> obstacleKinds, List<DecorKind> perimeterKinds, List<DecorKind> flowerKinds) {
        this.displayName = displayName;
        this.biome = biome;
        this.obstacles = obstacles;
        this.flowers = flowers;
        this.trees = trees;
        this.feature = feature;
        this.obstacleKinds = obstacleKinds;
        this.perimeterKinds = perimeterKinds;
        this.flowerKinds = flowerKinds;
    }

    public String displayName() { return displayName; }
    public Biome biome() { return biome; }
    public int obstacles() { return obstacles; }
    public int flowers() { return flowers; }
    /** Nombre d'éléments de décor autour de l'arène (arbres, cactus, torches…). */
    public int trees() { return trees; }
    public Feature feature() { return feature; }
    public List<DecorKind> obstacleKinds() { return obstacleKinds; }
    public List<DecorKind> perimeterKinds() { return perimeterKinds; }
    public List<DecorKind> flowerKinds() { return flowerKinds; }
}
