package dev.ninesliced.utils;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.iterator.CircleSpiralIterator;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMapSettings;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.configs.ModConfig;
import dev.ninesliced.configs.PlayerConfig;
import dev.ninesliced.exploration.ExplorationTracker;
import dev.ninesliced.managers.CaveModeManager;
import dev.ninesliced.managers.ExplorationManager;
import dev.ninesliced.managers.MapExpansionManager;
import dev.ninesliced.managers.PlayerConfigManager;
import dev.ninesliced.managers.WorldBorderManager;
import dev.ninesliced.providers.CaveModeImageBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Hooks into the Hytale WorldMap system to provide custom exploration behavior.
 * 
 * DYNAMIC CAVE MODE SYSTEM:
 * - Automatically detects when player goes underground (below Y=100 with ceiling)
 * - Shows cave view in a radius (default 8 chunks) around the player
 * - Y-levels are divided into layers (0-10, 10-20, 20-30, etc.)
 * - Normal surface map is always shown for explored areas
 * - Cave overlay appears seamlessly when underground
 * - Previously explored cave chunks persist per layer
 * - When returning to surface, cave overlay disappears automatically
 */
public class WorldMapHook {
    private static final Logger LOGGER = Logger.getLogger(WorldMapHook.class.getName());
    
    private static final Map<String, Set<Long>> caveModeLoadedChunks = new java.util.concurrent.ConcurrentHashMap<>();
        
    private static final Map<String, Set<Long>> caveModeFailedChunks = new java.util.concurrent.ConcurrentHashMap<>();
    
    private static final Map<String, Set<Long>> caveModeTargetChunks = new java.util.concurrent.ConcurrentHashMap<>();
    
    private static final Map<String, Set<Long>> caveModePendingChunks = new java.util.concurrent.ConcurrentHashMap<>();
    
    private static final Map<String, CompletableFuture<CaveModeImageBuilder>> pendingCaveModeFutures = new java.util.concurrent.ConcurrentHashMap<>();
    
    private static final Map<String, Integer> caveModeRetryCounter = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Gets or creates the cave mode loaded chunks set for a player.
     */
    private static Set<Long> getCaveModeLoadedChunks(String playerName) {
        return caveModeLoadedChunks.computeIfAbsent(playerName, k -> java.util.Collections.synchronizedSet(new HashSet<>()));
    }
    
    /**
     * Gets or creates the cave mode failed chunks set for a player (for retry).
     */
    private static Set<Long> getCaveModeFailedChunks(String playerName) {
        return caveModeFailedChunks.computeIfAbsent(playerName, k -> java.util.Collections.synchronizedSet(new HashSet<>()));
    }
    
    /**
     * Gets or creates the cave mode target chunks set for a player.
     */
    private static Set<Long> getCaveModeTargetChunks(String playerName) {
        return caveModeTargetChunks.computeIfAbsent(playerName, k -> java.util.Collections.synchronizedSet(new HashSet<>()));
    }
    
    /**
     * Gets or creates the cave mode pending chunks set for a player.
     */
    private static Set<Long> getCaveModePendingChunks(String playerName) {
        return caveModePendingChunks.computeIfAbsent(playerName, k -> java.util.Collections.synchronizedSet(new HashSet<>()));
    }
    
    /**
     * Clears the cave mode loaded chunks for a player.
     */
    public static void clearCaveModeLoadedChunks(String playerName) {
        Set<Long> chunks = caveModeLoadedChunks.get(playerName);
        if (chunks != null) {
            chunks.clear();
        }
        Set<Long> failed = caveModeFailedChunks.get(playerName);
        if (failed != null) {
            failed.clear();
        }
        Set<Long> targets = caveModeTargetChunks.get(playerName);
        if (targets != null) {
            targets.clear();
        }
        Set<Long> pending = caveModePendingChunks.get(playerName);
        if (pending != null) {
            for (Long idx : pending) {
                pendingCaveModeFutures.remove(playerName + "_" + idx);
            }
            pending.clear();
        }
        caveModeRetryCounter.remove(playerName);
    }
    
    /**
     * Removes a player from cave mode tracking (on disconnect).
     */
    public static void removeCaveModePlayer(String playerName) {
        Set<Long> pending = caveModePendingChunks.get(playerName);
        if (pending != null) {
            for (Long idx : pending) {
                pendingCaveModeFutures.remove(playerName + "_" + idx);
            }
        }
        caveModeLoadedChunks.remove(playerName);
        caveModeFailedChunks.remove(playerName);
        caveModeTargetChunks.remove(playerName);
        caveModePendingChunks.remove(playerName);
        caveModeRetryCounter.remove(playerName);
    }

    /**
     * Injects a custom RestrictedSpiralIterator into the player's world map tracker.
     *
     * @param player  The player.
     * @param tracker The world map tracker.
     */
    public static void hookPlayerMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", 999);

            World world = player.getWorld();
            if (world != null) {
                sendMapSettingsToPlayer(player);
            }

            ExplorationTracker.PlayerExplorationData explorationData = ExplorationTracker.getInstance().getOrCreatePlayerData(player);
            RestrictedSpiralIterator customIterator = new RestrictedSpiralIterator(explorationData, tracker);

            ReflectionHelper.setFieldValueRecursive(tracker, "spiralIterator", customIterator);

            LOGGER.info("Hooked map tracker for player: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to hook WorldMapTracker for player " + player.getDisplayName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Removes the custom hooks from the player's tracker, attempting to clean up.
     *
     * @param player  The player.
     * @param tracker The tracker.
     */
    public static void unhookPlayerMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                ((RestrictedSpiralIterator) spiralIterator).stop();
            }

            CircleSpiralIterator vanillaIterator = new CircleSpiralIterator();
            vanillaIterator.init(0, 0, 0, 1);
            ReflectionHelper.setFieldValueRecursive(tracker, "spiralIterator", vanillaIterator);
            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", null);

            try {
                Object pendingReloadFutures = ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadFutures");
                if (pendingReloadFutures instanceof Map) {
                    ((Map<?, ?>) pendingReloadFutures).clear();
                }
            } catch (Exception e) {
                LOGGER.fine("Could not clear pendingReloadFutures: " + e.getMessage());
            }

            try {
                Object pendingReloadChunks = ReflectionHelper.getFieldValueRecursive(tracker, "pendingReloadChunks");
                if (pendingReloadChunks instanceof Set) {
                    ((Set<?>) pendingReloadChunks).clear();
                }
            } catch (Exception e) {
                LOGGER.fine("Could not clear pendingReloadChunks: " + e.getMessage());
            }

            try {
                ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 999.0f);
            } catch (Exception ignored) {}

            LOGGER.info("Unhooked map tracker for player: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Error unhooking tracker for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Restores the vanilla CircleSpiralIterator to the tracker.
     *
     * @param player  The player.
     * @param tracker The tracker.
     */
    public static void restoreVanillaMapTracker(@Nonnull Player player, @Nonnull WorldMapTracker tracker) {
        try {
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator) {
                ((RestrictedSpiralIterator) spiralIterator).stop();
            }

            ReflectionHelper.setFieldValueRecursive(tracker, "viewRadiusOverride", null);

            CircleSpiralIterator vanillaIterator = new CircleSpiralIterator();
            vanillaIterator.init(0, 0, 0, 1);
            ReflectionHelper.setFieldValueRecursive(tracker, "spiralIterator", vanillaIterator);

            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);

            LOGGER.info("Restored vanilla map tracker for player: " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to restore vanilla tracker for " + player.getDisplayName() + ": " + e.getMessage());
        }
    }

    /**
     * Adjusts world map settings (resolution/scale) for the given world based on configuration.
     *
     * @param world The world.
     */
    public static void hookWorldMapResolution(@Nonnull World world) {
        try {
            LOGGER.info("Hooking WorldMap resolution for world: " + world.getName());
            WorldMapManager manager = world.getWorldMapManager();

            LOGGER.info("Modifying WorldMapSettings for world: " + world.getName());
            WorldMapSettings settings = manager.getWorldMapSettings();

            ModConfig.MapQuality quality = ModConfig.getInstance().getActiveMapQuality();
            ReflectionHelper.setFieldValueRecursive(settings, "imageScale", quality.scale);

            manager.clearImages();

            WorldBorderManager.getInstance().hookWorldMapManager(world);

            LOGGER.info("Modified WorldMapSettings imageScale to " + quality.scale + " (" + quality + " quality) for world: " + world.getName());
        } catch (Exception e) {
            LOGGER.warning("Failed to hook WorldMap resolution: " + e.getMessage());
        }
    }

    /**
     * Updates the exploration state for a player, updating boundaries and forcing a tracker update if moved.
     * Now uses DYNAMIC cave mode - automatically shows cave view when underground.
     * 
     * When cave mode is active and player is underground:
     * - Only cave chunks are marked as explored (not normal surface chunks)
     * - Only cave chunks are loaded (not normal chunks)
     * 
     * When cave mode is disabled globally:
     * - Normal chunks are always marked as explored (even when underground)
     * - Cave chunks are never saved
     *
     * @param player  The player.
     * @param tracker The tracker.
     * @param x       Player X.
     * @param z       Player Z.
     */
    public static void updateExplorationState(@Nonnull Player player, @Nonnull WorldMapTracker tracker, double x, double z) {
        try {
            ExplorationTracker explorationTracker = ExplorationTracker.getInstance();
            ExplorationTracker.PlayerExplorationData explorationData = explorationTracker.getPlayerData(player);

            if (explorationData == null) {
                explorationData = explorationTracker.getOrCreatePlayerData(player);
                if (explorationData == null) {
                    LOGGER.warning("[DEBUG] Could not create exploration data for " + player.getDisplayName());
                    return;
                }
            }

            World world = player.getWorld();
            if (world != null) {
                explorationData.setWorldName(world.getName());
            }

            int playerChunkX = ChunkUtil.blockToChunkCoord(x);
            int playerChunkZ = ChunkUtil.blockToChunkCoord(z);
            boolean hasMoved = explorationData.hasMovedToNewChunk(playerChunkX, playerChunkZ);

            TransformComponent transform = player.getTransformComponent();
            int playerY = transform != null ? (int) transform.getPosition().y : 100;
            boolean hasCeiling = checkForCeiling(world, player, x, playerY, z);
            
            CaveModeManager caveManager = CaveModeManager.getInstance();
            
            boolean caveModeGloballyEnabled = ModConfig.getInstance().isCaveModeEnabled();
            
            boolean stateChanged = false;
            boolean isUnderground = false;
            
            if (caveModeGloballyEnabled) {
                stateChanged = caveManager.updateUndergroundState(player, playerY, hasCeiling);
                isUnderground = caveManager.isPlayerUnderground(player);
            }
            
            if (hasMoved && (!caveModeGloballyEnabled || !isUnderground)) {
                int explorationRadius = ModConfig.getInstance().getExplorationRadius();
                int beforeCount = explorationData.getExploredChunks().getExploredCount();
                explorationData.getMapExpansion().updateBoundaries(playerChunkX, playerChunkZ, explorationRadius);
                explorationData.setLastChunkPosition(playerChunkX, playerChunkZ);
                int afterCount = explorationData.getExploredChunks().getExploredCount();
                
                if (afterCount > beforeCount) {
                    LOGGER.info("[EXPLORATION] Added " + (afterCount - beforeCount) + " new surface chunks. Total: " + afterCount);
                }
            }

            if (caveModeGloballyEnabled) {
                if (stateChanged && world != null) {
                    CaveModeManager.DynamicCaveModeState state = caveManager.getState(player);
                    
                    if (isUnderground) {
                        LOGGER.info("[DYNAMIC CAVE] Activating cave overlay for " + player.getDisplayName() + 
                                   " at layer " + state.getCurrentLayer() + "-" + (state.getCurrentLayer() + state.getLayerSize()));
                        
                    } else {
                        LOGGER.info("[DYNAMIC CAVE] Deactivating cave overlay for " + player.getDisplayName());
                        
                        clearCaveModeOverlay(player, world, tracker);
                    }
                }
                
                boolean layerChanged = caveManager.didLayerChange(player);
                if (layerChanged && isUnderground && world != null) {
                    CaveModeManager.DynamicCaveModeState state = caveManager.getState(player);
                    int previousLayer = caveManager.getPreviousLayer(player);
                    int currentLayer = state.getCurrentLayer();
                    
                    LOGGER.info("[DYNAMIC CAVE] Layer change: " + previousLayer + " -> " + currentLayer + 
                               ". Will regenerate cave images for new Y level.");
                    
                    state.setNeedsLayerRefresh(true);
                    
                    String playerName = player.getDisplayName();
                    for (Long pendingIdx : new ArrayList<>(state.getPendingCaveChunks())) {
                        pendingCaveModeFutures.remove(playerName + "_" + pendingIdx);
                    }
                    state.getPendingCaveChunks().clear();
                }

                if (isUnderground && world != null) {
                    CaveModeManager.DynamicCaveModeState state = caveManager.getState(player);
                    updateDynamicCaveOverlay(player, world, tracker, x, z, state);
                    return;
                }
            }
            
            if (hasMoved) {
                forceTrackerUpdate(player, tracker, x, z);
                int mapChunkX = playerChunkX >> 1;
                int mapChunkZ = playerChunkZ >> 1;
                manageLoadedChunks(player, tracker, mapChunkX, mapChunkZ);
            }
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Exception in updateExplorationState: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Checks if there's a ceiling (solid blocks) above the player.
     * Used to detect if player is actually underground vs just at low Y.
     * 
     * For now, we use a simplified approach: if player is below the threshold,
     * we assume they're in a cave. A more sophisticated check would scan actual blocks.
     */
    private static boolean checkForCeiling(@Nullable World world, @Nullable Player player, double x, int y, double z) {
        if (world == null) return false;
        
        int threshold = CaveModeManager.getConfigUndergroundThreshold();
        if (player != null) {
            CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
            if (state != null) {
                threshold = state.getUndergroundThreshold();
            }
        }
        
        return y < threshold;
            
    }
    
    /**
     * Updates the dynamic cave mode overlay around the player.
     * Shows explored cave chunks progressively (like normal map), prioritizing nearby ones.
     * Cave chunks within the immediate radius are always loaded regardless of surface exploration.
     * Previously explored cave chunks are also loaded if within range.
     */
    private static void updateDynamicCaveOverlay(@Nonnull Player player, @Nonnull World world,
                                                   @Nonnull WorldMapTracker tracker,
                                                   double playerX, double playerZ,
                                                   @Nonnull CaveModeManager.DynamicCaveModeState state) {
        try {
            WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
            float imageScale = settings.getImageScale();
            int imageSize = MathUtil.fastFloor(32.0F * imageScale);
            
            int playerMapChunkX = ((int) Math.floor(playerX)) >> 5;
            int playerMapChunkZ = ((int) Math.floor(playerZ)) >> 5;
            
            int caveRadius = state.getCaveRadius();
            int yLevel = state.getRenderYLevel();
            int verticalRange = state.getVerticalRange();
            
            Set<Long> loadedCaveChunks = state.getLoadedCaveChunks();
            Set<Long> pendingCaveChunks = state.getPendingCaveChunks();
            Set<Long> exploredCaveChunks = state.getExploredCaveChunks();
            
            Object loadedObj = ReflectionHelper.getFieldValueRecursive(tracker, "loaded");
            @SuppressWarnings("unchecked")
            Set<Long> trackerLoaded = (loadedObj instanceof Set) ? (Set<Long>) loadedObj : new HashSet<>();
            
            Set<Long> allCaveChunks = new HashSet<>();
            
            for (int dx = -caveRadius; dx <= caveRadius; dx++) {
                for (int dz = -caveRadius; dz <= caveRadius; dz++) {
                    if (dx * dx + dz * dz <= caveRadius * caveRadius) {
                        int mx = playerMapChunkX + dx;
                        int mz = playerMapChunkZ + dz;
                        long idx = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(mx, mz);
                        allCaveChunks.add(idx);
                    }
                }
            }
            
            allCaveChunks.addAll(exploredCaveChunks);
            
            List<Long> sortedCaveChunks = new ArrayList<>(allCaveChunks);
            sortedCaveChunks.sort(Comparator.comparingDouble(idx -> {
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                return Math.pow(mx - playerMapChunkX, 2) + Math.pow(mz - playerMapChunkZ, 2);
            }));
            
            int maxChunks = ModConfig.getInstance().getActiveMapQuality().maxChunks;
            Set<Long> targetCaveChunks = new HashSet<>();
            for (int i = 0; i < Math.min(sortedCaveChunks.size(), maxChunks); i++) {
                targetCaveChunks.add(sortedCaveChunks.get(i));
            }
            
            List<MapChunk> chunksToUnload = new ArrayList<>();
            for (Long loadedIdx : new ArrayList<>(loadedCaveChunks)) {
                if (!targetCaveChunks.contains(loadedIdx)) {
                    loadedCaveChunks.remove(loadedIdx);
                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(loadedIdx);
                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(loadedIdx);
                    chunksToUnload.add(new MapChunk(mx, mz, null));
                    trackerLoaded.remove(loadedIdx);
                }
            }
            
            int normalChunksToKeep = maxChunks / 4;
            int caveChunksAllowed = maxChunks - normalChunksToKeep;
            
            if (targetCaveChunks.size() > caveChunksAllowed) {
                List<Long> normalChunksLoaded = new ArrayList<>();
                for (Long idx : new ArrayList<>(trackerLoaded)) {
                    if (!loadedCaveChunks.contains(idx) && !targetCaveChunks.contains(idx)) {
                        normalChunksLoaded.add(idx);
                    }
                }
                
                normalChunksLoaded.sort(Comparator.comparingDouble(idx -> {
                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                    return -(Math.pow(mx - playerMapChunkX, 2) + Math.pow(mz - playerMapChunkZ, 2));
                }));
                
                int toRemove = normalChunksLoaded.size() - normalChunksToKeep;
                for (int i = 0; i < toRemove && i < normalChunksLoaded.size(); i++) {
                    Long idx = normalChunksLoaded.get(i);
                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                    chunksToUnload.add(new MapChunk(mx, mz, null));
                    trackerLoaded.remove(idx);
                }
            }
            
            if (!chunksToUnload.isEmpty()) {
                UpdateWorldMap unloadPacket = new UpdateWorldMap(chunksToUnload.toArray(new MapChunk[0]), null, null);
                sendPacket(player, unloadPacket);
                LOGGER.fine("[DYNAMIC CAVE] Unloaded " + chunksToUnload.size() + " chunks (cave + normal)");
            }
            
            List<MapChunk> chunksToSend = new ArrayList<>();
            String playerName = player.getDisplayName();
            
            boolean needsRefresh = state.needsLayerRefresh();
            if (needsRefresh) {
                LOGGER.info("[DYNAMIC CAVE] Refreshing " + loadedCaveChunks.size() + " chunks for new Y level: " + yLevel);
                
                int refreshCount = 0;
                for (Long chunkIdx : new ArrayList<>(loadedCaveChunks)) {
                    if (!targetCaveChunks.contains(chunkIdx)) continue;
                    
                    CompletableFuture<CaveModeImageBuilder> future = CaveModeImageBuilder.build(
                        chunkIdx, imageSize, imageSize, world, yLevel, verticalRange);
                    
                    if (future.isDone()) {
                        CaveModeImageBuilder builder = future.getNow(null);
                        if (builder != null && builder.getImage() != null && builder.getImage().data != null) {
                            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(chunkIdx);
                            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(chunkIdx);
                            chunksToSend.add(new MapChunk(mx, mz, builder.getImage()));
                            state.markCaveChunkExplored(chunkIdx);
                            refreshCount++;
                        }
                    } else {
                        pendingCaveChunks.add(chunkIdx);
                        pendingCaveModeFutures.put(playerName + "_" + chunkIdx, future);
                    }
                }
                
                state.setNeedsLayerRefresh(false);
                LOGGER.info("[DYNAMIC CAVE] Refreshed " + refreshCount + " chunks immediately, " + 
                           pendingCaveChunks.size() + " pending");
            }
            
            for (Long pendingIdx : new ArrayList<>(pendingCaveChunks)) {
                CompletableFuture<CaveModeImageBuilder> future = pendingCaveModeFutures.get(playerName + "_" + pendingIdx);
                if (future != null && future.isDone()) {
                    pendingCaveChunks.remove(pendingIdx);
                    pendingCaveModeFutures.remove(playerName + "_" + pendingIdx);
                    
                    if (!targetCaveChunks.contains(pendingIdx)) continue;
                    
                    CaveModeImageBuilder builder = future.getNow(null);
                    if (builder != null && builder.getImage() != null && builder.getImage().data != null) {
                        int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(pendingIdx);
                        int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(pendingIdx);
                        chunksToSend.add(new MapChunk(mx, mz, builder.getImage()));
                        loadedCaveChunks.add(pendingIdx);
                        trackerLoaded.add(pendingIdx);
                        
                        state.markCaveChunkExplored(pendingIdx);
                    }
                }
            }
            
            List<Long> sortedTargets = new ArrayList<>(targetCaveChunks);
            sortedTargets.sort(Comparator.comparingDouble(idx -> {
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                return Math.pow(mx - playerMapChunkX, 2) + Math.pow(mz - playerMapChunkZ, 2);
            }));
            
            int maxNewGenerations = 10;
            int maxImmediateLoads = 8;
            int immediateLoads = 0;
            
            for (Long chunkIdx : sortedTargets) {
                if (maxNewGenerations <= 0 && immediateLoads >= maxImmediateLoads) break;
                if (loadedCaveChunks.contains(chunkIdx) || pendingCaveChunks.contains(chunkIdx)) continue;
                
                CompletableFuture<CaveModeImageBuilder> future = CaveModeImageBuilder.build(
                    chunkIdx, imageSize, imageSize, world, yLevel, verticalRange);
                
                if (future.isDone() && immediateLoads < maxImmediateLoads) {
                    CaveModeImageBuilder builder = future.getNow(null);
                    if (builder != null && builder.getImage() != null && builder.getImage().data != null) {
                        int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(chunkIdx);
                        int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(chunkIdx);
                        chunksToSend.add(new MapChunk(mx, mz, builder.getImage()));
                        loadedCaveChunks.add(chunkIdx);
                        trackerLoaded.add(chunkIdx);
                        state.markCaveChunkExplored(chunkIdx);
                        immediateLoads++;
                    }
                } else if (!future.isDone() && maxNewGenerations > 0) {
                    pendingCaveChunks.add(chunkIdx);
                    pendingCaveModeFutures.put(playerName + "_" + chunkIdx, future);
                    maxNewGenerations--;
                }
            }
            
            if (!chunksToSend.isEmpty()) {
                int batchSize = 15;
                for (int i = 0; i < chunksToSend.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, chunksToSend.size());
                    List<MapChunk> batch = chunksToSend.subList(i, end);
                    UpdateWorldMap packet = new UpdateWorldMap(batch.toArray(new MapChunk[0]), null, null);
                    sendPacket(player, packet);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[DYNAMIC CAVE] Error updating overlay: " + e.getMessage());
        }
    }
    
    /**
     * Clears the cave mode overlay chunks and restores normal map view.
     * Sends unload packets (null images) for all loaded cave chunks, then forces normal map refresh.
     */
    private static void clearCaveModeOverlay(@Nonnull Player player, @Nonnull World world,
                                              @Nonnull WorldMapTracker tracker) {
        try {
            CaveModeManager.DynamicCaveModeState state = CaveModeManager.getInstance().getState(player);
            if (state == null) return;
            
            Set<Long> loadedCaveChunks = new HashSet<>(state.getLoadedCaveChunks());
            String playerName = player.getDisplayName();
            
            for (Long pendingIdx : state.getPendingCaveChunks()) {
                pendingCaveModeFutures.remove(playerName + "_" + pendingIdx);
            }
            state.getPendingCaveChunks().clear();
            
            Object loadedObj = ReflectionHelper.getFieldValueRecursive(tracker, "loaded");
            @SuppressWarnings("unchecked")
            Set<Long> trackerLoaded = (loadedObj instanceof Set) ? (Set<Long>) loadedObj : new HashSet<>();
            
            List<MapChunk> chunksToUnload = new ArrayList<>();
            for (Long caveChunkIdx : loadedCaveChunks) {
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(caveChunkIdx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(caveChunkIdx);
                chunksToUnload.add(new MapChunk(mx, mz, null));
                trackerLoaded.remove(caveChunkIdx);
            }
            
            if (!chunksToUnload.isEmpty()) {
                int batchSize = 50;
                for (int i = 0; i < chunksToUnload.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, chunksToUnload.size());
                    List<MapChunk> batch = chunksToUnload.subList(i, end);
                    UpdateWorldMap unloadPacket = new UpdateWorldMap(batch.toArray(new MapChunk[0]), null, null);
                    sendPacket(player, unloadPacket);
                }
                LOGGER.info("[DYNAMIC CAVE] Unloaded " + chunksToUnload.size() + " cave chunks for " + playerName);
            }
            
            state.clearLoadedCaveChunks();
            
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
                restrictedIterator.setCaveModeActive(false);
                restrictedIterator.resetState();
            }
            
            TransformComponent transform = player.getTransformComponent();
            if (transform != null) {
                var pos = transform.getPosition();
                forceTrackerUpdate(player, tracker, pos.x, pos.z);
                
                int playerChunkX = ChunkUtil.blockToChunkCoord(pos.x);
                int playerChunkZ = ChunkUtil.blockToChunkCoord(pos.z);
                int mapChunkX = playerChunkX >> 1;
                int mapChunkZ = playerChunkZ >> 1;
                manageLoadedChunks(player, tracker, mapChunkX, mapChunkZ);
            }
            
            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);
            
            LOGGER.info("[DYNAMIC CAVE] Cleared cave overlay and triggered normal map refresh for " + playerName);
            
        } catch (Exception e) {
            LOGGER.warning("[DYNAMIC CAVE] Error clearing overlay: " + e.getMessage());
        }
    }

    private static void manageLoadedChunks(@Nonnull Player player, @Nonnull WorldMapTracker tracker, int cx, int cz) {
        try {
            Object loadedObj = ReflectionHelper.getFieldValueRecursive(tracker, "loaded");
            if (!(loadedObj instanceof Set))
                return;
            
            @SuppressWarnings("unchecked")
            Set<Long> loaded = (Set<Long>) loadedObj;

            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (!(spiralIterator instanceof RestrictedSpiralIterator))
                return;

            List<Long> targetChunks = ((RestrictedSpiralIterator) spiralIterator).getTargetMapChunks();
            Set<Long> targetSet = new HashSet<>(targetChunks);

            List<Long> toUnload = new ArrayList<>();
            List<Long> loadedSnapshot = new ArrayList<>(loaded);

            List<MapChunk> unloadPackets = new ArrayList<>();

            for (Long idx : loadedSnapshot) {
                if (!targetSet.contains(idx)) {
                    toUnload.add(idx);
                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                    unloadPackets.add(new MapChunk(mx, mz, null));
                }
            }

            if (toUnload.isEmpty()) return;

            toUnload.forEach(loaded::remove);

            UpdateWorldMap packet = new UpdateWorldMap(
                    unloadPackets.toArray(new MapChunk[0]),
                    null,
                    null
            );
            sendPacket(player, packet);

        } catch (Exception e) {
            LOGGER.warning("Failed to manage loaded chunks: " + e.getMessage());
        }
    }

    private static void sendPacket(Player player, Packet packet) {
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                playerRef.getPacketHandler().write(packet);
            }
        }
    }

    private static void forceTrackerUpdate(@Nonnull Player player, @Nonnull WorldMapTracker tracker, double x, double z) {
        try {
            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
                int chunkX = (int) Math.floor(x) >> 5;
                int chunkZ = (int) Math.floor(z) >> 5;

                restrictedIterator.init(chunkX, chunkZ, 0, 999);
            }

            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Failed to force tracker update: " + e.getMessage());
        }
    }

    /**
     * Forces a full map refresh for a player.
     * This properly clears the client map and server state, then regenerates.
     * Uses native Hytale methods where possible for proper synchronization.
     *
     * @param player The player to refresh.
     */
    public static void forceFullMapRefresh(@Nonnull Player player) {
        try {
            World world = player.getWorld();
            if (world == null) return;

            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker == null) return;

            CaveModeManager caveManager = CaveModeManager.getInstance();
            boolean isUnderground = caveManager.isPlayerUnderground(player);
            
            LOGGER.info("[MAP REFRESH] Starting full map refresh for " + player.getDisplayName() + 
                       " (underground: " + isUnderground + ")");

            Object spiralIterator = ReflectionHelper.getFieldValueRecursive(tracker, "spiralIterator");
            if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
                restrictedIterator.setCaveModeActive(false);
                LOGGER.info("[MAP REFRESH] Set RestrictedSpiralIterator cave mode to: false (dynamic overlay mode)");
                
                restrictedIterator.resetState();
            }

            Object loadedObj = ReflectionHelper.getFieldValueRecursive(tracker, "loaded");
            if (loadedObj instanceof Set<?> loadedSet) {
                loadedSet.clear();
                LOGGER.info("[MAP REFRESH] Cleared loaded chunks set");
            }
            
            clearCaveModeLoadedChunks(player.getDisplayName());
            
            CaveModeManager.DynamicCaveModeState state = caveManager.getState(player);
            if (state != null) {
                state.clearLoadedCaveChunks();
            }

            tracker.clear();
            
            ReflectionHelper.setFieldValueRecursive(tracker, "sentViewRadius", 0);
            
            ReflectionHelper.setFieldValueRecursive(tracker, "updateTimer", 0.0f);
            
            TransformComponent transform = player.getTransformComponent();
            if (transform != null) {
                var pos = transform.getPosition();
                int chunkX = (int) Math.floor(pos.x) >> 5;
                int chunkZ = (int) Math.floor(pos.z) >> 5;
                
                if (spiralIterator instanceof RestrictedSpiralIterator restrictedIterator) {
                    restrictedIterator.init(chunkX, chunkZ, 0, 999);
                }
                
                forceTrackerUpdate(player, tracker, pos.x, pos.z);
                
                if (isUnderground && state != null) {
                    LOGGER.info("[DYNAMIC CAVE] Starting cave overlay at layer " + state.getCurrentLayer());
                    updateDynamicCaveOverlay(player, world, tracker, pos.x, pos.z, state);
                }
                    
                LOGGER.info("[MAP REFRESH] Re-initialized map at chunk " + chunkX + ", " + chunkZ);
            }

            LOGGER.info("[MAP REFRESH] Completed for " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to force full map refresh for " + player.getDisplayName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generates cave mode images and sends them to the player PROGRESSIVELY.
     * Works like the base map: only sends chunks that are ready, skips those that aren't.
     * This prevents freezing by not waiting for all futures to complete.
     *
     * @param player  The player.
     * @param world   The world.
     * @param tracker The world map tracker.
     * @param playerX Player X position.
     * @param playerZ Player Z position.
     * @param yLevel  The Y level for cave mode.
     * @param range   The vertical range.
     * @param maxGeneration Maximum number of pending generations allowed (like base map)
     * @return Number of remaining generation slots
     */
    private static int generateCaveModeImagesProgressive(@Nonnull Player player, @Nonnull World world,
                                                          @Nonnull WorldMapTracker tracker,
                                                          double playerX, double playerZ,
                                                          int yLevel, int range, int maxGeneration) {
        try {
            WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
            float imageScale = settings.getImageScale();
            int imageSize = MathUtil.fastFloor(32.0F * imageScale);
            
            int playerMapChunkX = ((int) Math.floor(playerX)) >> 5;
            int playerMapChunkZ = ((int) Math.floor(playerZ)) >> 5;
            
            Object loadedObj = ReflectionHelper.getFieldValueRecursive(tracker, "loaded");
            @SuppressWarnings("unchecked")
            final Set<Long> loaded = (loadedObj instanceof Set) ? (Set<Long>) loadedObj : new HashSet<>();
            
            String playerName = player.getDisplayName();
            Set<Long> caveModeLoaded = getCaveModeLoadedChunks(playerName);
            Set<Long> caveModeFailed = getCaveModeFailedChunks(playerName);
            Set<Long> caveModeTarget = getCaveModeTargetChunks(playerName);
            Set<Long> caveModePending = getCaveModePendingChunks(playerName);
            
            ExplorationTracker.PlayerExplorationData explorationData = ExplorationTracker.getInstance().getPlayerData(player);
            if (explorationData == null) {
                return maxGeneration;
            }

            Set<Long> exploredWorldChunks;
            if (ModConfig.getInstance().isShareAllExploration()) {
                String worldName = world.getName();
                exploredWorldChunks = ExplorationManager.getInstance().getAllExploredChunks(worldName);
            } else {
                exploredWorldChunks = explorationData.getExploredChunks().getExploredChunks();
            }

            if (exploredWorldChunks == null || exploredWorldChunks.isEmpty()) {
                return maxGeneration;
            }

            Set<Long> mapChunksSet = new HashSet<>();
            for (Long chunkIdx : exploredWorldChunks) {
                int wx = ChunkUtil.indexToChunkX(chunkIdx);
                int wz = ChunkUtil.indexToChunkZ(chunkIdx);
                int mx = wx >> 1;
                int mz = wz >> 1;
                long mapChunkIdx = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(mx, mz);
                mapChunksSet.add(mapChunkIdx);
            }

            List<Long> sortedChunks = new ArrayList<>(mapChunksSet);
            sortedChunks.sort(Comparator.comparingDouble(idx -> {
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                return Math.pow(mx - playerMapChunkX, 2) + Math.pow(mz - playerMapChunkZ, 2);
            }));
            
            caveModeTarget.clear();
            caveModeTarget.addAll(mapChunksSet);
            
            List<Long> completedPending = new ArrayList<>();
            List<MapChunk> chunksToSend = new ArrayList<>();
            
            for (Long pendingIdx : new ArrayList<>(caveModePending)) {
                CompletableFuture<CaveModeImageBuilder> future = pendingCaveModeFutures.get(playerName + "_" + pendingIdx);
                if (future != null && future.isDone()) {
                    completedPending.add(pendingIdx);
                    caveModePending.remove(pendingIdx);
                    pendingCaveModeFutures.remove(playerName + "_" + pendingIdx);
                    
                    CaveModeImageBuilder builder = future.getNow(null);
                    if (builder != null) {
                        MapImage image = builder.getImage();
                        if (image != null && image.data != null) {
                            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(pendingIdx);
                            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(pendingIdx);
                            chunksToSend.add(new MapChunk(mx, mz, image));
                            loaded.add(pendingIdx);
                            caveModeLoaded.add(pendingIdx);
                            caveModeFailed.remove(pendingIdx);
                        } else {
                            caveModeFailed.add(pendingIdx);
                        }
                    } else {
                        caveModeFailed.add(pendingIdx);
                    }
                }
            }
            
            for (Long chunkIdx : sortedChunks) {
                if (maxGeneration <= 0) break;
                
                if (caveModeLoaded.contains(chunkIdx) || caveModePending.contains(chunkIdx)) {
                    continue;
                }
                
                CompletableFuture<CaveModeImageBuilder> future = CaveModeImageBuilder.build(
                    chunkIdx, imageSize, imageSize, world, yLevel, range);
                
                if (future.isDone()) {
                    CaveModeImageBuilder builder = future.getNow(null);
                    if (builder != null) {
                        MapImage image = builder.getImage();
                        if (image != null && image.data != null) {
                            int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(chunkIdx);
                            int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(chunkIdx);
                            chunksToSend.add(new MapChunk(mx, mz, image));
                            loaded.add(chunkIdx);
                            caveModeLoaded.add(chunkIdx);
                            caveModeFailed.remove(chunkIdx);
                        } else {
                            caveModeFailed.add(chunkIdx);
                        }
                    } else {
                        caveModeFailed.add(chunkIdx);
                    }
                } else {
                    caveModePending.add(chunkIdx);
                    pendingCaveModeFutures.put(playerName + "_" + chunkIdx, future);
                    maxGeneration--;
                }
            }
            
            if (!chunksToSend.isEmpty()) {
                int batchSize = 25;
                for (int i = 0; i < chunksToSend.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, chunksToSend.size());
                    List<MapChunk> batch = chunksToSend.subList(i, end);
                    
                    UpdateWorldMap packet = new UpdateWorldMap(
                            batch.toArray(new MapChunk[0]),
                            null,
                            null
                    );
                    sendPacket(player, packet);
                }
                
                LOGGER.fine("[CAVE MODE] Sent " + chunksToSend.size() + " chunks (pending: " + caveModePending.size() + ")");
            }
            
            return maxGeneration;
        } catch (Exception e) {
            LOGGER.warning("[CAVE MODE] Error in progressive generation: " + e.getMessage());
            return maxGeneration;
        }
    }

    /**
     * Updates world map configuration settings on the server side.
     *
     * @param world The world.
     */
    public static void updateWorldMapConfigs(@Nonnull World world) {
        try {
            WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
            UpdateWorldMapSettings packet = (UpdateWorldMapSettings) ReflectionHelper.getFieldValue(settings, "settingsPacket");
            ModConfig config = ModConfig.getInstance();

            if (packet != null) {
                packet.minScale = config.getMinScale();
                packet.maxScale = config.getMaxScale();
            }

            ReflectionHelper.setFieldValueRecursive(settings, "minScale", config.getMinScale());
            ReflectionHelper.setFieldValueRecursive(settings, "maxScale", config.getMaxScale());

        } catch (Exception e) {
            LOGGER.warning("Failed to update world map configs: " + e.getMessage());
        }
    }

    /**
     * Triggers the broadcast of map settings to clients in the world.
     *
     * @param world The world.
     */
    public static void broadcastMapSettings(@Nonnull World world) {
        try {
            Object mapManager = world.getWorldMapManager();
            java.lang.reflect.Method sendSettings = mapManager.getClass().getMethod("sendSettings");
            sendSettings.invoke(mapManager);
        } catch (Exception e) {
            LOGGER.fine("Could not invoke mapManager.sendSettings(): " + e.getMessage());
        }
    }

    /**
     * Sends custom map settings packet to a specific player.
     *
     * @param player The player.
     */
    public static void sendMapSettingsToPlayer(@Nonnull Player player) {
        try {
            World world = player.getWorld();
            if (world == null)
                return;

            updateWorldMapConfigs(world);

            WorldMapSettings settings = world.getWorldMapManager().getWorldMapSettings();
            UpdateWorldMapSettings basePacket = (UpdateWorldMapSettings) ReflectionHelper.getFieldValue(settings, "settingsPacket");

            if (basePacket == null)
                return;

            UpdateWorldMapSettings packet = basePacket.clone();

            PlayerConfig playerConfig = PlayerConfigManager.getInstance().getPlayerConfig(((CommandSender) player).getUuid());

            if (playerConfig != null) {
                packet.minScale = playerConfig.getMinScale();
                packet.maxScale = playerConfig.getMaxScale();
            }

            WorldMapTracker tracker = player.getWorldMapTracker();
            packet.allowTeleportToCoordinates = tracker.isAllowTeleportToCoordinates();
            packet.allowTeleportToMarkers = tracker.isAllowTeleportToMarkers();

            sendPacket(player, packet);

            LOGGER.fine("Sent custom map settings to " + player.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send map settings to player: " + e.getMessage());
        }
    }

    /**
     * Refreshes the map trackers for all players in the given world.
     * Use this when exploration data sharing settings change.
     *
     * @param world The world.
     */
    public static void refreshTrackers(@Nonnull World world) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Holder<EntityStore> holder = playerRef.getHolder();
            if (holder == null) continue;
            Player player = holder.getComponent(Player.getComponentType());
            if (player == null) continue;

            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());

                    if (tc != null) {
                        var pos = tc.getPosition();
                        forceTrackerUpdate(player, player.getWorldMapTracker(), pos.x, pos.z);
                        updateExplorationState(player, player.getWorldMapTracker(), pos.x, pos.z);
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to refresh tracker for " + player.getDisplayName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Custom iterator that only returns chunks that have been explored or are within the persistent boundaries.
     * Thread-safe implementation to prevent race conditions with the WorldMap thread.
     */
    public static class RestrictedSpiralIterator extends CircleSpiralIterator {
        private final ExplorationTracker.PlayerExplorationData data;
        private final WorldMapTracker tracker;
        private volatile Iterator<Long> currentIterator;
        private volatile List<Long> targetMapChunks = new ArrayList<>();
        private volatile int currentGoalRadius;
        private volatile boolean stopped = false;
        private volatile boolean initialized = false;
        private volatile boolean caveModeActive = false;
        private volatile int centerX;
        private volatile int centerZ;
        private volatile int currentRadius;
        private int cleanupTimer = 0;
        private final Object lock = new Object();

        public RestrictedSpiralIterator(ExplorationTracker.PlayerExplorationData data, WorldMapTracker tracker) {
            super();
            super.init(0, 0, 0, 1);
            this.data = data;
            this.tracker = tracker;
            this.currentIterator = Collections.emptyIterator();
            this.initialized = true;
        }
        
        /**
         * Enables or disables cave mode. When cave mode is active, this iterator
         * will return no chunks, allowing the cave mode system to handle map generation.
         *
         * @param active Whether cave mode should be active.
         */
        public void setCaveModeActive(boolean active) {
            synchronized (lock) {
                this.caveModeActive = active;
                if (active) {
                    this.currentIterator = Collections.emptyIterator();
                }
            }
        }
        
        /**
         * Checks if cave mode is active.
         *
         * @return true if cave mode is active.
         */
        public boolean isCaveModeActive() {
            return caveModeActive;
        }

        public void stop() {
            synchronized (lock) {
                this.stopped = true;
                this.currentIterator = Collections.emptyIterator();
                try {
                    super.init(0, 0, 0, 1);
                } catch (Exception ignored) {}
            }
        }
        
        /**
         * Resets the iterator state to allow fresh chunk loading.
         * Called when switching between cave mode and normal mode.
         */
        public void resetState() {
            synchronized (lock) {
                this.stopped = false;
                this.initialized = true;
                this.currentIterator = Collections.emptyIterator();
                this.targetMapChunks = new ArrayList<>();
                this.currentGoalRadius = 0;
                this.currentRadius = 0;
                this.cleanupTimer = 0;
            }
        }

        /**
         * Gets the list of target chunks being iterated.
         *
         * @return List of chunk indices.
         */
        public List<Long> getTargetMapChunks() {
            return targetMapChunks;
        }

        @Override
        public void init(int cx, int cz, int startRadius, int endRadius) {
            try {
                super.init(cx, cz, startRadius, endRadius);
            } catch (Exception ignored) {}

            synchronized (lock) {
                if (stopped || caveModeActive) {
                    this.currentIterator = Collections.emptyIterator();
                    this.initialized = true;
                    return;
                }

                this.centerX = cx;
                this.centerZ = cz;
                this.currentRadius = startRadius;
                this.currentGoalRadius = endRadius;

                try {
                    Set<Long> mapChunks = new HashSet<>();
                    Set<Long> exploredWorldChunks;

                    Player player = tracker.getPlayer();
                    if (data == null) {
                        this.currentIterator = Collections.emptyIterator();
                        this.initialized = true;
                        return;
                    }

                    if (ModConfig.getInstance().isShareAllExploration()) {
                        World world = player.getWorld();
                        String worldName = world != null ? world.getName() : "world";
                        exploredWorldChunks = ExplorationManager.getInstance().getAllExploredChunks(worldName);
                    } else {
                        exploredWorldChunks = data.getExploredChunks().getExploredChunks();
                    }

                    if (exploredWorldChunks == null || exploredWorldChunks.isEmpty()) {
                        this.currentIterator = Collections.emptyIterator();
                        this.targetMapChunks = new ArrayList<>();
                        this.initialized = true;
                        return;
                    }

                    for (Long chunkIdx : exploredWorldChunks) {
                        int wx = ChunkUtil.indexToChunkX(chunkIdx);
                        int wz = ChunkUtil.indexToChunkZ(chunkIdx);

                        int mx = wx >> 1;
                        int mz = wz >> 1;

                        long mapChunkIdx = com.hypixel.hytale.math.util.ChunkUtil.indexChunk(mx, mz);
                        mapChunks.add(mapChunkIdx);
                    }

                    List<Long> rankedChunks = new ArrayList<>();
                    MapExpansionManager.MapBoundaries bounds = data.getMapExpansion().getCurrentBoundaries();
                    Set<Long> boundaryChunks = new HashSet<>();

                    if (bounds.minX != Integer.MAX_VALUE) {
                        boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.minX >> 1, bounds.minZ >> 1));
                        boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.maxX >> 1, bounds.minZ >> 1));
                        boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.minX >> 1, bounds.maxZ >> 1));
                        boundaryChunks.add(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(bounds.maxX >> 1, bounds.maxZ >> 1));
                    }

                    for (Long chunk : mapChunks) {
                        if (!boundaryChunks.contains(chunk)) {
                            rankedChunks.add(chunk);
                        }
                    }

                    rankedChunks.sort(Comparator.comparingDouble(idx -> {
                        int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                        int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                        return Math.sqrt(Math.pow(mx - cx, 2) + Math.pow(mz - cz, 2));
                    }));

                    int maxChunks = ModConfig.getInstance().getActiveMapQuality().maxChunks;
                    int searchLimit = maxChunks - boundaryChunks.size();
                    if (searchLimit < 0) searchLimit = 0;

                    if (rankedChunks.size() > searchLimit) {
                        rankedChunks = new ArrayList<>(rankedChunks.subList(0, searchLimit));
                    }

                    this.targetMapChunks = new ArrayList<>(boundaryChunks);
                    this.targetMapChunks.addAll(rankedChunks);

                    this.currentIterator = rankedChunks.iterator();
                    this.initialized = true;

                    if (++cleanupTimer > 100) {
                        cleanupTimer = 0;
                        cleanupFarChunks(rankedChunks);
                    }
                } catch (Exception e) {
                    LOGGER.warning("Error in RestrictedSpiralIterator.init(): " + e.getMessage());
                    this.currentIterator = Collections.emptyIterator();
                    this.initialized = true;
                }
            }
        }

        private void cleanupFarChunks(List<Long> keepChunks) {
            try {
                Object loadedObj = ReflectionHelper.getFieldValue(tracker, "loaded");
                if (loadedObj instanceof Set<?> loadedSet) {
                    if (loadedSet.size() > 20000) {
                        Set<Long> keepSet = new HashSet<>(keepChunks);
                        List<MapChunk> toRemovePackets = new ArrayList<>();

                        Iterator<?> it = loadedSet.iterator();
                        while (it.hasNext()) {
                            Object obj = it.next();
                            if (obj instanceof Long idx) {
                                if (!keepSet.contains(idx)) {
                                    it.remove();
                                    int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(idx);
                                    int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(idx);
                                    toRemovePackets.add(new MapChunk(mx, mz, null));
                                }
                            }
                        }

                        if (!toRemovePackets.isEmpty()) {
                            UpdateWorldMap packet = new UpdateWorldMap(toRemovePackets.toArray(new MapChunk[0]), null, null);
                            sendPacket(tracker.getPlayer(), (Packet) packet);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to cleanup far chunks: " + e.getMessage());
            }
        }

        @Override
        public boolean hasNext() {
            if (stopped || caveModeActive) return false;
            Iterator<Long> iter = currentIterator;
            return iter != null && iter.hasNext();
        }

        @Override
        public long next() {
            Iterator<Long> iter = currentIterator;
            if (stopped || iter == null || !iter.hasNext())
                return 0;

            try {
                long next = iter.next();
                int mx = com.hypixel.hytale.math.util.ChunkUtil.xOfChunkIndex(next);
                int mz = com.hypixel.hytale.math.util.ChunkUtil.zOfChunkIndex(next);
                this.currentRadius = (int) Math.sqrt(Math.pow(mx - centerX, 2) + Math.pow(mz - centerZ, 2));
                return next;
            } catch (java.util.NoSuchElementException e) {
                return 0;
            }
        }

        @Override
        public int getCompletedRadius() {
            return (stopped || caveModeActive) ? currentGoalRadius : currentRadius;
        }
    }
}
