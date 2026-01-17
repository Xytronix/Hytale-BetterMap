package dev.ninesliced.managers;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import dev.ninesliced.providers.PlayerRadarProvider;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Manages the registration and lifecycle of the PlayerRadarProvider for world maps.
 * <p>
 * This manager ensures that the radar marker provider is registered only once per world
 * and handles proper cleanup when worlds are unloaded.
 * </p>
 */
public class PlayerRadarManager {

    private static final Logger LOGGER = Logger.getLogger(PlayerRadarManager.class.getName());
    private static PlayerRadarManager instance;

    private final Set<String> registeredWorlds = new HashSet<>();
    private final PlayerRadarProvider radarProvider;

    private PlayerRadarManager() {
        this.radarProvider = new PlayerRadarProvider();
    }

    /**
     * Gets the singleton instance of the PlayerRadarManager.
     *
     * @return The manager instance.
     */
    public static synchronized PlayerRadarManager getInstance() {
        if (instance == null) {
            instance = new PlayerRadarManager();
        }
        return instance;
    }

    /**
     * Gets the PlayerRadarProvider instance.
     *
     * @return The radar provider.
     */
    public PlayerRadarProvider getRadarProvider() {
        return radarProvider;
    }

    /**
     * Registers the radar marker provider for a player's current world.
     * <p>
     * This method ensures that the provider is only registered once per world
     * to avoid duplicate markers.
     * </p>
     *
     * @param player The player for whose world to register the provider.
     */
    public void registerForPlayer(@Nonnull Player player) {
        World world = player.getWorld();
        if (world == null) {
            LOGGER.warning("Cannot register radar provider: player world is null");
            return;
        }

        registerForWorld(world);
    }

    /**
     * Registers the radar marker provider for a specific world.
     *
     * @param world The world to register the provider for.
     */
    public void registerForWorld(@Nonnull World world) {
        String worldName = world.getName();

        if (registeredWorlds.contains(worldName)) {
            return;
        }

        try {
            WorldMapManager mapManager = world.getWorldMapManager();
            if (mapManager == null) {
                LOGGER.warning("Cannot register radar provider: WorldMapManager is null for world " + worldName);
                return;
            }

            if (!mapManager.getMarkerProviders().containsKey(PlayerRadarProvider.PROVIDER_ID)) {
                mapManager.addMarkerProvider(PlayerRadarProvider.PROVIDER_ID, radarProvider);
                registeredWorlds.add(worldName);
                LOGGER.info("Registered PlayerRadarProvider for world: " + worldName);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to register radar provider for world " + worldName + ": " + e.getMessage());
        }
    }

    /**
     * Unregisters the radar provider for a specific world.
     * <p>
     * Called when a world is being unloaded or shut down.
     * </p>
     *
     * @param worldName The name of the world to unregister.
     */
    public void unregisterForWorld(@Nonnull String worldName) {
        registeredWorlds.remove(worldName);
        LOGGER.info("Unregistered PlayerRadarProvider for world: " + worldName);
    }

    /**
     * Cleans up all registrations. Called on plugin shutdown.
     */
    public void cleanup() {
        registeredWorlds.clear();
        LOGGER.info("PlayerRadarManager cleaned up");
    }
}
