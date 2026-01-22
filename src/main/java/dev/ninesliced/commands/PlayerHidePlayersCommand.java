package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the player's personal "hide players" setting.
 * Only works if players are not globally hidden.
 */
public class PlayerHidePlayersCommand extends AbstractCommand {

    public PlayerHidePlayersCommand() {
        super("hideplayers", "Toggle hiding other players on map for yourself");
    }

    @Override
    protected String generatePermissionNode() {
        return "hideplayers";
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
            
            // Check if globally disabled
            if (globalConfig.isHidePlayersOnMap()) {
                context.sendMessage(Message.raw("Players are globally hidden by the server.").color(Color.YELLOW));
                return;
            }

            UUID uuid = context.sender().getUuid();
            Player player = (Player) context.sender();
            World world = player.getWorld();
            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

            if (world == null || config == null) {
                context.sendMessage(Message.raw("Could not access player config.").color(Color.RED));
                return;
            }

            boolean newState = !config.isHidePlayersOnMap();
            config.setHidePlayersOnMap(newState);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            String status = newState ? "HIDDEN" : "VISIBLE";
            Color color = newState ? Color.RED : Color.GREEN;

            context.sendMessage(Message.raw("Other players are now " + status + " on your map.").color(color));
            context.sendMessage(Message.raw("Note: You may need to reopen the map to see changes.").color(Color.GRAY));
        });
    }
}
