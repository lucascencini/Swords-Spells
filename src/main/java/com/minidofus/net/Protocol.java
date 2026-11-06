package com.minidofus.net;

/**
 * Types de messages échangés entre clients et serveur. Le serveur fait autorité : les clients envoient des
 * intentions, le serveur vérifie, applique et diffuse. Chaque client rejoue les commandes reçues sur sa
 * propre copie du combat (créée avec la même graine) et compare l'empreinte d'état envoyée par le serveur.
 *
 * <pre>
 * client → serveur
 *   HELLO   version pseudo motDePasse jeton      connexion (jeton vide la première fois)
 *   CHAT    texte
 *   CLAIM   place | RELEASE place                prendre / libérer une place (0-2 rouges, 3-5 bleues)
 *   CLASS   place idClasse                       changer la classe d'une place (sa place, ou toutes pour le chef)
 *   SETTINGS carte tailleÉquipe difficulté chrono (chef) carte = « random » ou nom de MapTheme
 *   START                                        (chef) lancer le combat
 *   CMD     tour commande                        action du combattant actif (tour = numéro attendu)
 *   RESYNC                                       demande de rejouer tout le combat (désynchronisation)
 *   PING
 * serveur → client
 *   WELCOME idJoueur jeton
 *   REJECT  raison                               connexion refusée (la connexion est fermée ensuite)
 *   LOBBY   état                                 salon complet (voir {@link LobbyState#encode})
 *   CHAT    auteur texte                         auteur vide = message du serveur
 *   MATCH   graine carte rouges bleues contrôleurs chrono   début de combat (ou reprise)
 *   CMD     index commande empreinte             commande appliquée par le serveur
 *   SYNCED                                       fin du rattrapage après MATCH
 *   TIMER   secondes                             temps restant au combattant actif (0 = pas de chrono)
 *   NOTICE  texte                                information ponctuelle (erreur d'action…)
 *   PONG
 * </pre>
 */
public final class Protocol {

    /** À incrémenter à chaque changement incompatible du protocole ou des règles. */
    public static final int VERSION = 2;
    public static final int DEFAULT_PORT = 7777;
    public static final int MAX_PLAYERS = 6;
    public static final int MAX_NAME = 16;
    public static final int MAX_CHAT = 200;

    public static final String HELLO = "HELLO";
    public static final String CHAT = "CHAT";
    public static final String CLAIM = "CLAIM";
    public static final String RELEASE = "RELEASE";
    public static final String CLASS = "CLASS";
    public static final String SETTINGS = "SETTINGS";
    public static final String START = "START";
    public static final String CMD = "CMD";
    public static final String RESYNC = "RESYNC";
    public static final String PING = "PING";
    public static final String WELCOME = "WELCOME";
    public static final String REJECT = "REJECT";
    public static final String LOBBY = "LOBBY";
    public static final String MATCH = "MATCH";
    public static final String SYNCED = "SYNCED";
    public static final String TIMER = "TIMER";
    public static final String NOTICE = "NOTICE";
    public static final String PONG = "PONG";

    private Protocol() { }
}
