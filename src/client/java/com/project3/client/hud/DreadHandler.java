package com.project3.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side handler for Dread effects.
 * Receives dread level from server and renders visual/audio effects.
 *
 * Threshold 0 (0-20): Calm - nothing
 * Threshold 1 (21-50): Uneasy - subtle blue tint
 * Threshold 2 (51-80): Terrified - distorted sounds, camera shake
 * Threshold 3 (81-100): Breaking - random teleport, block corruption
 */
@Environment(EnvType.CLIENT)
public class DreadHandler {

    private static int dread = 0;
    private static int threshold = 0;
    private static int tickCounter = 0;

    public static void setLevel(int dreadLevel, int thresholdLevel) {
        dread = Math.max(0, Math.min(120, dreadLevel));
        threshold = Math.max(0, Math.min(4, thresholdLevel));
    }

    public static int getDread() {
        return dread;
    }

    public static int getThreshold() {
        return threshold;
    }

    public static void tick() {
        if (threshold <= 0) return;
        tickCounter++;
    }

    /** Returns screen tint color (ARGB). 0 = no tint. */
    public static int getScreenTint() {
        if (threshold < 1) return 0;
        if (threshold == 1) {
            // Subtle blue-grey tint, alpha ~20
            return (8 << 24) | (0x101828);
        }
        if (threshold == 2) {
            // Darker blue tint, alpha ~40
            return (20 << 24) | (0x0a1020);
        }
        // Threshold 3-4: heavy dark tint
        return (40 << 24) | (0x050810);
    }

    /** Returns camera shake intensity (0.0 - 1.0) */
    public static float getCameraShake() {
        if (threshold < 2) return 0.0f;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return 0.0f;
        float random = client.player.getRandom().nextFloat();
        if (threshold == 2) return random * 0.3f;
        return random * 0.8f;
    }

    /** Returns true if distorted ambient sounds should play */
    public static boolean shouldPlayDistortedSounds() {
        return threshold >= 2 && tickCounter % 40 == 0;
    }
}
