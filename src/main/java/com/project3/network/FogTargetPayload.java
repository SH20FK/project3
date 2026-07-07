package com.project3.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FogTargetPayload(float fogEnd) implements CustomPayload {
    public static final CustomPayload.Id<FogTargetPayload> ID = new CustomPayload.Id<>(Identifier.of("p3", "fog_target"));
    public static final PacketCodec<RegistryByteBuf, FogTargetPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, FogTargetPayload::fogEnd,
            FogTargetPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
