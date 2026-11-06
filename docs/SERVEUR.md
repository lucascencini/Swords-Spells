# Jouer en ligne : installer le serveur

Le jeu en ligne fonctionne avec un **serveur** auquel tous les joueurs se connectent. Le serveur, c'est le même fichier
`SpellsAndSwords.jar` lancé avec l'option `--server`. Il garde l'état de référence du combat, valide chaque action, joue les
places tenues par l'ordinateur et gère le chrono, le chat et les reconnexions. Il conserve aussi **les comptes et les
personnages** des joueurs (niveaux, expérience, talents, sorts) : c'est la « base de données » du jeu.

Deux façons de faire :

| | VPS (recommandé) | « Héberger sur ce PC » |
|---|---|---|
| Disponible | 24 h/24, même PC éteint | seulement quand le jeu est ouvert chez l'hôte |
| Réseau | n'importe où sur Internet | même réseau local, ou port ouvert sur la box |
| Installation | une fois, environ 5 minutes | aucune |

Un serveur = **un salon** (6 joueurs au maximum, 3 contre 3), plus les combats contre l'ordinateur que chaque joueur
connecté peut lancer avec ses personnages, en parallèle du salon.

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
Les comptes sont enregistrés dans `/var/lib/swords-spells/accounts/` (un petit fichier par compte).

### 1.4 Ouvrir le port dans le pare-feu IONOS

C'est l'étape qu'on oublie le plus souvent. Sans elle, personne ne peut se connecter.

1. Cloud Panel IONOS → **Réseau → Stratégies de pare-feu**.
2. Choisis la stratégie appliquée à ton serveur.
3. Ajoute une règle entrante : **TCP**, port **7777**, source **Toutes**.
4. Enregistre la règle (son application prend environ une minute).

### 1.5 Jouer

Chaque joueur lance le jeu, clique sur **Jouer en ligne**, puis saisit :

- **Serveur** : l'IP du VPS (ou `IP:port` si tu as changé le port) ;
- **Compte** et **mot de passe du compte** : au choix la première fois, avec le bouton **Créer un compte**, puis
  **Se connecter** les fois suivantes ;
- **Mot de passe du serveur** : celui choisi à l'installation (s'il y en a un).

**Jouer en invité** permet d'entrer dans le salon sans compte, avec les classes de base (sans progression).

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

**Changer le mot de passe du serveur** : modifie `SWORDS_PASSWORD=` dans `/etc/swords-spells.env` (avec `nano`), puis lance
`systemctl restart swords-spells`. Une valeur vide supprime le mot de passe.

### Sauvegarder les comptes

Toute la progression des joueurs tient dans le dossier `/var/lib/swords-spells`. Pour en faire une copie sur ton PC
(dans PowerShell) :

```powershell
ssh root@IP "tar czf /root/comptes.tgz -C /var/lib swords-spells"
scp root@IP:/root/comptes.tgz .
```

Pour la restaurer : arrête le service, recopie le dossier, puis redonne-le à l'utilisateur du service :

```bash
systemctl stop swords-spells
tar xzf /root/comptes.tgz -C /var/lib
chown -R swords:swords /var/lib/swords-spells
systemctl start swords-spells
```

Une mise à jour du jeu (recopie du JAR et `install-server.sh`) ne touche jamais à ce dossier.

### Sécurité des comptes

- Le mot de passe d'un compte **ne quitte jamais le PC du joueur** : le jeu envoie seulement une clé qui en est dérivée
  (PBKDF2), et le serveur ne garde qu'une empreinte salée de cette clé. Même avec les fichiers du serveur, on ne
  retrouve pas les mots de passe.
- La connexion n'est **pas chiffrée** : quelqu'un qui intercepte le trafic pourrait rejouer la clé d'un compte de ce
  serveur (mais pas retrouver le mot de passe). Pour un jeu entre amis c'est suffisant ; ne réutilisez simplement pas un
  mot de passe important.
- Il n'y a pas de récupération de mot de passe : en cas d'oubli, supprime le fichier du compte dans
  `/var/lib/swords-spells/accounts/` (le joueur recrée alors un compte, ses personnages sont perdus).
- Chaque choix de talents et de sorts est revérifié par le serveur (points, niveau, prérequis) : un client modifié ne
  peut pas s'attribuer de talents ou de sorts interdits.

---

## 3. Héberger depuis son PC (sans VPS)

Dans **Jouer en ligne**, clique sur **Héberger sur ce PC** : le jeu démarre un serveur sur le port 7777, et l'écran du salon
affiche l'adresse à donner aux amis.

- **Même réseau (Wi-Fi ou maison)** : les amis saisissent l'adresse affichée (`192.168.x.x`). Windows peut demander
  d'autoriser Java dans le pare-feu : accepte pour les **réseaux privés**.
- **Amis à distance** : il faut rediriger le port **TCP 7777** de ta box vers ton PC, puis donner ton IP publique (visible
  par exemple sur un site comme « quel est mon IP »). C'est plus contraignant que le VPS, qui évite tout ça.

Le salon disparaît quand l'hôte quitte le mode en ligne. Les comptes créés sur un PC hôte sont enregistrés dans le
dossier `.spells-and-swords/server` du dossier personnel de l'hôte ; à la connexion de l'hôte, son compte est créé
automatiquement s'il n'existe pas encore.

---

## 4. Ce que gère le serveur

- **Salon** : le premier arrivé est le chef. Il choisit la carte, la taille des équipes, la difficulté de l'ordinateur,
  le chrono et les niveaux (réels, ou égalisés au niveau du plus haut personnage). Chacun prend une place (rouge ou
  bleue) et y met un de ses personnages ou une classe de base. Les places libres sont jouées par l'ordinateur.
- **Progression** : à la fin de chaque combat, le serveur calcule l'expérience de chaque personnage engagé, l'enregistre
  et l'annonce à son joueur. Un combat contre l'ordinateur abandonné ne rapporte rien.
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
| « Mot de passe du serveur incorrect » | vérifier `/etc/swords-spells.env` |
| « Compte ou mot de passe incorrect » | mauvais mot de passe (les majuscules comptent), ou compte jamais créé : utiliser **Créer un compte** la première fois |
| « Le compte … existe déjà » | le nom est pris sur ce serveur (les majuscules ne comptent pas) |
| « … est un compte : connectez-vous avec son mot de passe » | un invité a pris le nom d'un compte existant |
| « Le pseudo … est déjà utilisé » | un joueur du salon porte déjà ce pseudo (les majuscules ne comptent pas) |
| « Le salon est complet » | 6 joueurs sont déjà connectés |
