package com.minidofus.model.map;

/**
 * Éléments de décor. {@code role} dit s'ils bloquent le jeu ; {@code occlusionRows} et
 * {@code halfWidth} décrivent grossièrement l'emprise à l'écran (en rangées iso et en unités x-y) pour
 * empêcher un grand arbre de masquer le terrain de jeu.
 */
public enum DecorKind {
    ROCK_CLUSTER_1("rock_cluster_1", Role.OBSTACLE, 0.66, 0, 0),
    ROCK_CLUSTER_2("rock_cluster_2", Role.OBSTACLE, 0.62, 0, 0),
    CRATE_1("crate_1", Role.OBSTACLE, 1.15, 0, 0),
    CRATE_2("crate_2", Role.OBSTACLE, 1.0, 0, 0),
    FALLEN_LOG("fallen_log", Role.OBSTACLE, 0.72, 0, 0),

    TREE_OAK_1("tree_oak_1", Role.TREE, 0.82, 7.0, 2.2),
    TREE_OAK_2("tree_oak_2", Role.TREE, 0.80, 7.5, 2.6),
    TREE_OAK_3("tree_oak_3", Role.TREE, 0.90, 7.0, 2.0),
    TREE_DECIDUOUS("tree_deciduous", Role.TREE, 0.95, 7.0, 2.2),
    TREE_PINE_1("tree_pine_1", Role.TREE, 0.95, 7.5, 1.8),
    TREE_PINE_2("tree_pine_2", Role.TREE, 0.88, 7.5, 1.8),
    TREE_PINE_3("tree_pine_3", Role.TREE, 0.85, 7.5, 2.0),
    BUSH("bush", Role.TREE, 0.75, 3.0, 1.8),

    FLOWERS_PURPLE("flowers_purple", Role.FLOWER, 0.55, 0, 0),
    FLOWERS_RED("flowers_red", Role.FLOWER, 0.55, 0, 0),
    FLOWERS_WHITE("flowers_white", Role.FLOWER, 0.55, 0, 0),
    FLOWERS_YELLOW("flowers_yellow", Role.FLOWER, 0.55, 0, 0),
    FLOWERS_BLUE("flowers_blue", Role.FLOWER, 0.6, 0, 0);

    public enum Role { OBSTACLE, TREE, FLOWER }

    private final String spriteKey;
    private final Role role;
    private final double baseScale;
    private final double occlusionRows;
    private final double halfWidth;

    DecorKind(String spriteKey, Role role, double baseScale, double occlusionRows, double halfWidth) {
        this.spriteKey = spriteKey;
        this.role = role;
        this.baseScale = baseScale;
        this.occlusionRows = occlusionRows;
        this.halfWidth = halfWidth;
    }

    /** Nom du fichier image (sans extension) dans {@code resources/decor/}. */
    public String spriteKey() { return spriteKey; }
    public Role role() { return role; }
    public double baseScale() { return baseScale; }
    public double occlusionRows() { return occlusionRows; }
    public double halfWidth() { return halfWidth; }
    public boolean isBlocking() { return role == Role.OBSTACLE; }
}
