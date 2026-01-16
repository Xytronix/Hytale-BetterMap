package dev.ninesliced.exploration;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class ExplorationEventListener {
    private static final Logger LOGGER = Logger.getLogger(ExplorationEventListener.class.getName());
    private static final java.util.Map<String, String> playerWorlds = new java.util.concurrent.ConcurrentHashMap<>();

    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            Player player = event.getPlayer();
            String playerName = player.getDisplayName();

            // Ensure map settings (zoom) are applied on join
            WorldMapHook.sendMapSettingsToPlayer(player);

            if (playerWorlds.containsKey(playerName)) {
                LOGGER.info("[DEBUG] Player " + playerName + " already tracked, skipping PlayerReadyEvent");
                return;
            }

            LOGGER.info("Player ready (initial join): " + playerName);

            World world = player.getWorld();
            String worldName = world.getName();
            playerWorlds.put(playerName, worldName);

            if (isDefaultWorld(world)) {
                ExplorationTracker.getInstance().getOrCreatePlayerData(player);
                ExplorationManager.getInstance().loadPlayerData(player);

                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    WorldMapHook.hookPlayerMapTracker(player, tracker);
                    WorldMapHook.hookWorldMapResolution(world);
                }

                LOGGER.info("Exploration tracking initialized for player: " + playerName);
            } else {
                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    WorldMapHook.restoreVanillaMapTracker(player, tracker);
                }
                LOGGER.info("Player " + playerName + " joined non-default world; leaving map vanilla.");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to handle player ready event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void onPlayerLeaveWorld(@Nonnull DrainPlayerFromWorldEvent event) {
        LOGGER.info("[DEBUG] DrainPlayerFromWorldEvent FIRED!");
        try {
            Holder<EntityStore> holder = event.getHolder();
            LOGGER.info("[DEBUG] Got holder: " + holder);

            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            LOGGER.info("[DEBUG] Got PlayerRef: " + (playerRef != null ? playerRef.getUsername() : "null"));

            Player player = playerRef.getComponent(Player.getComponentType());
            LOGGER.info("[DEBUG] Got Player: " + (player != null ? player.getDisplayName() : "null"));

            if (player != null) {
                World world = event.getWorld();
                String worldName = world.getName();
                LOGGER.info("[DEBUG] Player " + player.getDisplayName() + " leaving world " + worldName + " (world shutting down)");

                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    LOGGER.info("[DEBUG] Unhooking tracker for " + player.getDisplayName());
                    WorldMapHook.unhookPlayerMapTracker(player, tracker);
                } else {
                    LOGGER.warning("[DEBUG] Tracker is null for " + player.getDisplayName());
                }

                if (isDefaultWorld(world)) {
                    ExplorationManager.getInstance().savePlayerData(player.getDisplayName(), player.getUuid(), worldName);
                }

                LOGGER.info("[DEBUG] Clearing exploration data for " + player.getDisplayName());
                ExplorationTracker.getInstance().removePlayerData(player.getDisplayName());

                LOGGER.info("[DEBUG] Successfully handled DrainPlayerFromWorldEvent for " + player.getDisplayName());
            } else {
                LOGGER.warning("[DEBUG] Player was null in DrainPlayerFromWorldEvent!");
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Error handling player leave world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void onPlayerJoinWorld(@Nonnull AddPlayerToWorldEvent event) {
        LOGGER.info("[DEBUG] AddPlayerToWorldEvent FIRED!");
        try {
            Holder<EntityStore> holder = event.getHolder();
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            Player player = playerRef.getComponent(Player.getComponentType());

            if (player != null) {
                String playerName = player.getDisplayName();
                World newWorld = event.getWorld();
                String newWorldName = newWorld.getName();
                String oldWorldName = playerWorlds.get(playerName);
                World oldWorld = oldWorldName != null ? Universe.get().getWorld(oldWorldName) : null;

                LOGGER.info("[DEBUG] Player " + playerName + " joining world: " + newWorldName + " (previous: " + oldWorldName + ")");

                if (oldWorldName != null && !oldWorldName.equals(newWorldName)) {
                    LOGGER.info("[DEBUG] WORLD CHANGE DETECTED: " + playerName + " from " + oldWorldName + " to " + newWorldName);

                    WorldMapTracker tracker = player.getWorldMapTracker();
                    if (tracker != null) {
                        LOGGER.info("[DEBUG] Unhooking tracker for old world " + oldWorldName);
                        WorldMapHook.unhookPlayerMapTracker(player, tracker);
                    }

                    if (isDefaultWorld(oldWorld)) {
                        LOGGER.info("[DEBUG] Saving data for default world");
                        ExplorationManager.getInstance().savePlayerData(playerName, player.getUuid(), oldWorldName);
                    }

                    ExplorationTracker.getInstance().removePlayerData(playerName);
                }

                if (!isDefaultWorld(newWorld)) {
                    WorldMapTracker tracker = player.getWorldMapTracker();
                    if (tracker != null) {
                        WorldMapHook.restoreVanillaMapTracker(player, tracker);
                    }
                } else if (oldWorldName == null || !oldWorldName.equals(newWorldName)) {
                    LOGGER.info("[DEBUG] Initializing exploration for " + playerName + " in world " + newWorldName);

                    ExplorationTracker.getInstance().getOrCreatePlayerData(player);

                    ExplorationTracker.PlayerExplorationData newData = ExplorationTracker.getInstance().getPlayerData(playerName);
                    if (newData != null) {
                        newData.resetLastChunkPosition();
                        LOGGER.info("[DEBUG] Reset last chunk position for fresh start in " + newWorldName);
                    }

                    LOGGER.info("[DEBUG] Loading data for world: " + newWorldName);
                    ExplorationManager.getInstance().loadPlayerData(player, newWorldName);

                    WorldMapTracker tracker = player.getWorldMapTracker();
                    if (tracker != null) {
                        LOGGER.info("[DEBUG] Hooking tracker for world " + newWorldName);
                        WorldMapHook.hookPlayerMapTracker(player, tracker);
                        WorldMapHook.hookWorldMapResolution(newWorld);

                        WorldMapTracker finalTracker = tracker;
                    ExplorationTicker.getInstance().scheduleUpdate(() -> {
                        LOGGER.info("[DEBUG] Scheduled immediate update executing for " + playerName);
                        TransformComponent tc = holder.getComponent(TransformComponent.getComponentType());
                        if (tc != null) {
                            var pos = tc.getPosition();
                            WorldMapHook.updateExplorationState(player, finalTracker, pos.x, pos.z);
                        } else {
                            LOGGER.warning("[DEBUG] TransformComponent expected but null for immediate update");
                        }

                        try {
                            ReflectionHelper.setFieldValueRecursive(finalTracker, "updateTimer", 0.0f);
                        } catch (Exception e) {
                            LOGGER.warning("[DEBUG] Could not reset updateTimer: " + e.getMessage());
                        }
                    });
                    }
                }

                playerWorlds.put(playerName, newWorldName);
                LOGGER.info("[DEBUG] Updated world tracking for " + playerName + ": " + newWorldName);
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Error in AddPlayerToWorldEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void onPlayerQuit(@Nonnull PlayerDisconnectEvent event) {
        LOGGER.info("[DEBUG] PlayerDisconnectEvent FIRED!");
        try {
            com.hypixel.hytale.server.core.universe.PlayerRef playerRef = event.getPlayerRef();

            if (playerRef != null) {
                String playerName = playerRef.getUsername();
                java.util.UUID playerUUID = playerRef.getUuid();
                LOGGER.info("[DEBUG] Player " + playerName + " disconnecting from server");

                ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(playerName);
                LOGGER.info("[DEBUG] Exploration data exists: " + (data != null));

                if (data != null) {
                    LOGGER.info("[DEBUG] Data still exists, performing fallback save");
                    com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref = playerRef.getReference();
                    if (ref != null && ref.isValid()) {
                        try {
                            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = ref.getStore();
                            com.hypixel.hytale.server.core.universe.world.World world = store.getExternalData().getWorld();
                            String worldName = world.getName();

                            if (isDefaultWorld(world)) {
                                LOGGER.info("[DEBUG] Fallback save for player " + playerName + " disconnecting from default world");
                                ExplorationManager.getInstance().savePlayerData(playerName, playerUUID, worldName);
                            }
                            ExplorationTracker.getInstance().removePlayerData(playerName);
                        } catch (Exception e) {
                            LOGGER.warning("Could not determine world for fallback save: " + e.getMessage());
                            ExplorationTracker.getInstance().removePlayerData(playerName);
                        }
                    } else {
                        ExplorationTracker.getInstance().removePlayerData(playerName);
                    }
                } else {
                    LOGGER.info("Player " + playerName + " disconnect - data already saved");
                }

                playerWorlds.remove(playerName);
                LOGGER.info("[DEBUG] Removed world tracking for " + playerName);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to handle player quit event: " + e.getMessage());
        }
    }

    private static boolean isDefaultWorld(@javax.annotation.Nullable World world) {
        try {
            if (world == null) {
                return false;
            }

            Universe universe = Universe.get();
            World defaultWorld = universe != null ? universe.getDefaultWorld() : null;

            if (defaultWorld == null) {
                return World.DEFAULT.equals(world.getName());
            }

            if (world == defaultWorld) {
                return true;
            }

            try {
                String dw = defaultWorld.getName();
                String w = world.getName();
                if (dw.equals(w)) {
                    return true;
                }
            } catch (Exception ignore) {
            }

            return World.DEFAULT.equals(world.getName());
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Could not evaluate default world: " + e.getMessage());
            return false;
        }
    }
}
