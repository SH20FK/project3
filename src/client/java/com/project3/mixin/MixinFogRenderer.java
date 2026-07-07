package com.project3.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Proxy;
import java.util.List;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Shadow
    private static List FOG_MODIFIERS;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void onClinit(CallbackInfo ci) {
        FOG_MODIFIERS.add(p3$createFogModifier());
    }

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void onGetFogColor(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        if (p3$isGloomVoid(world)) {
            cir.setReturnValue(new Vector4f(0.01F, 0.005F, 0.01F, 1.0F));
        }
    }

    @Unique
    private static FogModifier p3$createFogModifier() {
        return (FogModifier) Proxy.newProxyInstance(
            FogModifier.class.getClassLoader(),
            new Class[]{FogModifier.class},
            (proxy, method, args) -> {
                if (method.getName().equals("applyStartEndModifier") && args != null && args.length >= 2) {
                    FogData data = (FogData) args[0];
                    ClientWorld world = (ClientWorld) args[2];
                    if (world != null && world.getRegistryKey().getValue().toString().equals("p3:gloom_void")) {
                        data.environmentalStart *= 0.3F;
                        data.environmentalEnd *= 0.6F;
                        data.renderDistanceStart *= 0.3F;
                        data.renderDistanceEnd *= 0.6F;
                        data.skyEnd *= 0.6F;
                        data.cloudEnd *= 0.6F;
                    }
                }
                if (method.getReturnType() == float.class) return 0.0f;
                if (method.getReturnType() == int.class) return 0;
                if (method.getReturnType() == boolean.class) return true;
                return null;
            }
        );
    }

    @Unique
    private boolean p3$isGloomVoid(ClientWorld world) {
        return world != null && world.getRegistryKey().getValue().toString().equals("p3:gloom_void");
    }
}
