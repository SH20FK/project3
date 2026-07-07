package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client payload syncing player-specific state (Happiness, Gloom, Unnamed Effect).
 * stateIndex: 1-6 mapping to the 6 HUD bar textures.
 */
public record PlayerStateSyncPayload(
    long happinessTicksLeft,
    boolean gloomPermanent,
    long gloomTicksLeft,
    boolean unnamedEffectActive,
    int progressLevel,
    int stateIndex
) implements CustomPayload {

    public static final Id<PlayerStateSyncPayload> ID =
            new Id<>(Identifier.of("p3", "player_state_sync"));

    public static final PacketCodec<PacketByteBuf, PlayerStateSyncPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeLong(value.happinessTicksLeft());
                        buf.writeBoolean(value.gloomPermanent());
                        buf.writeLong(value.gloomTicksLeft());
                        buf.writeBoolean(value.unnamedEffectActive());
                        buf.writeInt(value.progressLevel());
                        buf.writeInt(value.stateIndex());
                    },
                    buf -> new PlayerStateSyncPayload(
                            buf.readLong(),
                            buf.readBoolean(),
                            buf.readLong(),
                            buf.readBoolean(),
                            buf.readInt(),
                            buf.readInt()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
