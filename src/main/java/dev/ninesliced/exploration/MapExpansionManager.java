package dev.ninesliced.exploration;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the expansion of the explorable map area based on player exploration.
 * Maintains the boundaries of discovered territory and allows for persistent map growth.
 */
public class MapExpansionManager {

    private final ExploredChunksTracker exploredChunks;
    private int minChunkX = Integer.MAX_VALUE;
    private int maxChunkX = Integer.MIN_VALUE;
    private int minChunkZ = Integer.MAX_VALUE;
    private int maxChunkZ = Integer.MIN_VALUE;

    public MapExpansionManager(@Nonnull ExploredChunksTracker exploredChunks) {
        this.exploredChunks = exploredChunks;
    }

    /**
     * Update the map boundaries based on newly explored chunks
     */
    public void updateBoundaries(int playerChunkX, int playerChunkZ, int viewRadius) {
        Set<Long> newChunks = ChunkUtil.getChunksInCircularArea(playerChunkX, playerChunkZ, viewRadius);

        for (long chunkIndex : newChunks) {
            int chunkX = ChunkUtil.indexToChunkX(chunkIndex);
            int chunkZ = ChunkUtil.indexToChunkZ(chunkIndex);

            minChunkX = Math.min(minChunkX, chunkX);
            maxChunkX = Math.max(maxChunkX, chunkX);
            minChunkZ = Math.min(minChunkZ, chunkZ);
            maxChunkZ = Math.max(maxChunkZ, chunkZ);
        }

        exploredChunks.markChunksExplored(newChunks);
    }

    /**
     * Get the current exploration boundaries
     */
    @Nonnull
    public MapBoundaries getCurrentBoundaries() {
        if (minChunkX == Integer.MAX_VALUE) {
            return new MapBoundaries(0, 0, 0, 0);
        }
        return new MapBoundaries(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    /**
     * Get all chunks that are within the expanded map area
     */
    @Nonnull
    public Set<Long> getExpandedMapChunks() {
        if (minChunkX == Integer.MAX_VALUE) {
            return new HashSet<>();
        }
        return ChunkUtil.getChunksInRectangularArea(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    /**
     * Calculate the total explorable area in square chunks
     */
    public long getTotalExploredArea() {
        if (minChunkX == Integer.MAX_VALUE) {
            return 0;
        }
        long width = (long) (maxChunkX - minChunkX + 1);
        long height = (long) (maxChunkZ - minChunkZ + 1);
        return width * height;
    }

    /**
     * Reset the exploration boundaries
     */
    public void reset() {
        minChunkX = Integer.MAX_VALUE;
        maxChunkX = Integer.MIN_VALUE;
        minChunkZ = Integer.MAX_VALUE;
        maxChunkZ = Integer.MIN_VALUE;
        exploredChunks.clear();
    }

    /**
     * Represents the boundaries of explored map area
     */
    public static class MapBoundaries {
        public final int minX;
        public final int maxX;
        public final int minZ;
        public final int maxZ;

        public MapBoundaries(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public int getWidth() {
            return maxX - minX + 1;
        }

        public int getHeight() {
            return maxZ - minZ + 1;
        }

        public long getArea() {
            return (long) getWidth() * getHeight();
        }

        @Override
        public String toString() {
            return String.format("MapBoundaries{x:[%d,%d], z:[%d,%d], size:%dx%d}",
                    minX, maxX, minZ, maxZ, getWidth(), getHeight());
        }
    }
}
