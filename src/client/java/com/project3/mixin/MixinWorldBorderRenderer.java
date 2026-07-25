package com.project3.mixin;

import net.minecraft.client.render.WorldBorderRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the visible shimmering world border wall on the client,
 * while keeping the warning red overlay (warningBlocks) functional.
 */
@Mixin(WorldBorderRenderer.class)
public class MixinWorldBorderRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cancelBorderRendering(MatrixStack matrices, CallbackInfo ci) {
        ci.cancel();
    }
}
