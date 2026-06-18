package com.cp.ecoflux.network;

/**
 * Individual plant entry within a {@code VegetationVisualChunkSyncPayload}.
 *
 * <p>Structure: record holding a plant's block position, lifecycle stage, stage
 * progress (0.0-1.0), and birth game time. Includes a {@code StreamCodec} for
 * network serialization.
 *
 * <p>Role in Ecoflux: each entry provides just enough data for the client-side
 * visual system to compute scale, color tint, and other per-plant visual effects
 * without needing the full server-side {@code ActiveVegetationRecord}.
 */

import com.cp.ecoflux.api.data.VegetationLifecycleStage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record VegetationVisualSyncEntry(BlockPos pos, VegetationLifecycleStage stage, float stageProgress, long birthGameTime) {
    public static final StreamCodec<RegistryFriendlyByteBuf, VegetationVisualSyncEntry> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VegetationVisualSyncEntry decode(RegistryFriendlyByteBuf buf) {
            return new VegetationVisualSyncEntry(
                    buf.readBlockPos(),
                    buf.readEnum(VegetationLifecycleStage.class),
                    Mth.clamp(buf.readFloat(), 0.0F, 1.0F),
                    buf.readVarLong());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, VegetationVisualSyncEntry value) {
            buf.writeBlockPos(value.pos());
            buf.writeEnum(value.stage());
            buf.writeFloat(Mth.clamp(value.stageProgress(), 0.0F, 1.0F));
            buf.writeVarLong(value.birthGameTime());
        }
    };
}
