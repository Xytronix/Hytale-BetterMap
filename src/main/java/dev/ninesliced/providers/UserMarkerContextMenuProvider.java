package dev.ninesliced.providers;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.PlacedByMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore.WorldMarkersResource;

import javax.annotation.Nonnull;

/**
 * Custom marker provider that adds "Edit" context menu option to user map markers.
 * This replaces the built-in PersonalMarkersProvider and SharedMarkersProvider
 * to add BetterMap-specific context menu options.
 */
public class UserMarkerContextMenuProvider implements WorldMapManager.MarkerProvider {
    
    public static final UserMarkerContextMenuProvider INSTANCE = new UserMarkerContextMenuProvider();
    
    private UserMarkerContextMenuProvider() {
    }
    
    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        for (UserMapMarker marker : perWorldData.getUserMapMarkers()) {
            collector.add(buildMarkerWithContextMenu(marker));
        }
        
        WorldMarkersResource worldMarkersResource = world.getChunkStore().getStore().getResource(WorldMarkersResource.getResourceType());
        for (UserMapMarker marker : worldMarkersResource.getUserMapMarkers()) {
            collector.add(buildMarkerWithContextMenu(marker));
        }
    }
    
    /**
     * Builds a MapMarker with context menu options (Edit).
     */
    private MapMarker buildMarkerWithContextMenu(@Nonnull UserMapMarker marker) {
        MapMarkerBuilder builder = new MapMarkerBuilder(
            marker.getId(),
            marker.getIcon(),
            new Transform(marker.getX(), 100.0, marker.getZ())
        );
        
        if (marker.getName() != null) {
            builder.withCustomName(marker.getName());
        }
        
        if (marker.getColorTint() != null) {
            builder.withComponent(new TintComponent(marker.getColorTint()));
        }
        
        if (marker.getCreatedByName() != null) {
            builder.withComponent(new PlacedByMarkerComponent(
                Message.raw(marker.getCreatedByName()).getFormattedMessage(),
                marker.getCreatedByUuid()
            ));
        }
        
        builder.withContextMenuItem(new ContextMenuItem("Edit", "bettermap waypoint edit " + marker.getId()));
        
        return builder.build();
    }
}
