#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${MYTIX_DB_NAME:?Set MYTIX_DB_NAME to your selected project database}"
: "${MYTIX_DB_USER:?Set MYTIX_DB_USER to your local database account}"
node -e 'if (Number(process.versions.node.split(".")[0]) < 22) process.exit(1)' || { echo 'Node.js 22+ is required.' >&2; exit 1; }
sh ./mvnw -q compile dependency:copy-dependencies
(cd web && npm ci --no-fund && npm run build)
if [[ -z "${MYTIX_DB_PASSWORD:-}" ]]; then
  read -r -s -p 'MySQL password (local input only): ' MYTIX_DB_PASSWORD
  printf '\n'
  export MYTIX_DB_PASSWORD
fi
export MYTIX_API_SECRET="${MYTIX_API_SECRET:-$(node -e 'process.stdout.write(require("crypto").randomBytes(32).toString("base64"))')}"
export MYTIX_API_PORT="${MYTIX_API_PORT:-8081}"
export MYTIX_WEB_PORT="${MYTIX_WEB_PORT:-3000}"
export MYTIX_DEMO_MODE="${MYTIX_DEMO_MODE:-true}"
export MYTIX_API_URL="http://127.0.0.1:$MYTIX_API_PORT"
export MYTIX_WEB_ORIGIN="${MYTIX_WEB_ORIGIN:-http://127.0.0.1:$MYTIX_WEB_PORT}"
mkdir -p local/web-runtime
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp 'target/classes:target/dependency/*' mytix.api.WebServer >local/web-runtime/api-out.log 2>local/web-runtime/api-error.log &
api_pid=$!
trap 'kill "$api_pid" 2>/dev/null || true' EXIT INT TERM
node --input-type=module -e '
  let ready=false;
  for(let i=0;i<50;i++){
    try { const r=await fetch(process.env.MYTIX_API_URL+"/api/status",{method:"POST",headers:{"Content-Type":"application/json","X-Mytix-Key":process.env.MYTIX_API_SECRET},body:"{}"}); if(r.ok){ready=true;break;} } catch {}
    await new Promise(r=>setTimeout(r,200));
  }
  if(!ready)process.exit(1);
' || { echo 'Java API did not become ready. See local/web-runtime/api-error.log.' >&2; exit 1; }
kill -0 "$api_pid" || exit 1
echo "Open $MYTIX_WEB_ORIGIN — Ctrl+C stops both services."
unset MYTIX_DB_PASSWORD MYTIX_DB_NAME MYTIX_DB_USER MYTIX_DB_HOST MYTIX_DB_PORT
(cd web && node node_modules/next/dist/bin/next start --hostname 127.0.0.1 --port "$MYTIX_WEB_PORT")
