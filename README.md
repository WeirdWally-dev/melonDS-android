# melonDS Android port
Android port of [melonDS](https://melonds.kuribo64.net/), a DS and DSi emulator.

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=me.magnum.melonds&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80">](https://github.com/rafaelvcaetano/melonDS-android/releases/latest)

|Rom List|Dark Theme|Pocket Physics|Layout Editor|
|---|---|---|---|
|![Screenshot 1](./.github/images/screenshot_mobile0.png)|![Screenshot 2](./.github/images/screenshot_mobile1.png)|![Screenshot 3](./.github/images/screenshot_mobile2.png)|![Screenshot 4](./.github/images/screenshot_mobile3.png)|

# Local multiplayer (this fork)
Phones on the same Wi-Fi (or one phone's hotspot) can play local wireless games together.

**DS Lobby** (`DS-Lobby.apk` on the releases page) makes it feel like two DSes finding each other:
1. Install melonDS from this fork and DS Lobby on every phone. Add your ROM folder in melonDS as usual.
2. Open DS Lobby once and pick a name and favourite colour. That's all the setup.
3. Everyone opens DS Lobby. Nearby players appear on their own.
4. One player taps **Open a room** and picks a game. Everyone else taps **Join** on that room.
5. The host taps **Start**. Every phone opens melonDS straight into the game, already connected.
   Go to the game's wireless mode (or DS Download Play) and you'll find each other.

Your lobby name and colour become your in-game DS profile (when using melonDS's built-in firmware).
Each player needs their own copy of the game; a different region usually works.

Prefer doing it by hand? The ROM list menu also has **Local multiplayer**, where you can host or join by IP.

# Missing Features
*  DSi SD card support
*  Customizable button skins
*  More display filters

# Performance
Performance is solid on 64 bit devices with thread rendering and JIT enabled, and should run at full speed on flagship devices. Performance on older devices, specially
32 bit devices, is very poor due to the lack of JIT support.

# Integration with third-party frontends
It's possible to launch melonDS from third part frontends. For that, you will need to have the ROMs you want to launch already scanned by melonDS. Then, you can configure your
third-party frontend with the following configuration:
*  Package name: `me.magnum.melonds`
*  Activity name: `me.magnum.melonds.ui.emulator.EmulatorActivity`
*  Parameters (choose one):
    * Intent data (preferred) - a URI of the NDS ROM (ZIP and 7z files are supported). Ensure [read permission is granted](https://developer.android.com/reference/android/content/Intent#FLAG_GRANT_READ_URI_PERMISSION)
    * `uri` (deprecated) - a string with the [SAF](https://developer.android.com/guide/topics/providers/create-document-provider) URI of the NDS ROM (ZIP and 7z files are supported)
    * `PATH` (deprecated) - a string with the absolute path to the NDS ROM (ZIP and 7z files are supported)

### Pegasus metadata files
* [melonds.metadata.txt](./.github/pegasus/melonds.metadata.txt) 
* [melonds-nightly.metadata.txt](./.github/pegasus/melonds-nightly.metadata.txt) 

### Info regarding save files
When launching ROMs from third-party frontends, if melonDS hasn't scanned that particular ROM previously, it won't be able to create the save file next to the ROM file if the
option "Save next to ROM file" is enabled in the settings or the save file directory is not set. Instead, melonDS will create a save file in
`Android/data/me.magnum.melonds/files/saves`

# Nightly Builds

To have access to the latest changes, you can install nightly builds that you can find [here](https://github.com/rafaelvcaetano/melonDS-android/releases/tag/nightly-release).

Be aware that these builds can contain more bugs than usual and you may need to clear your app data to get it to work properly after updates.

# Building
To build the project you will need Android SDK, NDK and CMake.

## Build steps:
1.  Clone the project, including submodules with:
    
    `git clone --recurse-submodules https://github.com/rafaelvcaetano/melonDS-android.git`
2.  Install the Android SDK, NDK and CMake
3.  Build with:
    1.  Unix: `./gradlew :app:assembleGitHubProdDebug`
    2.  Windows: `gradlew.bat :app:assembleGitHubProdDebug`
4.  The generated APK can be found at `app/gitHubProd/debug`

If you want to create a release build, you will need to modify your `local.properties` file to include the following fields:  
*  `MELONDS_KEYSTORE=<path_to_your_keystore>`
*  `MELONDS_KEYSTORE_PASSWORD=<keystore_password>`
*  `MELONDS_KEY_ALIAS=<name_of_your_key_alias>`
*  `MELONDS_KEY_PASSWORD=<key_alias_password>`
