#!/bin/bash
# PARTY OS on a Mac plugged into a TV: builds what's needed, starts the party server, keeps the Mac awake,
# and opens the TV screen full screen with sound. Phones scan the QR code on the TV. Ctrl+C or close to stop.
set -e
cd "$(dirname "$0")"
ROOT="$(pwd)"
PORT="${PARTYOS_PORT:-8080}"

if [ -z "$JAVA_HOME" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE '"(1[7-9]|2[0-9])'; then
  for j in /opt/homebrew/opt/openjdk@17 /opt/homebrew/opt/openjdk /usr/local/opt/openjdk@17; do
    [ -d "$j/libexec/openjdk.jdk/Contents/Home" ] && export JAVA_HOME="$j/libexec/openjdk.jdk/Contents/Home" && break
  done
fi
[ -n "$JAVA_HOME" ] || { echo "Java 17 is needed: brew install openjdk@17"; exit 1; }
command -v npm >/dev/null || { echo "Node.js is needed: brew install node"; exit 1; }

# A second copy can't get the port, and its TV window would quietly open the first one.
if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port $PORT is already in use: PARTY OS is probably running in another Terminal window."
  echo "Close that window (or press Ctrl+C in it), then double-click this again."
  echo "Or use another port: PARTYOS_PORT=8081 \"$ROOT/Start Party OS.command\""
  exit 1
fi

echo "▶ Building the phone + TV screens…"
cd "$ROOT/controller"
[ -d node_modules ] || npm ci --no-audit --no-fund
BUILD_LOG="$(mktemp)"
npm run build --silent >"$BUILD_LOG" 2>&1 || { cat "$BUILD_LOG"; echo "The screens didn't build (errors above)."; exit 1; }
rm -f "$BUILD_LOG"

echo "▶ Building the party server…"
cd "$ROOT/tv"
./gradlew -q :devserver:installDist

PIN="${PARTYOS_PIN:-$(( RANDOM % 9000 + 1000 ))}"
"$ROOT/tv/devserver/build/install/devserver/bin/devserver" --port "$PORT" --pin "$PIN" --static "$ROOT/controller/dist" &
SERVER=$!
trap 'echo; echo "Stopping PARTY OS…"; kill $SERVER 2>/dev/null; exit 0' INT TERM EXIT
caffeinate -dimsu -w $SERVER &

for _ in $(seq 1 60); do curl -fs "http://127.0.0.1:$PORT/healthz" >/dev/null && break; sleep 0.5; done
if ! kill -0 $SERVER 2>/dev/null || ! curl -fs "http://127.0.0.1:$PORT/healthz" >/dev/null; then
  echo "The party server didn't start (see the messages above)."
  exit 1
fi

URL="http://127.0.0.1:$PORT/tv"
PROFILE="$HOME/Library/Application Support/PartyOS-TV"
if [ -d "/Applications/Google Chrome.app" ]; then
  open -na "Google Chrome" --args --user-data-dir="$PROFILE" --kiosk --autoplay-policy=no-user-gesture-required --no-first-run --disable-features=Translate "$URL"
else
  open "$URL"
fi

echo
echo "  PARTY OS is live on the TV window."
echo "  Host PIN for phones: $PIN  (phone host panel: /host)"
echo "  Rehearse with bots:  node \"$ROOT/controller/scripts/bots.mjs\" 6 http://127.0.0.1:$PORT"
echo "  Close this window or press Ctrl+C to stop. (Cmd+Q quits the TV window.)"
wait $SERVER
