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

        List<UserMapMarker> markers = new ArrayList<>(entry.store.getUserMapMarkers());
        boolean updated = false;
        UserMapMarker updatedMarker = null;
        for (int i = 0; i < markers.size(); i++) {
            UserMapMarker m = markers.get(i);
            if (!id.equals(m.getId())) continue;

            if (newName != null && !newName.trim().isEmpty()) {
                m.setName(newName.trim());
            }
            if (newIcon != null && !newIcon.trim().isEmpty()) {
                m.setIcon(normalizeIcon(newIcon.trim()));
            }
            if (newX != null && newZ != null) {
                m.setPosition(newX, newZ);
            }
            if (newTint != null) {
                m.setColorTint(newTint);
            }

            updated = true;
            updatedMarker = m;
            break;
        }

        if (updated) {
            entry.store.setUserMapMarkers(markers);
            
            if (entry.shared) {
                forceRefreshMarkerOnAllClients(world, id);
                if (updatedMarker != null) {
                    forceSendMarkerUpdateToAllClients(world, updatedMarker);
                }
            } else {
                forceRefreshMarkerOnClient(player, id);
                if (updatedMarker != null) {
                    forceSendMarkerUpdate(player, updatedMarker);
                }
            }
        }
        return updated;
    }
    
    /**
     * Forces a marker to be re-sent to the client by removing it from the tracker's sent cache.
     */
    private static void forceRefreshMarkerOnClient(@Nonnull Player player, @Nonnull String markerId) {
        try {
            var tracker = player.getWorldMapTracker();
            if (tracker == null) return;

            Object markerTrackerObj = ReflectionHelper.getFieldValueRecursive(tracker, "markerTracker");
            if (markerTrackerObj instanceof MapMarkerTracker markerTracker) {
                var sentMarkers = markerTracker.getSentMarkers();
                if (sentMarkers != null) {
                    sentMarkers.remove(markerId);
                }
                ReflectionHelper.setFieldValueRecursive(markerTracker, "smallMovementsTimer", 0.0f);
                return;
            }

            try {
                var sentMarkers = tracker.getSentMarkers();
                if (sentMarkers != null) {
                    sentMarkers.remove(markerId);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
        }
    }

    private static void forceSendMarkerUpdate(@Nonnull Player player, @Nonnull UserMapMarker marker) {
        try {
            MapMarker protocol = marker.toProtocolMarker();
            player.getPlayerConnection().writeNoCache(new UpdateWorldMap(null, new MapMarker[]{protocol}, new String[]{marker.getId()}));
        } catch (Exception ignored) {
        }
    }
    
    /**
     * Forces a shared marker to be re-sent to ALL clients in the world.
     */
    private static void forceRefreshMarkerOnAllClients(@Nonnull World world, @Nonnull String markerId) {
        try {
            for (PlayerRef worldPlayer : world.getPlayerRefs()) {
                Holder<EntityStore> holder = worldPlayer.getHolder();
                if (holder == null) continue;
                Player player = holder.getComponent(Player.getComponentType());
                if (player == null) continue;
                forceRefreshMarkerOnClient(player, markerId);
            }
        } catch (Exception _) {
        }
    }

    private static void forceSendMarkerUpdateToAllClients(@Nonnull World world, @Nonnull UserMapMarker marker) {
        try {
            for (PlayerRef worldPlayer : world.getPlayerRefs()) {
                Holder<EntityStore> holder = worldPlayer.getHolder();
                if (holder == null) continue;
                Player player = holder.getComponent(Player.getComponentType());
                if (player == null) continue;
                forceSendMarkerUpdate(player, marker);
            }
        } catch (Exception _) {
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
