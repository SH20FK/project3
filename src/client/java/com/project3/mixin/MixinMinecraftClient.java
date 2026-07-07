package com.project3.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void onDoItemUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player == null) return;

        ItemStack mainHand = client.player.getMainHandStack();
        ItemStack offHand = client.player.getOffHandStack();
        boolean holdingChrono = mainHand.isOf(com.project3.Project3Mod.AI_CHRONOMETER) || offHand.isOf(com.project3.Project3Mod.AI_CHRONOMETER);
        boolean holdingDump = mainHand.isOf(com.project3.Project3Mod.AI_DUMP_ANALYZER) || offHand.isOf(com.project3.Project3Mod.AI_DUMP_ANALYZER);

        if (!holdingChrono && !holdingDump) return;

        int itemType = holdingChrono ? 1 : 2;

        net.minecraft.util.hit.HitResult hitResult = client.crosshairTarget;
        if (hitResult != null) {
            if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY) {
                net.minecraft.util.hit.EntityHitResult entityHit = (net.minecraft.util.hit.EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof net.minecraft.entity.player.PlayerEntity targetPlayer) {
                    int actionType = 0;
                    if (net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(), net.minecraft.client.util.InputUtil.GLFW_KEY_LEFT_SHIFT) || net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(), net.minecraft.client.util.InputUtil.GLFW_KEY_RIGHT_SHIFT)) {
                        actionType = 1;
                    } else if (net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(), net.minecraft.client.util.InputUtil.GLFW_KEY_LEFT_CONTROL) || net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(), net.minecraft.client.util.InputUtil.GLFW_KEY_RIGHT_CONTROL)) {
                        actionType = 2;
                    }

                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.project3.network.AdminToolUsePayload(itemType, actionType, targetPlayer.getUuid(), null)
                    );

                    client.player.swingHand(mainHand.isOf(com.project3.Project3Mod.AI_CHRONOMETER) || mainHand.isOf(com.project3.Project3Mod.AI_DUMP_ANALYZER) ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND);
                    ci.cancel();
                }
            } else if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && holdingDump && (net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(), net.minecraft.client.util.InputUtil.GLFW_KEY_LEFT_SHIFT) || net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(), net.minecraft.client.util.InputUtil.GLFW_KEY_RIGHT_SHIFT))) {
                net.minecraft.util.hit.BlockHitResult blockHit = (net.minecraft.util.hit.BlockHitResult) hitResult;
                net.minecraft.util.math.BlockPos blockPos = blockHit.getBlockPos();

                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.project3.network.AdminToolUsePayload(itemType, 3, null, blockPos)
                );

                client.player.swingHand(mainHand.isOf(com.project3.Project3Mod.AI_DUMP_ANALYZER) ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND);
                ci.cancel();
            }
        }
    }
}
