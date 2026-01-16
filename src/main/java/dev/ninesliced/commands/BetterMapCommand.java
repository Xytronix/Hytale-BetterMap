package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.ninesliced.BetterMapConfig;

import java.awt.*;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class BetterMapCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(BetterMapCommand.class.getName());

    public BetterMapCommand() {
        super("bettermap", "Manage BetterMap plugin");
        this.addAliases("bm", "map");

        this.addSubCommand(new MapMinScaleCommand());
        this.addSubCommand(new MapMaxScaleCommand());
        this.addSubCommand(new ReloadCommand());
    }

    @Override
    protected String generatePermissionNode() {
        return "command.bettermap";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        BetterMapConfig config = BetterMapConfig.getInstance();

        context.sendMessage(Message.raw("=== BetterMap Settings ===").color(Color.ORANGE));
        context.sendMessage(Message.raw("Exploration Radius: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(config.getExplorationRadius())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Min Scale: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(config.getMinScale())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Max Scale: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(config.getMaxScale())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Map Quality: ").color(Color.YELLOW).insert(Message.raw(config.getMapQuality().name()).color(Color.WHITE)));
        context.sendMessage(Message.raw("NOTE: Players must rejoin the server for map quality changes to take effect."));

        return CompletableFuture.completedFuture(null);
    }
}
