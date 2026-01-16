package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ninesliced.configs.BetterMapConfig;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.awt.*;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class MapQualityCommand extends AbstractCommand {
    private final RequiredArg<String> qualityValueArg = this.withRequiredArg("value", "Quality map value", ArgTypes.STRING);

    protected MapQualityCommand() {
        super("quality", "Set the map quality (low, medium, high)");
    }

    @NullableDecl
    @Override
    protected String generatePermissionNode() {
        return "command.bettermap.quality";
    }

    @NullableDecl
    @Override
    protected CompletableFuture<Void> execute(@NonNullDecl CommandContext context) {
        String quality = context.get(this.qualityValueArg);
        BetterMapConfig.MapQuality mapQuality;

        try {
            mapQuality = BetterMapConfig.MapQuality.valueOf(quality.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            context.sendMessage(Message.raw("Invalid quality value: " + quality).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        BetterMapConfig.getInstance().setQuality(mapQuality);
        context.sendMessage(Message.raw("Map quality set to: " + mapQuality.name()).color(Color.GREEN));
        context.sendMessage(Message.raw("WARNING: Map Quality change pending restart (Active: " + BetterMapConfig.getInstance().getActiveMapQuality().name() + ")").color(Color.RED));
        return CompletableFuture.completedFuture(null);
    }
}
