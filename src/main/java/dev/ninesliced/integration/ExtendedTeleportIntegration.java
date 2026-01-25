package dev.ninesliced.integration;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Optional integration with ExtendedTeleport mod to check teleporter ownership.
 */
public class ExtendedTeleportIntegration {
    private static final Logger LOGGER = Logger.getLogger(ExtendedTeleportIntegration.class.getName());
    private static ExtendedTeleportIntegration instance;
    
    private boolean available = false;
    private Object teleporterManager;
    private Method getTeleporterByWarpNameMethod;
    private Method isOwnerMethod;
    private Method getOwnerUuidMethod;

    private ExtendedTeleportIntegration() {
        tryInitialize();
    }

    public static synchronized ExtendedTeleportIntegration getInstance() {
        if (instance == null) {
            instance = new ExtendedTeleportIntegration();
        }
        return instance;
    }

    private void tryInitialize() {
        try {
            // Try to load the ExtendedTeleport Main class
            Class<?> mainClass = Class.forName("com.hytale.extendedteleport.Main");
            
            // Get the TeleporterManager instance
            Method getManagerMethod = mainClass.getMethod("getTeleporterManager");
            teleporterManager = getManagerMethod.invoke(null);
            
            if (teleporterManager == null) {
                LOGGER.info("ExtendedTeleport: TeleporterManager not yet initialized");
                return;
            }

            Class<?> managerClass = teleporterManager.getClass();
            
            // Get getTeleporterByWarpName method
            getTeleporterByWarpNameMethod = managerClass.getMethod("getTeleporterByWarpName", String.class);
            
            // Get isOwner method (UUID, TeleporterInfo)
            Class<?> teleporterInfoClass = Class.forName("com.hytale.extendedteleport.data.TeleporterInfo");
            isOwnerMethod = managerClass.getMethod("isOwner", UUID.class, teleporterInfoClass);
            
            // Get getOwnerUuid method from TeleporterInfo
            getOwnerUuidMethod = teleporterInfoClass.getMethod("getOwnerUuid");

            available = true;
            LOGGER.info("ExtendedTeleport integration initialized successfully");
        } catch (ClassNotFoundException e) {
            LOGGER.fine("ExtendedTeleport mod not found - integration disabled");
        } catch (NoSuchMethodException e) {
            LOGGER.warning("ExtendedTeleport API changed - integration disabled: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.warning("Failed to initialize ExtendedTeleport integration: " + e.getMessage());
        }
    }

    /**
     * Checks if ExtendedTeleport integration is available.
     */
    public boolean isAvailable() {
        // Retry initialization if manager wasn't ready before
        if (!available && teleporterManager == null) {
            tryInitialize();
        }
        return available;
    }

    /**
     * Checks if the given player owns the teleporter associated with the warp name.
     *
     * @param playerUuid The player's UUID
     * @param warpName The warp name (teleporter name)
     * @return true if the player owns this teleporter, false otherwise or if not available
     */
    public boolean isPlayerTeleporterOwner(UUID playerUuid, String warpName) {
        if (!isAvailable() || playerUuid == null || warpName == null) {
            return false;
        }

        try {
            // Get the TeleporterInfo by warp name
            Object teleporterInfo = getTeleporterByWarpNameMethod.invoke(teleporterManager, warpName);
            if (teleporterInfo == null) {
                return false;
            }

            // Check if the player owns this teleporter
            Boolean isOwner = (Boolean) isOwnerMethod.invoke(teleporterManager, playerUuid, teleporterInfo);
            return isOwner != null && isOwner;
        } catch (Exception e) {
            LOGGER.fine("Error checking teleporter ownership: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the owner UUID of a teleporter by warp name.
     *
     * @param warpName The warp name (teleporter name)
     * @return The owner UUID, or null if not found or not available
     */
    public UUID getTeleporterOwner(String warpName) {
        if (!isAvailable() || warpName == null) {
            return null;
        }

        try {
            Object teleporterInfo = getTeleporterByWarpNameMethod.invoke(teleporterManager, warpName);
            if (teleporterInfo == null) {
                return null;
            }

            return (UUID) getOwnerUuidMethod.invoke(teleporterInfo);
        } catch (Exception e) {
            LOGGER.fine("Error getting teleporter owner: " + e.getMessage());
            return null;
        }
    }
}
