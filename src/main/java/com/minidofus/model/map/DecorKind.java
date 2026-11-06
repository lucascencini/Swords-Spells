package com.minidofus.model.map;

/**
 * Éléments de décor. {@code role} dit s'ils bloquent le jeu, {@code durability} s'ils peuvent être détruits ;
 * {@code occlusionRows} et {@code halfWidth} décrivent grossièrement l'emprise à l'écran (en rangées iso et
 * en unités x-y) pour empêcher un grand élément de masquer le terrain de jeu.
 */
public enum DecorKind {
    // forêt
    ROCK_CLUSTER_1("rock_cluster_1", Role.OBSTACLE, 0.66, 0, 0),
    ROCK_CLUSTER_2("rock_cluster_2", Role.OBSTACLE, 0.62, 0, 0),
    FALLEN_LOG("fallen_log", Role.OBSTACLE, 0.72, 0, 0, 18),

    // désert (éléments détourés de la planche, haute résolution d'où les petites échelles)
    DESERT_BOULDER_1("desert_rock_1", Role.OBSTACLE, 0.46, 0, 0),
    DESERT_BOULDER_2("desert_rock_2", Role.OBSTACLE, 0.56, 0, 0),
    DESERT_BOULDER_3("desert_rock_3", Role.OBSTACLE, 0.5, 0, 0),
    DESERT_BOULDER_4("desert_rock_4", Role.OBSTACLE, 0.56, 0, 0),
    SAGUARO_1("desert_saguaro_1", Role.OBSTACLE, 0.66, 0, 0, 12),
    SAGUARO_2("desert_saguaro_2", Role.OBSTACLE, 0.56, 0, 0, 12),
    BARREL_CACTUS_1("desert_barrel_1", Role.OBSTACLE, 0.72, 0, 0, 12),
    BARREL_CACTUS_2("desert_barrel_2", Role.OBSTACLE, 0.78, 0, 0, 12),

    // ruines (tools/import_maps.py : images à 2× leur taille, base à 80 % de la hauteur)
    RUINS_RUBBLE_1("ruins_rubble_1", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_RUBBLE_2("ruins_rubble_2", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_RUBBLE_3("ruins_rubble_3", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_RUBBLE_4("ruins_rubble_4", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_COLUMN_FALLEN("ruins_column_fallen", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_BLOCK_1("ruins_block_1", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_BLOCK_2("ruins_block_2", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_ARCH_BROKEN("ruins_arch_broken", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_WALL_BROKEN("ruins_wall_broken", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_COLUMN_HEAD("ruins_column_head", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_TOMB_1("ruins_tomb_1", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_TOMB_2("ruins_tomb_2", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_TOMB_3("ruins_tomb_3", Role.OBSTACLE, 0.5, 0, 0),
    RUINS_TOMB_4("ruins_tomb_4", Role.OBSTACLE, 0.5, 0, 0),

    // montagne (tools/import_maps.py : images à 2× leur taille, base à 80 % de la hauteur)
    MOUNTAIN_ROCK_1("mountain_rock_1", Role.OBSTACLE, 0.5, 0, 0),
    MOUNTAIN_ROCK_2("mountain_rock_2", Role.OBSTACLE, 0.5, 0, 0),
    MOUNTAIN_CRYSTAL_1("mountain_crystal_1", Role.OBSTACLE, 0.5, 0, 0),
    MOUNTAIN_CRYSTAL_2("mountain_crystal_2", Role.OBSTACLE, 0.5, 0, 0),
    MOUNTAIN_CRYSTAL_3("mountain_crystal_3", Role.OBSTACLE, 0.5, 0, 0),
    MOUNTAIN_MOUND_1("mountain_mound_1", Role.OBSTACLE, 0.5, 0, 0),
    MOUNTAIN_MOUND_2("mountain_mound_2", Role.OBSTACLE, 0.5, 0, 0),
    MOUNTAIN_MOUND_3("mountain_mound_3", Role.OBSTACLE, 0.5, 0, 0),
    MOUNTAIN_FROST_BUSH_1("mountain_frost_bush_1", Role.OBSTACLE, 0.5, 0, 0, 12),
    MOUNTAIN_FROST_BUSH_2("mountain_frost_bush_2", Role.OBSTACLE, 0.5, 0, 0, 12),
    MOUNTAIN_PINE_SMALL("mountain_pine_small", Role.OBSTACLE, 0.5, 0, 0),
    MOUNTAIN_LOG("mountain_log", Role.OBSTACLE, 0.5, 0, 0, 18),
    MOUNTAIN_STUMP("mountain_stump", Role.OBSTACLE, 0.5, 0, 0),

    // arène (tools/import_maps.py : images à 2× leur taille, base à 80 % de la hauteur)
    ARENA_PILLAR_1("arena_pillar_1", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_PILLAR_2("arena_pillar_2", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_PILLAR_BROKEN_1("arena_pillar_broken_1", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_PILLAR_BROKEN_2("arena_pillar_broken_2", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_STATUE_1("arena_statue_1", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_STATUE_2("arena_statue_2", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_BUST("arena_bust", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_CYPRESS("arena_cypress", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_BANNER_1("arena_banner_1", Role.OBSTACLE, 0.5, 0, 0, 12),
    ARENA_BANNER_2("arena_banner_2", Role.OBSTACLE, 0.5, 0, 0, 12),
    ARENA_AMPHORA_1("arena_amphora_1", Role.OBSTACLE, 0.5, 0, 0, 12),
    ARENA_AMPHORA_2("arena_amphora_2", Role.OBSTACLE, 0.5, 0, 0, 12),
    ARENA_AMPHORA_3("arena_amphora_3", Role.OBSTACLE, 0.5, 0, 0, 12),
    ARENA_LANTERN_1("arena_lantern_1", Role.OBSTACLE, 0.5, 0, 0, 12),
    ARENA_LANTERN_2("arena_lantern_2", Role.OBSTACLE, 0.5, 0, 0, 12),
    ARENA_SHIELD("arena_shield", Role.OBSTACLE, 0.5, 0, 0, 12),
    ARENA_RUBBLE_1("arena_rubble_1", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_RUBBLE_2("arena_rubble_2", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_RUBBLE_3("arena_rubble_3", Role.OBSTACLE, 0.5, 0, 0),
    ARENA_BENCH_1("arena_bench_1", Role.OBSTACLE, 0.5, 0, 0, 18),
    ARENA_BENCH_2("arena_bench_2", Role.OBSTACLE, 0.5, 0, 0, 18),
    RUINS_BUSH_1("ruins_bush_1", Role.FLOWER, 0.5, 0, 0),
    RUINS_BUSH_2("ruins_bush_2", Role.FLOWER, 0.5, 0, 0),
    RUINS_BUSH_3("ruins_bush_3", Role.FLOWER, 0.5, 0, 0),
    RUINS_BUSH_4("ruins_bush_4", Role.FLOWER, 0.5, 0, 0),
    RUINS_BUSH_5("ruins_bush_5", Role.FLOWER, 0.5, 0, 0),
    RUINS_BUSH_6("ruins_bush_6", Role.FLOWER, 0.5, 0, 0),
    RUINS_BUSH_7("ruins_bush_7", Role.FLOWER, 0.5, 0, 0),
    RUINS_BUSH_8("ruins_bush_8", Role.FLOWER, 0.5, 0, 0),
    RUINS_BUSH_9("ruins_bush_9", Role.FLOWER, 0.5, 0, 0),
    RUINS_SLAB_1("ruins_slab_1", Role.FLOWER, 0.5, 0, 0),
    RUINS_SLAB_2("ruins_slab_2", Role.FLOWER, 0.5, 0, 0),
    RUINS_SLAB_3("ruins_slab_3", Role.FLOWER, 0.5, 0, 0),
    RUINS_SLAB_4("ruins_slab_4", Role.FLOWER, 0.5, 0, 0),
    MOUNTAIN_ROCK_SMALL("mountain_rock_small", Role.FLOWER, 0.5, 0, 0),
    MOUNTAIN_TWIG_1("mountain_twig_1", Role.FLOWER, 0.5, 0, 0),
    MOUNTAIN_TWIG_2("mountain_twig_2", Role.FLOWER, 0.5, 0, 0),
    MOUNTAIN_TWIG_3("mountain_twig_3", Role.FLOWER, 0.5, 0, 0),
    MOUNTAIN_SNOW_ROCKS_1("mountain_snow_rocks_1", Role.FLOWER, 0.5, 0, 0),
    MOUNTAIN_SNOW_ROCKS_2("mountain_snow_rocks_2", Role.FLOWER, 0.5, 0, 0),
    MOUNTAIN_SNOW_ROCKS_3("mountain_snow_rocks_3", Role.FLOWER, 0.5, 0, 0),
    MOUNTAIN_ICE_FLOWER_1("mountain_ice_flower_1", Role.FLOWER, 0.5, 0, 0),
    MOUNTAIN_ICE_FLOWER_2("mountain_ice_flower_2", Role.FLOWER, 0.5, 0, 0),
    ARENA_STONES("arena_stones", Role.FLOWER, 0.5, 0, 0),
    ARENA_TILE_1("arena_tile_1", Role.FLOWER, 0.5, 0, 0),
    ARENA_TILE_2("arena_tile_2", Role.FLOWER, 0.5, 0, 0),
    ARENA_TILE_3("arena_tile_3", Role.FLOWER, 0.5, 0, 0),
    ARENA_TILE_4("arena_tile_4", Role.FLOWER, 0.5, 0, 0),
    ARENA_TILE_5("arena_tile_5", Role.FLOWER, 0.5, 0, 0),
    ARENA_PEBBLE_1("arena_pebble_1", Role.FLOWER, 0.5, 0, 0),

    // au sol, sans effet sur le jeu
    FLOWERS_PURPLE("flowers_purple", Role.FLOWER, 0.55, 0, 0),
    FLOWERS_RED("flowers_red", Role.FLOWER, 0.55, 0, 0),
    FLOWERS_WHITE("flowers_white", Role.FLOWER, 0.55, 0, 0),
    FLOWERS_YELLOW("flowers_yellow", Role.FLOWER, 0.55, 0, 0),
    FLOWERS_BLUE("flowers_blue", Role.FLOWER, 0.6, 0, 0),
    DESERT_FLOWERS_1("desert_flowers_1", Role.FLOWER, 0.46, 0, 0),
    DESERT_FLOWERS_2("desert_flowers_2", Role.FLOWER, 0.42, 0, 0),
    DESERT_FLOWERS_3("desert_flowers_3", Role.FLOWER, 0.46, 0, 0),
    DESERT_FLOWERS_4("desert_flowers_4", Role.FLOWER, 0.46, 0, 0),
    DESERT_FLOWERS_5("desert_flowers_5", Role.FLOWER, 0.4, 0, 0),
    DESERT_FLOWERS_6("desert_flowers_6", Role.FLOWER, 0.44, 0, 0),
    DESERT_GRASS_1("desert_grass_1", Role.FLOWER, 0.46, 0, 0),
    DESERT_GRASS_2("desert_grass_2", Role.FLOWER, 0.46, 0, 0),
    DESERT_GRASS_3("desert_grass_3", Role.FLOWER, 0.46, 0, 0),
    DESERT_AGAVE_1("desert_agave_1", Role.FLOWER, 0.4, 0, 0),
    DESERT_AGAVE_2("desert_agave_2", Role.FLOWER, 0.38, 0, 0),
    DESERT_PEBBLES_1("desert_pebbles_1", Role.FLOWER, 0.44, 0, 0),
    DESERT_PEBBLES_2("desert_pebbles_2", Role.FLOWER, 0.44, 0, 0),
    DESERT_PEBBLES_3("desert_pebbles_3", Role.FLOWER, 0.44, 0, 0);

    public enum Role { OBSTACLE, TREE, FLOWER }

    private final String spriteKey;
    private final Role role;
    private final double baseScale;
    private final double occlusionRows;
    private final double halfWidth;
    private final int durability;

    DecorKind(String spriteKey, Role role, double baseScale, double occlusionRows, double halfWidth) {
        this(spriteKey, role, baseScale, occlusionRows, halfWidth, 0);
    }

    DecorKind(String spriteKey, Role role, double baseScale, double occlusionRows, double halfWidth, int durability) {
        this.spriteKey = spriteKey;
        this.role = role;
        this.baseScale = baseScale;
        this.occlusionRows = occlusionRows;
        this.halfWidth = halfWidth;
        this.durability = durability;
    }

    /** Nom du fichier image (sans extension) dans {@code resources/decor/}. */
    public String spriteKey() { return spriteKey; }
    public Role role() { return role; }
    public double baseScale() { return baseScale; }
    public double occlusionRows() { return occlusionRows; }
    public double halfWidth() { return halfWidth; }
    public boolean isBlocking() { return role == Role.OBSTACLE; }
    /** Points de structure si l'obstacle est destructible, 0 sinon. */
    public int durability() { return durability; }
    /** Émet de la lumière (halo dessiné par la scène). */
    public boolean isLight() { return false; }
}
