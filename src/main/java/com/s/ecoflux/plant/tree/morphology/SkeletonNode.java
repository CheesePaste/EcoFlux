package com.s.ecoflux.plant.tree.morphology;

import net.minecraft.core.BlockPos;

public record SkeletonNode(
        BlockPos pos,
        NodeType type,
        float radius,
        int parentIndex,
        int depth
) {}
