# Installing PARTY OS on a TCL Google TV

PARTY OS is not on Google Play. You install (sideload) the APK yourself. It stays private to your TV.

## 1. Get the APK

**Install the release APK on the TV.** The debug APK is noticeably slower because Android runs debuggable apps unoptimised.

Pick either source:

- **From CI:** open the repo's **Actions** tab, then the latest **PARTY OS** run, and download **partyos-release-apk**. Unzip it to get `app-release.apk`.
- **Build locally:** run `cd controller && npm ci && npm run build`, then `cd ../tv && ./gradlew :app:assembleRelease`. The APK lands at `tv/app/build/outputs/apk/release/app-release.apk`.

Debug and release builds are signed with the same committed key (`tv/app/debug.keystore`), so any new build installs over the old one and keeps your settings and party history. If you add your own `RELEASE_KEYSTORE_*` secrets, uninstall once before switching keys.

## 2. Turn on developer options (one time)

1. On the TV: **Settings → System → About**.
2. Scroll to **Android TV OS build** and press OK on it 7 times until it says you're a developer.
3. Go back to **Settings → System → Developer options**. Turn on **USB debugging**, then **Wireless debugging**.

## 3. Install with ADB over Wi-Fi

Run these on the Mac. The Mac and the TV must be on the same network.

1. On the TV, open **Developer options → Wireless debugging → Pair device with pairing code**. Note the `IP:port` and the 6-digit code. Pairing is needed only once.
2. Pair:

   ```bash
   ~/Library/Android/sdk/platform-tools/adb pair <TV-IP>:<pairing-port> <6-digit-code>
   ```

3. The main Wireless debugging screen shows a different `IP:port`. Connect to that one, then install:

   ```bash
   ~/Library/Android/sdk/platform-tools/adb connect <TV-IP>:<port>
   ~/Library/Android/sdk/platform-tools/adb install -r app-release.apk
   ```

4. **PARTY OS** now appears in the TV's apps row. If it doesn't, look under **Apps → See all apps**.

Some TV builds skip the pairing step. There, `adb connect <TV-IP>:5555` works once USB debugging is on.

## Fallback: install without ADB

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Allow it to install apps: **Settings → Apps → Security & restrictions → Unknown sources → Downloader**.
3. On the Mac, run `cd tv/app/build/outputs/apk/release && python3 -m http.server 8000`.
4. In Downloader, enter `http://<your-Mac-IP>:8000/app-release.apk` and install.

## 4. Party night checklist

- **Network:** Put the TV on your main Wi-Fi, or on Ethernet with the phones on the same network. Guest networks usually block phones from reaching the TV.
- **Start it:** Open PARTY OS, choose **Play Games**, and have everyone scan the QR code.
- **Host PIN:** Your phone can be a remote. Open `http://<TV address>/host` and enter the PIN shown in **Settings**.
- **Mid-game controls:** Press **Back** during a game to pause, skip a phase, end the game, or remove a player.
- **Ending the night:** Press **Back** on the home screen and choose **Stop and exit**. Hosting keeps running in the background until you do.

## Checking performance on the TV

With ADB connected:

```bash
~/Library/Android/sdk/platform-tools/adb shell am start -n com.partyos.tv/.MainActivity --ez tour true
~/Library/Android/sdk/platform-tools/adb logcat -s PARTYOS_PERF
```

After about a minute, the log prints `PARTYOS_PERF tour=bluff-round frames=… onTime=…% janky=…%`. The target is **onTime ≥ 95%**. `onTime` counts frames that met their display deadline, and it only means something on the release APK.
