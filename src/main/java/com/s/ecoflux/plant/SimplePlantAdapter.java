package com.s.ecoflux.plant;

/**
 * {@link VegetationTypeAdapter} for simple (non-tree) plants: flowers, grass,
 * ferns, mushrooms, dead bushes, and double-height plants (tall grass, large
 * ferns, sunflowers).
 *
 * <p>Structure: singleton with {@link #matches} covering a broad set of block
 * tags and specific blocks. {@link #captureBirth} derives the
 * {@link VegetationCategory} (FLOWER, MUSHROOM, GROUND_COVER, or OTHER) and
 * assigns base point values (2 for flowers, 1 otherwise). {@link #observe}
 * drives the standard BORN/GROWING/MATURE/AGING lifecycle over scaled game
 * time, returning point values and maturity flags for succession scoring.
 * {@link #visualState} maps each stage to a normalized progress value for
 * client-side scale/tint interpolation.
 *
 * <p>Role in Ecoflux: handles the majority of ground-cover and decorative
 * plants. Its lifecycle progression and point values feed directly into
 * succession evaluation — mature flowers contribute more points, while aging
 * plants trigger the aging gate in
 * {@link com.s.ecoflux.succession.SuccessionEvaluator}.
 */

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.config.EcofluxServerConfig;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SimplePlantAdapter implements VegetationTypeAdapter {
    public static final SimplePlantAdapter INSTANCE = new SimplePlantAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("simple_plant");
    private static final long DECAY_TICKS = 6000L;

    private SimplePlantAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public VegetationCategory category() {
        return VegetationCategory.OTHER;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.LARGE_FERN)
                || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(BlockTags.TALL_FLOWERS)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.DEAD_BUSH);
    }

    @Override
    public ActiveVegetationRecord captureBirth(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            long gameTime,
            Optional<ResourceLocation> sourceBiomeId,
            Optional<ResourceLocation> sourcePathId) {
        VegetationCategory derivedCategory = deriveCategory(state);
        int basePointValue = derivedCategory == VegetationCategory.FLOWER ? 2 : 1;
        if (!EcofluxServerConfig.gradualPlantGrowth()) {
            int maxPoints = basePointValue + 1;
            return new ActiveVegetationRecord(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    typeId(),
                    derivedCategory,
                    pos.immutable(),
                    VegetationLifecycleStage.MATURE,
                    gameTime,
                    gameTime,
                    gameTime + 72000L,
                    maxPoints,
                    maxPoints,
                    sourceBiomeId.orElse(null),
                    sourcePathId.orElse(null),
                    null);
        }
        return new ActiveVegetationRecord(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                typeId(),
                derivedCategory,
                pos.immutable(),
                VegetationLifecycleStage.BORN,
                gameTime,
                gameTime,
                gameTime + 72000L,
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

    private static VegetationCategory deriveCategory(BlockState state) {
        if (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS) || state.is(BlockTags.FLOWERS)) {
            return VegetationCategory.FLOWER;
        }
        if (state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)) {
            return VegetationCategory.MUSHROOM;
        }
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH)) {
            return VegetationCategory.GROUND_COVER;
        }
        return VegetationCategory.OTHER;
    }
}
