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
 * Command to toggle the player's personal spawn marker visibility.
 * Requires override permission when spawn is globally hidden.
 */
public class PlayerHideSpawnCommand extends AbstractCommand {

    public PlayerHideSpawnCommand() {
        super("hidespawn", "Toggle hiding spawn marker for yourself");
    }

    @Override
    protected String generatePermissionNode() {
        return "hidespawn";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        return CompletableFuture.runAsync(() -> {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
                return;
            }

            BetterMapConfig globalConfig = BetterMapConfig.getInstance();
            Player player = (Player) context.sender();
            
            boolean globalSpawnFilters = globalConfig.isHideSpawnOnMap();
            if (!globalSpawnFilters) {
                var hiddenNames = globalConfig.getHiddenPoiNames();
                if (hiddenNames != null) {
                    for (String hidden : hiddenNames) {
                        if ("spawn".equalsIgnoreCase(hidden.trim())) {
                            globalSpawnFilters = true;
                            break;
                        }
                    }
                }
            }

            // Check if globally disabled
            if (globalSpawnFilters && !PermissionsUtil.canOverrideSpawn(player)) {
                context.sendMessage(Message.raw("Spawn marker is globally hidden by the server.").color(Color.YELLOW));
                return;
            }

            UUID uuid = player.getUuid();
            World world = player.getWorld();
            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

            if (world == null || config == null) {
                context.sendMessage(Message.raw("Could not access player config.").color(Color.RED));
                return;
            }

            if (globalSpawnFilters) {
                boolean newState = !config.isOverrideGlobalSpawnHide();
                config.setOverrideGlobalSpawnHide(newState);
                if (newState) {
                    config.setHideSpawnOnMap(false);
                }
                PlayerConfigManager.getInstance().savePlayerConfig(uuid);
                PoiPrivacyManager.getInstance().updatePrivacyState(world);
                WorldMapHook.refreshTrackers(world);

                boolean visible = newState;
                Color color = visible ? Color.GREEN : Color.RED;
                String status = visible ? "VISIBLE" : "HIDDEN";

                context.sendMessage(Message.raw("Spawn markers are now " + status + " for you.").color(color));
                if (visible) {
                    context.sendMessage(Message.raw("Override enabled; global hide is ignored.").color(Color.GRAY));
                } else {
                    context.sendMessage(Message.raw("Override disabled; global hide is applied.").color(Color.GRAY));
                }
                return;
            }

            boolean newState = !config.isHideSpawnOnMap();
            config.setOverrideGlobalSpawnHide(false);
            config.setHideSpawnOnMap(newState);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);
            PoiPrivacyManager.getInstance().updatePrivacyState(world);
            WorldMapHook.refreshTrackers(world);

            boolean visible = !newState;
            Color color = visible ? Color.GREEN : Color.RED;
            String status = visible ? "VISIBLE" : "HIDDEN";

            context.sendMessage(Message.raw("Spawn markers are now " + status + " for you.").color(color));
        });
    }
}
