package com.minidofus.model.progression;

/**
 * Effet d'un talent, par rang. Types : {@code hp_pct}, {@code power_pct}, {@code resist_pct}, {@code initiative}
 * (caractéristiques) ; {@code spell_power}, {@code spell_range}, {@code spell_cost}, {@code spell_cooldown},
 * {@code spell_param} (clé), {@code spell_onhit} (statut, tours) visent un sort par identifiant ou une famille
 * ({@code tag:fire}).
 */
public record TalentEffect(String type, String target, String key, String status, int turns, int value) { }
