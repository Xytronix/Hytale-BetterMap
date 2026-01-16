package dev.ninesliced.exploration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Tracks which chunks have been explored by the player.
 * Proxies to ExplorationComponent for persistence if available.
 */
public class ExploredChunksTracker {
    private final Set<Long> memoryExploredChunks;
    private final ExplorationComponent persistentComponent;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ExploredChunksTracker() {
        this(null);
    }

    public ExploredChunksTracker(@Nullable ExplorationComponent component) {
        this.persistentComponent = component;
        if (component == null) {
            this.memoryExploredChunks = ConcurrentHashMap.newKeySet();
        } else {
            this.memoryExploredChunks = null;
        }
    }

    /**
     * Mark a chunk as explored
     */
    public void markChunkExplored(long chunkIndex) {
        if (persistentComponent != null) {
            persistentComponent.addExploredChunk(chunkIndex);
            return;
        }

        lock.writeLock().lock();
        try {
            memoryExploredChunks.add(chunkIndex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Mark multiple chunks as explored (e.g., from a circular area around player)
     */
    public void markChunksExplored(@Nonnull Set<Long> chunkIndices) {
        if (persistentComponent != null) {
            for (Long chunk : chunkIndices) {
                persistentComponent.addExploredChunk(chunk);
            }
            return;
        }

        lock.writeLock().lock();
        try {
            memoryExploredChunks.addAll(chunkIndices);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if a chunk has been explored
     */
    public boolean isChunkExplored(long chunkIndex) {
        if (persistentComponent != null) {
            return persistentComponent.isExplored(chunkIndex);
        }

        lock.readLock().lock();
        try {
            return memoryExploredChunks.contains(chunkIndex);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get a copy of all explored chunks
     */
    @Nonnull
    public Set<Long> getExploredChunks() {
        if (persistentComponent != null) {
            return new HashSet<>(persistentComponent.getExploredChunks());
        }

        lock.readLock().lock();
        try {
            return new HashSet<>(memoryExploredChunks);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the number of explored chunks
     */
    public int getExploredCount() {
        if (persistentComponent != null) {
            return persistentComponent.getExploredChunks().size();
        }

        lock.readLock().lock();
        try {
            return memoryExploredChunks.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all explored chunks
     */
    public void clear() {
        if (persistentComponent != null) {
            persistentComponent.getExploredChunks().clear();
            return;
        }

        lock.writeLock().lock();
        try {
            memoryExploredChunks.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
