package com.s.ecoflux.client.visual;

/**
 * GPU texture encoding per-block scale and pivot for the custom vertex shader.
 *
 * <p>Hash key: camera-relative block position = {@code round(worldBlock - cameraPos)}.
 * Shader computes the same value as {@code ivec3(round(ChunkOffset + Position))}.
 * No world-space math, no CameraPos uniform — purely section-local.
 *
 * <p>Pixel encoding:
 * <pre>
 *   R = clamp(scale / 2.0, 0, 1) * 255
 *   G = sectionLocalX * 17   (0..15)
 *   B = sectionLocalY * 17
 *   A = sectionLocalZ * 17
 * </pre>
 */

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.s.ecoflux.EcofluxConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class EcofluxScaleTexture {
    public static final EcofluxScaleTexture INSTANCE = new EcofluxScaleTexture();

    static final int SIZE = 2048;
    static final int SIZE_MASK = SIZE - 1;
    static final ResourceLocation TEXTURE_ID = EcofluxConstants.id("scale_map");

    private NativeImage image;
    private DynamicTexture texture;
    private boolean ready;
    private int camRoundX, camRoundY, camRoundZ;

    private static final int DEFAULT_PIXEL = 0x00808080;

    private EcofluxScaleTexture() {
    }

    public void ensureReady() {
        if (ready) return;
        RenderSystem.assertOnRenderThread();
        image = new NativeImage(SIZE, SIZE, false);
        image.fillRect(0, 0, SIZE, SIZE, DEFAULT_PIXEL);
        texture = new DynamicTexture(image);
        texture.upload();
        ready = true;
    }

    /** Clear and snapshot camera position for this frame's hash. */
    public void clearAll() {
        if (!ready) return;
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        // round(camPos) used by Java; shader uses round(ChunkOffset + Position) which
        // is equivalent: round((sectionOrigin - camPos) + blockLocal) = round(worldBlock - camPos)
        camRoundX = (int) Math.round(cam.getPosition().x());
        camRoundY = (int) Math.round(cam.getPosition().y());
        camRoundZ = (int) Math.round(cam.getPosition().z());
        image.fillRect(0, 0, SIZE, SIZE, DEFAULT_PIXEL);
    }

    /** Write scale + section-local pivot for a block at absolute world position. */
    public void writeScale(int worldX, int worldY, int worldZ, float scale) {
        if (!ready) return;
        int sx = worldX & 15;
        int sy = worldY & 15;
        int sz = worldZ & 15;
        int pixel = encode(scale, sx, sy, sz);

        // Camera-relative position: the shader computes ivec3(round(ChunkOffset + Position))
        // which equals round(worldBlock - camPos). Use += to share in 8-neighbor loop.
        int rx = worldX - camRoundX;
        int ry = worldY - camRoundY;
        int rz = worldZ - camRoundZ;

        // 8 neighbors so boundary vertices always hit a texel even when floor(Position)
        // lands on the adjacent block
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int idx = hash(rx + dx, ry + dy, rz + dz) & 0x3FFFFF;
                    image.setPixelRGBA(idx & SIZE_MASK, (idx >> 11) & SIZE_MASK, pixel);
                }
            }
        }
    }

    static int encode(float scale, int sx, int sy, int sz) {
        int r = (int) (clamp(scale / 2.0f, 0.0f, 1.0f) * 255.0f);
        return (sz * 17 << 24) | (sy * 17 << 16) | (sx * 17 << 8) | r;
    }

    public void upload() {
        if (!ready) return;
        texture.upload();
    }

    public boolean isReady() { return ready; }
    public int getGlId() { return texture != null ? texture.getId() : 0; }

    public static int hash(int x, int y, int z) {
        int h = x * 73856093;
        h ^= y * 19349663;
        h ^= z * 83492791;
        return h;
    }

    private static float clamp(float v, float low, float high) {
        return v < low ? low : (Math.min(v, high));
    }
}
