# Glass Launcher for Sky / Converge TV box

Android TV home screen with a liquid-glass look.

- **Top left:** Google (Assistant) button and Search pill
- **Apps row:** IPTV, YouTube, Netflix, Play Store, then a **+** tile to add any app
- **Games row (lower screen):** every installed game, plus a "More games" tile that opens the Play Store
- **Bottom left:** time and date. **Bottom right:** Settings
- **Focus:** the selected tile gets a white ring and a small zoom. Tiles are rounded rectangles.

## Get the APK (pick one)

**A. No installs needed (GitHub).**
1. Create a free GitHub repo and upload this whole folder (keep the `.github` folder).
2. Open the **Actions** tab, then **Build APK**, then **Run workflow**.
3. When it finishes, download `SkyGlassLauncher-apk` from the run page. Unzip it to get `app-debug.apk`.

**B. Android Studio.**
Open this folder, wait for the sync, then **Build > Build APK(s)**. The file is at `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the box

1. Copy `app-debug.apk` to a USB stick, or use "Send Files to TV" / adb.
2. Install it with a file manager app on the box (allow "install unknown apps").
3. Open **Glass Launcher** once from the apps list to test it.

## Make it your Home screen

Press Home. If the box asks which launcher to use, choose Glass Launcher and "Always".

If it doesn't ask, use adb over the network:

```
adb connect <box-ip>:5555
adb shell cmd package set-home-activity com.sky.glasslauncher/.MainActivity
```

To go back, run the same command with the original launcher's package and activity.

## IPTV

On first run the launcher looks for an installed app whose name or package contains "iptv" and pins it first. If it finds none, the first tile is a TV icon. Press OK on it and pick your IPTV app from the list. Long-press OK on the IPTV tile to change it later.

## Using it

- **Add an app:** go to the **+** tile at the end of the Apps row and pick from the list.
- **Remove an app from home:** long-press OK on its tile (it stays installed).
- **Games:** detected automatically from the Play Store category. A game that isn't detected can be added with **+**, and it will then show in the Apps row.

## Tweaks

- Tile size: `layout/item_tile.xml` (168 x 94 dp).
- Colors and glass strength: `drawable/glass_tile.xml`, `drawable/glass_pill.xml`, `drawable/bg_wallpaper.xml`.
- Default pinned apps: `getPins()` in `MainActivity.kt`.
