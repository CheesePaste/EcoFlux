package com.s.ecoflux.plant.tree;

import net.minecraft.core.BlockPos;

/**
 * A block placed during a tree growth stage, tagged with the animation type
 * the client should play.
 */
public record GrowthPlacement(BlockPos pos, byte animType) {
    public static final byte ANIM_TRUNK = 0;
    public static final byte ANIM_LEAF_INFLATE = 1;
    public static final byte ANIM_LEAF_CLUSTER = 2;
}
