package com.project3;

import com.project3.block.entity.renderer.ProducerBlockEntityRenderer;
import com.project3.network.CameraRotatePayload;
import com.project3.network.AchievementSyncPayload;
import com.project3.client.hud.ActiveAchievementHud;
import com.project3.client.hud.ParanoiaHandler;
import com.project3.client.hud.DreadHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Client-side entry point for Project3.
 * Registers:
 *  - Network payload handlers (CameraRotate, ShowTutorial)
 *  - Tutorial HUD overlay
 *  - "Hide hint" key binding (default: X)
 *  - ProducerBlockEntityRenderer
 */
public class Project3Client implements ClientModInitializer {
    private static long lastChunkReloadTime = 0L;
    public static int glitchTickIndex = -1;
    public static final int[] STROBE_PATTERN = {1, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1, 0, 1, 0};
    public static final java.util.Set<Integer> GLITCHED_STATUE_IDS = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    public static int vignetteFlickerTick = 0;
    private static boolean isInGloomVoid = false;

    // Keybind for toggling achievement panel
    public static net.minecraft.client.option.KeyBinding togglePanelKey;

    @Override
    public void onInitializeClient() {
        // ── Register Keybind ─────────────────────────────────────────────
        togglePanelKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
            new net.minecraft.client.option.KeyBinding(
                "key.p3.toggle_panel",
                net.minecraft.client.util.InputUtil.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_P,
                net.minecraft.client.option.KeyBinding.Category.INVENTORY
            )
        );

        // ── Register BlockEntityRenderer ──────────────────────────────────
        BlockEntityRendererRegistry.register(
                Project3Mod.PRODUCER_BLOCK_ENTITY_TYPE,
                ProducerBlockEntityRenderer::new
        );
        BlockEntityRendererRegistry.register(
                Project3Mod.PHANTOM_BLOCK_ENTITY_TYPE,
                com.project3.block.entity.renderer.PhantomBlockEntityRenderer::new
        );

        // ── Register Screen Handler → Screen mapping ─────────────────────
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                Project3Mod.PRODUCER_SCREEN_HANDLER,
                com.project3.client.screen.ProducerScreen::new
        );

        // BlockRenderLayerMap no longer needed in 1.21.11+ for Cutout, 
        // as it's defined in model json or block properties usually.

        // ── Network: CameraRotatePayload → adjust yaw/pitch ───────────────
        ClientPlayNetworking.registerGlobalReceiver(
                CameraRotatePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        var player = context.client().player;
                        if (player != null) {
                            player.setYaw(player.getYaw() + payload.deltaYaw());
                            player.setPitch(
                                     Math.max(-90f, Math.min(90f,
                                            player.getPitch() + payload.deltaPitch()))
                            );
                        }
                    });
                }
        );

        // ── Network: AchievementSyncPayload → update HUD ──────────────────
        ClientPlayNetworking.registerGlobalReceiver(
                AchievementSyncPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        ActiveAchievementHud.update(payload);
                    });
                }
        );

        // ── Network: PlayerStateSyncPayload → update HUD state ─────────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.project3.network.PlayerStateSyncPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        ActiveAchievementHud.updatePlayerState(payload);
                    });
                }
        );

        // ── Network: ParanoiaPayload → update paranoia level ──────────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.project3.network.ParanoiaPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        com.project3.client.hud.ParanoiaHandler.setLevel(payload.level());
                    });
                }
        );

        // ── Network: DreadPayload → update dread level ──────────────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.project3.network.DreadPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        com.project3.client.hud.DreadHandler.setLevel(payload.dread(), payload.threshold());
                    });
                }
        );

        // ── Network: ChunkReloadPayload → reload world rendering ──────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.project3.network.ChunkReloadPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        long now = System.currentTimeMillis();
                        if (now - lastChunkReloadTime >= 5000L) {
                            lastChunkReloadTime = now;
                            if (context.client().worldRenderer != null) {
                                context.client().worldRenderer.reload();
                            }
                        }
                    });
                }
        );

        // ── Network: ShaderFlashPayload → trigger screen glitch ───────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.project3.network.ShaderFlashPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        triggerShaderGlitch(context.client());
                    });
                }
        );

        // ── Network: SpawnPhantomPayload → spawn fake player on client ────
        ClientPlayNetworking.registerGlobalReceiver(
                com.project3.network.SpawnPhantomPayload.ID,
                (payload, context) -> {
                    // Start filling the profile properties asynchronously to prevent network requests from freezing the main thread.
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            return new com.mojang.authlib.GameProfile(payload.skinUuid(), payload.skinName());
                        } catch (Exception e) {
                            return new com.mojang.authlib.GameProfile(payload.skinUuid(), payload.skinName());
                        }
                    }).thenAcceptAsync(filledProfile -> {
                        var world = context.client().world;
                        var player = context.client().player;
                        if (world != null && player != null) {
                            var fakePlayer = new net.minecraft.client.network.OtherClientPlayerEntity(
                                    world,
                                    filledProfile
                            );
                            fakePlayer.setId(payload.entityId());
                            fakePlayer.setPosition(payload.x(), payload.y(), payload.z());
                            fakePlayer.setYaw(payload.yaw());
                            fakePlayer.setPitch(payload.pitch());
                            fakePlayer.setHeadYaw(payload.yaw());
                            fakePlayer.setBodyYaw(payload.yaw());

                            // Copy player armor & equipment to phantom
                            for (var slot : net.minecraft.entity.EquipmentSlot.values()) {
                                fakePlayer.equipStack(slot, player.getEquippedStack(slot).copy());
                            }

                            world.addEntity(fakePlayer);
                        }
                    }, context.client()::execute);
                }
        );

        // ── Network: RemovePhantomPayload → despawn fake player ───────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.project3.network.RemovePhantomPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        var world = context.client().world;
                        if (world != null) {
                            world.removeEntity(payload.entityId(), net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                        }
                    });
                }
        );

        // ── Network: PhantomHeadSnapPayload → snap fake head 180 ───────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.project3.network.PhantomHeadSnapPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        var world = context.client().world;
                        if (world != null) {
                            var entity = world.getEntityById(payload.entityId());
                            if (entity != null) {
                                entity.setHeadYaw(entity.getHeadYaw() + 180.0f);
                                entity.setBodyYaw(entity.getBodyYaw() + 180.0f);
                                entity.setPitch(-25.0f);
                            }
                        }
                    });
                }
        );



        // ── Network: SpawnStatuePayload → spawn fake statue on client ──────
        ClientPlayNetworking.registerGlobalReceiver(
                com.project3.network.SpawnStatuePayload.ID,
                (payload, context) -> {
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        var gameProfile = new com.mojang.authlib.GameProfile(payload.skinUuid(), payload.skinName());
                        try {
                            return gameProfile;
                        } catch (Exception e) {
                            return gameProfile;
                        }
                    }).thenAcceptAsync(filledProfile -> {
                        var world = context.client().world;
                        var player = context.client().player;
                        if (world != null && player != null) {
                            var fakePlayer = new net.minecraft.client.network.OtherClientPlayerEntity(
                                    world,
                                    filledProfile
                            );
                            fakePlayer.setId(payload.entityId());
                            fakePlayer.setPosition(payload.x(), payload.y(), payload.z());
                            fakePlayer.setYaw(payload.yaw());
                            fakePlayer.setPitch(payload.pitch());
                            fakePlayer.setHeadYaw(payload.headYaw());
                            fakePlayer.setBodyYaw(payload.bodyYaw());

                            for (var slot : net.minecraft.entity.EquipmentSlot.values()) {
                                fakePlayer.equipStack(slot, player.getEquippedStack(slot).copy());
                            }

                            try {
                                fakePlayer.setPose(net.minecraft.entity.EntityPose.valueOf(payload.poseName()));
                            } catch (Exception e) {}

                            fakePlayer.setNoGravity(true);
                            fakePlayer.noClip = true;

                            world.addEntity(fakePlayer);
                            GLITCHED_STATUE_IDS.add(payload.entityId());
                        }
                    }, context.client()::execute);
                }
        );

        // ── Register HUD Render Callback ──────────────────────────────────
        HudRenderCallback.EVENT.register(ActiveAchievementHud::render);
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            if (isInGloomVoid) {
                p3$renderVignette(drawContext);
            }
        });

        // ── Register Client Tick Event for smooth HUD timers countdown ────
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ActiveAchievementHud.tick();
            ParanoiaHandler.tick();
            DreadHandler.tick();

            // Toggle achievement panel visibility
            if (togglePanelKey != null) {
                while (togglePanelKey.wasPressed()) {
                    ActiveAchievementHud.panelVisible = !ActiveAchievementHud.panelVisible;
                }
            }

            // Update Gloom Void state
            isInGloomVoid = client.world != null 
                && client.world.getRegistryKey().getValue().toString().equals("p3:gloom_void");

            // ── Ambient particles in Gloom Void ───────────────────────────
            if (isInGloomVoid && client.player != null) {
                var random = client.world.random;
                var player = client.player;

                // Campfire smoke particles
                if (random.nextInt(4) == 0) {
                    double px = player.getX() + (random.nextDouble() - 0.5) * 6.0;
                    double py = player.getY() + random.nextDouble() * 3.0;
                    double pz = player.getZ() + (random.nextDouble() - 0.5) * 6.0;
                    client.particleManager.addParticle(
                        net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        px, py, pz, 0.0, 0.015 + random.nextDouble() * 0.01, 0.0
                    );
                }

                // Dark dust particles near ground
                if (random.nextInt(6) == 0) {
                    double px = player.getX() + (random.nextDouble() - 0.5) * 8.0;
                    double py = player.getY() + 0.1 + random.nextDouble() * 0.3;
                    double pz = player.getZ() + (random.nextDouble() - 0.5) * 8.0;
                    client.particleManager.addParticle(
                        net.minecraft.particle.ParticleTypes.CURRENT_DOWN,
                        px, py, pz, 0.0, -0.02, 0.0
                    );
                }

                // Ambient ash particles drifting sideways
                if (random.nextInt(10) == 0) {
                    double px = player.getX() + (random.nextDouble() - 0.5) * 10.0;
                    double py = player.getY() + 2.0 + random.nextDouble() * 4.0;
                    double pz = player.getZ() + (random.nextDouble() - 0.5) * 10.0;
                    client.particleManager.addParticle(
                        net.minecraft.particle.ParticleTypes.ASH,
                        px, py, pz,
                        (random.nextDouble() - 0.5) * 0.02,
                        -0.005,
                        (random.nextDouble() - 0.5) * 0.02
                    );
                }
            }

            // Glitch shader ticking
            if (glitchTickIndex >= 0 && glitchTickIndex < STROBE_PATTERN.length) {
                int action = STROBE_PATTERN[glitchTickIndex];
                if (action == 1) {
                    net.minecraft.util.Identifier shaderId;
                    if (client.world.random.nextBoolean()) {
                        shaderId = net.minecraft.util.Identifier.of("minecraft", "invert");
                    } else {
                        shaderId = net.minecraft.util.Identifier.of("minecraft", "blur");
                    }
                    try {
                        ((com.project3.mixin.GameRendererAccessor) client.gameRenderer).invokeLoadPostProcessor(shaderId);
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
                if (client.world.random.nextFloat() < 0.004f) { // ~once every 250 ticks (12.5 seconds)
                    triggerShaderGlitch(client);
                }
                // Subtle screen flicker
                if (vignetteFlickerTick <= 0 && client.world.random.nextFloat() < 0.005f) {
                    vignetteFlickerTick = 4 + client.world.random.nextInt(4);
                }
            }
        });

        // ── Clean up client producers and clear state on disconnect ──────
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.project3.block.entity.ProducerBlockEntity.CLIENT_PRODUCERS.clear();
        });
    }

    public static void triggerShaderGlitch(net.minecraft.client.MinecraftClient client) {
        if (client.world == null) return;
        if (client.player != null) {
            client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, 
                1.0f, 1.0f);
        }
        glitchTickIndex = 0;
    }

    /**
     * Renders a dark vignette overlay around screen edges for Gloom Void atmosphere.
     * Creates a gradient from transparent center to dark red/black edges.
     */
    private static void p3$renderVignette(net.minecraft.client.gui.DrawContext ctx) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        // Darker, more oppressive vignette with red tint
        int vignetteAlphaBase = 80; // was 70
        int vignetteRings = 10;     // was 8 — smoother gradient

        for (int ring = 0; ring < vignetteRings; ring++) {
            float progress = ring / (float)(vignetteRings - 1);
            int expandX = (int) (width * 0.18f * progress);
            int expandY = (int) (height * 0.18f * progress);
            int alpha = (int) (progress * progress * vignetteAlphaBase);

            int left = Math.max(centerX - width / 2 - expandX, 0);
            int top = Math.max(centerY - height / 2 - expandY, 0);
            int right = Math.min(centerX + width / 2 + expandX, width);
            int bottom = Math.min(centerY + height / 2 + expandY, height);

            // Slight red tint to vignette color
            ctx.fill(left, top, right, bottom, (alpha << 24) | 0x080000);
        }

        // Top and bottom edge bars — darker, red-tinted
        int edgeAlpha = 50;
        ctx.fill(0, 0, width, 20, (edgeAlpha << 24) | 0x040000);
        ctx.fill(0, height - 20, width, height, (edgeAlpha << 24) | 0x040000);

        // Left and right edge bars for full-frame oppression
        ctx.fill(0, 0, 8, height, (edgeAlpha << 24) | 0x040000);
        ctx.fill(width - 8, 0, width, height, (edgeAlpha << 24) | 0x040000);

        // Dread tint overlay
        int dreadTint = DreadHandler.getScreenTint();
        if (dreadTint != 0) {
            ctx.fill(0, 0, width, height, dreadTint);
        }

        // Random full-screen alpha flicker for Gloom Void (subtle screen tearing feel)
        if (isInGloomVoid && vignetteFlickerTick > 0) {
            vignetteFlickerTick--;
            if (vignetteFlickerTick % 2 == 0) {
                ctx.fill(0, 0, width, height, 0x08000000);
            }
        }
    }
}
