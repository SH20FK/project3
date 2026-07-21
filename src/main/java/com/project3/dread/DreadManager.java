package com.project3.dread;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for the Dread (Страх) economy.
 * Tracks dread per player, handles generation from horror events, decay, thresholds, and death.
 */
public class DreadManager {

    private static final Map<UUID, Integer> DREAD_MAP = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DREAD_DECAY_TIMERS = new ConcurrentHashMap<>();

    public static final int DREAD_MAX = 100;
    public static final int THRESHOLD_UNEASY = 20;
    public static final int THRESHOLD_TERRIFIED = 50;
    public static final int THRESHOLD_BREAKING = 80;

    // ─── Get / Set ──────────────────────────────────────────────────────────

    public static int getDread(ServerPlayerEntity player) {
        return DREAD_MAP.getOrDefault(player.getUuid(), 0);
    }

    public static void setDread(ServerPlayerEntity player, int amount) {
        int clamped = Math.max(0, Math.min(amount, DREAD_MAX + 20));
        DREAD_MAP.put(player.getUuid(), clamped);
    }

    // ─── Generate Dread from horror events ─────────────────────────────────

    public static void addDread(ServerPlayerEntity player, int amount) {
        int current = getDread(player);
        int newVal = Math.min(current + amount, DREAD_MAX + 20);
        DREAD_MAP.put(player.getUuid(), newVal);
    }

    /** Phantom appears nearby */
    public static void onPhantomSpawn(ServerPlayerEntity player) {
        addDread(player, 3);
    }

    /** Paranoia spike (name flicker etc) */
    public static void onParanoia(ServerPlayerEntity player) {
        addDread(player, 2);
    }

    /** Screen glitch */
    public static void onGlitch(ServerPlayerEntity player) {
        addDread(player, 1);
    }

    /** Producer Block anomaly */
    public static void onProducerAnomaly(ServerPlayerEntity player) {
        addDread(player, 3);
    }

    /** Time spent in Gloom Void (called every 60 ticks = 3 sec) */
    public static void onGloomTick(ServerPlayerEntity player) {
        addDread(player, 1);
    }

    /** Near Stalker NPC (called every 200 ticks = 10 sec) */
    public static void onStalkerNearby(ServerPlayerEntity player) {
        addDread(player, 2);
    }

    // ─── Dread Decay ───────────────────────────────────────────────────────

    /** Called every server tick for all players */
    public static void tickDecay(MinecraftServer server) {
        long now = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!DREAD_MAP.containsKey(player.getUuid())) continue;

            boolean inVoid = player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw
                    && sw.getRegistryKey() == com.project3.Project3Mod.GLOOM_VOID_WORLD_KEY;

            long lastDecay = DREAD_DECAY_TIMERS.getOrDefault(player.getUuid(), 0L);

            if (!inVoid && now - lastDecay >= 60) {
                // -3 dread per 3 seconds in overworld
                int current = getDread(player);
                setDread(player, Math.max(0, current - 3));
                DREAD_DECAY_TIMERS.put(player.getUuid(), now);
            } else if (inVoid && now - lastDecay >= 200) {
                // -1 dread per 10 seconds in Gloom Void (slow decay)
                int current = getDread(player);
                setDread(player, Math.max(0, current - 1));
                DREAD_DECAY_TIMERS.put(player.getUuid(), now);
            }
        }
    }

    // ─── Threshold effects ─────────────────────────────────────────────────

    /** Returns current dread threshold level for effects */
    public static int getThreshold(ServerPlayerEntity player) {
        int dread = getDread(player);
        if (dread >= DREAD_MAX) return 4;  // Overload
        if (dread >= THRESHOLD_BREAKING) return 3;
        if (dread >= THRESHOLD_TERRIFIED) return 2;
        if (dread >= THRESHOLD_UNEASY) return 1;
        return 0;
    }

    /** Check if player has exceeded max dread — if so, kill them */
    public static boolean checkOverload(ServerPlayerEntity player) {
        if (getDread(player) >= DREAD_MAX) {
            if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                player.kill(sw);
            }
            DREAD_MAP.put(player.getUuid(), 0);
            return true;
        }
        return false;
    }

    // ─── Spending Dread (for Shadow Merchant) ──────────────────────────────

    public static boolean spendDread(ServerPlayerEntity player, int amount) {
        int current = getDread(player);
        if (current < amount) return false;
        setDread(player, current - amount);
        return true;
    }

    // ─── Cleanup on disconnect ─────────────────────────────────────────────

    public static void onDisconnect(UUID uuid) {
        DREAD_MAP.remove(uuid);
        DREAD_DECAY_TIMERS.remove(uuid);
    }
}
