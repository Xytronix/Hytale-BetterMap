package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the player's personal death marker visibility.
 * Requires override permission when death marker is globally hidden.
 * 
 * Note: Uses the game's native death storage. If the game's displayDeathMarker 
 * config is disabled, deaths won't be recorded and this toggle has no effect.
 */
public class PlayerHideDeathCommand extends AbstractCommand {

    public PlayerHideDeathCommand() {
        super("hidedeath", "Toggle hiding death marker for yourself");
    }

    @Override
    protected String generatePermissionNode() {
        return "hidedeath";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        BetterMapConfig globalConfig = BetterMapConfig.getInstance();
        Player player = (Player) context.sender();
        
        boolean globalDeathFilters = globalConfig.isHideDeathMarkerOnMap();
        if (!globalDeathFilters) {
            var hiddenNames = globalConfig.getHiddenPoiNames();
            if (hiddenNames != null) {
                for (String hidden : hiddenNames) {
                    if ("death".equalsIgnoreCase(hidden.trim())) {
                        globalDeathFilters = true;
                        break;
                    }
                }
            }
        }

        // Check if globally disabled
        if (globalDeathFilters && !PermissionsUtil.canOverrideDeath(player)) {
            context.sendMessage(Message.raw("Death marker is globally hidden by the server.").color(Color.YELLOW));
            return CompletableFuture.completedFuture(null);
        }

        UUID uuid = player.getUuid();
        World world = player.getWorld();
        PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

        if (world == null || config == null) {
            context.sendMessage(Message.raw("Could not access player config.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        final boolean finalGlobalDeathFilters = globalDeathFilters;

        // Run on world executor to ensure proper ordering
        return CompletableFuture.runAsync(() -> {
            if (finalGlobalDeathFilters) {
                // Toggle override mode
                boolean newState = !config.isOverrideGlobalDeathHide();
                config.setOverrideGlobalDeathHide(newState);
                if (newState) {
                    config.setHideDeathMarkerOnMap(false);
                }
                PlayerConfigManager.getInstance().savePlayerConfig(uuid);
                PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
                WorldMapHook.clearMarkerCaches(world);
                WorldMapHook.refreshTrackers(world);

                boolean visible = newState;
                Color color = visible ? Color.GREEN : Color.RED;
                String status = visible ? "VISIBLE" : "HIDDEN";

                context.sendMessage(Message.raw("Death markers are now " + status + " for you.").color(color));
                if (visible) {
                    context.sendMessage(Message.raw("Override enabled; global hide is ignored.").color(Color.GRAY));
                } else {
                    context.sendMessage(Message.raw("Override disabled; global hide is applied.").color(Color.GRAY));
                }
                return;
            }

            // Toggle personal hide setting
            boolean newState = !config.isHideDeathMarkerOnMap();
            config.setOverrideGlobalDeathHide(false);
            config.setHideDeathMarkerOnMap(newState);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);
            PoiPrivacyManager.getInstance().updatePrivacyStateSync(world);
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            boolean visible = !newState;
            Color color = visible ? Color.GREEN : Color.RED;
            String status = visible ? "VISIBLE" : "HIDDEN";

            context.sendMessage(Message.raw("Death markers are now " + status + " for you.").color(color));
        }, world);
    }
}
