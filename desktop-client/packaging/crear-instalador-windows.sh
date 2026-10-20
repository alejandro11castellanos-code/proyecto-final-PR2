#!/usr/bin/env bash
# Crea los instaladores de Windows de SVB-GUA con jpackage. Hay que ejecutarlo EN Windows
# (jpackage solo genera el instalador del sistema donde corre); lo normal es dejar que lo haga
# GitHub con el flujo .github/workflows/instalador-windows.yml.
#
# Genera en target/instalador:
#   SVB-GUA-portatil.zip   Versión portátil: descomprimir y abrir SVB-GUA.exe (no instala nada).
#   SVB-GUA-<version>.exe  Instalador con acceso directo y entrada en el menú Inicio, por usuario
#                          (no pide permisos de administrador). Necesita WiX Toolset 3.
#
# Requisitos: JDK 21 (trae jpackage), Maven y Git Bash. Para fijar el servidor por defecto:
#   SVB_API_URL=https://mi-servicio.onrender.com ./packaging/crear-instalador-windows.sh
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION_APP="1.0.0"
# Identificador fijo: permite que una versión nueva reemplace a la instalada en vez de duplicarla.
UUID_ACTUALIZACION="57a39ad8-dd94-43d8-a814-134cf109bd3a"

mvn -q -DskipTests package

JAR="$(ls target/svb-gua-client-*.jar | grep -v '^target/original-' | head -n 1)"
rm -rf target/jpackage-input target/instalador target/portatil
mkdir -p target/jpackage-input target/instalador
cp "$JAR" target/jpackage-input/

OPCIONES_JAVA=(--java-options "-Dfile.encoding=UTF-8")
if [ -n "${SVB_API_URL:-}" ]; then
  OPCIONES_JAVA+=(--java-options "-Dsvb.api.url=${SVB_API_URL}")
fi

COMUNES=(
  --name "SVB-GUA"
  --app-version "$VERSION_APP"
  --vendor "UMG Programacion II"
  --description "SVB-GUA: Sistema de Venta Local de Boletos"
  --input target/jpackage-input
  --main-jar "$(basename "$JAR")"
  --main-class gt.umg.svbgua.Launcher
  --icon packaging/SVB-GUA.ico
  "${OPCIONES_JAVA[@]}"
)

echo "==> Versión portátil"
jpackage --type app-image "${COMUNES[@]}" --dest target/portatil
jar --create --file target/instalador/SVB-GUA-portatil.zip --no-manifest -C target/portatil .

echo "==> Instalador .exe"
if jpackage --type exe "${COMUNES[@]}" \
    --win-menu --win-shortcut --win-dir-chooser --win-per-user-install \
    --win-upgrade-uuid "$UUID_ACTUALIZACION" --dest target/instalador; then
  :
else
  echo "AVISO: no se pudo crear el instalador .exe (¿falta WiX Toolset 3?). Queda la versión portátil." >&2
fi

echo
echo "Listo:"
ls -lh target/instalador
