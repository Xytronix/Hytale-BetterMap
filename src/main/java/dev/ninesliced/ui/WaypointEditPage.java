package dev.ninesliced.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import dev.ninesliced.managers.WaypointManager;
import dev.ninesliced.utils.PermissionsUtil;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.annotation.Nonnull;

public class WaypointEditPage extends InteractiveCustomUIPage<WaypointEditPage.EditData> {

    private static final String[] AVAILABLE_ICONS = {
        "UserA.png", "UserB.png", "UserC.png", "UserD.png", "UserE.png", "UserF.png"
    };

    private static final Color[] AVAILABLE_TINTS = {
        new Color((byte) -1, (byte) -1, (byte) -1),   // White #FFFFFF
        new Color((byte) -1, (byte) 68, (byte) 68),   // Red #FF4444
        new Color((byte) 68, (byte) -1, (byte) 68),   // Green #44FF44
        new Color((byte) 68, (byte) 68, (byte) -1),   // Blue #4444FF
        new Color((byte) -1, (byte) -1, (byte) 68),   // Yellow #FFFF44
        new Color((byte) -1, (byte) 68, (byte) -1),   // Magenta #FF44FF
        new Color((byte) 68, (byte) -1, (byte) -1),   // Cyan #44FFFF
        new Color((byte) -1, (byte) -120, (byte) 0),  // Orange #FF8800
    };

    private static final String[] TINT_NAMES = {
        "White", "Red", "Green", "Blue", "Yellow", "Magenta", "Cyan", "Orange"
    };

    @Nullable
    private final String targetId;
    private boolean shared = false;

    private String nameInput = "";
    private String inputX = "0.00";
    private String inputZ = "0.00";
    private int selectedIconIndex = 0;
    private int selectedTintIndex = 0;
    
    private boolean initialized = false;

    public WaypointEditPage(@Nonnull PlayerRef playerRef, @Nullable String targetId) {
        super(playerRef, CustomPageLifetime.CanDismiss, EditData.CODEC);
        this.targetId = targetId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append("Pages/BetterMap/WaypointEdit.ui");
        
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        
        if (!initialized) {
            if (targetId != null) {
                UserMapMarker marker = WaypointManager.getMarker(player, targetId);
                if (marker != null) {
                    this.nameInput = marker.getName() != null ? marker.getName() : "";
                    this.inputX = String.format(Locale.ROOT, "%.2f", marker.getX());
                    this.inputZ = String.format(Locale.ROOT, "%.2f", marker.getZ());
                    this.shared = WaypointManager.isSharedId(targetId);
                    this.selectedIconIndex = getIconIndex(marker.getIcon());
                    this.selectedTintIndex = getTintIndex(marker.getColorTint());
                }
            } else {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    var pos = transform.getPosition();
                    this.inputX = String.format(Locale.ROOT, "%.2f", pos.x);
                    this.inputZ = String.format(Locale.ROOT, "%.2f", pos.z);
                }
            }
            initialized = true;
        }

        ui.set("#NameInput.Value", this.nameInput);
        ui.set("#InputX.Value", this.inputX);
        ui.set("#InputZ.Value", this.inputZ);

        // Icon selection
        String iconName = AVAILABLE_ICONS[selectedIconIndex];
        ui.set("#IconLabel.Text", getIconDisplayName(iconName));
        ui.set("#IconPreview.Background", "../../Common/" + iconName);
        
        // Tint/Color selection
        ui.set("#ColorLabel.Text", getTintDisplayName(selectedTintIndex));
        Color tintColor = AVAILABLE_TINTS[selectedTintIndex];
        ui.set("#ColorPreview.Background", String.format("#%02X%02X%02X", tintColor.red & 0xFF, tintColor.green & 0xFF, tintColor.blue & 0xFF));

        boolean canShared = PermissionsUtil.canUseGlobalWaypoints(player);
        ui.set("#GlobalRow.Visible", canShared);
        if (canShared) {
            ui.set("#GlobalCheckbox.Value", this.shared);
        }

        // Input bindings
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NameInput",  
            new EventData().put(EditData.KEY_NAME_INPUT, "#NameInput.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputX", 
            new EventData().put(EditData.KEY_INPUT_X, "#InputX.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputZ", 
            new EventData().put(EditData.KEY_INPUT_Z, "#InputZ.Value"), false);
        
        // Icon navigation
        events.addEventBinding(CustomUIEventBindingType.Activating, "#IconPrev", 
            new EventData().put(EditData.KEY_ACTION, Action.ICON_PREV.name()), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#IconNext", 
            new EventData().put(EditData.KEY_ACTION, Action.ICON_NEXT.name()), false);

        // Tint navigation
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TintPrev", 
            new EventData().put(EditData.KEY_ACTION, Action.TINT_PREV.name()), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TintNext", 
            new EventData().put(EditData.KEY_ACTION, Action.TINT_NEXT.name()), false);

        // Action buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", 
            new EventData().put(EditData.KEY_ACTION, Action.BACK.name()), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", 
            new EventData().put(EditData.KEY_ACTION, Action.SAVE.name()), false);
            
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", 
            new EventData().put(EditData.KEY_ACTION, Action.CANCEL.name()), false);

        if (canShared) {
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#GlobalCheckbox",
                new EventData().put(EditData.KEY_GLOBAL, "#GlobalCheckbox.Value"), false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EditData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        
        if (data.nameInput != null) this.nameInput = data.nameInput;
        if (data.inputX != null) this.inputX = data.inputX;
        if (data.inputZ != null) this.inputZ = data.inputZ;
        if (data.global != null) this.shared = data.global;

        if (data.action == null) {
            return; 
        }

        Action action;
        try {
            action = Action.valueOf(data.action);
        } catch (Exception e) {
            return;
        }

        boolean canShared = PermissionsUtil.canUseGlobalWaypoints(player);
        if (targetId != null && WaypointManager.isSharedId(targetId) && !canShared) {
            return;
        }

        switch (action) {
            case ICON_PREV:
                selectedIconIndex = (selectedIconIndex - 1 + AVAILABLE_ICONS.length) % AVAILABLE_ICONS.length;
                refreshIconAndTint(ref, store);
                break;
            case ICON_NEXT:
                selectedIconIndex = (selectedIconIndex + 1) % AVAILABLE_ICONS.length;
                refreshIconAndTint(ref, store);
                break;
            case TINT_PREV:
                selectedTintIndex = (selectedTintIndex - 1 + AVAILABLE_TINTS.length) % AVAILABLE_TINTS.length;
                refreshIconAndTint(ref, store);
                break;
            case TINT_NEXT:
                selectedTintIndex = (selectedTintIndex + 1) % AVAILABLE_TINTS.length;
                refreshIconAndTint(ref, store);
                break;
            case CANCEL:
                player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(this.playerRef));
                break;
            case BACK:
                player.getPageManager().openCustomPage(ref, store, new WaypointMenuPage(this.playerRef));
                break;
            case SAVE:
                String newName = this.nameInput.trim();
                if (newName.isEmpty()) newName = generateDefaultName(player, targetId == null);
                
                float x = 0, z = 0;
                try {
                    x = Float.parseFloat(this.inputX);
                    z = Float.parseFloat(this.inputZ);
                } catch (NumberFormatException ignored) {}

                String selectedIcon = AVAILABLE_ICONS[selectedIconIndex];
                Color selectedTint = AVAILABLE_TINTS[selectedTintIndex];
                boolean wantsShared = this.shared && canShared;

                if (targetId != null) {
                    UserMapMarker existing = WaypointManager.getMarker(player, targetId);
                    boolean wasShared = WaypointManager.isSharedId(targetId);
                    
                    if (wantsShared != wasShared && existing != null) {
                        // Sharing status changed: remove old and create new
                        WaypointManager.removeMarker(player, targetId);
                        WaypointManager.addMarker(player, newName, selectedIcon, x, z, selectedTint, wantsShared);
                    } else if (existing != null) {
                        // Update existing marker
                        WaypointManager.updateMarker(player, targetId, newName, selectedIcon, x, z, selectedTint);
                    }
                } else {
                    // Create new marker
                    WaypointManager.addMarker(player, newName, selectedIcon, x, z, selectedTint, wantsShared);
                }
                
                WaypointMenuPage menuPage = new WaypointMenuPage(this.playerRef);
                player.getPageManager().openCustomPage(ref, store, menuPage);
                break;
        }
    }

    private int getIconIndex(@Nullable String icon) {
        if (icon == null) return 0;
        for (int i = 0; i < AVAILABLE_ICONS.length; i++) {
            if (AVAILABLE_ICONS[i].equalsIgnoreCase(icon)) return i;
        }
        return 0;
    }

    private int getTintIndex(@Nullable Color tint) {
        if (tint == null) return 0;
        for (int i = 0; i < AVAILABLE_TINTS.length; i++) {
            Color t = AVAILABLE_TINTS[i];
            if (t.red == tint.red && t.green == tint.green && t.blue == tint.blue) return i;
        }
        return 0;
    }

    private void refreshIconAndTint(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        
        // Update icon label and preview
        String iconName = AVAILABLE_ICONS[selectedIconIndex];
        ui.set("#IconLabel.Text", getIconDisplayName(iconName));
        ui.set("#IconPreview.Background", "../../Common/" + iconName);
        
        // Update tint label and preview
        ui.set("#ColorLabel.Text", getTintDisplayName(selectedTintIndex));
        Color tintColor = AVAILABLE_TINTS[selectedTintIndex];
        ui.set("#ColorPreview.Background", String.format("#%02X%02X%02X", tintColor.red & 0xFF, tintColor.green & 0xFF, tintColor.blue & 0xFF));
        
        sendUpdate(ui, events, false);
    }

    private String getIconDisplayName(@Nonnull String icon) {
        return icon.replace(".png", "");
    }

    private String getTintDisplayName(int index) {
        if (index >= 0 && index < TINT_NAMES.length) {
            return TINT_NAMES[index];
        }
        return "Unknown";
    }

    private String generateDefaultName(@Nonnull Player player, boolean isNew) {
        List<UserMapMarker> markers = WaypointManager.getUserMarkers(player);
        int count = markers.size();
        int suffix = isNew ? count + 1 : Math.max(count, 1);
        return "Waypoint" + suffix;
    }

    enum Action {
        SAVE, CANCEL, BACK, ICON_PREV, ICON_NEXT, TINT_PREV, TINT_NEXT
    }

    public static class EditData {
        public static final String KEY_ACTION = "Action";
        public static final String KEY_NAME_INPUT = "@NameInput";
        public static final String KEY_INPUT_X = "@InputX";
        public static final String KEY_INPUT_Z = "@InputZ";
        public static final String KEY_GLOBAL = "@Global";
        
        public String action;
        public String nameInput;
        public String inputX;
        public String inputZ;
        public Boolean global;

        public static final BuilderCodec<EditData> CODEC = BuilderCodec.<EditData>builder(EditData.class, EditData::new)
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .addField(new KeyedCodec<>(KEY_NAME_INPUT, Codec.STRING), (data, value) -> data.nameInput = value, data -> data.nameInput)
                .addField(new KeyedCodec<>(KEY_INPUT_X, Codec.STRING), (data, value) -> data.inputX = value, data -> data.inputX)
                .addField(new KeyedCodec<>(KEY_INPUT_Z, Codec.STRING), (data, value) -> data.inputZ = value, data -> data.inputZ)
                .addField(new KeyedCodec<>(KEY_GLOBAL, Codec.BOOLEAN), (data, value) -> data.global = value, data -> data.global)
                .build();
    }
}
