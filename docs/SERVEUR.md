# Jouer en ligne : installer le serveur

Le jeu en ligne fonctionne avec un **serveur** auquel tous les joueurs se connectent. Le serveur, c'est le même fichier
`SpellsAndSwords.jar` lancé avec l'option `--server`. Il garde l'état de référence du combat, valide chaque action, joue les
places tenues par l'ordinateur et gère le chrono, le chat et les reconnexions.

Deux façons de faire :

| | VPS (recommandé) | « Héberger sur ce PC » |
|---|---|---|
| Disponible | 24 h/24, même PC éteint | seulement quand le jeu est ouvert chez l'hôte |
| Réseau | n'importe où sur Internet | même réseau local, ou port ouvert sur la box |
| Installation | une fois, environ 5 minutes | aucune |

Un serveur = **un salon** (6 joueurs au maximum, 3 contre 3).

---

## 1. Sur un VPS IONOS (Linux XS ou mieux)

Le VPS Linux XS (1 vCPU, 1 Go de RAM) suffit largement : le serveur utilise environ 90 Mo de mémoire et presque pas de
processeur.

### 1.1 Construire le jeu (sur ton PC Windows)

Dans le dossier du projet :

```bat
build.bat
```

Le fichier `build\SpellsAndSwords.jar` est créé.

### 1.2 Copier les fichiers sur le VPS

L'adresse IP du VPS et le mot de passe root se trouvent dans le Cloud Panel IONOS : **Serveurs & Cloud → ton serveur**.
Dans PowerShell, depuis le dossier du projet (remplace `IP` par l'adresse du VPS) :

```powershell
scp build\SpellsAndSwords.jar deploy\install-server.sh root@IP:/root/
```

### 1.3 Installer le service

```powershell
ssh root@IP
```

Puis, sur le VPS :

```bash
bash install-server.sh SpellsAndSwords.jar --password MonMotDePasse
```

- `--password` est facultatif. Sans mot de passe, n'importe qui connaissant l'adresse peut entrer dans le salon.
- `--port 7777` permet de changer le port (7777 par défaut).

Le script installe Java, crée un utilisateur système `swords`, copie le jeu dans `/opt/swords-spells/` et crée le
service `swords-spells`. Ce service démarre tout seul au redémarrage du VPS et relance le serveur en cas de plantage.

### 1.4 Ouvrir le port dans le pare-feu IONOS

C'est l'étape qu'on oublie le plus souvent. Sans elle, personne ne peut se connecter.

1. Cloud Panel IONOS → **Réseau → Stratégies de pare-feu**.
2. Choisis la stratégie appliquée à ton serveur.
3. Ajoute une règle entrante : **TCP**, port **7777**, source **Toutes**.
4. Enregistre la règle (son application prend environ une minute).

### 1.5 Jouer

Chaque joueur lance le jeu, clique sur **Jouer en ligne**, puis saisit :

- **Adresse** : l'IP du VPS (ou `IP:port` si tu as changé le port) ;
- **Pseudo** : celui de son choix ;
- **Mot de passe** : celui choisi à l'installation.

> **Important : tout le monde doit avoir la même version du jeu.** Après chaque mise à jour, renvoie le nouveau
> `SpellsAndSwords.jar` à tes amis et mets aussi à jour le serveur (voir ci-dessous). Si les versions ne correspondent pas, la
> connexion est refusée avec un message clair.

---

## 2. Gérer le serveur

Toutes ces commandes se lancent sur le VPS (`ssh root@IP`).

| Je veux… | Commande |
|---|---|
| voir s'il tourne | `systemctl status swords-spells` |
| suivre le journal en direct (connexions, combats) | `journalctl -u swords-spells -f` |
| le redémarrer | `systemctl restart swords-spells` |
| l'arrêter / le relancer | `systemctl stop swords-spells` / `systemctl start swords-spells` |

**Mettre à jour le jeu** : recopie le nouveau JAR et relance le script. Les réglages sont conservés.

```powershell
scp build\SpellsAndSwords.jar root@IP:/root/
ssh root@IP "bash install-server.sh SpellsAndSwords.jar"
```

**Changer le mot de passe** : modifie `SWORDS_PASSWORD=` dans `/etc/swords-spells.env` (avec `nano`), puis lance
`systemctl restart swords-spells`. Une valeur vide supprime le mot de passe.

---

## 3. Héberger depuis son PC (sans VPS)

Dans **Jouer en ligne**, clique sur **Héberger sur ce PC** : le jeu démarre un serveur sur le port 7777, et l'écran du salon
affiche l'adresse à donner aux amis.

- **Même réseau (Wi-Fi ou maison)** : les amis saisissent l'adresse affichée (`192.168.x.x`). Windows peut demander
  d'autoriser Java dans le pare-feu : accepte pour les **réseaux privés**.
- **Amis à distance** : il faut rediriger le port **TCP 7777** de ta box vers ton PC, puis donner ton IP publique (visible
  par exemple sur un site comme « quel est mon IP »). C'est plus contraignant que le VPS, qui évite tout ça.

Le salon disparaît quand l'hôte quitte le mode en ligne.

---

## 4. Ce que gère le serveur

- **Salon** : le premier arrivé est le chef. Il choisit la carte, la taille des équipes, la difficulté de l'ordinateur
  et le chrono. Chacun prend une place (rouge ou bleue) et sa classe. Les places libres sont jouées par l'ordinateur.
- **Chrono par tour** : aucun, 30, 60 ou 90 secondes. À la fin du temps, le tour passe automatiquement.
- **Chat** : dans le salon, et en combat avec **Entrée**.
- **Reconnexion** : en cas de coupure, le jeu se reconnecte tout seul pendant 2 minutes et reprend le combat où il en
  était. En attendant, l'ordinateur joue la place du joueur absent.
- **Anti-triche de base** : chaque action est vérifiée par le serveur (bon joueur, action légale). Les clients
  comparent en permanence une empreinte de l'état du combat et se resynchronisent automatiquement en cas d'écart.

## 5. En cas de problème

| Symptôme | Cause probable |
|---|---|
| « Serveur injoignable » | port 7777 non ouvert dans le pare-feu IONOS, ou service arrêté (`systemctl status swords-spells`) |
| « Version différente du serveur » | le JAR du joueur ou celui du serveur n'est pas à jour |
| « Mot de passe incorrect » | vérifier `/etc/swords-spells.env` |
| « Le pseudo … est déjà utilisé » | un joueur du salon porte déjà ce pseudo (les majuscules ne comptent pas) |
| « Le salon est complet » | 6 joueurs sont déjà connectés |
