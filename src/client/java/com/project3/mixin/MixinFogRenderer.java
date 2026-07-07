package com.project3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.ByteBuffer;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void onGetFogColor(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        if (p3$isGloomVoid(world)) {
            cir.setReturnValue(new Vector4f(0.01F, 0.005F, 0.01F, 1.0F));
        }
    }

    @WrapOperation(method = "rotate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void wrapApplyFog(Operation<Void> original, FogRenderer instance, ByteBuffer buffer, int bufPos, Vector4f color,
        float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd,
        float skyEnd, float cloudEnd) {
        if (p3$isGloomVoid()) {
            environmentalStart *= 0.3F;
            environmentalEnd *= 0.6F;
            renderDistanceStart *= 0.3F;
            renderDistanceEnd *= 0.6F;
            skyEnd *= 0.6F;
            cloudEnd *= 0.6F;
        }
        original.call(instance, buffer, bufPos, color, environmentalStart, environmentalEnd,
            renderDistanceStart, renderDistanceEnd, skyEnd, cloudEnd);
    }

    @Unique
    private boolean p3$isGloomVoid(ClientWorld world) {
        return world != null && world.getRegistryKey().getValue().toString().equals("p3:gloom_void");
    }

    @Unique
    private boolean p3$isGloomVoid() {
        var client = MinecraftClient.getInstance();
        if (client != null) {
            var world = client.world;
            return world != null && world.getRegistryKey().getValue().toString().equals("p3:gloom_void");
        }
        return false;
    }
}
