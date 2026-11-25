#!/usr/bin/env bash
# Se ejecuta una sola vez, al crear el volumen (docker-entrypoint-initdb.d).
set -euo pipefail
psql=(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB")
for f in /geo-bcn/db/migration/V*.sql; do
  echo "== migración: $f"; "${psql[@]}" -f "$f"
done
echo "== carga fonts_beure"
"${psql[@]}" -f /geo-bcn/db/load/load_fonts_beure.sql < /geo-bcn/db/data/fonts_beure_2026.csv
