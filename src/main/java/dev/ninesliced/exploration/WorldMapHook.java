package dev.ninesliced.exploration;

import com.hypixel.hytale.math.iterator.CircleSpiralIterator;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMapSettings;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import dev.ninesliced.BetterMapConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class WorldMapHook {
    private static final Logger LOGGER = Logger.getLogger(WorldMapHook.class.getName());

    public static void hookPlayerMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", 999);

            // Enhance Zoom capability
            World world = player.getWorld();
            if (world != null) {
                sendMapSettingsToPlayer(player);
            }

            ExplorationTracker.PlayerExplorationData explorationData = ExplorationTracker.getInstance().getOrCreatePlayerData(player);
            RestrictedSpiralIterator customIterator = new RestrictedSpiralIterator(explorationData, tracker);

            ReflectionHelper.setFieldValueRecursive(tracker, "spiralIterator", customIterator);

            LOGGER.info("Hooked map tracker for player: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to hook WorldMapTracker for player " + player.getDisplayName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void unhookPlayerMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                ((RestrictedSpiralIterator) spiralIterator).stop();
            }

            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", null);

            try {
                Object pendingReloadFutures = ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadFutures");
                if (pendingReloadFutures instanceof java.util.Map) {
                    ((java.util.Map<?, ?>) pendingReloadFutures).clear();
                }
            } catch (Exception e) {
                LOGGER.warning("Could not clear pendingReloadFutures: " + e.getMessage());
            }

            try {
                Object pendingReloadChunks = ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadChunks");
                if (pendingReloadChunks instanceof java.util.Set) {
                    ((java.util.Set<?>) pendingReloadChunks).clear();
                }
            } catch (Exception e) {
                LOGGER.warning("Could not clear pendingReloadChunks: " + e.getMessage());
            }

            try {
                ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 999.0f);
            } catch (Exception e) {
            }

            LOGGER.info("Unhooked map tracker for player: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Error unhooking tracker for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    public static void restoreVanillaMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                ((RestrictedSpiralIterator) spiralIterator).stop();
            }

            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", null);
            ReflectionHelper.setFieldValueRecursive(tracker, "spiralIterator", new CircleSpiralIterator());
            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);

            LOGGER.info("Restored vanilla map tracker for player: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to restore vanilla tracker for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    public static void hookWorldMapResolution(@Nonnull com.hypixel.hytale.server.core.universe.world.World world) {
        try {
            LOGGER.info("Hooking WorldMap resolution for world: " + world.getName());
            com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager manager = world.getWorldMapManager();
            if (manager == null) return;

            LOGGER.info("Modifying WorldMapSettings for world: " + world.getName());
            com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings settings = manager.getWorldMapSettings();
            if (settings == null) return;

            // Use configured quality
            BetterMapConfig.MapQuality quality = BetterMapConfig.getInstance().getActiveMapQuality();
            ReflectionHelper.setFieldValueRecursive(settings, "imageScale", quality.scale);
            
            // Clear cache to force regenerate with new resolution
            manager.clearImages();
            
            LOGGER.info("Modified WorldMapSettings imageScale to " + quality.scale + " (" + quality + " quality) for world: " + world.getName());
        } catch (Exception e) {
            LOGGER.warning("Failed to hook WorldMap resolution: " + e.getMessage());
        }
    }

    public static void updateExplorationState(@Nonnull Player player, @Nonnull WorldMapTracker tracker, double x, double z) {
        try {
            ExplorationTracker explorationTracker = ExplorationTracker.getInstance();
            ExplorationTracker.PlayerExplorationData explorationData = explorationTracker.getPlayerData(player);

            if (explorationData == null) {
                LOGGER.info("[DEBUG] No exploration data for " + player.getDisplayName() + " in updateExplorationState");
                return;
            }

            int playerChunkX = ChunkUtil.blockToChunkCoord(x);
            int playerChunkZ = ChunkUtil.blockToChunkCoord(z);

            boolean hasMoved = explorationData.hasMovedToNewChunk(playerChunkX, playerChunkZ);

            if (hasMoved) {
                int explorationRadius = BetterMapConfig.getInstance().getExplorationRadius();

                explorationData.getMapExpansion().updateBoundaries(playerChunkX, playerChunkZ, explorationRadius);
                explorationData.setLastChunkPosition(playerChunkX, playerChunkZ);

                forceTrackerUpdate(player, tracker, x, z);

                // Manage loaded chunks (unload distant ones)
                int mapChunkX = playerChunkX >> 1;
                int mapChunkZ = playerChunkZ >> 1;
                manageLoadedChunks(player, tracker, mapChunkX, mapChunkZ);
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Exception in updateExplorationState: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void manageLoadedChunks(@Nonnull Player player, @Nonnull WorldMapTracker tracker, int cx, int cz) {
        try {
            Object loadedObj = ReflectionHelper.getFieldValueRecursive(tracker, "loaded");
            if (!(loadedObj instanceof Set)) return;

            @SuppressWarnings("unchecked")
            Set<Long> loaded = (Set<Long>) loadedObj;

            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (!(spiralIterator instanceof RestrictedSpiralIterator)) return;

            List<Long> targetChunks = ((RestrictedSpiralIterator) spiralIterator).getTargetMapChunks();
            Set<Long> targetSet = new HashSet<>(targetChunks);

            List<Long> toUnload = new ArrayList<>();
            List<Long> loadedSnapshot = new ArrayList<>(loaded);

            List<MapChunk> unloadPackets = new ArrayList<>();

            for (Long idx : loadedSnapshot) {
                if (!targetSet.contains(idx)) {
                    toUnload.add(idx);
                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                    unloadPackets.add(new MapChunk(mx, mz, null));
                }
            }

            if (toUnload.isEmpty()) return;

            loaded.removeAll(toUnload);

            UpdateWorldMap packet = new UpdateWorldMap(
                unloadPackets.toArray(new MapChunk[0]),
                null,
                null
            );
            player.getPlayerConnection().write((Packet) packet);

        } catch (Exception e) {
            LOGGER.warning("Failed to manage loaded chunks: " + e.getMessage());
        }
    }

    public static class RestrictedSpiralIterator extends CircleSpiralIterator {
        private final ExplorationTracker.PlayerExplorationData data;
        private final WorldMapTracker tracker;
        private Iterator<Long> currentIterator;
        private List<Long> targetMapChunks = new ArrayList<>();
        private int currentGoalRadius;
        private volatile boolean stopped = false;
        private int centerX;
        private int centerZ;
        private int currentRadius;
        private int cleanupTimer = 0;

        public RestrictedSpiralIterator(ExplorationTracker.PlayerExplorationData data, WorldMapTracker tracker) {
            this.data = data;
            this.tracker = tracker;
        }

        public void stop() {
            this.stopped = true;
        }

        public List<Long> getTargetMapChunks() {
            return targetMapChunks;
        }

        @Override
        public void init(int cx, int cz, int startRadius, int endRadius) {
            if (stopped) {
                this.currentIterator = java.util.Collections.emptyIterator();
                return;
            }

            this.centerX = cx;
            this.centerZ = cz;
            this.currentRadius = startRadius;
            this.currentGoalRadius = endRadius;

            Set<Long> mapChunks = new HashSet<>();
            Set<Long> exploredWorldChunks = data.getExploredChunks().getExploredChunks();

            for (Long chunkIdx : exploredWorldChunks) {
                int wx = ChunkUtil.indexToChunkX(chunkIdx);
                int wz = ChunkUtil.indexToChunkZ(chunkIdx);

                int mx = wx >> 1;
                int mz = wz >> 1;

                long mapChunkIdx = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(mx, mz);
                mapChunks.add(mapChunkIdx);
            }

            List<Long> rankedChunks = new ArrayList<>();
            MapExpansionManager.MapBoundaries bounds = data.getMapExpansion().getCurrentBoundaries();
            Set<Long> boundaryChunks = new HashSet<>();

            if (bounds.minX != Integer.MAX_VALUE) {
                // Add corners of the map to boundary chunks
                boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.minX >> 1, bounds.minZ >> 1));
                boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.maxX >> 1, bounds.minZ >> 1));
                boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.minX >> 1, bounds.maxZ >> 1));
                boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.maxX >> 1, bounds.maxZ >> 1));
            }

            // Add non-boundary chunks to ranked list
            for (Long chunk : mapChunks) {
                if (!boundaryChunks.contains(chunk)) {
                    rankedChunks.add(chunk);
                }
            }

            // Sort by distance to cx/cz (closest first)
            rankedChunks.sort(Comparator.comparingDouble(idx -> {
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                return Math.sqrt(Math.pow(mx - cx, 2) + Math.pow(mz - cz, 2));
            }));

            // Keep only the closest chunks, reserving space for boundaries
            int maxChunks = BetterMapConfig.getInstance().getActiveMapQuality().maxChunks;
            int searchLimit = maxChunks - boundaryChunks.size();
            if (searchLimit < 0) searchLimit = 0;

            if (rankedChunks.size() > searchLimit) {
                rankedChunks = rankedChunks.subList(0, searchLimit);
            }

            this.targetMapChunks = new ArrayList<>(boundaryChunks);
            this.targetMapChunks.addAll(rankedChunks);

            // We supply the whole list to WorldMapTracker to ensure any skipped chunks (holes) are filled.
            // WorldMapTracker efficiently skips already loaded chunks.
            this.currentIterator = rankedChunks.iterator();

            // Periodically cleanup far chunks (every 100 calls/ticks approx 5s)
            if (++cleanupTimer > 100) {
                cleanupTimer = 0;
                cleanupFarChunks(rankedChunks);
            }
        }

        private void cleanupFarChunks(List<Long> keepChunks) {
            try {
                Object loadedObj = ReflectionHelper.getFieldValue(tracker, "loaded");
                if (loadedObj instanceof java.util.Set) {
                    java.util.Set<?> loadedSet = (java.util.Set<?>) loadedObj; // Unbounded wildcard to avoid cast issues if HLongSet
                    // However, we need to iterate and cast elements to Long.
                    // HLongSet is Set<Long>, so elements are Long.

                    if (loadedSet.size() > 20000) {
                        java.util.Set<Long> keepSet = new HashSet<>(keepChunks);
                        List<MapChunk> toRemovePackets = new ArrayList<>();

                        // We must use iterator to remove safely
                        Iterator<?> it = loadedSet.iterator();
                        while (it.hasNext()) {
                            Object obj = it.next();
                            if (obj instanceof Long) {
                                Long idx = (Long) obj;
                                if (!keepSet.contains(idx)) {
                                    it.remove();
                                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                                    toRemovePackets.add(new MapChunk(mx, mz, null));
                                }
                            }
                        }

                        if (!toRemovePackets.isEmpty()) {
                            UpdateWorldMap packet = new UpdateWorldMap(toRemovePackets.toArray(new MapChunk[0]), null, null);
                            tracker.getPlayer().getPlayerConnection().write((Packet)packet);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to cleanup far chunks: " + e.getMessage());
            }
        }

        @Override
        public boolean hasNext() {
            if (stopped) {
                return false;
            }
            return currentIterator != null && currentIterator.hasNext();
        }

        @Override
        public long next() {
            if (stopped || currentIterator == null) {
                return 0;
            }
            long next = currentIterator.next();

            // Update currentRadius based on distance
            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(next);
            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(next);
            double dist = Math.sqrt(Math.pow(mx - centerX, 2) + Math.pow(mz - centerZ, 2));
            this.currentRadius = (int) dist;

            return next;
        }

        @Override
        public int getCompletedRadius() {
            if (stopped) {
                return currentGoalRadius;
            }
            return currentRadius;
        }
    }

    private static void forceTrackerUpdate(@Nonnull Player player, @Nonnull WorldMapTracker tracker, double x, double z) {
        try {
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                RestrictedSpiralIterator restrictedIterator = (RestrictedSpiralIterator) spiralIterator;

                // Use MAP chunk coordinates (>> 5 for block -> map chunk)
                int chunkX = (int)Math.floor(x) >> 5;
                int chunkZ = (int)Math.floor(z) >> 5;

                restrictedIterator.init(chunkX, chunkZ, 0, 999);
            }

            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Failed to force tracker update: " + e.getMessage());
        }
    }

    public static void updateWorldMapConfigs(@Nonnull World world) {
        try {
            WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
            UpdateWorldMapSettings packet = (UpdateWorldMapSettings) ReflectionHelper.getFieldValue(settings, "settingsPacket");
            BetterMapConfig config = BetterMapConfig.getInstance();

            if (packet != null) {
                packet.minScale = config.getMinScale();
                packet.maxScale = config.getMaxScale();
            }

            // Also try to set fields on the settings object directly if they exist
            // This ensures if the packet is regenerated it uses these values
            ReflectionHelper.setFieldValueRecursive(settings, "minScale", config.getMinScale());
            ReflectionHelper.setFieldValueRecursive(settings, "maxScale", config.getMaxScale());

        } catch (Exception e) {
            LOGGER.warning("Failed to update world map configs: " + e.getMessage());
        }
    }

    public static void broadcastMapSettings(@Nonnull World world) {
        try {
            // Attempt to call mapManager.sendSettings() which likely broadcasts
            Object mapManager = world.getWorldMapManager();
            java.lang.reflect.Method sendSettings = mapManager.getClass().getMethod("sendSettings");
            sendSettings.invoke(mapManager);
        } catch (Exception e) {
            // Fallback: iterate players if we can't invoke method
            // But we don't have easy access to player list here without Universe
            LOGGER.fine("Could not invoke mapManager.sendSettings(): " + e.getMessage());
        }
    }

    public static void sendMapSettingsToPlayer(@Nonnull Player player) {
        try {
            World world = player.getWorld();
            if (world == null) return;

            updateWorldMapConfigs(world);

            WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
            UpdateWorldMapSettings packet = (UpdateWorldMapSettings) ReflectionHelper.getFieldValue(settings, "settingsPacket");

            if (packet != null) {
                player.getPlayerConnection().write((Packet) packet);
                LOGGER.fine("Sent custom map settings to " + player.getDisplayName());
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send map settings to player: " + e.getMessage());
        }
    }
}
