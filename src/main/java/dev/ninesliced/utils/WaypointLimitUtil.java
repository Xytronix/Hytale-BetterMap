package dev.ninesliced.utils;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.asset.type.gameplay.worldmap.UserMapMarkerConfig;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarkersStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Utility for working with Hytale's user marker limits.
 * Supports runtime overrides via reflection.
 */
public final class WaypointLimitUtil {
    private static final Logger LOGGER = Logger.getLogger(WaypointLimitUtil.class.getName());
    private WaypointLimitUtil() {
    }

    /**
     * Returns the current max markers per player for the given scope.
     */
    public static int getMaxMarkers(@Nullable World world, boolean shared) {
        if (world == null) {
            return -1;
        }
        try {
            UserMapMarkerConfig config = world.getGameplayConfig().getWorldMapConfig().getUserMapMarkerConfig();
            return shared ? config.getMaxSharedMarkersPerPlayer() : config.getMaxPersonalMarkersPerPlayer();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Returns the current number of markers a player has for the given scope.
     */
    public static int getCurrentMarkers(@Nonnull Player player, boolean shared) {
        World world = player.getWorld();
        if (world == null) {
            return -1;
        }
        UserMapMarkersStore store = shared
            ? world.getChunkStore().getStore().getResource(WorldMarkersResource.getResourceType())
            : player.getPlayerConfigData().getPerWorldData(world.getName());
        if (store == null) {
            return -1;
        }
        UUID uuid = ((CommandSender) player).getUuid();
        return store.getUserMapMarkers(uuid).size();
    }

    /**
     * Returns an error message if the player cannot create a marker, or null if allowed.
     */
    @Nullable
    public static String getCreationError(@Nonnull Player player, boolean shared) {
        World world = player.getWorld();
        if (world == null) {
            return "World not loaded.";
        }
        try {
            UserMapMarkerConfig config = world.getGameplayConfig().getWorldMapConfig().getUserMapMarkerConfig();
            if (!config.isAllowCreatingMarkers()) {
                return "Waypoint creation is disabled in this world.";
            }
            int limit = shared ? config.getMaxSharedMarkersPerPlayer() : config.getMaxPersonalMarkersPerPlayer();
            if (limit <= 0) {
                return shared
                    ? "Shared waypoint creation is disabled (limit 0)."
                    : "Personal waypoint creation is disabled (limit 0).";
            }
            int current = getCurrentMarkers(player, shared);
            if (current < 0) {
                return "Could not access waypoint storage.";
            }
            if (current >= limit) {
                return "Waypoint limit reached (" + current + "/" + limit + ").";
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to read marker limits: " + e.getMessage());
            return "Could not validate waypoint limits.";
        }
        return null;
    }

    /**
     * Applies overrides to all worlds. Use -1 for unlimited.
     */
    public static void applyOverridesToAllWorlds(int personalOverride, int sharedOverride) {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        universe.getWorlds().values().forEach(world -> applyOverridesToWorld(world, personalOverride, sharedOverride));
    }

    /**
     * Applies overrides to a single world. Use -1 for unlimited.
     */
    public static void applyOverridesToWorld(@Nonnull World world, int personalOverride, int sharedOverride) {
        try {
            UserMapMarkerConfig config = world.getGameplayConfig().getWorldMapConfig().getUserMapMarkerConfig();
            int personal = personalOverride >= 0 ? personalOverride : Integer.MAX_VALUE;
            int shared = sharedOverride >= 0 ? sharedOverride : Integer.MAX_VALUE;

            ReflectionHelper.setFieldValue(config, "maxPersonalMarkersPerPlayer", personal);
            ReflectionHelper.setFieldValue(config, "maxSharedMarkersPerPlayer", shared);
        } catch (Exception e) {
            LOGGER.warning("Failed to apply marker limit overrides: " + e.getMessage());
        }
    }
}
