package com.s.ecoflux.plant;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.config.EcofluxBlockTags;
import com.s.ecoflux.config.EcofluxServerConfig;
import com.s.ecoflux.config.plant.PlantDefinition;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class SimplePlantAdapter implements VegetationTypeAdapter {
    public static final SimplePlantAdapter INSTANCE = new SimplePlantAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("simple_plant");
    private static final long DECAY_TICKS = 6000L;

    private SimplePlantAdapter() {}

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(EcofluxBlockTags.SIMPLE_VEGETATION);
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
        if (!EcofluxServerConfig.gradualPlantGrowth()) {
            int maxPoints = basePointValue + 1;
            return new ActiveVegetationRecord(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    typeId(),
                    pos.immutable(),
                    VegetationLifecycleStage.MATURE,
                    gameTime,
                    gameTime,
                    gameTime + maxAgeTicks,
                    maxPoints,
                    maxPoints,
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
                gameTime + maxAgeTicks,
                basePointValue,
                Math.max(0, basePointValue / 2),
                sourceBiomeId.orElse(null),
                sourcePathId.orElse(null),
                null);
    }

    @Override
    public VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record, BlockState state, long gameTime) {
        if (state.isAir() || !matches(state)) {
            return VegetationObservation.absent("简单植物已不存在。");
        }

        if (!EcofluxServerConfig.gradualPlantGrowth()) {
            return new VegetationObservation(
                    true,
                    VegetationLifecycleStage.MATURE,
                    record.basePointValue() + 1,
                    true,
                    false,
                    Optional.empty(),
                    "简单植物（跳过生命周期阶段推进）。");
        }

        long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
        long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());

        VegetationLifecycleStage stage;
        int pointValue;
        boolean mature = false;
        boolean aging = false;
        if (age < 200L) {
            stage = VegetationLifecycleStage.BORN;
            pointValue = Math.max(0, record.basePointValue() / 2);
        } else if (age < 1200L) {
            stage = VegetationLifecycleStage.GROWING;
            pointValue = record.basePointValue();
        } else if (age < 48000L) {
            stage = VegetationLifecycleStage.MATURE;
            pointValue = record.basePointValue() + 1;
            mature = true;
        } else {
            stage = VegetationLifecycleStage.AGING;
            pointValue = Math.max(1, record.basePointValue());
            aging = true;
        }

        if (age >= totalLifetime + DECAY_TICKS && record.lifeStage() == VegetationLifecycleStage.DEAD) {
            return VegetationObservation.absent("简单植物已死亡并腐烂。");
        }
        if (age >= totalLifetime) {
            return new VegetationObservation(
                    true, VegetationLifecycleStage.DEAD, 0, false, false,
                    Optional.empty(), "简单植物已死亡。");
        }

        return new VegetationObservation(
                true,
                stage,
                pointValue,
                mature,
                aging,
                Optional.empty(),
                "简单植物年龄为 " + age + " tick。");
    }

    @Override
    public VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        if (!EcofluxServerConfig.gradualPlantGrowth()) {
            return new VegetationVisualState(VegetationLifecycleStage.MATURE, 1.0F);
        }
        long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
        long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());
        return switch (record.lifeStage()) {
            case BORN -> new VegetationVisualState(VegetationLifecycleStage.BORN, VegetationTypeAdapter.progress(age, 0L, 200L));
            case GROWING -> new VegetationVisualState(VegetationLifecycleStage.GROWING, VegetationTypeAdapter.progress(age, 200L, 1200L));
            case MATURE -> new VegetationVisualState(VegetationLifecycleStage.MATURE, VegetationTypeAdapter.progress(age, 1200L, 48000L));
            case AGING -> new VegetationVisualState(VegetationLifecycleStage.AGING, VegetationTypeAdapter.progress(age, 48000L, totalLifetime));
            case DEAD -> new VegetationVisualState(VegetationLifecycleStage.DEAD, VegetationTypeAdapter.progress(age, totalLifetime, totalLifetime + DECAY_TICKS));
            default -> new VegetationVisualState(record.lifeStage(), 1.0F);
        };
    }
}
