package com.project3.registry;

import com.project3.network.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;

import static com.project3.Project3Mod.OPEN_INVENTORY_STAT_ID;

/**
 * Handles registration of all network payloads (C2S and S2C) and C2S server-side receivers.
 */
public final class NetworkRegistrar {

    private NetworkRegistrar() {}

    public static void registerAll() {
        // ── Register Payloads (S2C) ──────────────────────────────────────────
        PayloadTypeRegistry.playS2C().register(CameraRotatePayload.ID,      CameraRotatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AchievementSyncPayload.ID,   AchievementSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerStateSyncPayload.ID,   PlayerStateSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChunkReloadPayload.ID,       ChunkReloadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShaderFlashPayload.ID,       ShaderFlashPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpawnPhantomPayload.ID,      SpawnPhantomPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemovePhantomPayload.ID,     RemovePhantomPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PhantomHeadSnapPayload.ID,   PhantomHeadSnapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpawnStatuePayload.ID,       SpawnStatuePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FogTargetPayload.ID,         FogTargetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ParanoiaPayload.ID,          ParanoiaPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DreadPayload.ID,             DreadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BorderEffectPayload.ID,      BorderEffectPayload.CODEC);

        // ── Register Payloads (C2S) ──────────────────────────────────────────
        PayloadTypeRegistry.playC2S().register(OpenInventoryPayload.ID,     OpenInventoryPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AdminToolUsePayload.ID,      AdminToolUsePayload.CODEC);

        // ── Server-side C2S Receivers ────────────────────────────────────────

        ServerPlayNetworking.registerGlobalReceiver(AdminToolUsePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                AdminToolUseReceiver.handle(payload, player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenInventoryPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                Stat<?> stat = Stats.CUSTOM.getOrCreateStat(OPEN_INVENTORY_STAT_ID);
                player.incrementStat(stat);
            });
        });
    }
}
