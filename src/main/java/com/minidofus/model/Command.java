package com.minidofus.model;

/**
 * Action d'un joueur, sous une forme transmissible par le réseau. Appliquées dans le même ordre à des
 * combats créés avec la même graine, les mêmes commandes donnent exactement le même état : c'est ce qui
 * permet au serveur de ne diffuser que les commandes, et à chaque client de rejouer le combat chez lui.
 */
public sealed interface Command {

    record Move(int x, int y) implements Command { }

    /** {@code spell} = rang du sort dans la classe du combattant actif (0 à 4). */
    record Cast(int spell, int x, int y) implements Command { }

    record EndTurn() implements Command { }

    /** Forme texte compacte : {@code M 3 4}, {@code C 1 3 4}, {@code E}. */
    default String encode() {
        if (this instanceof Move m) return "M " + m.x() + " " + m.y();
        if (this instanceof Cast c) return "C " + c.spell() + " " + c.x() + " " + c.y();
        return "E";
    }

    /** Décodage tolérant : renvoie {@code null} si le texte est mal formé. */
    static Command decode(String text) {
        if (text == null) return null;
        String[] p = text.trim().split(" ");
        try {
            return switch (p[0]) {
                case "M" -> p.length == 3 ? new Move(Integer.parseInt(p[1]), Integer.parseInt(p[2])) : null;
                case "C" -> p.length == 4 ? new Cast(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])) : null;
                case "E" -> p.length == 1 ? new EndTurn() : null;
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
