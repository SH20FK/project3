package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client payload to sync paranoia level for horror effects.
 */
public record ParanoiaPayload(int level) implements CustomPayload {

    public static final Id<ParanoiaPayload> ID =
            new Id<>(Identifier.of("p3", "paranoia"));

    public static final PacketCodec<PacketByteBuf, ParanoiaPayload> CODEC =
            PacketCodec.of((value, buf) -> buf.writeByte(value.level()),
                           buf -> new ParanoiaPayload(buf.readByte()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
