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

    @Inject(method = "renderSky(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderSky(net.minecraft.client.render.FrameGraphBuilder frameGraphBuilder, net.minecraft.client.render.Camera camera, com.mojang.blaze3d.buffers.GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
        if (GloomVoidClientHandler.isInGloomVoid) {
            // Cancel vanilla sky rendering (Sun, Moon, Stars, Colors)
            // The background clear color is already dark from MixinFogRenderer, 
            // so canceling sky render leaves it completely pitch black.
            ci.cancel();
        }
    }
}
