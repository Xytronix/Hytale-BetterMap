package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.utils.PermissionsUtil;

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

            BetterMapConfig globalConfig = BetterMapConfig.getInstance();
            Player player = (Player) context.sender();
            
            // Check if globally disabled
            if (globalConfig.isHideOtherWarpsOnMap() && !PermissionsUtil.canOverrideWarps(player)) {
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

            boolean newState = !config.isHideOtherWarpsOnMap();
            config.setHideOtherWarpsOnMap(newState);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            String status = newState ? "HIDDEN" : "VISIBLE";
            Color color = newState ? Color.RED : Color.GREEN;

            context.sendMessage(Message.raw("Other players' warps are now " + status + " for you.").color(color));
        });
    }
}
