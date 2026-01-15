package dev.ninesliced.exploration;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class ExplorationTicker {
    private static final Logger LOGGER = Logger.getLogger(ExplorationTicker.class.getName());
    private static ExplorationTicker INSTANCE;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean isRunning = false;

    private ExplorationTicker() {
    }

    @Nonnull
    public static ExplorationTicker getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ExplorationTicker();
        }
        return INSTANCE;
    }

    public void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        scheduler.scheduleAtFixedRate(this::tick, 1000, 100, TimeUnit.MILLISECONDS);
        LOGGER.info("Exploration Ticker started.");
    }

    public void scheduleUpdate(Runnable task) {
        if (!isRunning) {
            return;
        }
        scheduler.schedule(task, 50, TimeUnit.MILLISECONDS);
    }
    
    public void stop() {
        isRunning = false;
        scheduler.shutdown();
    }

    private void tick() {
        if (!isRunning) {
            return;
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        universe.getWorlds().values().forEach(world -> {
            try {
                world.execute(() -> updateWorldPlayers(world));
            } catch (IllegalThreadStateException its) {
                LOGGER.fine("[DEBUG] Skipping world update; world not accepting tasks: " + world.getName());
            }
        });
    }

    private void updateWorldPlayers(World world) {
        if (world != null && !world.isAlive()) {
            return;
        }

        try {
            for (PlayerRef ref : Universe.get().getPlayers()) {
                Ref<EntityStore> playerRef = ref.getReference();
                if (playerRef != null && playerRef.isValid()) {
                    try {
                        if (playerRef.getStore().getExternalData().getWorld() == world) {
                            Player player = playerRef.getStore().getComponent(playerRef, Player.getComponentType());
                            if (player != null) {
                                ExplorationTracker.PlayerExplorationData data = ExplorationTracker.getInstance().getPlayerData(player.getDisplayName());
                                if (data == null) {
                                    continue;
                                }

                                WorldMapTracker tracker = player.getWorldMapTracker();
                                if (tracker != null) {
                                    WorldMapHook.updateExplorationState(player, tracker);
                                } else {
                                    LOGGER.warning("WorldMapTracker is null for " + player.getDisplayName());
                                }
                            }
                        }
                    } catch (IllegalThreadStateException its) {
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("ExplorationTicker failed: " + e.getMessage());
        }
    }
}
