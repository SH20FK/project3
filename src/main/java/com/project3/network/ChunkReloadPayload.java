package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client payload to trigger a client-side chunk reload (F3+A equivalent).
 */
public record ChunkReloadPayload() implements CustomPayload {

    public static final Id<ChunkReloadPayload> ID =
            new Id<>(Identifier.of("p3", "chunk_reload"));

    public static final PacketCodec<PacketByteBuf, ChunkReloadPayload> CODEC =
            PacketCodec.unit(new ChunkReloadPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
