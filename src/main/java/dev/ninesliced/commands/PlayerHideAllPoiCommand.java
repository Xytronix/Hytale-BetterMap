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
 * Command to toggle the player's personal "hide all POIs" setting.
 * Only works if POIs are not globally hidden.
 */
public class PlayerHideAllPoiCommand extends AbstractCommand {

    public PlayerHideAllPoiCommand() {
        super("hideallpoi", "Toggle hiding all POIs for yourself");
    }

    @Override
    protected String generatePermissionNode() {
        return "hideallpoi";
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
            if (globalConfig.isHideAllPoiOnMap()) {
                context.sendMessage(Message.raw("All POIs are globally hidden by the server.").color(Color.YELLOW));
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

            boolean newState = !config.isHideAllPoiOnMap();
            config.setHideAllPoiOnMap(newState);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            String status = newState ? "HIDDEN" : "VISIBLE";
            Color color = newState ? Color.RED : Color.GREEN;

            context.sendMessage(Message.raw("All POIs are now " + status + " for you.").color(color));
        });
    }
}
