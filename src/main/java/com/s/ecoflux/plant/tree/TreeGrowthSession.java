package com.s.ecoflux.plant.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class TreeGrowthSession {
    private static final String SAPLING_POS = "sapling_pos";
    private static final String TREE_TYPE = "tree_type";
    private static final String GROWTH_START_TIME = "growth_start_time";
    private static final String CURRENT_STAGE = "current_stage";
    private static final String LAST_STAGE_TIME = "last_stage_time";
    private static final String TOTAL_STAGES = "total_stages";
    private static final String TICKS_PER_STAGE = "ticks_per_stage";

    private final BlockPos saplingPos;
    private final ResourceLocation treeType;
    private final long growthStartTime;
    private final int totalStages;
    private final int ticksPerStage;
    private int currentStage;
    private long lastStageTime;

    public TreeGrowthSession(
            BlockPos saplingPos,
            ResourceLocation treeType,
            long growthStartTime,
            int totalStages,
            int ticksPerStage) {
        this.saplingPos = saplingPos;
        this.treeType = treeType;
        this.growthStartTime = growthStartTime;
        this.totalStages = totalStages;
        this.ticksPerStage = ticksPerStage;
        this.currentStage = 0;
        this.lastStageTime = growthStartTime;
    }

    public BlockPos saplingPos() {
        return saplingPos;
    }

    public ResourceLocation treeType() {
        return treeType;
    }

    public long growthStartTime() {
        return growthStartTime;
    }

    public int totalStages() {
        return totalStages;
    }

    public int ticksPerStage() {
        return ticksPerStage;
    }

    public int currentStage() {
        return currentStage;
    }

    public long lastStageTime() {
        return lastStageTime;
    }

    public boolean isComplete() {
        return currentStage >= totalStages;
    }

    public void advanceStage(long gameTime) {
        currentStage++;
        lastStageTime = gameTime;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(SAPLING_POS, saplingPos.asLong());
        tag.putString(TREE_TYPE, treeType.toString());
        tag.putLong(GROWTH_START_TIME, growthStartTime);
        tag.putInt(CURRENT_STAGE, currentStage);
        tag.putLong(LAST_STAGE_TIME, lastStageTime);
        tag.putInt(TOTAL_STAGES, totalStages);
        tag.putInt(TICKS_PER_STAGE, ticksPerStage);
        return tag;
    }

    public static TreeGrowthSession fromTag(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong(SAPLING_POS));
        ResourceLocation type = ResourceLocation.parse(tag.getString(TREE_TYPE));
        long startTime = tag.getLong(GROWTH_START_TIME);
        int stages = tag.getInt(TOTAL_STAGES);
        int interval = tag.getInt(TICKS_PER_STAGE);
        TreeGrowthSession session = new TreeGrowthSession(pos, type, startTime, stages, interval);
        session.currentStage = tag.getInt(CURRENT_STAGE);
        session.lastStageTime = tag.getLong(LAST_STAGE_TIME);
        return session;
    }
}
