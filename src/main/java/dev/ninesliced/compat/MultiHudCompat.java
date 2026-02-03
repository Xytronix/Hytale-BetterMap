package dev.ninesliced.compat;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Compatibility layer for the MultipleHUD mod (Buuz135:MultipleHUD).
 * <p>
 * Uses reflection to interact with MultipleHUD when present, allowing BetterMap
 * to work alongside other mods that use custom HUDs. Falls back to vanilla
 * Hytale HUD system when MultipleHUD is not available.
 * </p>
 *
 * @author NineSliced
 * @see <a href="https://github.com/Buuz135/MultipleHUD">MultipleHUD</a>
 */
public final class MultiHudCompat {

    private static final Logger LOGGER = Logger.getLogger(MultiHudCompat.class.getName());
    private static final String MHUD_CLASS = "com.buuz135.mhud.MultipleHUD";
    private static final Object LOCK = new Object();

    private static volatile boolean checked;
    private static volatile boolean available;
    private static volatile Method getInstanceMethod;
    private static volatile Method setCustomHudMethod;
    private static volatile Method hideCustomHudMethod;

    private MultiHudCompat() {
    }

    /**
     * Checks if the MultipleHUD mod is available on the server.
     * Performs lazy initialization on first call.
     *
     * @return true if MultipleHUD is available
     */
    public static boolean isAvailable() {
        if (!checked) {
            synchronized (LOCK) {
                if (!checked) {
                    init();
                    checked = true;
                }
            }
        }
        return available;
    }

    /**
     * Registers or updates a custom HUD for a player via MultipleHUD.
     * Must be called every tick to properly update the HUD.
     *
     * @param player    player entity
     * @param playerRef player reference
     * @param hudId     unique identifier for this HUD
     * @param hud       the custom HUD instance
     * @return true if successful
     */
    public static boolean setCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                       @Nonnull String hudId, @Nonnull CustomUIHud hud) {
        if (!isAvailable() || setCustomHudMethod == null) {
            return false;
        }
        try {
            Object instance = getInstanceMethod.invoke(null);
            if (instance == null) {
                return false;
            }
            setCustomHudMethod.invoke(instance, player, playerRef, hudId, hud);
            return true;
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to set custom HUD via MultipleHUD", e);
            return false;
        }
    }

    /**
     * Removes a custom HUD for a player via MultipleHUD.
     *
     * @param player    player entity
     * @param playerRef player reference
     * @param hudId     unique identifier of the HUD to remove
     * @return true if successful
     */
    public static boolean hideCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                        @Nonnull String hudId) {
        if (!isAvailable() || hideCustomHudMethod == null) {
            return false;
        }
        try {
            Object instance = getInstanceMethod.invoke(null);
            if (instance == null) {
                return false;
            }
            hideCustomHudMethod.invoke(instance, player, playerRef, hudId);
            return true;
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to hide custom HUD via MultipleHUD", e);
            return false;
        }
    }

    /**
     * Removes a custom HUD using only player and HUD ID.
     *
     * @param player player entity
     * @param hudId  unique identifier of the HUD to remove
     * @return true if successful
     */
    public static boolean hideCustomHud(@Nonnull Player player, @Nonnull String hudId) {
        if (!isAvailable()) {
            return false;
        }
        try {
            Object instance = getInstanceMethod.invoke(null);
            if (instance == null) {
                return false;
            }
            Method hideMethod = instance.getClass().getMethod("hideCustomHud", Player.class, String.class);
            hideMethod.invoke(instance, player, hudId);
            return true;
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to hide custom HUD via MultipleHUD", e);
            return false;
        }
    }

    private static void init() {
        try {
            Class<?> clazz = loadMultiHudClass();
            if (clazz == null) {
                throw new ClassNotFoundException(MHUD_CLASS);
            }

            getInstanceMethod = clazz.getMethod("getInstance");
            setCustomHudMethod = clazz.getMethod("setCustomHud",
                    Player.class, PlayerRef.class, String.class, CustomUIHud.class);
            hideCustomHudMethod = clazz.getMethod("hideCustomHud",
                    Player.class, PlayerRef.class, String.class);

            available = true;
            LOGGER.info("MultipleHUD compatibility initialized");
        } catch (Throwable e) {
            available = false;
            getInstanceMethod = null;
            setCustomHudMethod = null;
            hideCustomHudMethod = null;
            LOGGER.info("MultipleHUD not available, using vanilla HUD system");
        }
    }

    private static Class<?> loadMultiHudClass() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                return Class.forName(MHUD_CLASS, true, cl);
            }
        } catch (Throwable ignored) {
        }

        try {
            PluginManager pm = PluginManager.get();
            if (pm != null && pm.getBridgeClassLoader() != null) {
                return pm.getBridgeClassLoader().loadClass(MHUD_CLASS);
            }
        } catch (Throwable ignored) {
        }

        try {
            return Class.forName(MHUD_CLASS);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
