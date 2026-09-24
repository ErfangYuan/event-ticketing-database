#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -z "${MYTIX_DB_PASSWORD:-}" ]]; then
 read -r -s -p 'MySQL password (local input only): ' MYTIX_DB_PASSWORD
 printf '
'
 export MYTIX_DB_PASSWORD
fi
mvn -q compile dependency:copy-dependencies
exec java -cp 'target/classes:target/dependency/*' mytix.Main
