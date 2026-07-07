package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * C2S packet sent when an admin uses one of the toolkit items with modifiers.
 */
public record AdminToolUsePayload(
    int itemType,
    int actionType,
    UUID targetPlayerUuid,
    BlockPos targetBlockPos
) implements CustomPayload {

    public static final Id<AdminToolUsePayload> ID =
            new Id<>(Identifier.of("p3", "admin_tool_use"));

    public static final PacketCodec<PacketByteBuf, AdminToolUsePayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.itemType());
                        buf.writeInt(value.actionType());
                        buf.writeBoolean(value.targetPlayerUuid() != null);
                        if (value.targetPlayerUuid() != null) {
                            buf.writeUuid(value.targetPlayerUuid());
                        }
                        buf.writeBoolean(value.targetBlockPos() != null);
                        if (value.targetBlockPos() != null) {
                            buf.writeBlockPos(value.targetBlockPos());
                        }
                    },
                    buf -> {
                        int itemType = buf.readInt();
                        int actionType = buf.readInt();
                        UUID uuid = buf.readBoolean() ? buf.readUuid() : null;
                        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
                        return new AdminToolUsePayload(itemType, actionType, uuid, pos);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
