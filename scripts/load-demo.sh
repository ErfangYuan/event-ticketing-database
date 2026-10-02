#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ $# -lt 1 || $# -gt 2 || ( $# -eq 2 && "$2" != "--clear" ) ]]; then
  printf '%s\n' 'Usage: bash scripts/load-demo.sh <database> [--clear]' >&2
  exit 2
fi
database="$1"
if [[ -n "${MYTIX_DB_NAME:-}" && "$MYTIX_DB_NAME" != "$database" ]]; then
  printf '%s\n' 'Database argument must match MYTIX_DB_NAME. No data was changed.' >&2
  exit 2
fi
if [[ -n "${JAVA_HOME:-}" && ! -x "$JAVA_HOME/bin/javac" ]]; then
  printf '%s\n' 'JAVA_HOME must point to a JDK 17+ containing bin/javac.' >&2
  exit 2
fi
export MYTIX_DB_NAME="$database"
sh ./mvnw -q compile dependency:copy-dependencies
if [[ -z "${MYTIX_DB_PASSWORD:-}" ]]; then
  read -r -s -p 'MySQL password (local input only): ' MYTIX_DB_PASSWORD
  printf '\n'
  export MYTIX_DB_PASSWORD
fi
mode='--reset-and-load'
if [[ "${2:-}" == '--clear' ]]; then mode='--clear'; fi
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp 'target/classes:target/dependency/*' mytix.database.demo.DemoDataMain "$mode" "$database"
