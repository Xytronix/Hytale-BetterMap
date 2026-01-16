package dev.ninesliced.exploration;

import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExplorationTracker {
    private static final ExplorationTracker INSTANCE = new ExplorationTracker();

    private final Map<String, PlayerExplorationData> playerExplorationData = new ConcurrentHashMap<>();

    private ExplorationTracker() {
    }

    @Nonnull
    public static ExplorationTracker getInstance() {
        return INSTANCE;
    }

    @Nonnull
    public PlayerExplorationData getOrCreatePlayerData(@Nonnull Player player) {
        return getOrCreatePlayerData(player, null);
    }

    @Nonnull
    public PlayerExplorationData getOrCreatePlayerData(@Nonnull Player player, @Nullable ExplorationComponent component) {
        String playerName = player.getDisplayName();
        return playerExplorationData.compute(playerName, (k, v) -> {
            if (v == null) {
                return new PlayerExplorationData(component);
            }
            return v;
        });
    }

    public PlayerExplorationData getPlayerData(@Nonnull Player player) {
        return getPlayerData(player.getDisplayName());
    }

    public PlayerExplorationData getPlayerData(@Nonnull String playerName) {
        return playerExplorationData.get(playerName);
    }

    public void removePlayerData(@Nonnull Player player) {
        removePlayerData(player.getDisplayName());
    }

    public void removePlayerData(@Nonnull String playerName) {
        playerExplorationData.remove(playerName);
    }

    public void clear() {
        playerExplorationData.clear();
    }

    public static class PlayerExplorationData {
        private final ExploredChunksTracker exploredChunks;
        private final MapExpansionManager mapExpansion;
        private long lastUpdateTime;
        private int lastChunkX = Integer.MAX_VALUE;
        private int lastChunkZ = Integer.MAX_VALUE;

        public PlayerExplorationData(@Nullable ExplorationComponent component) {
            this.exploredChunks = new ExploredChunksTracker(component);
            this.mapExpansion = new MapExpansionManager(exploredChunks);
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public ExploredChunksTracker getExploredChunks() {
            return exploredChunks;
        }

        public MapExpansionManager getMapExpansion() {
            return mapExpansion;
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }

        public void setLastUpdateTime(long time) {
            this.lastUpdateTime = time;
        }

        public int getLastChunkX() {
            return lastChunkX;
        }

        public int getLastChunkZ() {
            return lastChunkZ;
        }

        public void setLastChunkPosition(int chunkX, int chunkZ) {
            this.lastChunkX = chunkX;
            this.lastChunkZ = chunkZ;
        }

        public void resetLastChunkPosition() {
            this.lastChunkX = Integer.MAX_VALUE;
            this.lastChunkZ = Integer.MAX_VALUE;
        }

        public boolean hasMovedToNewChunk(int chunkX, int chunkZ) {
            return lastChunkX != chunkX || lastChunkZ != chunkZ;
        }
    }
}
