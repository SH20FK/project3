package com.project3.mixin;

import net.minecraft.class_5636;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.render.fog.FogRenderer.FogType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

public class GloomFogModifier implements FogModifier {

    @Override
    public void applyStartEndModifier(FogData data, Camera camera, ClientWorld world, float tickDelta, FogType fogType) {
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
    public float applyDarknessModifier(LivingEntity cameraEntity, float darkness, float tickProgress) {
        return darkness;
    }

    @Override
    public boolean shouldApply(class_5636 submersionType, Entity cameraEntity) {
        return true;
    }

    @Override
    public int getFogColor(World world, Camera camera, int viewDistance, int skyDarkness) {
        return 0;
    }

    @Override
    public boolean isColorSource() {
        return false;
    }

    @Override
    public boolean isDarknessModifier() {
        return false;
    }
}
