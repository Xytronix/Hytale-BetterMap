package dev.ninesliced.exploration;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class ChunkUtil {
    private static final int CHUNK_SIZE = 16;

    public static long chunkCoordsToIndex(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int indexToChunkX(long index) {
        return (int) (index >> 32);
    }

    public static int indexToChunkZ(long index) {
        return (int) index;
    }

    public static int blockToChunkCoord(double blockCoord) {
        return (int) Math.floor(blockCoord) >> 4;
    }

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

    public static double getChunkDistance(int x1, int z1, int x2, int z2) {
        long dx = x1 - x2;
        long dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
