package com.minidofus.model.map;

import java.util.List;

import static com.minidofus.model.map.DecorKind.*;

/** Ambiances de carte : biome, densité du décor, particularité du terrain et éléments utilisés. */
public enum MapTheme {

    FOREST("Clairière forestière", Biome.MEADOW, 10, 0, 0, Feature.NONE,
            List.of(ROCK_CLUSTER_1, ROCK_CLUSTER_2, FALLEN_LOG),
            List.of(),
            List.of(FLOWERS_PURPLE, FLOWERS_RED, FLOWERS_WHITE, FLOWERS_YELLOW, FLOWERS_BLUE), "forest"),
    RUINS("Ruines oubliées", Biome.DUSK, 12, 16, 0, Feature.NONE,
            List.of(RUINS_RUBBLE_1, RUINS_RUBBLE_2, RUINS_RUBBLE_3, RUINS_RUBBLE_4, RUINS_COLUMN_FALLEN, RUINS_BLOCK_1, RUINS_BLOCK_2, RUINS_ARCH_BROKEN, RUINS_WALL_BROKEN, RUINS_COLUMN_HEAD, RUINS_TOMB_1, RUINS_TOMB_2, RUINS_TOMB_3, RUINS_TOMB_4),
            List.of(),
            List.of(RUINS_BUSH_1, RUINS_BUSH_2, RUINS_BUSH_3, RUINS_BUSH_4, RUINS_BUSH_5, RUINS_BUSH_6, RUINS_BUSH_7, RUINS_BUSH_8, RUINS_BUSH_9, RUINS_SLAB_1, RUINS_SLAB_2, RUINS_SLAB_3, RUINS_SLAB_4), "ruins"),
    MOUNTAIN("Sommet enneigé", Biome.SNOW, 11, 14, 0, Feature.NONE,
            List.of(MOUNTAIN_ROCK_1, MOUNTAIN_ROCK_2, MOUNTAIN_CRYSTAL_1, MOUNTAIN_CRYSTAL_2, MOUNTAIN_CRYSTAL_3, MOUNTAIN_MOUND_1, MOUNTAIN_MOUND_2, MOUNTAIN_MOUND_3, MOUNTAIN_FROST_BUSH_1, MOUNTAIN_FROST_BUSH_2, MOUNTAIN_PINE_SMALL, MOUNTAIN_LOG, MOUNTAIN_STUMP),
            List.of(),
            List.of(MOUNTAIN_ROCK_SMALL, MOUNTAIN_TWIG_1, MOUNTAIN_TWIG_2, MOUNTAIN_TWIG_3, MOUNTAIN_SNOW_ROCKS_1, MOUNTAIN_SNOW_ROCKS_2, MOUNTAIN_SNOW_ROCKS_3, MOUNTAIN_ICE_FLOWER_1, MOUNTAIN_ICE_FLOWER_2), "mountain"),
    DESERT("Désert brûlant", Biome.DESERT, 11, 16, 0, Feature.NONE,
            List.of(DESERT_BOULDER_1, DESERT_BOULDER_2, DESERT_BOULDER_3, DESERT_BOULDER_4,
                    SAGUARO_1, SAGUARO_2, BARREL_CACTUS_1, BARREL_CACTUS_2),
            List.of(),
            List.of(DESERT_FLOWERS_1, DESERT_FLOWERS_2, DESERT_FLOWERS_3, DESERT_FLOWERS_4, DESERT_FLOWERS_5,
                    DESERT_FLOWERS_6, DESERT_GRASS_1, DESERT_GRASS_2, DESERT_GRASS_3, DESERT_AGAVE_1,
                    DESERT_AGAVE_2, DESERT_PEBBLES_1, DESERT_PEBBLES_2, DESERT_PEBBLES_3), "desert"),
    ARENA("Arène impériale", Biome.DESERT, 12, 10, 0, Feature.NONE,
            List.of(ARENA_PILLAR_1, ARENA_PILLAR_2, ARENA_PILLAR_BROKEN_1, ARENA_PILLAR_BROKEN_2, ARENA_STATUE_1, ARENA_STATUE_2, ARENA_BUST, ARENA_CYPRESS, ARENA_BANNER_1, ARENA_BANNER_2, ARENA_AMPHORA_1, ARENA_AMPHORA_2, ARENA_AMPHORA_3, ARENA_LANTERN_1, ARENA_LANTERN_2, ARENA_SHIELD, ARENA_RUBBLE_1, ARENA_RUBBLE_2, ARENA_RUBBLE_3, ARENA_BENCH_1, ARENA_BENCH_2),
            List.of(),
            List.of(ARENA_STONES, ARENA_TILE_1, ARENA_TILE_2, ARENA_TILE_3, ARENA_TILE_4, ARENA_TILE_5, ARENA_PEBBLE_1), "arena");

    /** Particularité du sol propre à l'ambiance. */
    public enum Feature { NONE, STONE_PATH, LAKE, FROZEN_LAKE }

    private final String displayName;
    private final Biome biome;
    private final int obstacles;
    private final int flowers;
    private final int trees;
    private final Feature feature;
    private final List<DecorKind> obstacleKinds;
    private final List<DecorKind> perimeterKinds;
    private final List<DecorKind> flowerKinds;
    private final String backdrop;

    MapTheme(String displayName, Biome biome, int obstacles, int flowers, int trees, Feature feature,
             List<DecorKind> obstacleKinds, List<DecorKind> perimeterKinds, List<DecorKind> flowerKinds) {
        this(displayName, biome, obstacles, flowers, trees, feature, obstacleKinds, perimeterKinds, flowerKinds, null);
    }

    MapTheme(String displayName, Biome biome, int obstacles, int flowers, int trees, Feature feature,
             List<DecorKind> obstacleKinds, List<DecorKind> perimeterKinds, List<DecorKind> flowerKinds, String backdrop) {
        this.backdrop = backdrop;
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

    /**
     * Illustration servant de sol et de décor ({@code resources/backdrops/<nom>.jpg}), ou {@code null} pour
     * une carte construite en tuiles. Une carte illustrée n'a pas de décor généré autour de l'arène.
     */
    public String backdrop() { return backdrop; }
}
