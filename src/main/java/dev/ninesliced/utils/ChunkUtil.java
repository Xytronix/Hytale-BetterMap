package dev.ninesliced.utils;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for chunk coordinate calculations.
 */
public class ChunkUtil {
    private static final int CHUNK_SIZE = 16;

    /**
     * Packs chunk coordinates into a long index.
     *
     * @param chunkX Chunk X.
     * @param chunkZ Chunk Z.
     * @return Packed index.
     */
    public static long chunkCoordsToIndex(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Extracts X coordinate from packed index.
     *
     * @param index Packed index.
     * @return Chunk X.
     */
    public static int indexToChunkX(long index) {
        return (int) (index >> 32);
    }

    /**
     * Extracts Z coordinate from packed index.
     *
     * @param index Packed index.
     * @return Chunk Z.
     */
    public static int indexToChunkZ(long index) {
        return (int) index;
    }

    /**
     * Converts a block coordinate to a chunk coordinate.
     *
     * @param blockCoord Block coordinate.
     * @return Chunk coordinate.
     */
    public static int blockToChunkCoord(double blockCoord) {
        return (int) Math.floor(blockCoord) >> 4;
    }

    /**
     * Gets a set of chunk indices within a circular radius.
     *
     * @param centerChunkX Center chunk X.
     * @param centerChunkZ Center chunk Z.
     * @param radiusChunks Radius in chunks.
     * @return Set of chunk indices.
     */
    @Nonnull
    public static Set<Long> getChunksInCircularArea(int centerChunkX, int centerChunkZ, int radiusChunks) {
        Set<Long> chunks = new HashSet<>();

        int radiusSquared = radiusChunks * radiusChunks;

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                if (dx * dx + dz * dz <= radiusSquared) {
                    chunks.add(chunkCoordsToIndex(centerChunkX + dx, centerChunkZ + dz));
                }
            }
        }

        return chunks;
    }

    /**
     * Gets a set of chunk indices within a rectangular area.
     *
     * @param minChunkX Min X.
     * @param maxChunkX Max X.
     * @param minChunkZ Min Z.
     * @param maxChunkZ Max Z.
     * @return Set of chunk indices.
     */
    @Nonnull
    public static Set<Long> getChunksInRectangularArea(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        Set<Long> chunks = new HashSet<>();

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunks.add(chunkCoordsToIndex(x, z));
            }
        }

        return chunks;
    }

    /**
     * Calculates Euclidean distance between two chunk coordinates.
     *
     * @param x1 First X.
     * @param z1 First Z.
     * @param x2 Second X.
     * @param z2 Second Z.
     * @return Distance.
     */
    public static double getChunkDistance(int x1, int z1, int x2, int z2) {
        long dx = x1 - x2;
        long dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
