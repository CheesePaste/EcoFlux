package com.cp.ecoflux.plant;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.attachment.ActiveVegetationRecord;
import com.cp.ecoflux.config.plant.PlantDefinition;
import com.cp.ecoflux.config.SuccessionSpeedConfig;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class TreeStructureAdapter implements VegetationTypeAdapter {
    public static final TreeStructureAdapter INSTANCE = new TreeStructureAdapter();
    public static final ResourceLocation TYPE_ID = EcofluxConstants.id("tree_structure");
    private static final long DECAY_TICKS = 72000L;

    private TreeStructureAdapter() {}

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
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
        return new ActiveVegetationRecord(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                typeId(),
                pos.immutable(),
                VegetationLifecycleStage.MATURE,
                gameTime,
                gameTime,
                gameTime + variedMaxAge,
                basePointValue,
                basePointValue + 1,
                sourceBiomeId.orElse(null),
                sourcePathId.orElse(null),
                null);
    }

    @Override
    public VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record, BlockState state, long gameTime) {
        if (state.isAir() || !matches(state)) {
            return VegetationObservation.absent("树木结构已不存在。");
        }

        long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
        long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());
        long matureDuration = totalLifetime / 3;

        VegetationLifecycleStage stage = age < matureDuration ? VegetationLifecycleStage.MATURE : VegetationLifecycleStage.AGING;
        int pointValue = stage == VegetationLifecycleStage.MATURE
                ? record.basePointValue() + 1
                : Math.max(1, record.basePointValue() - 1);

        if (age >= totalLifetime + DECAY_TICKS && record.lifeStage() == VegetationLifecycleStage.DEAD) {
            return VegetationObservation.absent("树木结构已死亡并腐烂。");
        }
        if (age >= totalLifetime) {
            return observeDeath(level, record, age);
        }
        if (stage == VegetationLifecycleStage.AGING && record.lifeStage() != VegetationLifecycleStage.AGING) {
            EcofluxConstants.LOGGER.error(
                    "树木进入衰老期！位置={}, 缩放年龄={}/{}tick, 速度倍率={}x, 实际游戏天数={}",
                    record.position(), age, totalLifetime,
                    String.format("%.1f", SuccessionSpeedConfig.getSpeedMultiplier()),
                    gameTime / 24000L);
        }

        return new VegetationObservation(
                true, stage, pointValue,
                stage == VegetationLifecycleStage.MATURE,
                stage == VegetationLifecycleStage.AGING,
                Optional.empty(),
                "树木结构年龄为 " + age + " tick。");
    }

    private VegetationObservation observeDeath(ServerLevel level, ActiveVegetationRecord record,
                                                long age) {
        if (record.lifeStage() != VegetationLifecycleStage.DEAD) {
            EcofluxConstants.LOGGER.info(
                    "树木已死亡！位置={}, 缩放年龄={}/{}tick, 速度倍率={}x",
                    record.position(), age, record.expireGameTime() - record.birthGameTime(),
                    String.format("%.1f", SuccessionSpeedConfig.getSpeedMultiplier()));
        }

        return new VegetationObservation(
                true, VegetationLifecycleStage.DEAD, 0,
                false, false, Optional.empty(), "树木结构已死亡。");
    }

    @Override
    public VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
        long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());
        long matureDuration = totalLifetime / 3;
        return switch (record.lifeStage()) {
            case MATURE -> new VegetationVisualState(VegetationLifecycleStage.MATURE, VegetationTypeAdapter.progress(age, 0L, matureDuration));
            case AGING -> new VegetationVisualState(VegetationLifecycleStage.AGING, VegetationTypeAdapter.progress(age, matureDuration, totalLifetime));
            case DEAD -> new VegetationVisualState(VegetationLifecycleStage.DEAD, VegetationTypeAdapter.progress(age, totalLifetime, totalLifetime + DECAY_TICKS));
            default -> new VegetationVisualState(record.lifeStage(), 1.0F);
        };
    }
}
