package com.cp.ecoflux.client.visual;

/**
 * Fallback visual adapter for any non-air block not matched by a specific adapter.
 *
 * <p>Structure: matches any non-air block, excluding structural blocks (logs,
 * mushroom stems) that should always render at full scale. Provides a default
 * profile with 200 born / 1000 growing / 46800 mature / 24000 aging ticks,
 * scale from 0.35 to 1.0 to 0.90, and a mild aging color shift (hue -0.05,
 * saturation x0.75, brightness x0.82).
 * <p>Role in Ecoflux: the last adapter in {@link VisualLifecycleRegistry}'s
 * ordered list, ensuring every tracked block has at least a default visual
 * lifecycle. Excluding logs and stems prevents structural tree blocks from
 * being visually distorted while still allowing unrecognized plant types to
 * participate in the visual system.
 */

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.api.client.VisualLifecycleAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class GenericVisualLifecycleAdapter implements VisualLifecycleAdapter {
    public static final GenericVisualLifecycleAdapter INSTANCE = new GenericVisualLifecycleAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("generic_tracked_visual");

    private GenericVisualLifecycleAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        // Exclude structural blocks that should always render at full scale
        if (state.is(BlockTags.LOGS) || state.is(Blocks.MUSHROOM_STEM)) {
            return false;
        }
        return !state.isAir();
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
                0.90F,
                -0.05F,
                0.75F,
                0.82F);
    }

    @Override
    public String supportSummary() {
        return "any tracked non-air block (excluding logs and stems)";
    }
}
