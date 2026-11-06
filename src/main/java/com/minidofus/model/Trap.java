package com.minidofus.model;

/** Piège posé sur une case : il se déclenche quand un ennemi de son poseur y entre. */
public record Trap(Cell cell, Fighter owner, IntRange damage) { }
