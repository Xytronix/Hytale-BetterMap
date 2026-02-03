package dev.ninesliced.hud;

/**
 * Represents the available positions for the Location HUD on screen.
 */
public enum HudPosition {
    TOP_LEFT("top_left", "Top Left"),
    TOP_CENTER("top_center", "Top Center"),
    TOP_RIGHT("top_right", "Top Right"),
    BOTTOM_LEFT("bottom_left", "Bottom Left"),
    BOTTOM_CENTER("bottom_center", "Bottom Center"),
    BOTTOM_RIGHT("bottom_right", "Bottom Right");

    private final String id;
    private final String displayName;

    HudPosition(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets a HudPosition from its string ID.
     *
     * @param id the position ID
     * @return the HudPosition, or TOP_LEFT if not found
     */
    public static HudPosition fromId(String id) {
        if (id == null) {
            return TOP_LEFT;
        }
        String normalized = id.trim().toLowerCase();
        for (HudPosition pos : values()) {
            if (pos.id.equals(normalized)) {
                return pos;
            }
        }
        return TOP_LEFT;
    }

    /**
     * Gets a comma-separated list of all position IDs for help messages.
     */
    public static String getAllIds() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values().length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values()[i].id);
        }
        return sb.toString();
    }
}
