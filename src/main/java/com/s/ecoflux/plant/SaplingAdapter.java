package com.s.ecoflux.plant;

/**
 * {@link VegetationTypeAdapter} for tree saplings and propagules (e.g.
 * mangrove).
 *
 * <p>Structure: singleton recognizing any block that is an {@code instanceof}
 * {@link net.minecraft.world.level.block.SaplingBlock} or in the
 * {@code SAPLINGS} block tag. Implements the BORN/JUVENILE/GROWING lifecycle
 * and overrides {@link #detectTransformation} to detect when the sapling has
 * been replaced by a log or leaf block — at which point it emits a
 * {@link VegetationTransformation} targeting
 * {@link TreeStructureAdapter#TYPE_ID}, effectively promoting the tracked
 * record from sapling to mature tree.
 *
 * <p>Role in Ecoflux: bridges the gap between block-level sapling growth
 * (intercepted by {@code SaplingBlockMixin} and routed through
 * {@link com.s.ecoflux.plant.tree.TreeGrowthHandler}) and the succession
 * tracker. When the morphology system finishes growing a tree, the sapling
 * block is replaced with wood/leaves; this adapter detects that change and
 * transitions the vegetation record to the tree category so it continues
 * contributing points as mature tree biomass.
 */

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.config.EcofluxServerConfig;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SaplingAdapter implements VegetationTypeAdapter {
    public static final SaplingAdapter INSTANCE = new SaplingAdapter();
    public static final ResourceLocation TYPE_ID = EcofluxConstants.id("sapling");
    private static final long DECAY_TICKS = 6000L;

    private SaplingAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public VegetationCategory category() {
        return VegetationCategory.SAPLING;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.getBlock() instanceof SaplingBlock || state.is(BlockTags.SAPLINGS);
    }

    @Override
    public ActiveVegetationRecord captureBirth(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            long gameTime,
            Optional<ResourceLocation> sourceBiomeId,
            Optional<ResourceLocation> sourcePathId) {
        if (!EcofluxServerConfig.gradualPlantGrowth()) {
            return new ActiveVegetationRecord(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    typeId(),
                    VegetationCategory.SAPLING,
                    pos.immutable(),
                    VegetationLifecycleStage.GROWING,
                    gameTime,
                    gameTime,
                    gameTime + 144000L,
                    2,
                    2,
                    sourceBiomeId.orElse(null),
                    sourcePathId.orElse(null),
                    null);
        }
        return new ActiveVegetationRecord(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                typeId(),
                VegetationCategory.SAPLING,
                pos.immutable(),
                VegetationLifecycleStage.BORN,
                gameTime,
                gameTime,
                gameTime + 144000L,
                2,
                1,
                sourceBiomeId.orElse(null),
                sourcePathId.orElse(null),
                null);
    }

    @Override
    public VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record, BlockState state, long gameTime) {
        if (state.isAir()) {
            return VegetationObservation.absent("树苗已消失。");
        }

        Optional<VegetationTransformation> transformation = detectTransformation(level, record, state, gameTime);
        if (transformation.isPresent()) {
            return new VegetationObservation(
                    true,
                    VegetationLifecycleStage.TRANSFORMED,
                    transformation.get().targetCurrentPointValue(),
                    true,
                    false,
                    transformation,
                    "树苗已转化为树木结构。");
        }

        if (!matches(state)) {
            return VegetationObservation.absent("树苗方块已被不支持的方块替换。");
        }

        if (!EcofluxServerConfig.gradualPlantGrowth()) {
            return new VegetationObservation(
                    true,
                    VegetationLifecycleStage.GROWING,
                    2,
                    false,
                    false,
                    Optional.empty(),
                    "树苗（跳过生命周期阶段推进）。");
        }

        long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
        VegetationLifecycleStage stage = age < 1200L ? VegetationLifecycleStage.JUVENILE : VegetationLifecycleStage.GROWING;
        int pointValue = age < 24000L ? 1 : 2;

        if (gameTime >= record.expireGameTime() + DECAY_TICKS) {
            return VegetationObservation.absent("树苗已死亡并腐烂。");
        }
        if (gameTime >= record.expireGameTime()) {
            return new VegetationObservation(
                    true,
                    VegetationLifecycleStage.DEAD,
                    0,
                    false,
                    false,
                    Optional.empty(),
                    "树苗已死亡。");
        }

        return new VegetationObservation(
                true,
                stage,
                pointValue,
                false,
                false,
                Optional.empty(),
                "树苗年龄为 " + age + " tick。");
    }

    @Override
    public VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        if (!EcofluxServerConfig.gradualPlantGrowth()) {
            return new VegetationVisualState(VegetationLifecycleStage.GROWING, 1.0F);
        }
        long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
        long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());
        return switch (record.lifeStage()) {
            case BORN, JUVENILE -> new VegetationVisualState(VegetationLifecycleStage.JUVENILE, VegetationTypeAdapter.progress(age, 0L, 1200L));
            case GROWING -> new VegetationVisualState(VegetationLifecycleStage.GROWING, VegetationTypeAdapter.progress(age, 1200L, totalLifetime));
            case MATURE -> new VegetationVisualState(VegetationLifecycleStage.MATURE, 1.0F);
            case AGING -> new VegetationVisualState(VegetationLifecycleStage.AGING, VegetationTypeAdapter.progress(age, 24000L, totalLifetime));
            case DEAD -> new VegetationVisualState(VegetationLifecycleStage.DEAD, VegetationTypeAdapter.progress(gameTime, record.expireGameTime(), record.expireGameTime() + DECAY_TICKS));
            default -> new VegetationVisualState(record.lifeStage(), 1.0F);
        };
    }

    @Override
    public Optional<VegetationTransformation> detectTransformation(
            ServerLevel level,
            ActiveVegetationRecord record,
            BlockState state,
            long gameTime) {
        if (matches(state)) {
            return Optional.empty();
        }

        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return Optional.of(new VegetationTransformation(
                    blockId,
                    TreeStructureAdapter.TYPE_ID,
                    VegetationCategory.TREE,
                    VegetationLifecycleStage.MATURE,
                    4,
                    5));
        }

        return Optional.empty();
    }
}
