package dev.ninesliced.exploration;

import com.hypixel.hytale.math.iterator.CircleSpiralIterator;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import dev.ninesliced.BetterMapConfig;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class WorldMapHook {
    private static final Logger LOGGER = Logger.getLogger(WorldMapHook.class.getName());

    public static void hookPlayerMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", 999);

            ExplorationTracker.PlayerExplorationData explorationData = ExplorationTracker.getInstance().getOrCreatePlayerData(player);
            RestrictedSpiralIterator customIterator = new RestrictedSpiralIterator(explorationData);

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
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Exception in updateExplorationState: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static class RestrictedSpiralIterator extends CircleSpiralIterator {
        private final ExplorationTracker.PlayerExplorationData data;
        private Iterator<Long> currentIterator;
        private int currentGoalRadius;
        private volatile boolean stopped = false;

        public RestrictedSpiralIterator(ExplorationTracker.PlayerExplorationData data) {
            this.data = data;
        }

        public void stop() {
            this.stopped = true;
        }

        @Override
        public void init(int cx, int cz, int startRadius, int endRadius) {
            if (stopped) {
                this.currentIterator = java.util.Collections.emptyIterator();
                return;
            }

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

            this.currentIterator = mapChunks.iterator();
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
            return currentIterator.next();
        }

        @Override
        public int getCompletedRadius() {
            if (stopped) {
                return currentGoalRadius;
            }
            return Math.max(0, currentGoalRadius - 1);
        }
    }

    private static void forceTrackerUpdate(@Nonnull Player player, @Nonnull WorldMapTracker tracker, double x, double z) {
        try {
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                RestrictedSpiralIterator restrictedIterator = (RestrictedSpiralIterator) spiralIterator;

                int chunkX = ChunkUtil.blockToChunkCoord(x);
                int chunkZ = ChunkUtil.blockToChunkCoord(z);

                restrictedIterator.init(chunkX, chunkZ, 0, 999);
                LOGGER.info("[DEBUG] Forced tracker update for " + player.getDisplayName());
            }

            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Failed to force tracker update: " + e.getMessage());
        }
    }
}
