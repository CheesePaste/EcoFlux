package com.cp.ecoflux.client.ui.map;

/**
 * Renders a top-down satellite map using vanilla's
 * {@link net.minecraft.client.renderer.texture.DynamicTexture} approach
 * (same technique as vanilla maps).
 *
 * <p>For an 8×8 chunk viewport, samples every block column (128×128 pixels,
 * one pixel per block) to produce a
 * {@link com.mojang.blaze3d.platform.NativeImage}. Each pixel uses the
 * block's {@link net.minecraft.world.level.material.MapColor} with
 * height-difference edge highlighting so terrain features are legible.
 *
 * <p>Cache strategy:
 * <ul>
 *   <li>Rebuilds the entire texture when the player changes chunk.</li>
 *   <li>Rebuilds every 20 ticks as a periodic refresh.</li>
 *   <li>Renders from the cached texture otherwise.</li>
 * </ul>
 *
 * <p>The Screen calls {@code tick()} each client tick and {@code render()}
 * from its own {@code render()} method.
 */

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class SatelliteMapRenderer {

    public static final int CHUNK_GRID = 8;

    /** One pixel per block column in the viewport. */
    private static final int TEXELS = CHUNK_GRID * 16; // 128

    private DynamicTexture texture;
    private int glTextureId = -1;

    private ChunkPos lastCenterChunk;
    private int tickCounter;
    private boolean dirty = true;

    // ── Tick ─────────────────────────────────────────────────────────

    public void tick() {
        if (++tickCounter >= 20) {
            tickCounter = 0;
            forceDirty();
        }
    }

    public void forceDirty() {
        dirty = true;
    }

    // ── Build ────────────────────────────────────────────────────────

    private void rebuildIfNeeded(Level level, ChunkPos centerChunk) {
        boolean centerChanged = !centerChunk.equals(lastCenterChunk);
        if (centerChanged) {
            lastCenterChunk = centerChunk;
            dirty = true;
        }
        if (!dirty) return;
        if (level == null) return;

        // Allocate or reuse the NativeImage
        NativeImage image;
        if (texture != null && texture.getPixels() != null) {
            image = texture.getPixels();
        } else {
            image = new NativeImage(TEXELS, TEXELS, false);
            texture = new DynamicTexture(image);
            glTextureId = texture.getId();
        }

        // Top-left world-block corner
        int baseWorldX = (centerChunk.x - CHUNK_GRID / 2) << 4;
        int baseWorldZ = (centerChunk.z - CHUNK_GRID / 2) << 4;

        // Pre-read all surface heights into a 2D array so we can compute
        // edge highlights from height differences.
        int[] heights = new int[TEXELS * TEXELS];

        for (int z = 0; z < TEXELS; z++) {
            int worldZ = baseWorldZ + z;
            for (int x = 0; x < TEXELS; x++) {
                int worldX = baseWorldX + x;
                int idx = z * TEXELS + x;

                int chunkX = worldX >> 4;
                int chunkZ = worldZ >> 4;
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    heights[idx] = Integer.MIN_VALUE;
                    continue;
                }

                heights[idx] = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
            }
        }

        // Second pass: resolve colors
        for (int z = 0; z < TEXELS; z++) {
            int worldZ = baseWorldZ + z;
            for (int x = 0; x < TEXELS; x++) {
                int worldX = baseWorldX + x;
                int idx = z * TEXELS + x;

                int surfaceY = heights[idx];
                if (surfaceY == Integer.MIN_VALUE) {
                    image.setPixelRGBA(x, z, 0xFF404040); // unloaded = dark gray
                    continue;
                }

                int solidY = surfaceY - 1;
                if (solidY < level.getMinBuildHeight()) {
                    image.setPixelRGBA(x, z, 0xFF404040);
                    continue;
                }

                BlockPos pos = new BlockPos(worldX, solidY, worldZ);
                BlockState state = level.getBlockState(pos);

                // Skip transparent blocks; dig down to find the actual surface
                int attempts = 0;
                while (state.isAir() && pos.getY() > level.getMinBuildHeight() && attempts < 32) {
                    pos = pos.below();
                    state = level.getBlockState(pos);
                    attempts++;
                }

                if (state.isAir()) {
                    image.setPixelRGBA(x, z, 0xFF404040);
                    continue;
                }

                MapColor mapColor = state.getMapColor(level, pos);
                MapColor.Brightness brightness = computeBrightness(heights, TEXELS, x, z);
                int argb = mapColor.calculateRGBColor(brightness);
                image.setPixelRGBA(x, z, argb);
            }
        }

        // Upload to GPU
        texture.upload();
        dirty = false;
    }

    /**
     * Uses height differences with neighbors to create an edge-highlight
     * effect similar to relief shading on real satellite maps.
     */
    private static MapColor.Brightness computeBrightness(int[] heights, int stride, int x, int z) {
        int center = heights[z * stride + x];
        if (center == Integer.MIN_VALUE) return MapColor.Brightness.NORMAL;

        // Skip neighbor checks at image borders
        if (x < 1 || x >= stride - 1 || z < 1 || z >= stride - 1) {
            return MapColor.Brightness.NORMAL;
        }

        int left   = heights[z * stride + (x - 1)];
        int right  = heights[z * stride + (x + 1)];
        int top    = heights[(z - 1) * stride + x];
        int bottom = heights[(z + 1) * stride + x];

        int maxDiff = 0;
        if (left   != Integer.MIN_VALUE) maxDiff = Math.max(maxDiff, Math.abs(center - left));
        if (right  != Integer.MIN_VALUE) maxDiff = Math.max(maxDiff, Math.abs(center - right));
        if (top    != Integer.MIN_VALUE) maxDiff = Math.max(maxDiff, Math.abs(center - top));
        if (bottom != Integer.MIN_VALUE) maxDiff = Math.max(maxDiff, Math.abs(center - bottom));

        if (maxDiff >= 4) return MapColor.Brightness.LOWEST;  // steep edge → dark
        if (maxDiff >= 2) return MapColor.Brightness.LOW;      // moderate slope
        return MapColor.Brightness.NORMAL;                      // flat terrain
    }

    // ── Render ───────────────────────────────────────────────────────

    /**
     * Draws the satellite map as a single textured quad at screen
     * coordinates ({@code mapLeft}, {@code mapTop})–({@code mapLeft+mapSize},
     * {@code mapTop+mapSize}).
     */
    public void render(int mapLeft, int mapTop, int mapSize) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        rebuildIfNeeded(level, new ChunkPos(mc.player.blockPosition()));

        if (texture == null) return;

        RenderSystem.setShaderTexture(0, glTextureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        float x1 = mapLeft;
        float y1 = mapTop;
        float x2 = mapLeft + mapSize;
        float y2 = mapTop  + mapSize;

        builder.addVertex(x1, y2, 0).setUv(0, 1);
        builder.addVertex(x2, y2, 0).setUv(1, 1);
        builder.addVertex(x2, y1, 0).setUv(1, 0);
        builder.addVertex(x1, y1, 0).setUv(0, 0);

        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ── Accessors ────────────────────────────────────────────────────

    public ChunkPos getCenterChunk() {
        return lastCenterChunk;
    }

    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
            glTextureId = -1;
        }
    }
}
