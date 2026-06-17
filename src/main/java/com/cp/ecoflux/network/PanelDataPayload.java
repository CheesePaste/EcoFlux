package com.cp.ecoflux.network;

import com.cp.ecoflux.EcofluxConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;

public record PanelDataPayload(
        double globalAvgProgress,
        int totalTrackedChunks,
        int totalVegetation,
        boolean successionEnabled,
        List<ChunkDataEntry> entries,
        List<ChunkPos> excludedChunks) implements CustomPacketPayload {

    public static final Type<PanelDataPayload> TYPE =
            new Type<>(EcofluxConstants.id("panel_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, v) -> {
                        buf.writeDouble(v.globalAvgProgress());
                        buf.writeVarInt(v.totalTrackedChunks());
                        buf.writeVarInt(v.totalVegetation());
                        buf.writeBoolean(v.successionEnabled());
                        buf.writeVarInt(v.entries().size());
                        for (ChunkDataEntry e : v.entries()) {
                            ChunkDataEntry.STREAM_CODEC.encode(buf, e);
                        }
                        buf.writeVarInt(v.excludedChunks().size());
                        for (ChunkPos cp : v.excludedChunks()) {
                            buf.writeVarInt(cp.x);
                            buf.writeVarInt(cp.z);
                        }
                    },
                    buf -> {
                        double avg = buf.readDouble();
                        int chunks = buf.readVarInt();
                        int veg = buf.readVarInt();
                        boolean en = buf.readBoolean();
                        int count = buf.readVarInt();
                        List<ChunkDataEntry> entries = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            entries.add(ChunkDataEntry.STREAM_CODEC.decode(buf));
                        }
                        int exclCount = buf.readVarInt();
                        List<ChunkPos> excluded = new ArrayList<>(exclCount);
                        for (int i = 0; i < exclCount; i++) {
                            excluded.add(new ChunkPos(buf.readVarInt(), buf.readVarInt()));
                        }
                        return new PanelDataPayload(avg, chunks, veg, en, List.copyOf(entries), List.copyOf(excluded));
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
