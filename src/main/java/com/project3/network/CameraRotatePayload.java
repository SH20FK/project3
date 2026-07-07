package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client payload to rotate the player's camera by delta yaw/pitch.
 */
public record CameraRotatePayload(float deltaYaw, float deltaPitch) implements CustomPayload {

    public static final Id<CameraRotatePayload> ID =
            new Id<>(Identifier.of("p3", "camera_rotate"));

    public static final PacketCodec<PacketByteBuf, CameraRotatePayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeFloat(value.deltaYaw());
                        buf.writeFloat(value.deltaPitch());
                    },
                    buf -> new CameraRotatePayload(buf.readFloat(), buf.readFloat())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
