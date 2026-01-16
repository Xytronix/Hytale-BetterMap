# Changelog

## v1.1.0 - Optimization & Customization Update

**Optimizations & Fixes**

* **Dynamic Chunk Loading:** Fixed the "Blue Map" issue and crashes caused by memory overflows. The mod now dynamically loads discovered chunks nearest to the player and unloads further ones to optimize memory usage.
* **Map Quality Settings:** Introduced a new `mapQuality` setting to balance visual fidelity and performance.
* **Options:** `LOW`, `MEDIUM`, `HIGH` (Default is `MEDIUM`).
* **Map Quality details:**
  - `LOW`: Loads up to 30 000 chunks with 8x8 images.
  - `MEDIUM`: Loads up to 10 000 chunks with 16x16 images.
  - `HIGH`: Loads up to 3 000 chunks with 32x32 images.
* **Performance Note:** `HIGH` quality increases texture resolution but drastically limits the number of chunks loaded simultaneously to prevent Memory Overflow errors.


* **Debug Toggle:** Added a toggle to enable or disable debug logs to prevent console crowding.

**New Features**

* **Custom Zoom Scaling:** Players can now customize the map zoom limits. You can define how far you can zoom out (`minScale`) and how close you can zoom in (`maxScale`).

**Configuration**
The `config.json` has been updated with new parameters:

```json
{
  "explorationRadius": 16,
  "updateRateMs": 500,
  "mapQuality": "MEDIUM", // Load 10000 chunks with 16x16 images
  "minScale": 10.0, // Base zoom out is 32.0
  "maxScale": 256.0,
  "debug": false
}

```

*Note: Changing `mapQuality` requires a server restart to take effect.*

**Commands & Permissions**
We have expanded the command list to allow in-game configuration changes.

* `/bettermap` (Aliases: `/bm`, `/map`)
* **Description:** Displays current settings (Radius, Scale, Quality, Debug status).
* **Permission:** `command.bettermap`


* `/bettermap min <value>`
* **Description:** Sets the minimum map zoom scale (lower value = zoom out further). Must be greater than 2.0.
* **Permission:** `command.bettermap.min`


* `/bettermap max <value>`
* **Description:** Sets the maximum map zoom scale (higher value = zoom in closer).
* **Permission:** `command.bettermap.max`


* `/bettermap debug <true/false>`
* **Description:** Toggles debug logging on or off.
* **Permission:** `command.bettermap.debug`


* `/bettermap reload`
* **Description:** Reloads the configuration file and applies changes to loaded worlds immediately.
* **Permission:** `command.bettermap.reload`



---

## v1.0.0 - Initial Release

**New Features**

* **Persistent Map:** Added the core functionality to save explored map areas. Fog of war no longer resets between sessions.
* **Custom View Range:** Players (or admins) can now define the size of the exploration circle.
* **Data Storage:** Implemented a file saving system. Map data is stored in `mods/bettermap/data/"worldname"/"userId"`.
* **Configuration:** Added `config.json` support located in `mods/bettermap/`.

**Commands**

* Added `/bettermap`: This command reloads the configuration file instantly without requiring a server restart.

**Credits**

* Mod created by Paralaxe and Theobosse (Team Ninesliced).
