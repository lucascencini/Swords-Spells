#!/usr/bin/env bash
# Installe (ou met à jour) le serveur Swords & Spells sur un VPS Linux (Ubuntu ou Debian), en service systemd.
#
#   sudo bash install-server.sh mini-dofus.jar            installation ou mise à jour
#   sudo bash install-server.sh mini-dofus.jar --password monSecret   avec un mot de passe de serveur
#
# Ce que fait le script :
#   - installe Java (sans interface graphique) s'il manque ;
#   - crée un utilisateur système « swords » sans droits particuliers ;
#   - copie le jeu dans /opt/swords-spells et le lance en service (redémarrage automatique) ;
#   - ouvre le port 7777 dans le pare-feu ufw s'il est actif.
set -euo pipefail

JAR="${1:-}"
PORT=7777
PASSWORD=""
shift || true
while [ $# -gt 0 ]; do
  case "$1" in
    --password) PASSWORD="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    *) echo "Option inconnue : $1"; exit 1 ;;
  esac
done

if [ "$(id -u)" -ne 0 ]; then echo "À lancer en root (ou avec sudo)."; exit 1; fi
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  echo "Usage : sudo bash install-server.sh chemin/vers/mini-dofus.jar [--password secret] [--port 7777]"
  exit 1
fi

echo "== Java"
if ! command -v java >/dev/null 2>&1; then
  apt-get update -q
  apt-get install -y -q openjdk-21-jre-headless || apt-get install -y -q openjdk-17-jre-headless
fi
java -version 2>&1 | head -1

echo "== Utilisateur et fichiers"
id swords >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin swords
install -d -o root -g root -m 755 /opt/swords-spells
install -o root -g root -m 644 "$JAR" /opt/swords-spells/mini-dofus.jar

# le mot de passe est conservé d'une mise à jour à l'autre, sauf si un nouveau est donné
if [ -n "$PASSWORD" ] || [ ! -f /etc/swords-spells.env ]; then
  umask 077
  printf 'SWORDS_PASSWORD=%s\n' "$PASSWORD" > /etc/swords-spells.env
  chown root:swords /etc/swords-spells.env
  chmod 640 /etc/swords-spells.env
fi

echo "== Service systemd"
cat > /etc/systemd/system/swords-spells.service <<UNIT
[Unit]
Description=Serveur de jeu Swords & Spells
After=network-online.target
Wants=network-online.target

[Service]
User=swords
EnvironmentFile=/etc/swords-spells.env
# 1 Go de mémoire sur le VPS : 160 Mo suffisent largement au serveur
ExecStart=/usr/bin/java -Xmx160m -XX:+UseSerialGC -Djava.awt.headless=true -jar /opt/swords-spells/mini-dofus.jar --server --port ${PORT}
Restart=always
RestartSec=3
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
PrivateDevices=true

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable swords-spells >/dev/null
systemctl restart swords-spells

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  echo "== Pare-feu ufw : ouverture du port ${PORT}"
  ufw allow "${PORT}/tcp"
fi

sleep 3
if systemctl is-active --quiet swords-spells; then
  IP=$(hostname -I 2>/dev/null | awk '{print $1}')
  echo
  echo "Serveur démarré. Dans le jeu : « Jouer en ligne », adresse ${IP:-<IP du VPS>}$( [ "$PORT" != 7777 ] && echo ":$PORT" )"
  echo "Journal en direct : journalctl -u swords-spells -f"
  echo "N'oubliez pas d'ouvrir le port ${PORT} (TCP) dans le pare-feu du panneau IONOS."
else
  echo "Le service n'a pas démarré :"
  journalctl -u swords-spells -n 30 --no-pager
  exit 1
fi
