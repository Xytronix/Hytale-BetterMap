# Hytale BetterMap Plugin

**Enhance your world exploration with saved map data, customizable zoom, and performance-optimized rendering.**

---

## What is this?

By default, the Hytale in-game map is fleeting. It only displays a small circular area around the player, and as soon as you walk away, the map "forgets" where you have been.

**Hytale Persistent Map** changes this. This server plugin introduces a saved exploration feature. As you travel through the world, the plugin records the areas you have visited, effectively removing the "fog of war" permanently. Your map grows bigger the more you explore, allowing you to retrace your steps and navigate with ease.

## New Features & Optimizations

* **Persistent Exploration:** The map retains all previously visited areas across sessions.
* **Dynamic Chunk Loading (Optimization):** We have fixed the "Blue Map" crashes and memory overflow issues. The plugin now intelligently manages memory by loading only the explored chunks nearest to the player and unloading distant ones as you move.
* **Map Quality Settings:** Choose between `LOW`, `MEDIUM`, or `HIGH` quality to balance visual fidelity with performance.
* *Note: `HIGH` quality offers better visuals but drastically limits the number of chunks loaded to prevent memory errors.*
* **Map Quality details:**
  - `LOW`: Loads up to 30 000 chunks with 8x8 images.
  - `MEDIUM`: Loads up to 10 000 chunks with 16x16 images.
  - `HIGH`: Loads up to 3 000 chunks with 32x32 images.

* **Customizable Zoom:** You are no longer locked to the default zoom. Set your own Minimum (zoom out) and Maximum (zoom in) scales.

* **Debug Mode:** Toggle console logging on or off to keep your server logs clean.

## Commands & Permissions

This plugin includes a full suite of commands for players and admins to manage map settings in real-time.

### General Command

* `/bettermap` (Aliases: `/bm`, `/map`)
* **Description:** Displays the current configuration settings (Radius, Scale, Quality, Debug status).
* **Permission:** `command.bettermap`



### Configuration Commands

* `/bettermap min <value>`
* **Description:** Sets the minimum zoom scale (how far you can zoom out). Lower values allow seeing more of the map at once. Must be greater than 2.0.
* **Permission:** `command.bettermap.min`


* `/bettermap max <value>`
* **Description:** Sets the maximum zoom scale (how close you can zoom in). Higher values allow for closer inspection.
* **Permission:** `command.bettermap.max`


* `/bettermap debug <true/false>`
* **Description:** Enables or disables debug logs in the server console. Useful for troubleshooting without crowding your logs during normal play.
* **Permission:** `command.bettermap.debug`


* `/bettermap reload`
* **Description:** Reloads the configuration file and applies changes to loaded worlds immediately.
* **Permission:** `command.bettermap.reload`



## Configuration & Data Storage

All plugin files are located within the server's `mods` directory.

### Configuration File

You can modify the plugin settings in `mods/bettermap/config.json`.

*Note: Changing `mapQuality` requires a server restart to take effect.*

**Default Configuration:**

```json
{
  "explorationRadius": 16,
  "updateRateMs": 500,
  "mapQuality": "MEDIUM",
  "minScale": 10.0,
  "maxScale": 256.0,
  "debug": false
}

```

### Saved Exploration Data

Map data is saved per world and per player. You can find the saved exploration files here:
`mods/bettermap/data/`

## Credits

This project was created to improve the exploration quality of life in Hytale.

* **Created by:** Paralaxe and Theobosse
* **Team:** [Ninesliced](https://ninesliced.com)

---

*Found a bug? Have a suggestion? Please report it in the comments!*