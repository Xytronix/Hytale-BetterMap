package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.BetterMapConfig;
import dev.ninesliced.exploration.WorldMapHook;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MapMaxScaleCommand extends AbstractCommand {
    private final RequiredArg<Float> zoomValueArg = (RequiredArg<Float>) this.withRequiredArg("value", "Max zoom value", (ArgumentType)ArgTypes.FLOAT);

    public MapMaxScaleCommand() {
        super("max", "Set max map zoom scale (higher = zoom in closer)");
    }

    @Override
    protected String generatePermissionNode() {
        return "command.bettermap.max";
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command must be run by a player").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        try {
            Float newMax = (Float)context.get(this.zoomValueArg);
            if (newMax.floatValue() <= 0.0f) {
                context.sendMessage(Message.raw("Max scale must be greater than 0").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            World world = this.findWorld(context);
            if (world == null) {
                context.sendMessage(Message.raw("Could not access world").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            // Update config
            BetterMapConfig config = BetterMapConfig.getInstance();
            if (newMax <= config.getMinScale()) {
                 context.sendMessage(Message.raw("Max scale must be greater than min scale (" + config.getMinScale() + ")").color(Color.RED));
                 return CompletableFuture.completedFuture(null);
            }

            config.setMaxScale(newMax);

            // Update runtime for the current world
            WorldMapHook.updateWorldMapConfigs(world);
            WorldMapHook.broadcastMapSettings(world);

            context.sendMessage(Message.raw("Map max scale set to: ").color(Color.GREEN).insert(Message.raw(String.valueOf(newMax)).color(Color.YELLOW)));

        }
        catch (Exception e) {
            context.sendMessage(Message.raw("Error: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }

    private World findWorld(CommandContext context) {
        try {
            CommandSender sender = context.sender();
            if (sender instanceof Player) {
                return ((Player)sender).getWorld();
            }
        }
        catch (Exception exception) {
        }
        return null;
    }
}
