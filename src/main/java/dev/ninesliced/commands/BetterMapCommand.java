package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.ninesliced.BetterMapConfig;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class BetterMapCommand extends AbstractCommand {
    private static final Logger LOGGER = Logger.getLogger(BetterMapCommand.class.getName());

    public BetterMapCommand() {
        super("bettermap", "Manage BetterMap plugin");
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        BetterMapConfig.getInstance().reload();
        context.sendMessage(Message.raw("BetterMap configuration reloaded!"));
        context.sendMessage(Message.raw("Exploration Radius: " + BetterMapConfig.getInstance().getExplorationRadius()));

        return CompletableFuture.completedFuture(null);
    }
}
