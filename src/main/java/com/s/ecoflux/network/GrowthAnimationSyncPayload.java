package com.s.ecoflux.network;

import com.s.ecoflux.EcofluxConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;

/**
 * Server→Client: tells the client to play growth scale animations at the given positions.
 * Sent after each tree growth stage places new blocks.
 */
public record GrowthAnimationSyncPayload(
        ChunkPos chunkPos,
        List<GrowthAnimEntry> entries
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GrowthAnimationSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(EcofluxConstants.id("growth_animation_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrowthAnimationSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    StreamCodec.of(
                            (buf, cp) -> { buf.writeInt(cp.x); buf.writeInt(cp.z); },
                            buf -> new ChunkPos(buf.readInt(), buf.readInt())
                    ),
                    GrowthAnimationSyncPayload::chunkPos,
                    GrowthAnimEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    GrowthAnimationSyncPayload::entries,
                    GrowthAnimationSyncPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record GrowthAnimEntry(BlockPos pos, byte animType, int delayTicks) {
        public GrowthAnimEntry(BlockPos pos, byte animType) {
            this(pos, animType, 0);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, GrowthAnimEntry> STREAM_CODEC =
                StreamCodec.composite(
                        StreamCodec.of(
                                (buf, bp) -> buf.writeBlockPos(bp),
                                buf -> buf.readBlockPos()
                        ),
                        GrowthAnimEntry::pos,
                        ByteBufCodecs.BYTE,
                        GrowthAnimEntry::animType,
                        ByteBufCodecs.VAR_INT,
                        GrowthAnimEntry::delayTicks,
                        GrowthAnimEntry::new
                );
    }
}
