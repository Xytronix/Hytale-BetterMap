package dev.ninesliced.providers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.ShaderType;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Advanced cave mode image builder inspired by Xaero's Minimap layered cave rendering.
 * 
 * Key features:
 * - Multi-layer scanning: Scans a range of Y levels and composites them
 * - Depth-based coloring: Deeper areas are darker, shallower areas brighter (legible cave maps)
 * - Cave space detection: Shows navigable air spaces vs solid walls
 * - Fluid prominence: Lava and water are highly visible with glow effects
 * - Ore highlighting: Special blocks like ores stand out
 * - Cave volume indication: Larger caves appear brighter/more prominent
 * - Openness ratio: Shows how much of a column is navigable space
 */
public class CaveModeImageBuilder {
    private static final Logger LOGGER = Logger.getLogger(CaveModeImageBuilder.class.getName());
    
    private static final int COLOR_FLOOR_HIGH = packColor(180, 155, 130, 255);
    private static final int COLOR_FLOOR_MID = packColor(140, 115, 95, 255);
    private static final int COLOR_FLOOR_LOW = packColor(100, 80, 65, 255);
    
    private static final int COLOR_WALL_SOLID = packColor(55, 50, 60, 255);
    private static final int COLOR_WALL_PARTIAL = packColor(75, 70, 80, 255);
    
    private static final int COLOR_AIR_SPACE = packColor(35, 30, 45, 220);
    private static final int COLOR_UNEXPLORED = packColor(15, 12, 20, 255);
    
    private static final int COLOR_LAVA_CORE = packColor(255, 180, 50, 255);
    private static final int COLOR_LAVA_EDGE = packColor(255, 100, 20, 255);
    private static final int COLOR_WATER_DEEP = packColor(30, 80, 160, 230);
    private static final int COLOR_WATER_SHALLOW = packColor(70, 150, 220, 200);
    
    private static final int COLOR_CAVE_TINY = packColor(90, 75, 65, 255);
    private static final int COLOR_CAVE_SMALL = packColor(120, 100, 85, 255);
    private static final int COLOR_CAVE_MEDIUM = packColor(150, 125, 105, 255);
    private static final int COLOR_CAVE_LARGE = packColor(175, 150, 125, 255);
    private static final int COLOR_CAVE_HUGE = packColor(200, 175, 150, 255);
    
    private final long index;
    private final World world;
    @Nonnull
    private final MapImage image;
    private final int sampleWidth;
    private final int sampleHeight;
    private final int blockStepX;
    private final int blockStepZ;
    private final int targetYLevel;
    private final int verticalRange;
    
    @Nonnull private final CaveColumnData[] columnData;
    
    private final Color outColor = new Color();
    @Nullable
    private WorldChunk worldChunk;
    private FluidSection[] fluidSections;
    
    /**
     * Creates a new cave mode image builder.
     */
    public CaveModeImageBuilder(long index, int imageWidth, int imageHeight, World world, int yLevel, int range) {
        this.index = index;
        this.world = world;
        this.image = new MapImage(imageWidth, imageHeight, new int[imageWidth * imageHeight]);
        this.sampleWidth = Math.min(32, imageWidth);
        this.sampleHeight = Math.min(32, imageHeight);
        this.blockStepX = Math.max(1, 32 / imageWidth);
        this.blockStepZ = Math.max(1, 32 / imageHeight);
        this.targetYLevel = yLevel;
        this.verticalRange = Math.max(range, 12);
        
        this.columnData = new CaveColumnData[sampleWidth * sampleHeight];
        for (int i = 0; i < columnData.length; i++) {
            columnData[i] = new CaveColumnData();
        }
    }
    
    public long getIndex() {
        return index;
    }
    
    @Nonnull
    public MapImage getImage() {
        return image;
    }
    
    @Nonnull
    private CompletableFuture<CaveModeImageBuilder> fetchChunk() {
        return world.getChunkStore().getChunkReferenceAsync(index)
            .thenApplyAsync(ref -> {
                try {
                    if (ref != null && ref.isValid()) {
                        this.worldChunk = ref.getStore().getComponent((Ref<ChunkStore>) ref, WorldChunk.getComponentType());
                        ChunkColumn chunkColumn = ref.getStore().getComponent((Ref<ChunkStore>) ref, ChunkColumn.getComponentType());
                        this.fluidSections = new FluidSection[10];
                        
                        for (int y = 0; y < 10; y++) {
                            Ref<ChunkStore> sectionRef = chunkColumn.getSection(y);
                            this.fluidSections[y] = world.getChunkStore().getStore().getComponent(sectionRef, FluidSection.getComponentType());
                        }
                        
                        return this;
                    } else {
                        return null;
                    }
                } catch (Exception e) {
                    LOGGER.fine("Failed to fetch chunk " + index + ": " + e.getMessage());
                    return null;
                }
            }, world)
            .exceptionally(ex -> {
                LOGGER.fine("Exception fetching chunk " + index + ": " + ex.getMessage());
                return null;
            });
    }
    
    /**
     * Generates the cave map image using multi-layer analysis.
     */
    @Nonnull
    private CaveModeImageBuilder generateCaveImage() {
        if (worldChunk == null) {
            for (int i = 0; i < image.data.length; i++) {
                image.data[i] = COLOR_UNEXPLORED;
            }
            return this;
        }
        
        for (int ix = 0; ix < sampleWidth; ix++) {
            for (int iz = 0; iz < sampleHeight; iz++) {
                int sampleIndex = iz * sampleWidth + ix;
                int x = ix * blockStepX;
                int z = iz * blockStepZ;
                
                scanColumnMultiLayer(x, z, columnData[sampleIndex]);
            }
        }
        
        int globalMinFloorY = Integer.MAX_VALUE;
        int globalMaxFloorY = Integer.MIN_VALUE;
        int globalMaxCaveHeight = 0;
        
        for (CaveColumnData col : columnData) {
            if (col.hasValidFloor) {
                globalMinFloorY = Math.min(globalMinFloorY, col.floorY);
                globalMaxFloorY = Math.max(globalMaxFloorY, col.floorY);
                globalMaxCaveHeight = Math.max(globalMaxCaveHeight, col.caveHeight);
            }
        }
        
        if (globalMinFloorY == Integer.MAX_VALUE) {
            globalMinFloorY = targetYLevel - verticalRange;
            globalMaxFloorY = targetYLevel + verticalRange;
        }
        
        int heightRange = Math.max(1, globalMaxFloorY - globalMinFloorY);
        int maxCaveHeightNorm = Math.max(1, globalMaxCaveHeight);
        
        float imageToSampleRatioWidth = (float) sampleWidth / image.width;
        float imageToSampleRatioHeight = (float) sampleHeight / image.height;
        
        for (int ix = 0; ix < image.width; ix++) {
            for (int iz = 0; iz < image.height; iz++) {
                int sampleX = Math.min((int) (ix * imageToSampleRatioWidth), sampleWidth - 1);
                int sampleZ = Math.min((int) (iz * imageToSampleRatioHeight), sampleHeight - 1);
                int sampleIndex = sampleZ * sampleWidth + sampleX;
                
                CaveColumnData col = columnData[sampleIndex];
                renderColumn(col, globalMinFloorY, heightRange, maxCaveHeightNorm, ix, iz);
            }
        }
        
        return this;
    }
    
    /**
     * Scans a column using multi-layer analysis for better cave detection.
     * Instead of just finding one floor, we analyze the entire vertical slice.
     */
    private void scanColumnMultiLayer(int x, int z, CaveColumnData result) {
        result.reset();
        
        int minY = Math.max(0, targetYLevel - verticalRange);
        int maxY = Math.min(319, targetYLevel + verticalRange);
        
        int airCount = 0;
        int solidCount = 0;
        int totalBlocks = maxY - minY + 1;
        
        int lowestAirY = -1;
        int highestAirY = -1;
        int bestFloorY = -1;
        int bestFloorBlock = 0;
        int ceilingY = -1;
        
        boolean inCave = false;
        int currentCaveFloorY = -1;
        int currentCaveCeilingY = -1;
        int bestCaveSize = 0;
        
        for (int y = minY; y <= maxY; y++) {
            int blockId = worldChunk.getBlock(x, y, z);
            boolean isAir = isAirOrPassable(blockId);
            
            int fluidId = getFluidAt(x, y, z);
            if (fluidId != 0) {
                if (result.primaryFluidId == 0) {
                    result.primaryFluidId = fluidId;
                    result.fluidY = y;
                    result.fluidDepth = 1;
                } else if (fluidId == result.primaryFluidId) {
                    result.fluidDepth++;
                }
                if (Math.abs(y - targetYLevel) < Math.abs(result.fluidY - targetYLevel)) {
                    result.primaryFluidId = fluidId;
                    result.fluidY = y;
                }
                result.hasFluid = true;
            }
            
            if (isAir) {
                airCount++;
                if (lowestAirY == -1) lowestAirY = y;
                highestAirY = y;
                
                if (!inCave) {
                    inCave = true;
                    currentCaveFloorY = y - 1;
                }
                currentCaveCeilingY = y;
            } else {
                solidCount++;
                
                if (inCave) {
                    int caveSize = currentCaveCeilingY - currentCaveFloorY;
                    
                    boolean containsTarget = currentCaveFloorY <= targetYLevel && currentCaveCeilingY >= targetYLevel;
                    boolean bestContainsTarget = bestFloorY != -1 && 
                        bestFloorY <= targetYLevel && (bestFloorY + bestCaveSize) >= targetYLevel;
                    
                    if (containsTarget && !bestContainsTarget) {
                        bestFloorY = currentCaveFloorY;
                        bestFloorBlock = worldChunk.getBlock(x, Math.max(0, currentCaveFloorY), z);
                        ceilingY = currentCaveCeilingY + 1;
                        bestCaveSize = caveSize;
                    } else if ((containsTarget == bestContainsTarget) && caveSize > bestCaveSize) {
                        bestFloorY = currentCaveFloorY;
                        bestFloorBlock = worldChunk.getBlock(x, Math.max(0, currentCaveFloorY), z);
                        ceilingY = currentCaveCeilingY + 1;
                        bestCaveSize = caveSize;
                    }
                    
                    inCave = false;
                }
            }
        }
        
        if (inCave && currentCaveFloorY != -1) {
            int caveSize = currentCaveCeilingY - currentCaveFloorY;
            if (caveSize > bestCaveSize) {
                bestFloorY = currentCaveFloorY;
                bestFloorBlock = worldChunk.getBlock(x, Math.max(0, currentCaveFloorY), z);
                ceilingY = maxY;
                bestCaveSize = caveSize;
            }
        }
        
        result.openness = (float) airCount / totalBlocks;
        result.totalAirBlocks = airCount;
        
        if (bestFloorY >= 0 && bestCaveSize >= 2) {
            result.hasValidFloor = true;
            result.floorY = bestFloorY;
            result.floorBlockId = bestFloorBlock;
            result.ceilingY = ceilingY;
            result.caveHeight = bestCaveSize;
        } else if (airCount > 0) {
            result.hasValidFloor = false;
            result.floorY = lowestAirY > 0 ? lowestAirY - 1 : targetYLevel;
            result.floorBlockId = worldChunk.getBlock(x, Math.max(0, result.floorY), z);
            result.caveHeight = highestAirY - lowestAirY + 1;
        }
        
        int targetBlock = worldChunk.getBlock(x, targetYLevel, z);
        result.isWallAtTarget = !isAirOrPassable(targetBlock);
        result.targetBlockId = targetBlock;
        
        result.lightLevel = Math.max(
            worldChunk.getBlockChunk().getBlockLight(x, targetYLevel, z),
            worldChunk.getBlockChunk().getSkyLight(x, targetYLevel, z)
        );
        
        if (result.hasValidFloor && result.floorY >= 0 && result.floorY < 320) {
            int floorLight = Math.max(
                worldChunk.getBlockChunk().getBlockLight(x, result.floorY + 1, z),
                worldChunk.getBlockChunk().getSkyLight(x, result.floorY + 1, z)
            );
            result.lightLevel = Math.max(result.lightLevel, floorLight);
        }
    }
    
    /**
     * Renders a single column to the output image.
     */
    private void renderColumn(CaveColumnData col, int minFloorY, int heightRange, 
                              int maxCaveHeight, int imageX, int imageZ) {
        
        if (col.hasFluid && col.primaryFluidId != 0) {
            renderFluid(col.primaryFluidId, col.fluidY, col.caveHeight, col.fluidDepth);
            image.data[imageZ * image.width + imageX] = outColor.pack();
            return;
        }
        
        if (col.isWallAtTarget && col.openness < 0.15f) {
            renderWall(col.targetBlockId, col.openness);
            image.data[imageZ * image.width + imageX] = outColor.pack();
            return;
        }
        
        if (col.hasValidFloor) {
            renderCaveBySize(col, minFloorY, heightRange, maxCaveHeight);
            image.data[imageZ * image.width + imageX] = outColor.pack();
            return;
        }
        
        if (col.openness > 0.1f) {
            renderPartialCave(col, maxCaveHeight);
            image.data[imageZ * image.width + imageX] = outColor.pack();
            return;
        }
        
        image.data[imageZ * image.width + imageX] = COLOR_WALL_SOLID;
    }
    
    /**
     * Renders a cave based on its SIZE - the main visual indicator.
     * Larger caves = brighter/warmer colors, small caves = darker.
     */
    private void renderCaveBySize(CaveColumnData col, int minFloorY, int heightRange, int maxCaveHeight) {
        int caveHeight = col.caveHeight;
        
        if (caveHeight <= 3) {
            unpackColor(COLOR_CAVE_TINY, outColor);
        } else if (caveHeight <= 6) {
            float t = (caveHeight - 3) / 3.0f;
            lerpColor(COLOR_CAVE_TINY, COLOR_CAVE_SMALL, t, outColor);
        } else if (caveHeight <= 12) {
            float t = (caveHeight - 6) / 6.0f;
            lerpColor(COLOR_CAVE_SMALL, COLOR_CAVE_MEDIUM, t, outColor);
        } else if (caveHeight <= 20) {
            float t = (caveHeight - 12) / 8.0f;
            lerpColor(COLOR_CAVE_MEDIUM, COLOR_CAVE_LARGE, t, outColor);
        } else {
            float t = Math.min(1.0f, (caveHeight - 20) / 15.0f);
            lerpColor(COLOR_CAVE_LARGE, COLOR_CAVE_HUGE, t, outColor);
            outColor.r = Math.min(255, outColor.r + 10);
        }
        
        float depthFactor = (float)(col.floorY - minFloorY) / heightRange;
        depthFactor = Math.max(0, Math.min(1, depthFactor));
        float brightness = 0.8f + 0.2f * depthFactor;
        outColor.multiply(brightness);
        
        if (col.openness > 0.4f) {
            outColor.brighten(0.1f * col.openness);
        }
    }
    
    /**
     * Renders a partial cave (some air space but no clear floor/ceiling).
     */
    private void renderPartialCave(CaveColumnData col, int maxCaveHeight) {
        float openFactor = Math.min(1.0f, col.openness * 2.5f);
        
        int wallR = (COLOR_WALL_SOLID >> 24) & 0xFF;
        int wallG = (COLOR_WALL_SOLID >> 16) & 0xFF;
        int wallB = (COLOR_WALL_SOLID >> 8) & 0xFF;
        
        int caveR = (COLOR_CAVE_SMALL >> 24) & 0xFF;
        int caveG = (COLOR_CAVE_SMALL >> 16) & 0xFF;
        int caveB = (COLOR_CAVE_SMALL >> 8) & 0xFF;
        
        outColor.r = (int) lerp(wallR, caveR, openFactor);
        outColor.g = (int) lerp(wallG, caveG, openFactor);
        outColor.b = (int) lerp(wallB, caveB, openFactor);
        outColor.a = 255;
    }
    
    /**
     * Renders fluid with depth-based coloring.
     * Deeper pools = darker/more saturated, shallow = lighter.
     */
    private void renderFluid(int fluidId, int fluidY, int caveHeight, int fluidDepth) {
        Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
        
        if (fluid != null && fluid.hasEffect(ShaderType.Lava)) {
            float depthIntensity = Math.min(1.0f, fluidDepth / 8.0f);
            outColor.r = 255;
            outColor.g = (int) lerp(180, 80, depthIntensity);
            outColor.b = (int) lerp(50, 20, depthIntensity);
            outColor.a = 255;
            
            if (fluidDepth > 4) {
                outColor.r = 255;
                outColor.g = Math.max(60, outColor.g - 20);
            }
        } else {
            float depthFactor = Math.min(1.0f, fluidDepth / 10.0f);
            
            outColor.r = (int) lerp(80, 20, depthFactor);
            outColor.g = (int) lerp(180, 60, depthFactor);
            outColor.b = (int) lerp(230, 140, depthFactor);
            outColor.a = (int) lerp(180, 240, depthFactor);
            
            if (fluidDepth > 6) {
                outColor.b = Math.min(255, outColor.b + 20);
            }
        }
    }
    
    /**
     * Lerps between two packed colors and stores in outColor.
     */
    private void lerpColor(int colorA, int colorB, float t, Color out) {
        int aR = (colorA >> 24) & 0xFF;
        int aG = (colorA >> 16) & 0xFF;
        int aB = (colorA >> 8) & 0xFF;
        int aA = colorA & 0xFF;
        
        int bR = (colorB >> 24) & 0xFF;
        int bG = (colorB >> 16) & 0xFF;
        int bB = (colorB >> 8) & 0xFF;
        int bA = colorB & 0xFF;
        
        out.r = (int) lerp(aR, bR, t);
        out.g = (int) lerp(aG, bG, t);
        out.b = (int) lerp(aB, bB, t);
        out.a = (int) lerp(aA, bA, t);
    }
    
    /**
     * Renders wall/solid blocks.
     */
    private void renderWall(int blockId, float openness) {
        int baseR = (COLOR_WALL_SOLID >> 24) & 0xFF;
        int baseG = (COLOR_WALL_SOLID >> 16) & 0xFF;
        int baseB = (COLOR_WALL_SOLID >> 8) & 0xFF;
        
        float factor = 1.0f + openness * 0.5f;
        outColor.r = Math.min(255, (int)(baseR * factor));
        outColor.g = Math.min(255, (int)(baseG * factor));
        outColor.b = Math.min(255, (int)(baseB * factor));
        outColor.a = 255;
    }
    
    /**
     * Gets floor color based on block type (used for subtle block-based tinting).
     */
    private void getFloorBlockColor(int blockId, Color outColor) {
        if (blockId == 0) {
            outColor.r = 100;
            outColor.g = 85;
            outColor.b = 70;
            outColor.a = 255;
            return;
        }
        
        BlockType block = BlockType.getAssetMap().getAsset(blockId);
        if (block == null) {
            outColor.r = 130;
            outColor.g = 110;
            outColor.b = 90;
            outColor.a = 255;
            return;
        }
        
        com.hypixel.hytale.protocol.Color particleColor = block.getParticleColor();
        if (particleColor != null) {
            outColor.r = particleColor.red & 255;
            outColor.g = particleColor.green & 255;
            outColor.b = particleColor.blue & 255;
            outColor.a = 255;
            
            outColor.r = Math.min(255, outColor.r + 10);
            outColor.g = Math.max(0, outColor.g - 5);
        } else {
            outColor.r = 140;
            outColor.g = 115;
            outColor.b = 95;
            outColor.a = 255;
        }
    }
    
    /**
     * Checks if a block is air or passable.
     */
    private boolean isAirOrPassable(int blockId) {
        if (blockId == 0) return true;
        
        BlockType block = BlockType.getAssetMap().getAsset(blockId);
        if (block == null) return true;
        
        return block.getParticleColor() == null && block.getTintUp() == null;
    }
    
    /**
     * Gets fluid ID at position.
     */
    private int getFluidAt(int x, int y, int z) {
        int chunkY = ChunkUtil.chunkCoordinate(y);
        if (chunkY >= 0 && chunkY < 10 && fluidSections != null) {
            FluidSection fluidSection = fluidSections[chunkY];
            if (fluidSection != null && !fluidSection.isEmpty()) {
                return fluidSection.getFluidId(x, y, z);
            }
        }
        return 0;
    }
        
    private static int packColor(int r, int g, int b, int a) {
        return (r & 0xFF) << 24 | (g & 0xFF) << 16 | (b & 0xFF) << 8 | (a & 0xFF);
    }
    
    private static void unpackColor(int color, Color out) {
        out.r = (color >> 24) & 0xFF;
        out.g = (color >> 16) & 0xFF;
        out.b = (color >> 8) & 0xFF;
        out.a = color & 0xFF;
    }
    
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    /**
     * Builds a cave mode map image asynchronously.
     */
    @Nonnull
    public static CompletableFuture<CaveModeImageBuilder> build(long index, int imageWidth, int imageHeight,
                                                                  World world, int yLevel, int range) {
        return CompletableFuture.completedFuture(new CaveModeImageBuilder(index, imageWidth, imageHeight, world, yLevel, range))
                .thenCompose(CaveModeImageBuilder::fetchChunk)
                .thenApplyAsync(builder -> builder != null ? builder.generateCaveImage() : null);
    }
        
    /**
     * Stores analyzed data for a single column.
     */
    private static class CaveColumnData {
        boolean hasValidFloor = false;
        int floorY = -1;
        int floorBlockId = 0;
        int ceilingY = -1;
        int caveHeight = 0;
        
        boolean hasFluid = false;
        int primaryFluidId = 0;
        int fluidY = -1;
        int fluidDepth = 0;
        
        boolean isWallAtTarget = false;
        int targetBlockId = 0;
        
        float openness = 0;
        int lightLevel = 0;
        int totalAirBlocks = 0;
        
        void reset() {
            hasValidFloor = false;
            floorY = -1;
            floorBlockId = 0;
            ceilingY = -1;
            caveHeight = 0;
            hasFluid = false;
            primaryFluidId = 0;
            fluidY = -1;
            fluidDepth = 0;
            isWallAtTarget = false;
            targetBlockId = 0;
            openness = 0;
            lightLevel = 0;
            totalAirBlocks = 0;
        }
    }
    
    /**
     * Simple color class for manipulation.
     */
    private static class Color {
        int r, g, b, a;
        
        int pack() {
            return (r & 0xFF) << 24 | (g & 0xFF) << 16 | (b & 0xFF) << 8 | (a & 0xFF);
        }
        
        void multiply(float value) {
            r = Math.min(255, Math.max(0, (int)(r * value)));
            g = Math.min(255, Math.max(0, (int)(g * value)));
            b = Math.min(255, Math.max(0, (int)(b * value)));
        }
        
        void brighten(float amount) {
            r = Math.min(255, (int)(r + 255 * amount));
            g = Math.min(255, (int)(g + 255 * amount));
            b = Math.min(255, (int)(b + 255 * amount));
        }
    }
}
