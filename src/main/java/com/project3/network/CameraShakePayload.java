package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CameraShakePayload(float traumaAmount) implements CustomPayload {

    public static final Id<CameraShakePayload> ID =
            new Id<>(Identifier.of("p3", "camera_shake"));

    public static final PacketCodec<PacketByteBuf, CameraShakePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.FLOAT, CameraShakePayload::traumaAmount, CameraShakePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
