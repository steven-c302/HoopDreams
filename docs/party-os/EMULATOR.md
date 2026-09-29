# Running PARTY OS in the Android TV emulator on the Mac

## One-time setup

The Android TV emulator needs about **7.5 GB of free disk** for its user-data partition. Everything else needs about 12 GB more.

```bash
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
# Android command-line tools unpacked into ~/Library/Android/sdk/cmdline-tools/latest, then:
~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "emulator" "platforms;android-37.0" "system-images;android-34;google-tv;arm64-v8a"
~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager create avd -n partyos_tv -k "system-images;android-34;google-tv;arm64-v8a" -d tv_1080p
```

## Every time

```bash
~/Library/Android/sdk/emulator/emulator -avd partyos_tv -no-snapshot-save &
cd controller && npm run build && cd ../tv && ./gradlew :app:installDebug
../scripts/emulator-party.sh     # keep this running; Ctrl+C to stop
```

The emulator lives on a private network inside the Mac, where its own address is 10.0.2.15. `emulator-party.sh` connects real phones to it in three steps:

1. **`adb forward`:** maps the Mac's `127.0.0.1:18080` to the app's port 8080 inside the emulator.
2. **`lan-proxy.mjs`:** listens on the Mac's Wi-Fi address on port 8080 and pipes traffic, including WebSockets, to `127.0.0.1:18080`.
3. **The QR code:** the script sets the app's *Advertised address override* (debug builds only), so the QR code points at the Mac.

Scan the QR code on the emulated TV with a real phone on the same Wi-Fi. The emulator maps these keyboard keys to the remote:

| Key | Remote button |
|---|---|
| Arrow keys | D-pad |
| Enter | OK |
| Esc | Back |
| M | Menu |

## Phone-only development, without the emulator

```bash
cd tv && ./gradlew :devserver:installDist && build/install/devserver/bin/devserver --pin 1234 --static ../controller/dist
```

This prints a join URL for your Wi-Fi address. For live reloading, run `npm run dev` in `controller/`; Vite proxies `/api` and `/ws` to the devserver.
