#!/usr/bin/env bash
# Crea el instalador de macOS (.dmg) de SVB-GUA con jpackage.
#
#   ./packaging/crear-instalador-mac.sh            # genera target/instalador/SVB-GUA-<version>.dmg
#   ./packaging/crear-instalador-mac.sh app-image  # solo la app suelta (para probarla sin instalar)
#
# Requisitos: macOS, JDK 21 (trae jpackage) y Maven. El instalador incluye su propio Java:
# quien lo instala no necesita tener Java ni JavaFX. jpackage solo genera el instalador del
# sistema en el que corre; el de Windows hay que armarlo desde Windows.
set -euo pipefail

cd "$(dirname "$0")/.."

TIPO="${1:-dmg}"
VERSION_APP="1.0.0" # macOS exige que el primer número sea mayor que 0

if ! command -v jpackage >/dev/null 2>&1; then
  echo "No se encontró jpackage. Use un JDK 21 (por ejemplo: export JAVA_HOME=/opt/homebrew/opt/openjdk@21 y agregue su bin al PATH)." >&2
  exit 1
fi

mvn -q -DskipTests package

JAR="$(ls target/svb-gua-client-*.jar | grep -v '^target/original-' | head -n 1)"
rm -rf target/jpackage-input target/instalador
mkdir -p target/jpackage-input
cp "$JAR" target/jpackage-input/

jpackage \
  --type "$TIPO" \
  --name "SVB-GUA" \
  --app-version "$VERSION_APP" \
  --vendor "UMG Programacion II" \
  --description "SVB-GUA: Sistema de Venta Local de Boletos" \
  --input target/jpackage-input \
  --main-jar "$(basename "$JAR")" \
  --main-class gt.umg.svbgua.Launcher \
  --icon packaging/SVB-GUA.icns \
  --java-options "-Dfile.encoding=UTF-8" \
  --dest target/instalador

echo
echo "Listo:"
ls -lh target/instalador
