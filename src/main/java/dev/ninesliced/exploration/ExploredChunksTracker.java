package dev.ninesliced.exploration;

import dev.ninesliced.components.ExplorationComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe tracker for the set of explored chunks.
 * Uses a persistent component if available, otherwise falls back to memory storage.
 */
public class ExploredChunksTracker {
    private final Set<Long> memoryExploredChunks;
    private final ExplorationComponent persistentComponent;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a new tracker.
     *
     * @param component The persistent component to use (can be null).
     */
    public ExploredChunksTracker(@Nullable ExplorationComponent component) {
        this.persistentComponent = component;
        if (component == null) {
            this.memoryExploredChunks = ConcurrentHashMap.newKeySet();
        } else {
            this.memoryExploredChunks = null;
        }
    }

    /**
     * Marks a single chunk as explored.
     *
     * @param chunkIndex The chunk index to mark.
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
     * Marks multiple chunks as explored.
     *
     * @param chunkIndices The set of chunk indices.
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
     * Checks if a chunk has been explored.
     *
     * @param chunkIndex The chunk index.
     * @return True if explored.
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
     * Gets a copy of all explored chunk indices.
     *
     * @return Set of all explored chunk indices.
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
     * Gets the count of explored chunks.
     *
     * @return The number of explored chunks.
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
     * Clears all explored chunks data.
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
