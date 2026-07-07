package com.project3.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Server → Client payload to open Producer Block GUI.
 */
public record OpenProducerScreenPayload(int x, int y, int z, int progress, int maxProgress, int zone) implements CustomPayload {

    public static final Id<OpenProducerScreenPayload> ID =
            new Id<>(Identifier.of("p3", "open_producer_screen"));

    public static final PacketCodec<PacketByteBuf, OpenProducerScreenPayload> CODEC =
            PacketCodec.of((value, buf) -> {
                buf.writeInt(value.x());
                buf.writeInt(value.y());
                buf.writeInt(value.z());
                buf.writeInt(value.progress());
                buf.writeInt(value.maxProgress());
                buf.writeInt(value.zone());
            }, buf -> new OpenProducerScreenPayload(
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
