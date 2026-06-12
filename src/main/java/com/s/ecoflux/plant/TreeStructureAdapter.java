package com.s.ecoflux.plant;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class TreeStructureAdapter implements VegetationTypeAdapter {
    public static final TreeStructureAdapter INSTANCE = new TreeStructureAdapter();
    public static final ResourceLocation TYPE_ID = EcofluxConstants.id("tree_structure");
    private static final long MATURE_DURATION = 96000L;
    private static final long TOTAL_LIFETIME = 288000L;
    private static final long DECAY_TICKS = 24000L;

    private TreeStructureAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public VegetationCategory category() {
        return VegetationCategory.TREE;
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
            Optional<ResourceLocation> sourcePathId) {
        return new ActiveVegetationRecord(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                typeId(),
                VegetationCategory.TREE,
                pos.immutable(),
                VegetationLifecycleStage.MATURE,
                gameTime,
                gameTime,
                gameTime + 288000L,
                4,
                5,
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

        VegetationLifecycleStage stage = age < MATURE_DURATION ? VegetationLifecycleStage.MATURE : VegetationLifecycleStage.AGING;
        int pointValue = stage == VegetationLifecycleStage.MATURE ? 5 : 3;

        if (age >= TOTAL_LIFETIME + DECAY_TICKS && record.lifeStage() == VegetationLifecycleStage.DEAD) {
            return VegetationObservation.absent("树木结构已死亡并腐烂。");
        }
        if (age >= TOTAL_LIFETIME) {
            return observeDeath(level, record, age);
        }
        if (stage == VegetationLifecycleStage.AGING && record.lifeStage() != VegetationLifecycleStage.AGING) {
            EcofluxConstants.LOGGER.error(
                    "树木进入衰老期！位置={}, 缩放年龄={}/{}tick, 速度倍率={}x, 实际游戏天数={}",
                    record.position(), age, TOTAL_LIFETIME,
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
            EcofluxConstants.LOGGER.error(
                    "树木已死亡！位置={}, 缩放年龄={}/{}tick, 速度倍率={}x, 实际游戏天数={}",
                    record.position(), age, TOTAL_LIFETIME,
                    String.format("%.1f", SuccessionSpeedConfig.getSpeedMultiplier()),
                    level.getGameTime() / 24000L);

            // ────────────────────────────────────────────────────────────────
            // !!! 仅测试用 — 树木死亡时传送玩家到死亡位置 !!!
            // TODO: 测试完毕后删除此段
            for (ServerPlayer player : level.players()) {
                BlockPos tpPos = record.position();
                player.teleportTo(level, tpPos.getX() + 0.5, tpPos.getY() + 2, tpPos.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                EcofluxConstants.LOGGER.error(
                        "[测试] 已将玩家 {} 传送至树木死亡位置 {}", player.getName().getString(), tpPos);
                break;
            }
            // ────────────────────────────────────────────────────────────────
        }

        return new VegetationObservation(
                true, VegetationLifecycleStage.DEAD, 0,
                false, false, Optional.empty(), "树木结构已死亡。");
    }

    @Override
    public VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
        return switch (record.lifeStage()) {
            case MATURE -> new VegetationVisualState(VegetationLifecycleStage.MATURE, VegetationTypeAdapter.progress(age, 0L, MATURE_DURATION));
            case AGING -> new VegetationVisualState(VegetationLifecycleStage.AGING, VegetationTypeAdapter.progress(age, MATURE_DURATION, TOTAL_LIFETIME));
            case DEAD -> new VegetationVisualState(VegetationLifecycleStage.DEAD, VegetationTypeAdapter.progress(age, TOTAL_LIFETIME, TOTAL_LIFETIME + DECAY_TICKS));
            default -> new VegetationVisualState(record.lifeStage(), 1.0F);
        };
    }

}
