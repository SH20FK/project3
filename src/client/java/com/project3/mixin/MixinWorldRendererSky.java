package com.project3.mixin;

import com.project3.client.GloomVoidClientHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public class MixinWorldRendererSky {

    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderSky(org.joml.Matrix4f matrix4f, org.joml.Matrix4f matrix4f2, float f, net.minecraft.client.render.Camera camera, boolean bl, Runnable runnable, CallbackInfo ci) {
        if (GloomVoidClientHandler.isInGloomVoid) {
            // Cancel vanilla sky rendering (Sun, Moon, Stars, Colors)
            // The background clear color is already dark from MixinFogRenderer, 
            // so canceling sky render leaves it completely pitch black.
            ci.cancel();
        }
    }
}
