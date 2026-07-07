package com.project3.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void onGetFogColor(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        if (p3$isGloomVoid(world)) {
            cir.setReturnValue(new Vector4f(0.01F, 0.005F, 0.01F, 1.0F));
        }
    }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 3)
    private float p3$modifyEnvironmentalStart(float envStart) {
        return p3$isGloomVoid() ? envStart * 0.3F : envStart;
    }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 4)
    private float p3$modifyEnvironmentalEnd(float envEnd) {
        return p3$isGloomVoid() ? envEnd * 0.6F : envEnd;
    }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 5)
    private float p3$modifyRenderDistanceStart(float rdStart) {
        return p3$isGloomVoid() ? rdStart * 0.3F : rdStart;
    }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 6)
    private float p3$modifyRenderDistanceEnd(float rdEnd) {
        return p3$isGloomVoid() ? rdEnd * 0.6F : rdEnd;
    }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 7)
    private float p3$modifySkyEnd(float skyEnd) {
        return p3$isGloomVoid() ? skyEnd * 0.6F : skyEnd;
    }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), argsOnly = true, index = 8)
    private float p3$modifyCloudEnd(float cloudEnd) {
        return p3$isGloomVoid() ? cloudEnd * 0.6F : cloudEnd;
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
