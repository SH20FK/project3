package com.project3.client;

import com.project3.Project3Mod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Client-side handler for the escalating border violation effects.
 * Receives tick count from the server and renders progressive
 * fog, screen desaturation, and distortion overlays.
 *
 * Stages (based on violationTicks):
 *   0-100   (0-5s)    — ambient fog creeping in
 *   100-300 (5-15s)   — thick fog + dark vignette
 *   300-450 (15-22.5s) — desaturated screen + heavy vignette
 *   450+    (22.5-30s) — collapse imminent (server handles teleport)
 */
@Environment(EnvType.CLIENT)
public final class BorderVoidHandler {

    private BorderVoidHandler() {}

    private static int violationTicks = 0;
    private static boolean shaderActive = false;
    private static int cleanupTicks = 0;

    /** Called when a BorderEffectPayload is received. */
    public static void setViolationTicks(int ticks) {
        if (ticks == 0) {
            // Fully reset
            violationTicks = 0;
            cleanupShader();
            return;
        }
        violationTicks = ticks;
        cleanupTicks = 0;
    }

    public static int getViolationTicks() {
        return violationTicks;
    }

    /** Called every client tick. */
    public static void tick() {
        if (violationTicks <= 0) return;

        // If we haven't received an update in 100 ticks, start cleanup
        cleanupTicks++;
        if (cleanupTicks > 100) {
            violationTicks = Math.max(0, violationTicks - 1);
            if (violationTicks == 0) {
                cleanupShader();
            }
        }

        // Stage 2+ (300+ ticks): activate desaturate shader
        if (violationTicks >= 300 && !shaderActive) {
            applyDesaturateShader();
        } else if (violationTicks < 300 && shaderActive) {
            cleanupShader();
        }
    }

    /** Draws the border effect overlay. */
    public static void renderOverlay(DrawContext context) {
        if (violationTicks <= 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        // ── Stage 0: faint edge fog (tickCount 0-100) ──────────────────────
        // ── Stage 1: vignette closes in (tickCount 100-300) ────────────────
        // ── Stage 2+: heavy dark vignette (tickCount 300+) ─────────────────

        float progress;
        int baseAlpha;
        int rings;

        if (violationTicks < 100) {
            // Stage 0: barely visible edge fog
            progress = violationTicks / 100.0f;
            baseAlpha = (int)(progress * 40);
            rings = 5;
        } else if (violationTicks < 300) {
            // Stage 1: fog thickens
            progress = (violationTicks - 100) / 200.0f;
            baseAlpha = 40 + (int)(progress * 80);
            rings = 8;
        } else {
            // Stage 2+: heavy darkness
            progress = Math.min((violationTicks - 300) / 150.0f, 1.0f);
            baseAlpha = 120 + (int)(progress * 100);
            rings = 12;

            // Additional full-screen dark overlay (subtle)
            int darkAlpha = (int)(progress * 60);
            if (darkAlpha > 0) {
                context.fill(0, 0, width, height, (darkAlpha << 24) | 0x000000);
            }
        }

        // Draw vignette rings from center outward
        int centerX = width / 2;
        int centerY = height / 2;

        for (int ring = 0; ring < rings; ring++) {
            float ringProgress = ring / (float)(rings - 1);
            int expandX = (int)(width * 0.20f * ringProgress);
            int expandY = (int)(height * 0.20f * ringProgress);
            int alpha = (int)(ringProgress * ringProgress * baseAlpha);

            int left = Math.max(centerX - width / 2 - expandX, 0);
            int top = Math.max(centerY - height / 2 - expandY, 0);
            int right = Math.min(centerX + width / 2 + expandX, width);
            int bottom = Math.min(centerY + height / 2 + expandY, height);

            context.fill(left, top, right, bottom, (alpha << 24) | 0x000805);
        }

        // Edge bars (stage 1+)
        if (violationTicks >= 100) {
            int edgeAlpha = 40 + (int)(progress * 60);
            context.fill(0, 0, width, Math.min(15 + (int)(progress * 20), height / 4), (edgeAlpha << 24) | 0x000805);
            context.fill(0, height - Math.min(15 + (int)(progress * 20), height / 4), width, height, (edgeAlpha << 24) | 0x000805);
            context.fill(0, 0, Math.min(8 + (int)(progress * 12), width / 4), height, (edgeAlpha << 24) | 0x000805);
            context.fill(width - Math.min(8 + (int)(progress * 12), width / 4), 0, width, height, (edgeAlpha << 24) | 0x000805);
        }

        // Stage 2+: add grey corners / desaturation hint
        if (violationTicks >= 250) {
            int greyAlpha = (int)((violationTicks - 250) / 200.0f * 40);
            if (greyAlpha > 0) {
                context.fill(0, 0, width, height, (greyAlpha << 24) | 0x555555);
            }
        }

        // Stage 3: flicker effect before teleport
        if (violationTicks >= 450) {
            if (client.world != null && client.world.random.nextInt(3) == 0) {
                context.fill(0, 0, width, height, 0x30FFFFFF);
            }
        }
    }

    private static void applyDesaturateShader() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        try {
            var shaderId = Identifier.of("minecraft", "invert");
            var accessor = (com.project3.mixin.GameRendererAccessor) client.gameRenderer;
            accessor.invokeLoadPostProcessor(shaderId);
            shaderActive = true;
        } catch (Exception e) {
            // Shader may not be available
        }
    }

    private static void cleanupShader() {
        if (shaderActive) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.gameRenderer != null) {
                client.gameRenderer.clearPostProcessor();
            }
            shaderActive = false;
        }
    }

    /** Call when the player is fully reset (teleported back safe). */
    public static void reset() {
        violationTicks = 0;
        cleanupTicks = 0;
        cleanupShader();
    }
}
