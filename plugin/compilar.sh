#!/bin/bash
# Compila el plugin contra las librerías del server y deja build/ClaudiaMC.jar.
set -e
cd "$(dirname "$0")"
SERVER=${1:-$HOME/mc-server}  # carpeta de un server de Paper ya arrancado una vez (trae las librerías)
CP=$(find "$SERVER/libraries" -name '*.jar' | tr '\n' ':')$(ls "$SERVER"/plugins/voicechat-bukkit-*.jar 2>/dev/null | head -1)
rm -rf build && mkdir -p build/clases
~/.local/jdk-25/bin/javac --release 25 -nowarn -cp "$CP" -d build/clases $(find src -name '*.java')
cp res/plugin.yml res/config.yml build/clases/
~/.local/jdk-25/bin/jar --create --file build/ClaudiaMC.jar -C build/clases .
echo "OK: build/ClaudiaMC.jar"
