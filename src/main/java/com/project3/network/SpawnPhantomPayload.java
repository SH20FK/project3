package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

/**
 * Server -> Client packet to spawn a glitched client-side phantom NPC with a custom player's skin.
 */
public record SpawnPhantomPayload(
    int entityId,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    UUID skinUuid,
    String skinName
) implements CustomPayload {

    public static final Id<SpawnPhantomPayload> ID =
            new Id<>(Identifier.of("p3", "spawn_phantom"));

    public static final PacketCodec<PacketByteBuf, SpawnPhantomPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.entityId());
                        buf.writeDouble(value.x());
                        buf.writeDouble(value.y());
                        buf.writeDouble(value.z());
                        buf.writeFloat(value.yaw());
                        buf.writeFloat(value.pitch());
                        buf.writeUuid(value.skinUuid());
                        buf.writeString(value.skinName());
                    },
                    buf -> new SpawnPhantomPayload(
                            buf.readInt(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readUuid(),
                            buf.readString()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
