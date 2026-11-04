package com.minidofus.model;

/** Comment un sort désigne sa cible. Sert aussi à l'interface pour colorer les cases. */
public enum TargetMode {
    /** Un ennemi doit se trouver sur la case visée. */
    ENEMY,
    /** N'importe quelle case à portée (sorts de zone). */
    CELL,
    /** Le lanceur lui-même. */
    SELF,
    /** Une case alignée avec le lanceur (charge). */
    LINE
}
