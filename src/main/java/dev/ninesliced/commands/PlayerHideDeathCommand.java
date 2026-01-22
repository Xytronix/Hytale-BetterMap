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
 * Command to toggle the player's personal death marker visibility.
 * Only works if death marker is not globally hidden.
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
        return CompletableFuture.runAsync(() -> {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
                return;
            }

            BetterMapConfig globalConfig = BetterMapConfig.getInstance();
            
            // Check if globally disabled
            if (globalConfig.isHideDeathMarkerOnMap()) {
                context.sendMessage(Message.raw("Death marker is globally hidden by the server.").color(Color.YELLOW));
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

            boolean newState = !config.isHideDeathMarkerOnMap();
            config.setHideDeathMarkerOnMap(newState);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            String status = newState ? "HIDDEN" : "VISIBLE";
            Color color = newState ? Color.RED : Color.GREEN;

            context.sendMessage(Message.raw("Death marker is now " + status + " for you.").color(color));
        });
    }
}
