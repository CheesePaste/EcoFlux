package com.cp.ecoflux.client.visual;

/**
 * Visual adapter for small and tall flowers.
 *
 * <p>Structure: matches {@code BlockTags.SMALL_FLOWERS}, {@code TALL_FLOWERS},
 * and the broader {@code FLOWERS} tag. Provides a profile with 200 born / 1000
 * growing / 46800 mature / 24000 aging ticks, a scale curve from 0.30 (born)
 * through 1.0 (mature) to 0.86 (aging), and an aging color shift toward
 * desaturated, darkened brown tones (hue -0.03, saturation x0.72, brightness x0.84).
 * <p>Role in Ecoflux: one of four registered adapters in
 * {@link VisualLifecycleRegistry}, giving flower blocks their own visual
 * lifecycle distinct from grass and saplings.
 */

import com.cp.ecoflux.EcofluxConstants;
import java.util.List;

import com.cp.ecoflux.api.client.VisualLifecycleAdapter;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FlowerVisualLifecycleAdapter implements VisualLifecycleAdapter {
    public static final FlowerVisualLifecycleAdapter INSTANCE = new FlowerVisualLifecycleAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("flower_visual_growth");

    private FlowerVisualLifecycleAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(BlockTags.SMALL_FLOWERS)
                || state.is(BlockTags.TALL_FLOWERS)
                || (state.is(BlockTags.FLOWERS) && !state.is(BlockTags.SMALL_FLOWERS) && !state.is(BlockTags.TALL_FLOWERS));
    }

    @Override
    public VisualLifecycleProfile createProfile(BlockState state) {
        return new VisualLifecycleProfile(
                200,
                1000,
                46800,
                24000,
                0.30F,
                1.0F,
                0.86F,
                -0.03F,
                0.72F,
                0.84F);
    }

    @Override
    public List<Block> demoBlocks() {
        return List.of(
                Blocks.DANDELION,
                Blocks.POPPY,
                Blocks.BLUE_ORCHID,
                Blocks.ALLIUM,
                Blocks.AZURE_BLUET,
                Blocks.OXEYE_DAISY,
                Blocks.CORNFLOWER,
                Blocks.LILY_OF_THE_VALLEY,
                Blocks.TORCHFLOWER);
    }

    @Override
    public String supportSummary() {
        return "#minecraft:small_flowers";
    }
}
