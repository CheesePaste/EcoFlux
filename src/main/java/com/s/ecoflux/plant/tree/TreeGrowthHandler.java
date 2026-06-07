package com.s.ecoflux.plant.tree;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class TreeGrowthHandler {
    public static final TreeGrowthHandler INSTANCE = new TreeGrowthHandler();

    static final int DEFAULT_TOTAL_STAGES = 5;
    static final int DEFAULT_TICKS_PER_STAGE = 2400;

    private final Map<BlockPos, TreeGrowthSession> activeGrowths = new HashMap<>();

    private TreeGrowthHandler() {
    }

    public void interceptGrowth(ServerLevel level, BlockPos pos, ActiveVegetationRecord record) {
        if (activeGrowths.containsKey(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        ResourceLocation treeType = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        TreeGrowthSession session = new TreeGrowthSession(
                pos.immutable(),
                treeType,
                level.getGameTime(),
                DEFAULT_TOTAL_STAGES,
                DEFAULT_TICKS_PER_STAGE);
        activeGrowths.put(pos.immutable(), session);

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Intercepted tree growth at {} (type={}), session created. totalStages={}, ticksPerStage={}",
                pos, treeType, DEFAULT_TOTAL_STAGES, DEFAULT_TICKS_PER_STAGE);
    }

    @Nullable
    public TreeGrowthSession getSession(BlockPos pos) {
        return activeGrowths.get(pos);
    }

    public void removeSession(BlockPos pos) {
        activeGrowths.remove(pos);
    }

    public Map<BlockPos, TreeGrowthSession> activeGrowths() {
        return activeGrowths;
    }
}
