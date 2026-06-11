package com.s.ecoflux.client.visual;

/**
 * Visual adapter for tree saplings.
 *
 * <p>Structure: matches {@code BlockTags.SAPLINGS} (all vanilla sapling types).
 * Provides a profile with 200 born / 1000 growing / 46800 mature / 24000 aging
 * ticks, and a scale curve that overshoots slightly at maturity (0.45 born ->
 * 1.08 mature -> 0.94 aging), representing the sapling's growth surge before the
 * tree transformation event.
 * <p>Role in Ecoflux: one of four registered adapters in
 * {@link VisualLifecycleRegistry}. The mature overshoot scale (>1.0) visually
 * signals that the sapling is approaching its transformation into a full tree
 * via the server-side {@code TreeGrowthHandler}.
 */

import com.s.ecoflux.EcofluxConstants;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SaplingVisualLifecycleAdapter implements VisualLifecycleAdapter {
    public static final SaplingVisualLifecycleAdapter INSTANCE = new SaplingVisualLifecycleAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("sapling_visual_growth");

    private SaplingVisualLifecycleAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(BlockTags.SAPLINGS);
    }

    @Override
    public VisualLifecycleProfile createProfile(BlockState state) {
        return new VisualLifecycleProfile(
                200,
                1000,
                46800,
                24000,
                0.45F,
                1.08F,
                0.94F,
                -0.08F,
                0.70F,
                0.76F);
    }

    @Override
    public List<Block> demoBlocks() {
        return List.of(
                Blocks.OAK_SAPLING,
                Blocks.BIRCH_SAPLING,
                Blocks.SPRUCE_SAPLING,
                Blocks.JUNGLE_SAPLING,
                Blocks.ACACIA_SAPLING,
                Blocks.DARK_OAK_SAPLING,
                Blocks.CHERRY_SAPLING);
    }

    @Override
    public String supportSummary() {
        return "#minecraft:saplings";
    }
}
