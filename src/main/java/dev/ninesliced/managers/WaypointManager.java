package dev.ninesliced.managers;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarkersStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;
import dev.ninesliced.listeners.ExplorationListener;
import dev.ninesliced.utils.ReflectionHelper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Manages user map markers through Hytale's built-in UserMapMarkersStore system.
 * BetterMap uses this for CRUD operations on markers; Hytale's PersonalMarkersProvider
 * and SharedMarkersProvider handle the actual rendering on the map.
 */
public class WaypointManager {
    private static final Logger LOGGER = Logger.getLogger(WaypointManager.class.getName());
    private static final String PERSONAL_ID_PREFIX = "user_personal_";
    private static final String SHARED_ID_PREFIX = "user_shared_";

    private WaypointManager() {
    }

    /**
     * Kept for backwards compatibility with the old initialization flow.
     * No-op now that we rely on Hytale's built-in marker storage.
     */
    public static void initialize(@Nonnull Path configDir) {
    }

    /**
     * Gets all user markers (personal and shared) for a player.
     */
    @Nonnull
    public static List<UserMapMarker> getUserMarkers(@Nonnull Player player) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) {
            return List.of();
        }

        List<UserMapMarker> result = new ArrayList<>();
        
        UserMapMarkersStore personalStore = resolveStore(world, player, false);
        if (personalStore != null) {
            result.addAll(personalStore.getUserMapMarkers());
        }
        
        UserMapMarkersStore sharedStore = resolveStore(world, player, true);
        if (sharedStore != null) {
            result.addAll(sharedStore.getUserMapMarkers());
        }
        
        return result;
    }

    /**
     * Creates a new marker with the given parameters.
     */
    public static void addMarker(@Nonnull Player player, 
                                 @Nonnull String name, 
                                 @Nonnull String icon,
                                 float x, 
                                 float z,
                                 @Nullable Color tint,
                                 boolean shared) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return;

        UserMapMarkersStore store = resolveStore(world, player, shared);
        if (store == null) return;

        UserMapMarker marker = new UserMapMarker();
        marker.setId((shared ? SHARED_ID_PREFIX : PERSONAL_ID_PREFIX) + UUID.randomUUID());
        marker.setName(name);
        marker.setIcon(normalizeIcon(icon));
        marker.setPosition(x, z);
        marker.setColorTint(tint != null ? tint : new Color((byte) 0, (byte) 0, (byte) 0));
        marker.withCreatedByName(player.getDisplayName());
        marker.withCreatedByUuid(((CommandSender) player).getUuid());
        
        store.addUserMapMarker(marker);
    }

    /**
     * Removes a marker by ID or name.
     */
    public static boolean removeMarker(@Nonnull Player player, @Nonnull String idOrName) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return false;

        MarkerEntry entry = findMarkerEntry(player, world, idOrName);
        if (entry == null) {
            return false;
        }

        entry.store.removeUserMapMarker(entry.marker.getId());
        return true;
    }

    /**
     * Updates a marker's properties.
     * When position changes, the marker is deleted and recreated with a new ID
     * to ensure both map and compass update correctly.
     */
    public static boolean updateMarker(@Nonnull Player player, 
                                       @Nonnull String id, 
                                       @Nullable String newName, 
                                       @Nullable String newIcon,
                                       @Nullable Float newX,
                                       @Nullable Float newZ,
                                       @Nullable Color newTint) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return false;

        MarkerEntry entry = findMarkerEntry(player, world, id);
        if (entry == null) {
            return false;
        }

        UserMapMarker existing = entry.marker;
        
        boolean positionChanging = newX != null && newZ != null && 
            (Math.abs(existing.getX() - newX) > 0.01f || Math.abs(existing.getZ() - newZ) > 0.01f);
        
        if (positionChanging) {
            String finalName = (newName != null && !newName.trim().isEmpty()) ? newName.trim() : existing.getName();
            String finalIcon = (newIcon != null && !newIcon.trim().isEmpty()) ? normalizeIcon(newIcon.trim()) : existing.getIcon();
            Color finalTint = newTint != null ? newTint : existing.getColorTint();
            
            entry.store.removeUserMapMarker(id);
            
            UserMapMarker newMarker = new UserMapMarker();
            newMarker.setId((entry.shared ? SHARED_ID_PREFIX : PERSONAL_ID_PREFIX) + UUID.randomUUID());
            newMarker.setName(finalName);
            newMarker.setIcon(finalIcon);
            newMarker.setPosition(newX, newZ);
            newMarker.setColorTint(finalTint);
            newMarker.withCreatedByName(existing.getCreatedByName());
            newMarker.withCreatedByUuid(existing.getCreatedByUuid());
            
            entry.store.addUserMapMarker(newMarker);
            return true;
        }
        
        List<UserMapMarker> markers = new ArrayList<>(entry.store.getUserMapMarkers());
        boolean updated = false;
        for (int i = 0; i < markers.size(); i++) {
            UserMapMarker m = markers.get(i);
            if (!id.equals(m.getId())) continue;

            if (newName != null && !newName.trim().isEmpty()) {
                m.setName(newName.trim());
            }
            if (newIcon != null && !newIcon.trim().isEmpty()) {
                m.setIcon(normalizeIcon(newIcon.trim()));
            }
            if (newTint != null) {
                m.setColorTint(newTint);
            }

            updated = true;
            break;
        }

        if (updated) {
            entry.store.setUserMapMarkers(markers);
            
            if (entry.shared) {
                forceRemoveAndResyncMarkerForAllClients(world, id);
            } else {
                forceRemoveAndResyncMarker(player, id);
            }
        }
        return updated;
    }
    
    /**
     * Forces a marker to be removed from client and server caches, then immediately re-synced.
     * This is the key to updating both map AND compass - we fully remove the old marker,
     * then the provider will re-add it fresh on the next tick.
     */
    private static void forceRemoveAndResyncMarker(@Nonnull Player player, @Nonnull String markerId) {
        try {
            var tracker = player.getWorldMapTracker();
            if (tracker == null) return;

            Object markerTrackerObj = ReflectionHelper.getFieldValueRecursive(tracker, "markerTracker");
            if (!(markerTrackerObj instanceof MapMarkerTracker markerTracker)) return;
            
            var sentMarkers = markerTracker.getSentMarkers();
            if (sentMarkers != null) {
                sentMarkers.remove(markerId);
            }
            
            player.getPlayerConnection().writeNoCache(new UpdateWorldMap(
                null, 
                null, 
                new String[]{markerId}
            ));
            
            ReflectionHelper.setFieldValueRecursive(markerTracker, "smallMovementsTimer", 0.0f);
        } catch (Exception e) {
            LOGGER.warning("Failed to force marker resync: " + e.getMessage());
        }
    }
    
    /**
     * Forces a shared marker to be removed and re-synced for ALL clients in the world.
     */
    private static void forceRemoveAndResyncMarkerForAllClients(@Nonnull World world, @Nonnull String markerId) {
        try {
            for (PlayerRef worldPlayer : world.getPlayerRefs()) {
                Holder<EntityStore> holder = worldPlayer.getHolder();
                if (holder == null) continue;
                Player player = holder.getComponent(Player.getComponentType());
                if (player == null) continue;
                forceRemoveAndResyncMarker(player, markerId);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to force marker resync for all clients: " + e.getMessage());
        }
    }

    /**
     * Gets a marker by ID.
     */
    @Nullable
    public static UserMapMarker getMarker(@Nonnull Player player, @Nonnull String id) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return null;

        MarkerEntry entry = findMarkerEntry(player, world, id);
        return entry != null ? entry.marker : null;
    }

    /**
     * Finds a marker by name or ID.
     */
    @Nullable
    public static UserMapMarker findMarker(@Nonnull Player player, @Nonnull String nameOrId) {
        World world = player.getWorld();
        if (world == null || !isTrackedWorld(world)) return null;

        MarkerEntry entry = findMarkerEntry(player, world, nameOrId);
        return entry != null ? entry.marker : null;
    }

    /**
     * Checks if marker ID is a shared marker.
     */
    public static boolean isSharedId(@Nonnull String id) {
        return id.startsWith(SHARED_ID_PREFIX);
    }

    private static UserMapMarkersStore resolveStore(@Nonnull World world, @Nonnull Player player, boolean shared) {
        if (shared) {
            return world.getChunkStore().getStore().getResource(WorldMarkersResource.getResourceType());
        }
        return player.getPlayerConfigData().getPerWorldData(world.getName());
    }

    @Nullable
    private static MarkerEntry findMarkerEntry(@Nonnull Player player, @Nonnull World world, @Nonnull String nameOrId) {
        UserMapMarkersStore personal = resolveStore(world, player, false);
        MarkerEntry personalEntry = locateInStore(personal, nameOrId, false);
        if (personalEntry != null) {
            return personalEntry;
        }
        UserMapMarkersStore shared = resolveStore(world, player, true);
        return locateInStore(shared, nameOrId, true);
    }

    @Nullable
    private static MarkerEntry locateInStore(@Nullable UserMapMarkersStore store, @Nonnull String nameOrId, boolean shared) {
        if (store == null) return null;
        for (UserMapMarker marker : store.getUserMapMarkers()) {
            if (marker == null || marker.getId() == null) continue;
            String markerName = marker.getName();
            boolean matchId = marker.getId().equalsIgnoreCase(nameOrId);
            boolean matchName = markerName != null && markerName.equalsIgnoreCase(nameOrId);
            if (matchId || matchName) {
                return new MarkerEntry(marker, store, shared);
            }
        }
        return null;
    }

    public static boolean isTrackedWorld(@Nullable World world) {
        return ExplorationListener.isTrackedWorld(world);
    }

    private static String normalizeIcon(@Nullable String icon) {
        if (icon == null || icon.isEmpty()) {
            return "UserA.png";
        }
        if (icon.endsWith(".png")) {
            return icon;
        }
        return icon + ".png";
    }

    /**
     * Called when a player joins. No-op now since Hytale handles marker sync.
     */
    public static void onPlayerJoin(@Nonnull Player player) {
    }

    private record MarkerEntry(UserMapMarker marker, UserMapMarkersStore store, boolean shared) {
    }
}
