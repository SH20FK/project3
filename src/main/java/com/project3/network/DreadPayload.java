package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client payload to sync dread level for client-side effects.
 */
public record DreadPayload(int dread, int threshold) implements CustomPayload {

    public static final Id<DreadPayload> ID =
            new Id<>(Identifier.of("p3", "dread"));

    public static final PacketCodec<PacketByteBuf, DreadPayload> CODEC =
            PacketCodec.of((value, buf) -> {
                buf.writeByte(value.dread());
                buf.writeByte(value.threshold());
            }, buf -> new DreadPayload(buf.readByte(), buf.readByte()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
