package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.ninesliced.BetterMapConfig;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.hypixel.hytale.server.core.universe.Universe;
import dev.ninesliced.exploration.WorldMapHook;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

public class ReloadCommand extends AbstractCommand {
    protected ReloadCommand() {
        super("reload", "Reload BetterMap configuration");
    }

    @Override
    protected String generatePermissionNode() {
        return "command.bettermap.reload";
    }

    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext context) {
        BetterMapConfig.getInstance().reload();

        // Apply changes to all loaded worlds
        Universe universe = Universe.get();
        if (universe != null) {
            universe.getWorlds().values().forEach(world -> {
                try {
                    WorldMapHook.updateWorldMapConfigs(world);
                    WorldMapHook.broadcastMapSettings(world);
                } catch (Exception e) {
                    // Ignore errors for specific worlds
                }
            });
        }

        context.sendMessage(Message.raw("BetterMap configuration reloaded!").color(Color.GREEN));
        context.sendMessage(Message.raw("Exploration Radius: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(BetterMapConfig.getInstance().getExplorationRadius())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Min Scale: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(BetterMapConfig.getInstance().getMinScale())).color(Color.WHITE)));
        context.sendMessage(Message.raw("Max Scale: ").color(Color.YELLOW).insert(Message.raw(String.valueOf(BetterMapConfig.getInstance().getMaxScale())).color(Color.WHITE)));

        BetterMapConfig config = BetterMapConfig.getInstance();
        context.sendMessage(Message.raw("Map Quality: ").color(Color.YELLOW).insert(Message.raw(config.getMapQuality().name()).color(Color.WHITE)));
        
        if (config.getMapQuality() != config.getActiveMapQuality()) {
            context.sendMessage(Message.raw("WARNING: Map Quality change pending restart (Active: " + config.getActiveMapQuality().name() + ")").color(Color.RED));
        }

        context.sendMessage(Message.raw("NOTE: The server must be restarted for map quality changes to take effect."));

        return CompletableFuture.completedFuture(null);
    }
}
