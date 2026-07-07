package com.project3.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenInventoryPayload() implements CustomPayload {
    public static final Id<OpenInventoryPayload> ID = new Id<>(Identifier.of("p3", "open_inventory"));
    public static final PacketCodec<RegistryByteBuf, OpenInventoryPayload> CODEC = PacketCodec.unit(new OpenInventoryPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
