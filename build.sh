#!/usr/bin/env sh
# Compile, teste et assemble Mini Dofus avec le seul JDK (17 ou plus) : ni Maven ni Gradle requis.
#
#   ./build.sh          compile + tests + build/SpellsAndSwords.jar
#   ./build.sh run      idem puis lance le jeu
#   ./build.sh package  idem puis crée une application autonome (Java embarqué) dans build/dist
set -e
cd "$(dirname "$0")"

OUT=build
JAVAC_OPTS="-encoding UTF-8 --release 17 -Xlint:all"

echo "== Compilation"
rm -rf "$OUT/classes" "$OUT/test"
mkdir -p "$OUT/classes" "$OUT/test"
find src/main/java -name '*.java' > "$OUT/sources.txt"
javac $JAVAC_OPTS -d "$OUT/classes" @"$OUT/sources.txt"
cp -R src/main/resources/. "$OUT/classes/"

echo "== Tests"
find src/test/java -name '*.java' > "$OUT/test-sources.txt"
javac $JAVAC_OPTS -cp "$OUT/classes" -d "$OUT/test" @"$OUT/test-sources.txt"
java -Dstdout.encoding=UTF-8 -cp "$OUT/classes:$OUT/test" com.minidofus.AllTests

echo "== JAR"
jar --create --file "$OUT/SpellsAndSwords.jar" --main-class com.minidofus.Main -C "$OUT/classes" .
echo "OK : $OUT/SpellsAndSwords.jar"

case "$1" in
  run)
    java -jar "$OUT/SpellsAndSwords.jar"
    ;;
  package)
    rm -rf "$OUT/dist" "$OUT/jpackage-input"
    mkdir -p "$OUT/jpackage-input"
    cp "$OUT/SpellsAndSwords.jar" "$OUT/jpackage-input/"
    ICON=packaging/icon.png
    [ "$(uname)" = "Darwin" ] && ICON=""   # macOS attend un .icns
    jpackage --type app-image --name SpellsAndSwords --app-version 1.0.0 \
      --input "$OUT/jpackage-input" --main-jar SpellsAndSwords.jar --main-class com.minidofus.Main \
      --add-modules java.desktop \
      --jlink-options "--strip-debug --no-header-files --no-man-pages --strip-native-commands" \
      ${ICON:+--icon "$ICON"} --dest "$OUT/dist"
    echo "OK : application autonome dans $OUT/dist/SpellsAndSwords"
    ;;
esac
