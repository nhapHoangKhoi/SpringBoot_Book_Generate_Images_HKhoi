#!/usr/bin/env bash
# Builds the frontend into the backend's static resources and starts one Spring Boot process
# on http://localhost:8080 — one command, one origin, no CORS to configure.
#
# For UI work, run the backend with `./mvnw spring-boot:run` and the Vite dev server with
# `npm --prefix frontend run dev` instead: it proxies /api to :8080 and hot-reloads.
set -euo pipefail
cd "$(dirname "$0")"

if [ -d frontend ]; then
  echo "==> Building the frontend"
  npm --prefix frontend install
  npm --prefix frontend run build
  rm -rf src/main/resources/static
  mkdir -p src/main/resources/static
  cp -r frontend/dist/* src/main/resources/static/
fi

echo "==> Starting on http://localhost:8080"
echo "    Gemini mode: $(grep -E '^gemini\.mode' src/main/resources/application.properties || echo 'unset (defaults to real)')"
exec ./mvnw spring-boot:run
