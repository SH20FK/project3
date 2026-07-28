package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client payload for escalating border violation effects.
 * tickCount: accumulated ticks the player has spent outside the border.
 */
public record BorderEffectPayload(int tickCount) implements CustomPayload {

    public static final Id<BorderEffectPayload> ID =
            new Id<>(Identifier.of("p3", "border_effect"));

    public static final PacketCodec<PacketByteBuf, BorderEffectPayload> CODEC =
            PacketCodec.of(
                    (payload, buf) -> buf.writeInt(payload.tickCount),
                    buf -> new BorderEffectPayload(buf.readInt())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
