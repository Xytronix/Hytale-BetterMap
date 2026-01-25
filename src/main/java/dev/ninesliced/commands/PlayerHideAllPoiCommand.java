package dev.ninesliced.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ninesliced.configs.BetterMapConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.PoiPrivacyManager;
import dev.ninesliced.utils.PermissionsUtil;
import dev.ninesliced.utils.WorldMapHook;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command to toggle the player's personal "hide all POIs" setting.
 * Requires override permission when POIs are globally hidden.
 */
public class PlayerHideAllPoiCommand extends AbstractCommand {

    public PlayerHideAllPoiCommand() {
        super("hideallpoi", "Toggle hiding all POIs for yourself");
        this.addAliases("hidepoi", "hidepois", "hideallpois");
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
            Player player = (Player) context.sender();
            
            boolean globalHideAll = globalConfig.isHideAllPoiOnMap();
            boolean globalHideUnexplored = globalConfig.isHideUnexploredPoiOnMap();
            boolean globalHiddenNames = globalConfig.getHiddenPoiNames() != null
                && !globalConfig.getHiddenPoiNames().isEmpty();
            boolean globalPoiFilters = globalHideAll || globalHideUnexplored || globalHiddenNames;

            boolean canOverrideGlobalPoi = PermissionsUtil.canOverridePoi(player)
                || (!globalHideAll && !globalHiddenNames && globalHideUnexplored
                    && PermissionsUtil.canOverrideUnexploredPoi(player));

            // Check if globally disabled
            if (globalPoiFilters && !canOverrideGlobalPoi) {
                context.sendMessage(Message.raw("POIs are globally hidden by the server.").color(Color.YELLOW));
                return;
            }

            UUID uuid = player.getUuid();
            World world = player.getWorld();
            PlayerConfig config = PlayerConfigManager.getInstance().getPlayerConfig(uuid);

            if (world == null || config == null) {
                context.sendMessage(Message.raw("Could not access player config.").color(Color.RED));
                return;
            }

            if (globalPoiFilters) {
                boolean newState = !config.isOverrideGlobalPoiHide();
                config.setOverrideGlobalPoiHide(newState);
                if (newState) {
                    config.setHideAllPoiOnMap(false);
                }
                PlayerConfigManager.getInstance().savePlayerConfig(uuid);
                PoiPrivacyManager.getInstance().updatePrivacyState(world);
                WorldMapHook.refreshTrackers(world);

                boolean visible = newState;
                Color color = visible ? Color.GREEN : Color.RED;
                String status = visible ? "VISIBLE" : "HIDDEN";

                context.sendMessage(Message.raw("POIs are now " + status + " for you.").color(color));
                if (visible) {
                    context.sendMessage(Message.raw("Override enabled; global hide is ignored.").color(Color.GRAY));
                } else {
                    context.sendMessage(Message.raw("Override disabled; global hide is applied.").color(Color.GRAY));
                }
                return;
            }

            boolean newState = !config.isHideAllPoiOnMap();
            config.setOverrideGlobalPoiHide(false);
            config.setHideAllPoiOnMap(newState);
            PlayerConfigManager.getInstance().savePlayerConfig(uuid);
            PoiPrivacyManager.getInstance().updatePrivacyState(world);
            WorldMapHook.refreshTrackers(world);

            boolean visible = !newState;
            Color color = visible ? Color.GREEN : Color.RED;
            String status = visible ? "VISIBLE" : "HIDDEN";

            context.sendMessage(Message.raw("POIs are now " + status + " for you.").color(color));
        });
    }
}
