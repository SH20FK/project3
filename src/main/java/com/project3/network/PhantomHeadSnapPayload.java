package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C packet to snap a client-side phantom's head 180 degrees backward.
 */
public record PhantomHeadSnapPayload(
    int entityId
) implements CustomPayload {

    public static final Id<PhantomHeadSnapPayload> ID =
            new Id<>(Identifier.of("p3", "phantom_head_snap"));

    public static final PacketCodec<PacketByteBuf, PhantomHeadSnapPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> buf.writeInt(value.entityId()),
                    buf -> new PhantomHeadSnapPayload(buf.readInt())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
