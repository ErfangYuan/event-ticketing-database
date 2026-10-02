#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" && ! -x "$JAVA_HOME/bin/javac" ]]; then
 printf '%s\n' 'JAVA_HOME must point to a JDK 17+ directory containing bin/javac, not its parent directory.' >&2
 exit 1
fi
sh ./mvnw -q compile dependency:copy-dependencies
if [[ -z "${MYTIX_DB_PASSWORD:-}" ]]; then
 read -r -s -p 'MySQL password (local input only): ' MYTIX_DB_PASSWORD
 printf '
'
 export MYTIX_DB_PASSWORD
fi
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp 'target/classes:target/dependency/*' mytix.Main
