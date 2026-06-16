package com.cp.ecoflux.plant.adapters.dynamictrees;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.api.VegetationAdapterCapability;
import com.cp.ecoflux.api.data.ActiveVegetationRecord;
import com.cp.ecoflux.config.SuccessionSpeedConfig;
import com.cp.ecoflux.api.config.PlantDefinition;
import com.cp.ecoflux.api.data.VegetationLifecycleStage;
import com.cp.ecoflux.api.data.VegetationObservation;
import com.cp.ecoflux.api.adapter.VegetationTypeAdapter;
import com.cp.ecoflux.api.data.VegetationVisualState;
import com.dtteam.dynamictrees.api.treedata.TreePart;
import com.dtteam.dynamictrees.tree.TreeHelper;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * VegetationTypeAdapter for Dynamic Trees tree structures.
 *
 * <p>Recognizes DT branch/leaves/root blocks and tracks them through
 * EcoFlux's lifecycle stages (MATURE → AGING → DEAD).
 * Only active when Dynamic Trees mod is loaded.
 */
public final class DTTreeAdapter implements VegetationTypeAdapter {

    public static final DTTreeAdapter INSTANCE = new DTTreeAdapter();
    public static final ResourceLocation TYPE_ID = EcofluxConstants.id("dt_tree");

    private static final long DECAY_TICKS = 72000L;

    private DTTreeAdapter() {}

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public Set<VegetationAdapterCapability> capabilities() {
        return Set.of(VegetationAdapterCapability.HAS_STRUCTURE, VegetationAdapterCapability.LONG_LIFECYCLE);
    }

    @Override
    public boolean matches(BlockState state) {
        // Fast path: check block tags
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            // Only match if this is actually a DT tree part (avoid false positives)
            return state.getBlock() instanceof TreePart;
        }
        return false;
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

        // Try to find the actual root position for recording
        BlockPos rootPos = findRootPos(level, pos);
        int basePointValue = plantDefinition.pointValue();
        long maxAgeTicks = plantDefinition.maxAgeTicks();
        Random random = new Random(rootPos.asLong());
        double lifespanVariation = 0.8 + random.nextDouble() * 0.4;
        long variedMaxAge = (long) (maxAgeTicks * lifespanVariation);

        EcofluxConstants.LOGGER.info("[Ecoflux-DT] captureBirth: plantId={} pos={} rootPos={} maxAge={} variedMaxAge={} expireGameTime={}",
                plantDefinition.plantId(), pos.toShortString(), rootPos.toShortString(),
                maxAgeTicks, variedMaxAge, gameTime + variedMaxAge);

        return new ActiveVegetationRecord(
                plantDefinition.plantId(),
                typeId(),
                rootPos,
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
    public VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record,
                                         BlockState state, long gameTime) {
        BlockPos pos = record.position();
        BlockState currentState = level.getBlockState(pos);
        float speed = SuccessionSpeedConfig.getSpeedMultiplier();

        // Check if tree still exists by looking for the root or any tree part
        if (!isTreePartAt(level, pos)) {
            // Try nearby positions — the record may be at the old sapling pos
            BlockPos rootPos = findRootPos(level, pos);
            if (rootPos.equals(pos) || !isTreePartAt(level, rootPos)) {
                EcofluxConstants.LOGGER.info("[Ecoflux-DT] observe: ABSENT pos={} block={}",
                        pos.toShortString(), currentState.getBlock().getDescriptionId());
                return VegetationObservation.absent("DT树木结构已移除。");
            }
            // Update our tracking to the root position? No, just continue observing
        }

        long age = (long) (Math.max(0L, gameTime - record.birthGameTime())
                * speed);
        long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());

        // Simple lifecycle: MATURE (first 2/3) → AGING (last 1/3) → DEAD
        long matureDuration = totalLifetime * 2 / 3;

        VegetationLifecycleStage stage;
        int pointValue;
        if (age < matureDuration) {
            stage = VegetationLifecycleStage.MATURE;
            pointValue = record.basePointValue() + 1;
        } else if (age < totalLifetime) {
            stage = VegetationLifecycleStage.AGING;
            pointValue = Math.max(1, record.basePointValue() - 1);
        } else if (age < totalLifetime + DECAY_TICKS) {
            stage = VegetationLifecycleStage.DEAD;
            pointValue = 0;
        } else {
            EcofluxConstants.LOGGER.info("[Ecoflux-DT] observe: DECAYED pos={} age={} totalLifetime={}",
                    pos.toShortString(), age, totalLifetime);
            return VegetationObservation.absent("DT树木已死亡并腐烂。");
        }

        boolean stageChanged = record.lifeStage() != stage;
        if (stageChanged) {
            EcofluxConstants.LOGGER.info("[Ecoflux-DT] observe: STAGE {} → {} pos={} age={}/{} speed={}x plantId={}",
                    record.lifeStage(), stage, pos.toShortString(), age, totalLifetime,
                    String.format("%.1f", speed), record.vegetationId());
        } else {
            EcofluxConstants.LOGGER.debug("[Ecoflux-DT] observe: stage={} pos={} age={}/{} speed={}x",
                    stage, pos.toShortString(), age, totalLifetime, String.format("%.1f", speed));
        }

        return new VegetationObservation(true, stage, pointValue,
                stage == VegetationLifecycleStage.MATURE,
                stage == VegetationLifecycleStage.AGING,
                Optional.empty(),
                "DT树木年龄=" + age + " tick。");
    }

    @Override
    public VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        return new VegetationVisualState(record.lifeStage(), 1.0F);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static boolean isTreePartAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof TreePart
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES);
    }

    private static BlockPos findRootPos(ServerLevel level, BlockPos pos) {
        int attempts = 0;
        BlockPos current = pos;
        while (attempts < 64) {
            BlockState state = level.getBlockState(current);
            if (state.getBlock() instanceof TreePart tp) {
                var type = tp.getTreePartType();
                if (type == TreePart.TreePartType.ROOT) {
                    return current.immutable();
                }
            }
            // Try to find the root via DT's TreeHelper
            BlockPos root = TreeHelper.findRootNode(level, current);
            if (!root.equals(BlockPos.ZERO)) {
                return root.immutable();
            }
            break;
        }
        return pos.immutable();
    }
}
