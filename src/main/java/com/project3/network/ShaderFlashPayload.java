package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client payload to trigger a client-side shader flash.
 */
public record ShaderFlashPayload() implements CustomPayload {

    public static final Id<ShaderFlashPayload> ID =
            new Id<>(Identifier.of("p3", "shader_flash"));

    public static final PacketCodec<PacketByteBuf, ShaderFlashPayload> CODEC =
            PacketCodec.unit(new ShaderFlashPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
