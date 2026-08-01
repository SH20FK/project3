package com.project3.client;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

public final class CameraShakeManager {

    private static float trauma = 0f;
    private static float time = 0f;

    private static final float DECAY_RATE = 0.02f;
    private static final float MAX_ANGLE = 4.0f;
    private static final float NOISE_FREQUENCY = 0.6f;

    public static void addTrauma(float amount) {
        trauma = Math.min(1.0f, trauma + amount);
    }

    public static void tick() {
        trauma = Math.max(0f, trauma - DECAY_RATE);
        time += 1f;
    }

    public static boolean isActive() {
        return trauma > 0.001f;
    }

    public static void applyShake(MatrixStack matrices, float tickDelta) {
        if (trauma <= 0.001f) return;

        float shakeAmount = trauma * trauma;

        float t = time + tickDelta;
        float pitch = MAX_ANGLE * shakeAmount * noise1D(t * NOISE_FREQUENCY);
        float yaw   = MAX_ANGLE * shakeAmount * noise1D(t * NOISE_FREQUENCY + 100f);
        float roll  = MAX_ANGLE * shakeAmount * noise1D(t * NOISE_FREQUENCY + 200f);

        matrices.multiply(new Quaternionf().rotationZ((float) Math.toRadians(roll)));
        matrices.multiply(new Quaternionf().rotationX((float) Math.toRadians(pitch)));
        matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(yaw * 0.3f)));
    }

    private static float noise1D(float x) {
        int xi = (int) Math.floor(x);
        float xf = x - xi;
        float v0 = hashToFloat(xi);
        float v1 = hashToFloat(xi + 1);
        float smooth = xf * xf * (3f - 2f * xf);
        return v0 + (v1 - v0) * smooth;
    }

    private static float hashToFloat(int i) {
        int h = i * 374761393;
        h = (h ^ (h >> 13)) * 1274126177;
        h = h ^ (h >> 16);
        return (h & 0xFFFF) / 65535f * 2f - 1f;
    }
}
