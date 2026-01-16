package dev.ninesliced.exploration;

import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class ExplorationPersistence {

    private static final Logger LOGGER = Logger.getLogger(ExplorationPersistence.class.getName());
    private static final int DATA_VERSION = 1;

    private final Path storageDir;

    public ExplorationPersistence() {
        Path serverRoot = Paths.get(".").toAbsolutePath().normalize();
        this.storageDir = serverRoot.resolve("mods").resolve("BetterMap").resolve("Data");

        LOGGER.info("Exploration storage root directory: " + this.storageDir.toString());
        try {
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to create exploration data directory: " + e.getMessage());
        }
    }

    public void load(@Nonnull Player player, @Nonnull String worldName) {
        UUID playerUUID = player.getUuid();
        if (playerUUID == null) return;

        Path worldDir = storageDir.resolve(worldName);
        Path file = worldDir.resolve(playerUUID.toString() + ".bin");

        if (!Files.exists(file)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            if (version != DATA_VERSION) {
                LOGGER.warning("Unknown data version for player " + player.getDisplayName() + ": " + version);
            }

            int count = in.readInt();
            Set<Long> loadedChunks = new HashSet<>(count);

            for (int i = 0; i < count; i++) {
                loadedChunks.add(in.readLong());
            }

            ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getOrCreatePlayerData(player);
            data.getExploredChunks().markChunksExplored(loadedChunks);

            for (long chunkIdx : loadedChunks) {
                int x = ChunkUtil.indexToChunkX(chunkIdx);
                int z = ChunkUtil.indexToChunkZ(chunkIdx);

                data.getMapExpansion().updateBoundaries(x, z, 0);
            }

            LOGGER.info("Loaded " + count + " explored chunks for " + player.getDisplayName() + " in world " + worldName);

        } catch (IOException e) {
            LOGGER.severe("Failed to load exploration data for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    public void save(@Nonnull Player player) {
        save(player.getDisplayName(), player.getUuid(), player.getWorld().getName());
    }

    public void save(String playerName, UUID playerUUID, @Nonnull String worldName) {
        if (playerUUID == null) {
            LOGGER.warning("Cannot save data: Player UUID is null for " + playerName);
            return;
        }

        ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(playerName);
        if (data == null) {
            return;
        }

        Set<Long> chunks = data.getExploredChunks().getExploredChunks();

        Path worldDir = storageDir.resolve(worldName);
        try {
            if (!Files.exists(worldDir)) {
                Files.createDirectories(worldDir);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to create world exploration directory: " + e.getMessage());
            return;
        }

        Path file = worldDir.resolve(playerUUID.toString() + ".bin");
        LOGGER.info("[DEBUG] Saving " + chunks.size() + " chunks for " + playerName + " in world " + worldName);

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(DATA_VERSION);
            out.writeInt(chunks.size());

            for (Long chunk : chunks) {
                out.writeLong(chunk);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to save exploration data for " + playerName + ": " + e.getMessage());
        }
    }
}
