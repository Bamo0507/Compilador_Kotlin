#!/usr/bin/env bash
# Uso: scripts/arm.sh programa.s
# Ensambla el programa con gcc dentro de un contenedor ARM de 32 bits, lo ejecuta y
# devuelve su codigo de salida.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Uso: $0 programa.s" >&2
  exit 2
fi

carpeta="$(cd "$(dirname "$1")" && pwd)"
nombre="$(basename "$1" .s)"

# El ejecutable queda en /tmp del contenedor, para no dejar binarios junto al fuente.
docker run --rm --platform linux/arm/v7 \
  -v "$carpeta":/trabajo -w /trabajo compiscript-arm \
  sh -c "gcc -o /tmp/$nombre $nombre.s && /tmp/$nombre"
