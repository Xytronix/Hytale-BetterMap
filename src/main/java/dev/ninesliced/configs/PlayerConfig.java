package dev.ninesliced.configs;

import java.util.UUID;

/**
 * Configuration specific to a single player.
 */
public class PlayerConfig {
    private transient UUID playerUuid;
    private float minScale;
    private float maxScale;
    private boolean locationEnabled;
    private boolean caveModeEnabled;
    private boolean discoverSurfaceUnderground;

    public PlayerConfig(UUID playerUuid, float minScale, float maxScale, boolean locationEnabled) {
        this.playerUuid = playerUuid;
        this.minScale = minScale;
        this.maxScale = maxScale;
        this.locationEnabled = locationEnabled;
        this.caveModeEnabled = true;
        this.discoverSurfaceUnderground = false;
    }

    public float getMinScale() {
        return minScale;
    }

    public void setMinScale(float minScale) {
        this.minScale = Math.max(minScale, 2);
    }

    public float getMaxScale() {
        return maxScale;
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    public boolean isLocationEnabled() {
        return locationEnabled;
    }

    public void setLocationEnabled(boolean locationEnabled) {
        this.locationEnabled = locationEnabled;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    /**
     * Gets the player's cave mode preference.
     * Note: This only reflects the player's preference. Use isCaveModeEffectivelyEnabled()
     * to check if cave mode is actually active (respects server config).
     */
    public boolean isCaveModeEnabled() {
        return caveModeEnabled;
    }

    /**
     * Sets the player's cave mode preference.
     */
    public void setCaveModeEnabled(boolean caveModeEnabled) {
        this.caveModeEnabled = caveModeEnabled;
    }

    /**
     * Gets whether surface exploration is enabled while underground.
     * When enabled, walking underground will also discover surface map chunks.
     */
    public boolean isDiscoverSurfaceUnderground() {
        return discoverSurfaceUnderground;
    }

    /**
     * Sets whether surface exploration is enabled while underground.
     */
    public void setDiscoverSurfaceUnderground(boolean discoverSurfaceUnderground) {
        this.discoverSurfaceUnderground = discoverSurfaceUnderground;
    }

    /**
     * Checks if cave mode is effectively enabled for this player.
     * Returns true only if BOTH the server config AND the player config have it enabled.
     * If the server disables cave mode globally, the player cannot enable it.
     */
    public boolean isCaveModeEffectivelyEnabled() {
        if (!ModConfig.getInstance().isCaveModeEnabled()) {
            return false;
        }
        return this.caveModeEnabled;
    }
}
