package com.project3.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void onGetFogColor(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        if (p3$isGloomVoid(world)) {
            cir.setReturnValue(new Vector4f(0.01F, 0.005F, 0.01F, 1.0F));
        }
    }

    @Inject(method = "getFogStart", at = @At("RETURN"), cancellable = true)
    private void onGetFogStart(Camera camera, float tickDelta, ClientWorld world, float viewDistance, float skyDarkness, CallbackInfoReturnable<Float> cir) {
        if (p3$isGloomVoid(world)) {
            float val = cir.getReturnValue();
            cir.setReturnValue(val * 0.3F);
        }
    }

    @Inject(method = "getFogEnd", at = @At("RETURN"), cancellable = true)
    private void onGetFogEnd(Camera camera, float tickDelta, ClientWorld world, float viewDistance, float skyDarkness, CallbackInfoReturnable<Float> cir) {
        if (p3$isGloomVoid(world)) {
            float val = cir.getReturnValue();
            cir.setReturnValue(val * 0.6F);
        }
    }

    @Unique
    private boolean p3$isGloomVoid(ClientWorld world) {
        return world != null && world.getRegistryKey().getValue().toString().equals("p3:gloom_void");
    }
}
