package com.project3.mixin;

import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerListS2CPacket.Entry.class)
public class MixinPlayerListS2CPacketEntry {

    @Redirect(
        method = "<init>(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;getLatency()I"
        )
    )
    private static int redirectGetLatency(ServerPlayNetworkHandler instance) {
        return instance != null ? instance.getLatency() : 0;
    }
}
