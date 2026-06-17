package com.cp.ecoflux.network;

/**
 * Client→server packet. Sent when the player clicks a chunk on the map tab
 * to toggle succession exclusion for that chunk.
 *
 * <p>When {@code excluded=true}: the chunk is excluded from succession —
 * no plant spawning, no evaluation, no lifecycle observation.
 * When {@code excluded=false}: succession resumes normally.
 */

import com.cp.ecoflux.EcofluxConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ToggleChunkExclusionPayload(int chunkX, int chunkZ, boolean excluded)
        implements CustomPacketPayload {

    public static final Type<ToggleChunkExclusionPayload> TYPE =
            new Type<>(EcofluxConstants.id("toggle_chunk_exclusion"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleChunkExclusionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ToggleChunkExclusionPayload decode(RegistryFriendlyByteBuf buf) {
                    return new ToggleChunkExclusionPayload(
                            buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ToggleChunkExclusionPayload v) {
                    buf.writeVarInt(v.chunkX());
                    buf.writeVarInt(v.chunkZ());
                    buf.writeBoolean(v.excluded());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
