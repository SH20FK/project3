package com.project3.mixin;

import com.project3.client.CameraShakeManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "bobView", at = @At("TAIL"))
    private void onBobView(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        CameraShakeManager.applyShake(matrices, tickDelta);
    }
}
