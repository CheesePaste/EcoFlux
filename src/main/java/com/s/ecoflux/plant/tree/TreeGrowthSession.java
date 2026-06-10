package com.s.ecoflux.plant.tree;

import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import com.s.ecoflux.plant.tree.morphology.TreeMorphology;
import com.s.ecoflux.plant.tree.morphology.TreeMorphology.GrowStagePlan;
import com.s.ecoflux.plant.tree.morphology.TreeSkeleton;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

public final class TreeGrowthSession {
    private static final String SAPLING_POS = "sapling_pos";
    private static final String TREE_TYPE = "tree_type";
    private static final String GROWTH_START_TIME = "growth_start_time";
    private static final String CURRENT_STAGE = "current_stage";
    private static final String LAST_STAGE_TIME = "last_stage_time";
    private static final String TOTAL_STAGES = "total_stages";
    private static final String TICKS_PER_STAGE = "ticks_per_stage";
    private static final String RESOLVED_HEIGHT = "resolved_height";

    private final BlockPos saplingPos;
    private final ResourceLocation treeType;
    private final long growthStartTime;
    private final int totalStages;
    private final int ticksPerStage;
    private final int resolvedHeight;
    private int currentStage;
    private long lastStageTime;

    private transient TreeSkeleton skeleton;
    private transient MorphologyParams morphologyParams;
    private transient GrowStagePlan stagePlan;

    public TreeGrowthSession(
            BlockPos saplingPos,
            ResourceLocation treeType,
            long growthStartTime,
            int totalStages,
            int ticksPerStage,
            int resolvedHeight) {
        this.saplingPos = saplingPos;
        this.treeType = treeType;
        this.growthStartTime = growthStartTime;
        this.totalStages = totalStages;
        this.ticksPerStage = ticksPerStage;
        this.resolvedHeight = resolvedHeight;
        this.currentStage = 0;
        this.lastStageTime = growthStartTime;
    }

    public void ensureSkeleton(ServerLevel level, MorphologyParams params) {
        if (skeleton != null && params.equals(morphologyParams)) return;
        this.morphologyParams = params;
        RandomSource random = TreeShapeUtils.positionRandom(saplingPos, level.getSeed());
        this.skeleton = TreeMorphology.generateSkeleton(saplingPos, params, resolvedHeight, random);
        this.stagePlan = TreeMorphology.planStages(skeleton, params);
    }

    @Nullable
    public TreeSkeleton skeleton() {
        return skeleton;
    }

    @Nullable
    public MorphologyParams morphologyParams() {
        return morphologyParams;
    }

    @Nullable
    public GrowStagePlan stagePlan() {
        return stagePlan;
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

    public int resolvedHeight() {
        return resolvedHeight;
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
        tag.putInt(RESOLVED_HEIGHT, resolvedHeight);
        return tag;
    }

    public static TreeGrowthSession fromTag(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong(SAPLING_POS));
        ResourceLocation type = ResourceLocation.parse(tag.getString(TREE_TYPE));
        long startTime = tag.getLong(GROWTH_START_TIME);
        int stages = tag.getInt(TOTAL_STAGES);
        int interval = tag.getInt(TICKS_PER_STAGE);
        int height = tag.getInt(RESOLVED_HEIGHT);
        TreeGrowthSession session = new TreeGrowthSession(pos, type, startTime, stages, interval, height);
        session.currentStage = tag.getInt(CURRENT_STAGE);
        session.lastStageTime = tag.getLong(LAST_STAGE_TIME);
        return session;
    }
}
