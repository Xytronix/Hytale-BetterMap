package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Command to toggle whether the player discovers surface areas while underground.
 * Note: This only works if the server has cave mode enabled globally.
 */
public class PlayerDiscoverSurfaceCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(PlayerDiscoverSurfaceCommand.class.getName());

    public PlayerDiscoverSurfaceCommand() {
        super("discoversurface", "Toggle discovering surface areas while underground");
    }

    @Override
    protected String generatePermissionNode() {
        return "discoversurface";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (!ModConfig.getInstance().isCaveModeEnabled()) {
                context.sendMessage(Message.raw("Cave mode is disabled by the server. This option requires cave mode.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            Player player = (Player) context.sender();
            UUID uuid = ((CommandSender) player).getUuid();
            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

            if (config == null) {
                context.sendMessage(Message.raw("Could not load player config.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            boolean newValue = !config.isDiscoverSurfaceUnderground();
            config.setDiscoverSurfaceUnderground(newValue);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);

            if (newValue) {
                context.sendMessage(Message.raw("Surface discovery while underground enabled.").color(Color.GREEN));
            } else {
                context.sendMessage(Message.raw("Surface discovery while underground disabled.").color(Color.YELLOW));
            }

        } catch (Exception e) {
            context.sendMessage(Message.raw("Error toggling surface discovery: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(null);
    }
}
