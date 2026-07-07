package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client payload syncing the player's active achievement details.
 */
public record AchievementSyncPayload(
    String id,
    String title,
    String description,
    String iconItemId,
    int currentValue,
    int targetValue,
    int completedCount,
    int totalCount
) implements CustomPayload {

    public static final Id<AchievementSyncPayload> ID =
            new Id<>(Identifier.of("p3", "achievement_sync"));

    public static final PacketCodec<PacketByteBuf, AchievementSyncPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.id);
                        buf.writeString(value.title);
                        buf.writeString(value.description);
                        buf.writeString(value.iconItemId != null ? value.iconItemId : "");
                        buf.writeInt(value.currentValue);
                        buf.writeInt(value.targetValue);
                        buf.writeInt(value.completedCount);
                        buf.writeInt(value.totalCount);
                    },
                    buf -> new AchievementSyncPayload(
                            buf.readString(),
                            buf.readString(),
                            buf.readString(),
                            buf.readString(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
