#!/usr/bin/env bash
# Lets real phones on your Wi-Fi play against PARTY OS running in the Android TV emulator.
#   1. adb forward:   Mac 127.0.0.1:18080  -> emulator :8080
#   2. lan-proxy:     Mac <wifi-ip>:8080   -> 127.0.0.1:18080
#   3. QR override:   the app advertises http://<wifi-ip>:8080 instead of the emulator's 10.0.2.x address
set -euo pipefail
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
[ -n "$IP" ] || { echo "Could not find this Mac's Wi-Fi IP address." >&2; exit 1; }

"$ADB" wait-for-device
"$ADB" forward tcp:18080 tcp:8080
"$ADB" shell am start -n com.partyos.tv/.MainActivity --es override "$IP:8080" >/dev/null
echo "Phones join at: http://$IP:8080  (the QR on the emulated TV points here too)"
exec node "$(dirname "$0")/../tv/tools/lan-proxy.mjs" --listen "0.0.0.0:8080" --target "127.0.0.1:18080"
