package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * S2C packet to spawn a glitched frozen 3D statue/clone on the client side.
 */
public record SpawnStatuePayload(
    int entityId,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    float headYaw,
    float bodyYaw,
    String poseName,
    UUID skinUuid,
    String skinName
) implements CustomPayload {

    public static final Id<SpawnStatuePayload> ID =
            new Id<>(Identifier.of("p3", "spawn_statue"));

    public static final PacketCodec<PacketByteBuf, SpawnStatuePayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.entityId());
                        buf.writeDouble(value.x());
                        buf.writeDouble(value.y());
                        buf.writeDouble(value.z());
                        buf.writeFloat(value.yaw());
                        buf.writeFloat(value.pitch());
                        buf.writeFloat(value.headYaw());
                        buf.writeFloat(value.bodyYaw());
                        buf.writeString(value.poseName());
                        buf.writeUuid(value.skinUuid());
                        buf.writeString(value.skinName());
                    },
                    buf -> new SpawnStatuePayload(
                            buf.readInt(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readString(),
                            buf.readUuid(),
                            buf.readString()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
