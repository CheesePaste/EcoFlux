package com.cp.ecoflux.network;

import com.cp.ecoflux.EcofluxConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Per-chunk snapshot embedded in {@link PanelDataPayload}.
 */
public record ChunkDataEntry(
        int chunkX,
        int chunkZ,
        @Nullable ResourceLocation currentBiome,
        @Nullable ResourceLocation targetBiome,
        @Nullable ResourceLocation activePathId,
        double progress,
        int contributingPoints,
        int consumingValue,
        int contributingCount,
        int totalVegetationCount,
        boolean successionDisabled) {

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkDataEntry> STREAM_CODEC =
            StreamCodec.of(
                    (buf, v) -> {
                        buf.writeVarInt(v.chunkX());
                        buf.writeVarInt(v.chunkZ());
                        buf.writeNullable(v.currentBiome(), (b, r) -> b.writeResourceLocation(r));
                        buf.writeNullable(v.targetBiome(), (b, r) -> b.writeResourceLocation(r));
                        buf.writeNullable(v.activePathId(), (b, r) -> b.writeResourceLocation(r));
                        buf.writeDouble(v.progress());
                        buf.writeVarInt(v.contributingPoints());
                        buf.writeVarInt(v.consumingValue());
                        buf.writeVarInt(v.contributingCount());
                        buf.writeVarInt(v.totalVegetationCount());
                        buf.writeBoolean(v.successionDisabled());
                    },
                    buf -> new ChunkDataEntry(
                            buf.readVarInt(), buf.readVarInt(),
                            buf.readNullable(b -> b.readResourceLocation()),
                            buf.readNullable(b -> b.readResourceLocation()),
                            buf.readNullable(b -> b.readResourceLocation()),
                            buf.readDouble(),
                            buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(),
                            buf.readBoolean())
            );

    public boolean hasActivePath() {
        return activePathId != null;
    }
}
