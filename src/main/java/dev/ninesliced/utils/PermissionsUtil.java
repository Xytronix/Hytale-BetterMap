package dev.ninesliced.utils;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.asset.type.gameplay.worldmap.UserMapMarkerConfig;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PermissionsUtil {
    private static final String ADMIN_PERMISSION = "bettermap.admin";
    private static final String ADMIN_COMMAND_PERMISSION = "command.bettermap.admin";
    private static final String TELEPORT_PERMISSION = "dev.ninesliced.bettermap.command.teleport";
    private static final String WARP_GO_PERMISSION = "hytale.command.warp.go";

    private PermissionsUtil() {
    }

    public static boolean isAdmin(@Nonnull Player player) {
        UUID uuid = ((CommandSender) player).getUuid();
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }
        return perms.hasPermission(uuid, ADMIN_PERMISSION) || perms.hasPermission(uuid, ADMIN_COMMAND_PERMISSION);
    }

    public static boolean canTeleport(@Nonnull Player player) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null) {
            return false;
        }

        UUID uuid = ((CommandSender) player).getUuid();
        Set<String> groups = perms.getGroupsForUser(uuid);
        if (groups != null && groups.contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, WARP_GO_PERMISSION)
            || perms.hasPermission(uuid, TELEPORT_PERMISSION);
    }

    /**
     * Checks if the player can use global/shared waypoints based on Hytale's UserMapMarkerConfig.
     * Returns true if the world allows creating markers and the max shared markers limit is > 0.
     */
    public static boolean canUseGlobalWaypoints(@Nonnull Player player) {
        World world = player.getWorld();
        if (world == null) {
            return false;
        }
        
        try {
            UserMapMarkerConfig config = world.getGameplayConfig().getWorldMapConfig().getUserMapMarkerConfig();
            return config.isAllowCreatingMarkers() && config.getMaxSharedMarkersPerPlayer() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
