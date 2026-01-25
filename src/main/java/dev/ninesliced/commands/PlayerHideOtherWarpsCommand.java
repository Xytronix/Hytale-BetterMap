package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.integration.ExtendedTeleportIntegration;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.WarpPrivacyManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the player's personal "hide other warps" setting.
 * Requires override permission when other warps are globally hidden.
 */
public class PlayerHideOtherWarpsCommand extends AbstractCommand {

    public PlayerHideOtherWarpsCommand() {
        super("hideotherwarps", "Toggle hiding other players' warps for yourself");
        this.addAliases("hideotherwarp", "hidewarps");
    }

    @Override
    protected String generatePermissionNode() {
        return "hideotherwarps";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        return CompletableFuture.runAsync(() -> {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
                return;
            }

            // Check if ExtendedTeleport is available - required for ownership-based filtering
            if (!ExtendedTeleportIntegration.getInstance().isAvailable()) {
                context.sendMessage(Message.raw("This feature requires ExtendedTeleport to be installed.").color(Color.YELLOW));
                context.sendMessage(Message.raw("Without it, warp ownership cannot be determined.").color(Color.GRAY));
                return;
            }

            BetterMapConfig globalConfig = BetterMapConfig.getInstance();
            Player player = (Player) context.sender();
            
            boolean globalOtherWarpsHidden = globalConfig.isHideOtherWarpsOnMap();

            // Check if globally disabled
            if (globalOtherWarpsHidden && !PermissionsUtil.canOverrideWarps(player)) {
                context.sendMessage(Message.raw("Other players' warps are globally hidden by the server.").color(Color.YELLOW));
                return;
            }

            UUID uuid = player.getUuid();
            World world = player.getWorld();
            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

            if (world == null || config == null) {
                context.sendMessage(Message.raw("Could not access player config.").color(Color.RED));
                return;
            }

            if (globalOtherWarpsHidden) {
                boolean newState = !config.isOverrideGlobalOtherWarpsHide();
                config.setOverrideGlobalOtherWarpsHide(newState);
                if (newState) {
                    config.setHideOtherWarpsOnMap(false);
                }
                PlayerConfigManager.getInstance().savePlayerConfig(uuid);
                WarpPrivacyManager.getInstance().updatePrivacyState();
                WorldMapHook.clearMarkerCaches(world);
                WorldMapHook.refreshTrackers(world);

                boolean visible = newState;
                Color color = visible ? Color.GREEN : Color.RED;
                String status = visible ? "VISIBLE" : "HIDDEN";

                context.sendMessage(Message.raw("Other players' warps are now " + status + " for you.").color(color));
                if (visible) {
                    context.sendMessage(Message.raw("Override enabled; global hide is ignored.").color(Color.GRAY));
                } else {
                    context.sendMessage(Message.raw("Override disabled; global hide is applied.").color(Color.GRAY));
                }
                return;
            }

            boolean newState = !config.isHideOtherWarpsOnMap();
            config.setOverrideGlobalOtherWarpsHide(false);
            config.setHideOtherWarpsOnMap(newState);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);
            WarpPrivacyManager.getInstance().updatePrivacyState();
            WorldMapHook.clearMarkerCaches(world);
            WorldMapHook.refreshTrackers(world);

            boolean visible = !newState;
            Color color = visible ? Color.GREEN : Color.RED;
            String status = visible ? "VISIBLE" : "HIDDEN";

            context.sendMessage(Message.raw("Other players' warps are now " + status + " for you.").color(color));
        });
    }
}
