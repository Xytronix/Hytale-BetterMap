package dev.ninesliced.exploration;

import com.hypixel.hytale.server.core.entity.entities.Player;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class ExplorationManager {
    private static final Logger LOGGER = Logger.getLogger(ExplorationManager.class.getName());
    private static ExplorationManager INSTANCE;

    private boolean initialized = false;
    private int maxStoredChunksPerPlayer = Integer.MAX_VALUE;
    private float explorationUpdateRate = 0.5f;
    private boolean persistenceEnabled = true;

    private ExplorationPersistence persistence;

    private String persistencePath = "universe/exploration_data";

    private ExplorationManager() {
    }

    @Nonnull
    public static synchronized ExplorationManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ExplorationManager();
        }
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (initialized) {
            LOGGER.info("Exploration system already initialized");
            return;
        }

        try {
            LOGGER.info("Initializing Exploration System...");

            persistence = new ExplorationPersistence();

            LOGGER.info("- Exploration Tracker: " + ExplorationTracker.class.getSimpleName());
            LOGGER.info("- Update Rate: " + explorationUpdateRate + " seconds");
            LOGGER.info("- Persistence: " + (persistenceEnabled ? "ENABLED" : "DISABLED"));

            initialized = true;
            LOGGER.info("Exploration System initialized successfully");
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize exploration system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void loadPlayerData(@Nonnull Player player) {
        if (player.getWorld() != null) {
            loadPlayerData(player, player.getWorld().getName());
        }
    }

    public void loadPlayerData(@Nonnull Player player, @Nonnull String worldName) {
        if (persistenceEnabled && persistence != null) {
            persistence.load(player, worldName);
        }
    }

    public void savePlayerData(@Nonnull Player player) {
        if (persistenceEnabled && persistence != null) {
            persistence.save(player);
        }
    }

    public void savePlayerData(String playerName, UUID playerUUID, String worldName) {
        if (persistenceEnabled && persistence != null) {
            persistence.save(playerName, playerUUID, worldName);
        }
    }

    public synchronized void shutdown() {
        try {
            LOGGER.info("Shutting down Exploration System...");
            ExplorationTracker.getInstance().clear();
            LOGGER.info("Exploration System shutdown complete");
        } catch (Exception e) {
            LOGGER.severe("Error during exploration system shutdown: " + e.getMessage());
        }
    }

    public void registerPlayer(@Nonnull Player player) {
        try {
            ExplorationTracker.getInstance().getOrCreatePlayerData(player);
            LOGGER.fine("Registered player for exploration tracking: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to register player " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    public void unregisterPlayer(@Nonnull Player player) {
        try {
            ExplorationTracker.getInstance().removePlayerData(player);
            LOGGER.fine("Unregistered player from exploration tracking: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to unregister player " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    @Nonnull
    public static ConfigBuilder config() {
        return new ConfigBuilder();
    }

    public void setMaxStoredChunksPerPlayer(int max) {
        this.maxStoredChunksPerPlayer = max;
        LOGGER.info("Max stored chunks per player set to: " + max);
    }

    public void setExplorationUpdateRate(float seconds) {
        this.explorationUpdateRate = Math.max(0.1f, seconds);
        LOGGER.info("Exploration update rate set to: " + explorationUpdateRate + " seconds");
    }

    public void setPersistenceEnabled(boolean enabled) {
        this.persistenceEnabled = enabled;
        LOGGER.info("Persistence " + (enabled ? "enabled" : "disabled"));
    }

    public void setPersistencePath(@Nonnull String path) {
        this.persistencePath = path;
        LOGGER.info("Persistence path set to: " + path);
    }

    public int getMaxStoredChunksPerPlayer() {
        return maxStoredChunksPerPlayer;
    }

    public float getExplorationUpdateRate() {
        return explorationUpdateRate;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public String getPersistencePath() {
        return persistencePath;
    }

    public static class ConfigBuilder {
        private final ExplorationManager manager = getInstance();

        public ConfigBuilder maxChunksPerPlayer(int max) {
            manager.setMaxStoredChunksPerPlayer(max);
            return this;
        }

        public ConfigBuilder updateRate(float seconds) {
            manager.setExplorationUpdateRate(seconds);
            return this;
        }

        public ConfigBuilder enablePersistence(String path) {
            manager.setPersistencePath(path);
            manager.setPersistenceEnabled(true);
            return this;
        }

        public ConfigBuilder disablePersistence() {
            manager.setPersistenceEnabled(false);
            return this;
        }

        public ExplorationManager build() {
            manager.initialize();
            return manager;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "ExplorationManager{initialized=%s, maxChunksPerPlayer=%d, updateRate=%.2fs, persistence=%s}",
                initialized, maxStoredChunksPerPlayer, explorationUpdateRate,
                persistenceEnabled ? "enabled@" + persistencePath : "disabled"
        );
    }
}
