package com.s.ecoflux.client.visual;

/**
 * Visual adapter for grass, ferns, and similar ground cover.
 *
 * <p>Structure: matches {@code Blocks.SHORT_GRASS}, {@code FERN},
 * {@code TALL_GRASS}, and {@code LARGE_FERN}. Provides a profile with 200 born /
 * 1000 growing / 46800 mature / 24000 aging ticks, a scale curve from 0.35 (born)
 * through 1.0 (mature) to 0.92 (aging), and an aging color shift toward dull,
 * desaturated green (hue -0.08, saturation x0.55, brightness x0.72).
 * <p>Role in Ecoflux: the first adapter checked in {@link VisualLifecycleRegistry},
 * giving grass and fern blocks a subtle growth animation and pronounced
 * desaturation during aging.
 */

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.config.EcofluxBlockTags;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class GrassVisualLifecycleAdapter implements VisualLifecycleAdapter {
    public static final GrassVisualLifecycleAdapter INSTANCE = new GrassVisualLifecycleAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("grass_visual_growth");

    private GrassVisualLifecycleAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(EcofluxBlockTags.GRASS_COVER);
    }

    @Override
    public VisualLifecycleProfile createProfile(BlockState state) {
        return new VisualLifecycleProfile(
                200,
                1000,
                46800,
                24000,
                0.35F,
                1.0F,
                0.92F,
                -0.08F,
                0.55F,
                0.72F);
    }

    @Override
    public List<Block> demoBlocks() {
        return List.of(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.TALL_GRASS, Blocks.LARGE_FERN);
    }

    @Override
    public String supportSummary() {
        return "minecraft:short_grass, minecraft:fern, minecraft:tall_grass, minecraft:large_fern";
    }
}
