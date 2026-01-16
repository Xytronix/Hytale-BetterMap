package dev.ninesliced;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.commands.BetterMapCommand;
import dev.ninesliced.exploration.*;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

public class BetterMap extends JavaPlugin {

    private static final Logger LOGGER = Logger.getLogger(BetterMap.class.getName());
    private static BetterMap instance;
    private ComponentType<EntityStore, ExplorationComponent> explorationComponentType;

    public BetterMap(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static BetterMap get() {
        return instance;
    }

    public ComponentType<EntityStore, ExplorationComponent> getExplorationComponentType() {
        return explorationComponentType;
    }

    @Override
    protected void setup() {
        instance = this;
        LOGGER.info("========================================");
        LOGGER.info("Setting up Persistent Map Exploration Mod");
        LOGGER.info("========================================");

        try {
            this.explorationComponentType = this.getEntityStoreRegistry()
                    .registerComponent(ExplorationComponent.class, "ExplorationData", ExplorationComponent.CODEC);
            LOGGER.info("Exploration Component: REGISTERED");

            this.getEntityStoreRegistry().registerSystem(new ExplorationPlayerSetupSystem());
            LOGGER.info("Exploration Setup System: REGISTERED");

            java.nio.file.Path serverRoot = java.nio.file.Paths.get(".").toAbsolutePath().normalize();
            BetterMapConfig.getInstance().initialize(serverRoot);

            ExplorationManager.config()
                    .updateRate(0.5f)
                    .enablePersistence("exploration_data")
                    .build();

            LOGGER.info("Exploration Manager: INITIALIZED");

            ExplorationTicker.getInstance().start();
            LOGGER.info("Exploration Ticker: STARTED");

            this.getCommandRegistry().registerCommand(new BetterMapCommand());
            LOGGER.info("Example Command: REGISTERED");

            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ExplorationEventListener::onPlayerReady);
            this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, ExplorationEventListener::onPlayerQuit);

            this.getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent.class, ExplorationEventListener::onPlayerJoinWorld);

            this.getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent.class, ExplorationEventListener::onPlayerLeaveWorld);
            LOGGER.info("Exploration Events: REGISTERED");

            LOGGER.info("========================================");
            LOGGER.info("Plugin Setup Complete!");
            LOGGER.info("Players will now have persistent");
            LOGGER.info("exploration tracking on the world map");
            LOGGER.info("========================================");

        } catch (Exception e) {
            LOGGER.severe("Failed to setup Exploration Plugin: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Plugin initialization failed", e);
        }
    }
}
