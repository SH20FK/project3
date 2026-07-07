package com.project3.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

public class GloomFogModifier extends FogModifier {

    @Override
    public void applyStartEndModifier(FogData data, Camera camera, ClientWorld world, float tickDelta, RenderTickCounter renderTickCounter) {
        if (world != null && world.getRegistryKey().getValue().toString().equals("p3:gloom_void")) {
            data.environmentalStart *= 0.3F;
            data.environmentalEnd *= 0.6F;
            data.renderDistanceStart *= 0.3F;
            data.renderDistanceEnd *= 0.6F;
            data.skyEnd *= 0.6F;
            data.cloudEnd *= 0.6F;
        }
    }

    @Override
    public boolean shouldApply(net.minecraft.block.enums.CameraSubmersionType submersionType, Entity entity) {
        return true;
    }

}
