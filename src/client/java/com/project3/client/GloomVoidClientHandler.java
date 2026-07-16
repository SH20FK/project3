package com.project3.client;

import com.project3.client.hud.DreadHandler;
import com.project3.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles all client-side rendering and effects for the Gloom Void.
 * (Vignette, ambient particles, shader glitches).
 */
public final class GloomVoidClientHandler {

    private GloomVoidClientHandler() {}

    public static int glitchTickIndex = -1;
    public static final int[] STROBE_PATTERN = {1, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1, 0, 1, 0};
    public static final Set<Integer> GLITCHED_STATUE_IDS = Collections.synchronizedSet(new HashSet<>());
    public static int vignetteFlickerTick = 0;
    public static boolean isInGloomVoid = false;

    public static void tick(MinecraftClient client) {
        isInGloomVoid = client.world != null 
            && client.world.getRegistryKey().getValue().toString().equals("p3:gloom_void");

        // Ambient particles in Gloom Void
        if (isInGloomVoid && client.player != null) {
            var random = client.world.random;
            var player = client.player;

            if (random.nextInt(4) == 0) {
                double px = player.getX() + (random.nextDouble() - 0.5) * 6.0;
                double py = player.getY() + random.nextDouble() * 3.0;
                double pz = player.getZ() + (random.nextDouble() - 0.5) * 6.0;
                client.particleManager.addParticle(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    px, py, pz, 0.0, 0.015 + random.nextDouble() * 0.01, 0.0
                );
            }

            if (random.nextInt(6) == 0) {
                double px = player.getX() + (random.nextDouble() - 0.5) * 8.0;
                double py = player.getY() + 0.1 + random.nextDouble() * 0.3;
                double pz = player.getZ() + (random.nextDouble() - 0.5) * 8.0;
                client.particleManager.addParticle(
                    ParticleTypes.CURRENT_DOWN,
                    px, py, pz, 0.0, -0.02, 0.0
                );
            }

            if (random.nextInt(10) == 0) {
                double px = player.getX() + (random.nextDouble() - 0.5) * 10.0;
                double py = player.getY() + 2.0 + random.nextDouble() * 4.0;
                double pz = player.getZ() + (random.nextDouble() - 0.5) * 10.0;
                client.particleManager.addParticle(
                    ParticleTypes.ASH,
                    px, py, pz,
                    (random.nextDouble() - 0.5) * 0.02, -0.005, (random.nextDouble() - 0.5) * 0.02
                );
            }
        }

        // Glitch shader ticking
        if (glitchTickIndex >= 0 && glitchTickIndex < STROBE_PATTERN.length) {
            int action = STROBE_PATTERN[glitchTickIndex];
            if (action == 1) {
                Identifier shaderId = client.world.random.nextBoolean() 
                    ? Identifier.of("minecraft", "invert") 
                    : Identifier.of("minecraft", "blur");
                try {
                    ((GameRendererAccessor) client.gameRenderer).invokeLoadPostProcessor(shaderId);
                } catch (Exception e) {}
            } else {
                client.gameRenderer.clearPostProcessor();
            }
            glitchTickIndex++;
            if (glitchTickIndex >= STROBE_PATTERN.length) {
                glitchTickIndex = -1;
                client.gameRenderer.clearPostProcessor();
            }
        } else if (isInGloomVoid) {
            if (client.world.random.nextFloat() < 0.004f) { // ~once every 250 ticks
                triggerShaderGlitch(client);
            }
            if (vignetteFlickerTick <= 0 && client.world.random.nextFloat() < 0.005f) {
                vignetteFlickerTick = 4 + client.world.random.nextInt(4);
            }
        }
    }

    public static void triggerShaderGlitch(MinecraftClient client) {
        if (client.world == null) return;
        if (client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, 1.0f, 1.0f);
        }
        glitchTickIndex = 0;
    }

    public static void renderVignette(DrawContext ctx) {
        if (!isInGloomVoid) return;

        var client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        int vignetteAlphaBase = 80;
        int vignetteRings = 10;

        for (int ring = 0; ring < vignetteRings; ring++) {
            float progress = ring / (float)(vignetteRings - 1);
            int expandX = (int) (width * 0.18f * progress);
            int expandY = (int) (height * 0.18f * progress);
            int alpha = (int) (progress * progress * vignetteAlphaBase);

            int left = Math.max(centerX - width / 2 - expandX, 0);
            int top = Math.max(centerY - height / 2 - expandY, 0);
            int right = Math.min(centerX + width / 2 + expandX, width);
            int bottom = Math.min(centerY + height / 2 + expandY, height);

            ctx.fill(left, top, right, bottom, (alpha << 24) | 0x080000);
        }

        int edgeAlpha = 50;
        ctx.fill(0, 0, width, 20, (edgeAlpha << 24) | 0x040000);
        ctx.fill(0, height - 20, width, height, (edgeAlpha << 24) | 0x040000);
        ctx.fill(0, 0, 8, height, (edgeAlpha << 24) | 0x040000);
        ctx.fill(width - 8, 0, width, height, (edgeAlpha << 24) | 0x040000);

        int dreadTint = DreadHandler.getScreenTint();
        if (dreadTint != 0) {
            ctx.fill(0, 0, width, height, dreadTint);
        }

        if (vignetteFlickerTick > 0) {
            vignetteFlickerTick--;
            if (vignetteFlickerTick % 2 == 0) {
                ctx.fill(0, 0, width, height, 0x08000000);
            }
        }
    }
}
