package dev.ninesliced.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.ModConfig.MapQuality;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.hud.HudPosition;
import dev.ninesliced.managers.MapPrivacyManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.WorldBorderManager;
import dev.ninesliced.managers.CaveModeManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WorldMapHook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

public class ConfigMenuPage extends InteractiveCustomUIPage<ConfigMenuPage.ConfigEventData> {

    private enum BindingType { STRING, NUMBER, BOOLEAN }

    private static final String LAYOUT_PATH = "Pages/BetterMap/ConfigMenu.ui";

    private boolean restartRequired = false;

    public ConfigMenuPage(PlayerRef player) {
        super(player, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append(LAYOUT_PATH);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        boolean isAdmin = PermissionsUtil.isAdmin(player);

        PlayerConfig pConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());
        ui.set("#PlayerMinScale.Value", pConfig.getMinScale());
        ui.set("#PlayerMaxScale.Value", pConfig.getMaxScale());
        
        boolean serverLocationEnabled = ModConfig.getInstance().isLocationEnabled();
        boolean serverCaveModeEnabled = ModConfig.getInstance().isCaveModeEnabled();

        if (serverLocationEnabled) {
            ui.set("#PlayerLocationEnabled.Value", pConfig.isLocationEnabled());
            applyLocationPositionDropdown(ui, pConfig.getEffectiveLocationHudPosition(), "#PlayerLocationPosition");
        } else {
            ui.set("#PlayerLocationHeader.Visible", false);
            ui.set("#PlayerLocationCard.Visible", false);
        }

        if (serverCaveModeEnabled) {
            ui.set("#PlayerCaveModeEnabled.Value", pConfig.isCaveModeEnabled());
            ui.set("#PlayerDiscoverSurface.Value", pConfig.isDiscoverSurfaceUnderground());
        } else {
            ui.set("#PlayerCaveModeHeader.Visible", false);
            ui.set("#PlayerCaveModeCard.Visible", false);
        }

        bindChange(events, "#PlayerMinScale", "player_min_scale", BindingType.NUMBER);
        bindChange(events, "#PlayerMaxScale", "player_max_scale", BindingType.NUMBER);
        bindChange(events, "#PlayerLocationEnabled", "player_location", BindingType.BOOLEAN);
        bindChange(events, "#PlayerLocationPosition", "player_location_pos", BindingType.STRING);
        bindChange(events, "#PlayerCaveModeEnabled", "player_cavemode", BindingType.BOOLEAN);
        bindChange(events, "#PlayerDiscoverSurface", "player_discover_surface", BindingType.BOOLEAN);
        bindClick(events, "#PlayerViewBtn", "view_player");
        bindClick(events, "#AdminViewBtn", "view_admin");
        bindClick(events, "#OpenWaypointsBtn", "open_waypoints");
        bindClick(events, "#CloseBtn", "close_menu");

        if (isAdmin) {
             ui.set("#NavBar.Visible", true);

             ModConfig gConfig = ModConfig.getInstance();

             ui.set("#AdminExplorationRadius.Value", gConfig.getExplorationRadius());
             ui.set("#AdminMapQualityInfo.Text", gConfig.getMapQuality().name());
             ui.set("#AdminMaxChunksToLoad.Value", gConfig.getMaxChunksToLoad());

             ui.set("#AdminMinScale.Value", (int) gConfig.getMinScale());
             ui.set("#AdminMaxScale.Value", (int) gConfig.getMaxScale());

             ui.set("#AllowWaypointTeleport.Value", gConfig.isAllowWaypointTeleports());
             ui.set("#ShareAllExploration.Value", gConfig.isShareAllExploration());
             ui.set("#DebugMode.Value", gConfig.isDebug());
             ui.set("#LocationHudEnabled.Value", gConfig.isLocationEnabled());
             applyLocationPositionDropdown(ui, gConfig.getLocationHudPosition(), "#AdminLocationPosition");
             ui.set("#RadarEnabled.Value", gConfig.isRadarEnabled());
             ui.set("#HidePlayers.Value", gConfig.isHidePlayersOnMap());
             ui.set("#HideOtherWarps.Value", gConfig.isHideOtherWarpsOnMap());
             ui.set("#HideUnexploredWarps.Value", gConfig.isHideUnexploredWarpsOnMap());
             ui.set("#HideAllPois.Value", gConfig.isHideAllPoiOnMap());
             ui.set("#HideUnexploredPois.Value", gConfig.isHideUnexploredPoiOnMap());

             ui.set("#RadarRange.Value", gConfig.getRadarRange());


             ui.set("#HiddenPoisList.Value", String.join(", ", gConfig.getHiddenPoiNames()));
             ui.set("#AllowedWorldList.Value", String.join(", ", gConfig.getAllowedWorlds()));
             ui.set("#AutoSaveInterval.Value", gConfig.getAutoSaveInterval());

             ui.set("#WorldBorderEnabled.Value", gConfig.isWorldBorderEnabled());
             ui.set("#WorldBorderRadius.Value", gConfig.getWorldBorderRadius());
             ui.set("#WorldBorderOffsetX.Value", gConfig.getWorldBorderOffsetX());
             ui.set("#WorldBorderOffsetZ.Value", gConfig.getWorldBorderOffsetZ());

             ui.set("#CaveModeEnabled.Value", gConfig.isCaveModeEnabled());
             ui.set("#CaveModeLayerSize.Value", gConfig.getCaveModeLayerSize());
             ui.set("#CaveModeThreshold.Value", gConfig.getCaveModeUndergroundThreshold());
             ui.set("#CaveModeRadius.Value", gConfig.getCaveModeRadius());

             bindChange(events, "#AdminExplorationRadius", "admin_exp_radius", BindingType.NUMBER);
             bindClick(events, "#AdminMapQualityInfo", "admin_map_quality");
             bindChange(events, "#AdminMaxChunksToLoad", "admin_max_chunks", BindingType.NUMBER);
             bindChange(events, "#AdminMinScale", "admin_min_scale", BindingType.NUMBER);
             bindChange(events, "#AdminMaxScale", "admin_max_scale", BindingType.NUMBER);

             bindChange(events, "#AllowWaypointTeleport", "admin_wp_teleport", BindingType.BOOLEAN);
             bindChange(events, "#ShareAllExploration", "admin_share_exp", BindingType.BOOLEAN);
             bindChange(events, "#DebugMode", "admin_debug", BindingType.BOOLEAN);
             bindChange(events, "#LocationHudEnabled", "admin_location_enabled", BindingType.BOOLEAN);
             bindChange(events, "#AdminLocationPosition", "admin_location_pos", BindingType.STRING);

             bindChange(events, "#RadarEnabled", "admin_radar_enabled", BindingType.BOOLEAN);
             bindChange(events, "#RadarRange", "admin_radar_range", BindingType.NUMBER);

             bindChange(events, "#HidePlayers", "admin_hide_players", BindingType.BOOLEAN);
             bindChange(events, "#HideOtherWarps", "admin_hide_other_warps", BindingType.BOOLEAN);
             bindChange(events, "#HideUnexploredWarps", "admin_hide_unex_warps", BindingType.BOOLEAN);
             bindChange(events, "#HideAllPois", "admin_hide_all_pois", BindingType.BOOLEAN);
             bindChange(events, "#HideUnexploredPois", "admin_hide_unex_pois", BindingType.BOOLEAN);

             bindChange(events, "#HiddenPoisList", "admin_hidden_pois", BindingType.STRING);
             bindChange(events, "#AllowedWorldList", "admin_allowed_worlds", BindingType.STRING);
             bindClick(events, "#AddCurrentWorldBtn", "admin_add_current_world");
             bindChange(events, "#AutoSaveInterval", "admin_autosave", BindingType.NUMBER);

             bindChange(events, "#WorldBorderEnabled", "admin_world_border_enabled", BindingType.BOOLEAN);
             bindChange(events, "#WorldBorderRadius", "admin_world_border_radius", BindingType.NUMBER);
             bindChange(events, "#WorldBorderOffsetX", "admin_world_border_offset_x", BindingType.NUMBER);
             bindChange(events, "#WorldBorderOffsetZ", "admin_world_border_offset_z", BindingType.NUMBER);

             bindChange(events, "#CaveModeEnabled", "admin_cavemode_enabled", BindingType.BOOLEAN);
             bindChange(events, "#CaveModeLayerSize", "admin_cavemode_layer", BindingType.NUMBER);
             bindChange(events, "#CaveModeThreshold", "admin_cavemode_threshold", BindingType.NUMBER);
             bindChange(events, "#CaveModeRadius", "admin_cavemode_radius", BindingType.NUMBER);
        }
    }

    private void bindChange(UIEventBuilder events, String elementId, String action, BindingType type) {
        EventData data = new EventData().put("Action", action);
        if (type == BindingType.NUMBER) {
            data.put("@ValueNum", elementId + ".Value");
        } else if (type == BindingType.BOOLEAN) {
            data.put("@ValueBool", elementId + ".Value");
        } else {
            data.put("@Value", elementId + ".Value");
        }
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, elementId, data, false);
    }

    private void bindClick(UIEventBuilder events, String elementId, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, elementId,
            new EventData()
                .put("Action", action),
            false
        );
    }

    private void applyLocationPositionDropdown(UICommandBuilder ui, String currentPosition, String elementId) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        for (HudPosition pos : HudPosition.values()) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(pos.getDisplayName()), pos.getId()));
        }
        ui.set(elementId + ".Entries", entries);
        ui.set(elementId + ".Value", currentPosition != null ? currentPosition : HudPosition.TOP_LEFT.getId());
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ConfigEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        switch (data.action) {
            case "view_player" -> {
                ui.set("#PlayerView.Visible", true);
                ui.set("#AdminView.Visible", false);
                sendUpdate(ui, events, false);
                return;
            }
            case "view_admin" -> {
                if (PermissionsUtil.isAdmin(player)) {
                    ui.set("#PlayerView.Visible", false);
                    ui.set("#AdminView.Visible", true);
                    sendUpdate(ui, events, false);
                }
                return;
            }
            case "open_waypoints" -> {
                player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(playerRef));
                return;
            }
            case "close_menu" -> {
                if (restartRequired) {
                    var packetHandler = playerRef.getPacketHandler();

                    var primaryMessage = Message.raw("Restart Required").color("#FF0000");
                    var secondaryMessage = Message.raw("Map settings changed. Restart server to apply.").color("#FFAA00");
                    var icon = new ItemStack("Weapon_Spellbook_Demon", 1).toPacket();

                    NotificationUtil.sendNotification(
                        packetHandler,
                        primaryMessage,
                        secondaryMessage,
                        icon
                    );
                }
                player.getPageManager().setPage(ref, store, Page.None);
                return;
            }
        }

        if (data.action.startsWith("player_")) {
            handlePlayerUpdate(data, player);
        } else if (data.action.startsWith("admin_")) {
             if (PermissionsUtil.isAdmin(player)) {
                handleAdminUpdate(data, ui, player);
             }
        }
    }

    private void handlePlayerUpdate(ConfigEventData data, Player player) {
        PlayerConfig pConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());
        String val = data.getEffectiveValue();
        World world = player.getWorld();
        try {
            if (val == null) return;
            switch (data.action) {
                case "player_min_scale":
                    pConfig.setMinScale(Float.parseFloat(val));
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    if (world != null)
                        world.execute(() -> WorldMapHook.sendMapSettingsToPlayer(player));
                    break;
                case "player_max_scale":
                    pConfig.setMaxScale(Float.parseFloat(val));
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    if (world != null)
                        world.execute(() -> WorldMapHook.sendMapSettingsToPlayer(player));
                    break;
                case "player_location":
                    if (!ModConfig.getInstance().isLocationEnabled()) {
                        break;
                    }
                    boolean locationEnabled = Boolean.parseBoolean(val);
                    pConfig.setLocationEnabled(locationEnabled);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    break;
                case "player_location_pos":
                    if (!ModConfig.getInstance().isLocationEnabled()) {
                        break;
                    }
                    pConfig.setLocationHudPosition(val.trim().toLowerCase());
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    break;
                case "player_cavemode":
                    if (!ModConfig.getInstance().isCaveModeEnabled()) {
                        break;
                    }
                    boolean enabled = Boolean.parseBoolean(val);
                    pConfig.setCaveModeEnabled(enabled);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
                    if (state != null) {
                        state.setDynamicModeEnabled(enabled);
                        if (!enabled) {
                            state.setCurrentlyUnderground(false);
                        }
                    }
                    if (world != null) {
                        world.execute(() -> WorldMapHook.forceFullMapRefresh(player));
                    }
                    break;
                case "player_discover_surface":
                    if (!ModConfig.getInstance().isCaveModeEnabled()) {
                        break;
                    }
                    boolean discoverSurface = Boolean.parseBoolean(val);
                    pConfig.setDiscoverSurfaceUnderground(discoverSurface);
                    PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
                    break;
            }
            PlayerConfigManager.getInstance().savePlayerConfig(((CommandSender) player).getUuid());
        } catch (NumberFormatException _) {}
    }

    private void handleAdminUpdate(ConfigEventData data, UICommandBuilder ui, Player player) {
        ModConfig gConfig = ModConfig.getInstance();
        String val = data.getEffectiveValue();
        try {
            switch (data.action) {
                case "admin_exp_radius":
                     if (val != null) gConfig.setExplorationRadius(Integer.parseInt(val));
                case "admin_map_quality":
                    MapQuality current = gConfig.getMapQuality();
                    MapQuality next = MapQuality.values()[(current.ordinal() + 1) % MapQuality.values().length];
                    gConfig.setQuality(next);
                    ui.set("#AdminMapQualityInfo.Text", next.name());
                    ui.set("#AdminMaxChunksToLoad.Value", gConfig.getMaxChunksToLoad());
                    sendUpdate(ui, new UIEventBuilder(), false);
                    restartRequired = true;
                    break;
                case "admin_max_chunks":
                    if (val != null) {
                        try {
                            int inputValue = Integer.parseInt(val);
                            int maxAllowed = gConfig.getMapQuality().maxChunks;

                            if (inputValue > maxAllowed) {
                                gConfig.setMaxChunksToLoad(maxAllowed);
                                ui.set("#AdminMaxChunksToLoad.Value", maxAllowed);
                                sendUpdate(ui, new UIEventBuilder(), false);

                                var packetHandler = playerRef.getPacketHandler();

                                var primaryMessage = Message.raw("Limit Exceeded").color("#FF0000");
                                var secondaryMessage = Message.raw("Max for " + gConfig.getMapQuality().name() + " quality is " + maxAllowed).color("#FFAA00");
                                var icon = new ItemStack("Recipe_Book_Magic_Void", 1).toPacket();

                                NotificationUtil.sendNotification(
                                        packetHandler,
                                        primaryMessage,
                                        secondaryMessage,
                                        icon
                                );
                            } else {
                                gConfig.setMaxChunksToLoad(inputValue);
                            }
                            restartRequired = true;
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                case "admin_min_scale":
                     if (val != null) {
                         float f = Float.parseFloat(val);
                         gConfig.setMinScale(f);
                     }
                    break;
                case "admin_max_scale":
                     if (val != null) {
                         float f2 = Float.parseFloat(val);
                         gConfig.setMaxScale(f2);
                     }
                    break;
                case "admin_wp_teleport":
                    if (val != null) gConfig.setAllowWaypointTeleports(Boolean.parseBoolean(val));
                    break;
                case "admin_share_exp":
                    if (val != null) {
                        gConfig.setShareAllExploration(Boolean.parseBoolean(val));

                        Universe universe = Universe.get();
                        if (universe != null) {
                            universe.getWorlds().values().forEach(WorldMapHook::refreshTrackers);
                        }
                    }
                    break;
                case "admin_debug":
                     if (val != null) gConfig.setDebug(Boolean.parseBoolean(val));
                    break;
                case "admin_location_enabled":
                    if (val != null) gConfig.setLocationEnabled(Boolean.parseBoolean(val));
                    break;
                case "admin_location_pos":
                    if (val != null && !val.isBlank()) {
                        gConfig.setLocationHudPosition(val.trim().toLowerCase());
                    }
                    break;
                case "admin_radar_enabled":
                     if (val != null) gConfig.setRadarEnabled(Boolean.parseBoolean(val));
                    break;
                case "admin_radar_range":
                     if (val != null) gConfig.setRadarRange(Integer.parseInt(val));
                    break;
                case "admin_hide_players":
                     if (val != null) {
                         gConfig.setHidePlayersOnMap(Boolean.parseBoolean(val));
                         MapPrivacyManager.getInstance().updatePrivacyState();
                     }
                    break;
                case "admin_hide_other_warps":
                     if (val != null) {
                         gConfig.setHideOtherWarpsOnMap(Boolean.parseBoolean(val));
                         MapPrivacyManager.getInstance().updatePrivacyState();
                     }
                    break;
                case "admin_hide_unex_warps":
                     if (val != null) {
                         gConfig.setHideUnexploredWarpsOnMap(Boolean.parseBoolean(val));
                         MapPrivacyManager.getInstance().updatePrivacyState();
                     }
                    break;
                case "admin_hide_all_pois":
                     if (val != null) {
                         gConfig.setHideAllPoiOnMap(Boolean.parseBoolean(val));
                         MapPrivacyManager.getInstance().updatePrivacyState();
                     }
                    break;
                case "admin_hide_unex_pois":
                    if (val != null) {
                        gConfig.setHideUnexploredPoiOnMap(Boolean.parseBoolean(val));
                        MapPrivacyManager.getInstance().updatePrivacyState();
                    }
                    break;
                case "admin_hidden_pois":
                    if (val != null) {
                        List<String> pois = Arrays.stream(val.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                        gConfig.setHiddenPoiNames(pois);
                        MapPrivacyManager.getInstance().updatePrivacyState();
                    }
                    break;
                case "admin_allowed_worlds":
                    if (val != null) {
                        List<String> worlds = Arrays.stream(val.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                        gConfig.setAllowedWorlds(worlds);
                    }
                    break;
                case "admin_add_current_world":
                    World world = player.getWorld();
                    if (world == null) break;
                    String worldName = world.getName();

                    List<String> allowed = new ArrayList<>(gConfig.getAllowedWorlds());
                    if (!allowed.contains(worldName)) {
                        allowed.add(worldName);
                        gConfig.setAllowedWorlds(allowed);
                        ui.set("#AllowedWorldList.Value", String.join(", ", allowed));
                        sendUpdate(ui, new UIEventBuilder(), false);
                    }
                    break;
                case "admin_autosave":
                    if (val != null) gConfig.setAutoSaveInterval(Integer.parseInt(val));
                    break;
                case "admin_world_border_enabled":
                    if (val != null) {
                        gConfig.setWorldBorderEnabled(Boolean.parseBoolean(val));
                        WorldBorderManager.getInstance().clearAllCaches();
                    }
                    break;
                case "admin_world_border_radius":
                    if (val != null) {
                        gConfig.setWorldBorderRadius(Integer.parseInt(val));
                        WorldBorderManager.getInstance().clearAllCaches();
                    }
                    break;
                case "admin_world_border_offset_x":
                    if (val != null) {
                        gConfig.setWorldBorderOffsetX(Integer.parseInt(val));
                        WorldBorderManager.getInstance().clearAllCaches();
                    }
                    break;
                case "admin_world_border_offset_z":
                    if (val != null) {
                        gConfig.setWorldBorderOffsetZ(Integer.parseInt(val));
                        WorldBorderManager.getInstance().clearAllCaches();
                    }
                    break;
                case "admin_cavemode_enabled":
                    if (val != null) {
                        boolean caveEnabled = Boolean.parseBoolean(val);
                        gConfig.setCaveModeEnabled(caveEnabled);
                        CaveModeManager.DynamicCaveModeState caveState = CaveModeManager.getInstance().getState(player);
                        if (caveState != null) {
                            caveState.setDynamicModeEnabled(caveEnabled);
                            if (!caveEnabled) {
                                caveState.setCurrentlyUnderground(false);
                            }
                        }
                        World caveWorld = player.getWorld();
                        if (caveWorld != null) {
                            caveWorld.execute(() -> WorldMapHook.forceFullMapRefresh(player));
                        }
                    }
                    break;
                case "admin_cavemode_layer":
                    if (val != null) {
                        int layerSize = Integer.parseInt(val);
                        layerSize = Math.max(1, Math.min(layerSize, 20));
                        gConfig.setCaveModeLayerSize(layerSize);
                        final int finalLayerSize = layerSize;
                        World layerWorld = player.getWorld();
                        if (layerWorld != null) {
                            for (PlayerRef pRef : layerWorld.getPlayerRefs()) {
                                var pHolder = pRef.getHolder();
                                if (pHolder != null) {
                                    Player p = pHolder.getComponent(Player.getComponentType());
                                    if (p != null) {
                                        CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(p);
                                        if (state != null) {
                                            state.setLayerSize(finalLayerSize);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                case "admin_cavemode_threshold":
                    if (val != null) {
                        int threshold = Integer.parseInt(val);
                        threshold = Math.max(0, Math.min(threshold, 319));
                        gConfig.setCaveModeUndergroundThreshold(threshold);
                        final int finalThreshold = threshold;
                        World threshWorld = player.getWorld();
                        if (threshWorld != null) {
                            for (PlayerRef pRef : threshWorld.getPlayerRefs()) {
                                var pHolder = pRef.getHolder();
                                if (pHolder != null) {
                                    Player p = pHolder.getComponent(Player.getComponentType());
                                    if (p != null) {
                                        CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(p);
                                        if (state != null) {
                                            state.setUndergroundThreshold(finalThreshold);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                case "admin_cavemode_radius":
                    if (val != null) {
                        int radius = Integer.parseInt(val);
                        radius = Math.max(1, Math.min(radius, 16));
                        gConfig.setCaveModeRadius(radius);
                        final int finalRadius = radius;
                        World radiusWorld = player.getWorld();
                        if (radiusWorld != null) {
                            for (PlayerRef pRef : radiusWorld.getPlayerRefs()) {
                                var pHolder = pRef.getHolder();
                                if (pHolder != null) {
                                    Player p = pHolder.getComponent(Player.getComponentType());
                                    if (p != null) {
                                        CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(p);
                                        if (state != null) {
                                            state.setCaveRadius(finalRadius);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
            }
            gConfig.save();
        } catch (NumberFormatException _) {}
    }

    public static class ConfigEventData {
        public String action;
        public String value;
        public Double valueNum;
        public Boolean valueBool;
        public String checked;

        public ConfigEventData() {}

        public String getEffectiveValue() {
            if (value != null) return value;
            if (valueNum != null) {
                 if (valueNum % 1 == 0 && !Double.isInfinite(valueNum)) {
                     return String.valueOf(valueNum.longValue());
                 }
                 return String.valueOf(valueNum);
            }
            if (valueBool != null) return String.valueOf(valueBool);
            if (checked != null) return checked;
            return null;
        }

        @SuppressWarnings("deprecation")
        public static final BuilderCodec<ConfigEventData> CODEC = BuilderCodec.builder(ConfigEventData.class, ConfigEventData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action)
            .addField(new KeyedCodec<>("@Value", Codec.STRING), (o, v) -> o.value = v, o -> o.value)
            .addField(new KeyedCodec<>("@ValueNum", Codec.DOUBLE), (o, v) -> o.valueNum = v, o -> o.valueNum)
            .addField(new KeyedCodec<>("@ValueBool", Codec.BOOLEAN), (o, v) -> o.valueBool = v, o -> o.valueBool)
            .addField(new KeyedCodec<>("@Checked", Codec.STRING), (o, v) -> o.checked = v, o -> o.checked)
            .build();
    }
}
