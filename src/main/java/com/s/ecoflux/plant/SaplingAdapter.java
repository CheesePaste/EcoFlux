package com.s.ecoflux.plant;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.config.EcofluxServerConfig;
import com.s.ecoflux.config.plant.PlantDefinition;
import com.s.ecoflux.config.plant.PlantRegistry;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import java.util.Optional;
import java.util.Random;

import com.s.ecoflux.config.plant.PlantSpawnRules;
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

    private SaplingAdapter() {}

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
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
            Optional<ResourceLocation> sourcePathId,
            PlantDefinition plantDefinition) {
        int basePointValue = plantDefinition.pointValue();
        long maxAgeTicks = plantDefinition.maxAgeTicks();
        Random random = new Random(pos.asLong());
        double lifespanVariation = 0.8 + random.nextDouble() * 0.4;
        long variedMaxAge = (long) (maxAgeTicks * lifespanVariation);
        if (!EcofluxServerConfig.gradualPlantGrowth()) {
            return new ActiveVegetationRecord(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    typeId(),
                    pos.immutable(),
                    VegetationLifecycleStage.GROWING,
                    gameTime,
                    gameTime,
                    gameTime + variedMaxAge,
                    basePointValue,
                    basePointValue,
                    sourceBiomeId.orElse(null),
                    sourcePathId.orElse(null),
                    null);
        }
        return new ActiveVegetationRecord(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                typeId(),
                pos.immutable(),
                VegetationLifecycleStage.BORN,
                gameTime,
                gameTime,
                gameTime + variedMaxAge,
                basePointValue,
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
                    record.basePointValue(),
                    false,
                    false,
                    Optional.empty(),
                    "树苗（跳过生命周期阶段推进）。");
        }

        long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
        VegetationLifecycleStage stage = age < 1200L ? VegetationLifecycleStage.JUVENILE : VegetationLifecycleStage.GROWING;
        int pointValue = age < 24000L ? 1 : record.basePointValue();

        long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());

        if (age >= totalLifetime + DECAY_TICKS && record.lifeStage() == VegetationLifecycleStage.DEAD) {
            return VegetationObservation.absent("树苗已死亡并腐烂。");
        }
        if (age >= totalLifetime) {
            logStageTransition(record, VegetationLifecycleStage.DEAD, age, totalLifetime);
            return new VegetationObservation(
                    true, VegetationLifecycleStage.DEAD, 0, false, false,
                    Optional.empty(), "树苗已死亡。");
        }

        logStageTransition(record, stage, age, totalLifetime);
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
            case DEAD -> new VegetationVisualState(VegetationLifecycleStage.DEAD, VegetationTypeAdapter.progress(age, totalLifetime, totalLifetime + DECAY_TICKS));
            default -> new VegetationVisualState(record.lifeStage(), 1.0F);
        };
    }

    private static void logStageTransition(ActiveVegetationRecord record, VegetationLifecycleStage newStage,
                                            long age, long totalLifetime) {
        if (record.lifeStage() == newStage) return;
        EcofluxConstants.LOGGER.error(
                "树苗阶段变迁：{} → {}，位置={}, 缩放年龄={}/{}tick, 速度倍率={}x",
                record.lifeStage(), newStage, record.position(), age, totalLifetime,
                String.format("%.1f", SuccessionSpeedConfig.getSpeedMultiplier()));
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
            PlantDefinition treeDef = PlantRegistry.INSTANCE.getDefinition(blockId)
                    .orElseGet(() -> {
                        EcofluxConstants.LOGGER.warn("[Ecoflux] No PlantDefinition for mature tree block {}, using fallback", blockId);
                        return new PlantDefinition(blockId, 4, 288000L, PlantSpawnRules.EMPTY);
                    });
            long treeMaxAge = treeDef.maxAgeTicks();
            Random treeRandom = new Random(record.position().asLong());
            long variedTreeMaxAge = (long) (treeMaxAge * (0.8 + treeRandom.nextDouble() * 0.4));
            return Optional.of(new VegetationTransformation(
                    blockId,
                    TreeStructureAdapter.TYPE_ID,
                    VegetationLifecycleStage.MATURE,
                    treeDef.pointValue(),
                    treeDef.pointValue() + 1,
                    gameTime + variedTreeMaxAge));
        }

        return Optional.empty();
    }
}
