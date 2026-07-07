package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> Client packet to remove a glitched client-side phantom NPC.
 */
public record RemovePhantomPayload(
    int entityId
) implements CustomPayload {

    public static final Id<RemovePhantomPayload> ID =
            new Id<>(Identifier.of("p3", "remove_phantom"));

    public static final PacketCodec<PacketByteBuf, RemovePhantomPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> buf.writeInt(value.entityId()),
                    buf -> new RemovePhantomPayload(buf.readInt())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
