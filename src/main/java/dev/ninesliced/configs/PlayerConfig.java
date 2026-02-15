package dev.ninesliced.configs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerConfig {
    private transient UUID playerUuid;
    private float minScale;
    private float maxScale;
    private boolean locationEnabled;
    private String locationHudPosition;
    private boolean caveModeEnabled;

    private boolean hideAllPoiOnMap = false;
    private boolean hideSpawnOnMap = false;
    private boolean hideDeathMarkerOnMap = false;
    private List<String> hiddenPoiNames = new ArrayList<>();
    private boolean hidePlayersOnMap = false;
    private boolean hideAllWarpsOnMap = false;
    private boolean hideOtherWarpsOnMap = false;
    private boolean overrideGlobalPoiHide = false;
    private boolean overrideGlobalSpawnHide = false;
    private boolean overrideGlobalDeathHide = false;
    private boolean overrideGlobalPlayersHide = false;
    private boolean overrideGlobalAllWarpsHide = false;
    private boolean overrideGlobalOtherWarpsHide = false;
    private boolean hideGlobalWaypointsOnMap = false;
    private boolean hidePersonalWaypointsOnMap = false;
    private boolean overrideGlobalWaypointHide = false;

    public PlayerConfig(UUID playerUuid, float minScale, float maxScale, boolean locationEnabled) {
        this.playerUuid = playerUuid;
        this.minScale = minScale;
        this.maxScale = maxScale;
        this.locationEnabled = locationEnabled;
        this.locationHudPosition = null;
        this.caveModeEnabled = true;
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
        this.maxScale = Math.max(maxScale, minScale + 1);
    }

    public boolean isLocationEnabled() {
        return locationEnabled;
    }

    public void setLocationEnabled(boolean locationEnabled) {
        this.locationEnabled = locationEnabled;
    }

    /**
     * Gets the player's preferred location HUD position.
     * May be null if player uses server default.
     */
    public String getLocationHudPosition() {
        return locationHudPosition;
    }

    /**
     * Sets the player's preferred location HUD position.
     * Set to null to use server default.
     */
    public void setLocationHudPosition(String locationHudPosition) {
        this.locationHudPosition = locationHudPosition;
    }

    /**
     * Gets the effective location HUD position for this player.
     * Returns player's preference if set, otherwise server default.
     */
    public String getEffectiveLocationHudPosition() {
        if (this.locationHudPosition != null && !this.locationHudPosition.isEmpty()) {
            return this.locationHudPosition;
        }
        return ModConfig.getInstance().getLocationHudPosition();
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
     * Resets this player's config to default values.
     */
    public void resetToDefaults() {
        ModConfig mainConfig = ModConfig.getInstance();
        this.minScale = mainConfig.getMinScale();
        this.maxScale = mainConfig.getMaxScale();
        this.locationEnabled = false;
        this.locationHudPosition = null;
        this.caveModeEnabled = true;
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

    /**
     * Checks if the location HUD is effectively enabled for this player.
     * Returns true only if BOTH the server config AND the player config have it enabled.
     * If the server disables location HUD globally, the player cannot enable it.
     */
    public boolean isLocationEffectivelyEnabled() {
        if (!ModConfig.getInstance().isLocationEnabled()) {
            return false;
        }
        return this.locationEnabled;
    }

    public boolean isHideAllPoiOnMap() {
        return hideAllPoiOnMap;
    }

    public void setHideAllPoiOnMap(boolean hideAllPoiOnMap) {
        this.hideAllPoiOnMap = hideAllPoiOnMap;
    }

    public boolean isHideSpawnOnMap() {
        return hideSpawnOnMap;
    }

    public void setHideSpawnOnMap(boolean hideSpawnOnMap) {
        this.hideSpawnOnMap = hideSpawnOnMap;
    }

    public boolean isHideDeathMarkerOnMap() {
        return hideDeathMarkerOnMap;
    }

    public void setHideDeathMarkerOnMap(boolean hideDeathMarkerOnMap) {
        this.hideDeathMarkerOnMap = hideDeathMarkerOnMap;
    }

    public List<String> getHiddenPoiNames() {
        if (hiddenPoiNames == null) {
            hiddenPoiNames = new ArrayList<>();
        }
        return hiddenPoiNames;
    }

    public void setHiddenPoiNames(List<String> hiddenPoiNames) {
        this.hiddenPoiNames = hiddenPoiNames != null ? hiddenPoiNames : new ArrayList<>();
    }

    public boolean isHidePlayersOnMap() {
        return hidePlayersOnMap;
    }

    public void setHidePlayersOnMap(boolean hidePlayersOnMap) {
        this.hidePlayersOnMap = hidePlayersOnMap;
    }

    public boolean isHideAllWarpsOnMap() {
        return hideAllWarpsOnMap;
    }

    public void setHideAllWarpsOnMap(boolean hideAllWarpsOnMap) {
        this.hideAllWarpsOnMap = hideAllWarpsOnMap;
    }

    public boolean isHideOtherWarpsOnMap() {
        return hideOtherWarpsOnMap;
    }

    public void setHideOtherWarpsOnMap(boolean hideOtherWarpsOnMap) {
        this.hideOtherWarpsOnMap = hideOtherWarpsOnMap;
    }

    public boolean isOverrideGlobalPoiHide() {
        return overrideGlobalPoiHide;
    }

    public void setOverrideGlobalPoiHide(boolean overrideGlobalPoiHide) {
        this.overrideGlobalPoiHide = overrideGlobalPoiHide;
    }

    public boolean isOverrideGlobalSpawnHide() {
        return overrideGlobalSpawnHide;
    }

    public void setOverrideGlobalSpawnHide(boolean overrideGlobalSpawnHide) {
        this.overrideGlobalSpawnHide = overrideGlobalSpawnHide;
    }

    public boolean isOverrideGlobalDeathHide() {
        return overrideGlobalDeathHide;
    }

    public void setOverrideGlobalDeathHide(boolean overrideGlobalDeathHide) {
        this.overrideGlobalDeathHide = overrideGlobalDeathHide;
    }

    public boolean isOverrideGlobalPlayersHide() {
        return overrideGlobalPlayersHide;
    }

    public void setOverrideGlobalPlayersHide(boolean overrideGlobalPlayersHide) {
        this.overrideGlobalPlayersHide = overrideGlobalPlayersHide;
    }

    public boolean isOverrideGlobalAllWarpsHide() {
        return overrideGlobalAllWarpsHide;
    }

    public void setOverrideGlobalAllWarpsHide(boolean overrideGlobalAllWarpsHide) {
        this.overrideGlobalAllWarpsHide = overrideGlobalAllWarpsHide;
    }

    public boolean isOverrideGlobalOtherWarpsHide() {
        return overrideGlobalOtherWarpsHide;
    }

    public void setOverrideGlobalOtherWarpsHide(boolean overrideGlobalOtherWarpsHide) {
        this.overrideGlobalOtherWarpsHide = overrideGlobalOtherWarpsHide;
    }

    public boolean isHideGlobalWaypointsOnMap() {
        return hideGlobalWaypointsOnMap;
    }

    public void setHideGlobalWaypointsOnMap(boolean hideGlobalWaypointsOnMap) {
        this.hideGlobalWaypointsOnMap = hideGlobalWaypointsOnMap;
    }

    public boolean isHidePersonalWaypointsOnMap() {
        return hidePersonalWaypointsOnMap;
    }

    public void setHidePersonalWaypointsOnMap(boolean hidePersonalWaypointsOnMap) {
        this.hidePersonalWaypointsOnMap = hidePersonalWaypointsOnMap;
    }

    public boolean isOverrideGlobalWaypointHide() {
        return overrideGlobalWaypointHide;
    }

    public void setOverrideGlobalWaypointHide(boolean overrideGlobalWaypointHide) {
        this.overrideGlobalWaypointHide = overrideGlobalWaypointHide;
    }
}
