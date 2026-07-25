package com.project3.client;

import com.project3.client.hud.ActiveAchievementHud;
import com.project3.client.hud.DreadHandler;
import com.project3.client.hud.ParanoiaHandler;
import com.project3.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Handles registration and processing of all client-side network payloads.
 */
public final class ClientNetworkHandlers {

    private ClientNetworkHandlers() {}

    private static long lastChunkReloadTime = 0L;

    public static void registerAll() {
        ClientPlayNetworking.registerGlobalReceiver(CameraRotatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                var player = context.client().player;
                if (player != null) {
                    player.setYaw(player.getYaw() + payload.deltaYaw());
                    player.setPitch(Math.max(-90f, Math.min(90f, player.getPitch() + payload.deltaPitch())));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AchievementSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ActiveAchievementHud.update(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(PlayerStateSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ActiveAchievementHud.updatePlayerState(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(ParanoiaPayload.ID, (payload, context) -> {
            context.client().execute(() -> ParanoiaHandler.setLevel(payload.level()));
        });

        ClientPlayNetworking.registerGlobalReceiver(DreadPayload.ID, (payload, context) -> {
            context.client().execute(() -> DreadHandler.setLevel(payload.dread(), payload.threshold()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ChunkReloadPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                long now = System.currentTimeMillis();
                if (now - lastChunkReloadTime >= 5000L) {
                    lastChunkReloadTime = now;
                    if (context.client().worldRenderer != null) {
                        context.client().worldRenderer.reload();
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ShaderFlashPayload.ID, (payload, context) -> {
            context.client().execute(() -> GloomVoidClientHandler.triggerShaderGlitch(context.client()));
        });

        ClientPlayNetworking.registerGlobalReceiver(BorderEffectPayload.ID, (payload, context) -> {
            context.client().execute(() -> BorderVoidHandler.setViolationTicks(payload.tickCount()));
        });

        ClientPlayNetworking.registerGlobalReceiver(SpawnPhantomPayload.ID, (payload, context) -> {
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
                    var fakePlayer = new net.minecraft.client.network.OtherClientPlayerEntity(world, filledProfile);
                    fakePlayer.setId(payload.entityId());
                    fakePlayer.setPosition(payload.x(), payload.y(), payload.z());
                    fakePlayer.setYaw(payload.yaw());
                    fakePlayer.setPitch(payload.pitch());
                    fakePlayer.setHeadYaw(payload.yaw());
                    fakePlayer.setBodyYaw(payload.yaw());

                    for (var slot : net.minecraft.entity.EquipmentSlot.values()) {
                        fakePlayer.equipStack(slot, player.getEquippedStack(slot).copy());
                    }
                    world.addEntity(fakePlayer);
                }
            }, context.client()::execute);
        });

        ClientPlayNetworking.registerGlobalReceiver(RemovePhantomPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                var world = context.client().world;
                if (world != null) {
                    world.removeEntity(payload.entityId(), net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PhantomHeadSnapPayload.ID, (payload, context) -> {
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
        });

        ClientPlayNetworking.registerGlobalReceiver(SpawnStatuePayload.ID, (payload, context) -> {
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
                    var fakePlayer = new net.minecraft.client.network.OtherClientPlayerEntity(world, filledProfile);
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
                    GloomVoidClientHandler.GLITCHED_STATUE_IDS.add(payload.entityId());
                }
            }, context.client()::execute);
        });
    }
}
