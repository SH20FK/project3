package com.project3.player;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized storage for all per-player cooldown maps and transient tracking data.
 * Previously scattered as public static fields across Project3Mod.
 */
public final class PlayerCooldowns {

    private PlayerCooldowns() {}

    // ─── Gloom Void ambient effect cooldowns ────────────────────────────────

    public static final Map<UUID, Integer> FLASHLIGHT   = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> AMBIENT      = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> SECTOR       = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> PHANTOM      = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> MUSIC        = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> SMOKE        = new ConcurrentHashMap<>();

    // ─── World Oddities cooldowns (progress level events) ───────────────────

    public static final Map<UUID, Integer> DEAD_SCENARIO = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> CHAT_ECHO     = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> STATIC        = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> DEJA_VU       = new ConcurrentHashMap<>();

    // ─── Lighting / phantom tracking ────────────────────────────────────────

    /** Dynamic LIGHT block placed at player's eye position in Gloom Void */
    public static final Map<UUID, BlockPos> PLAYER_LIGHT_POSITIONS  = new ConcurrentHashMap<>();
    /** Dynamic LIGHT block placed near each player's phantom */
    public static final Map<UUID, BlockPos> PHANTOM_LIGHT_POSITIONS = new ConcurrentHashMap<>();
    /** LIGHT blocks placed by /p3 commands */
    public static final Map<UUID, List<BlockPos>> COMMAND_SPAWNED_LIGHTS = new ConcurrentHashMap<>();

    // ─── Virtual phantom (client-side NPC) tracking ─────────────────────────

    public static final Map<UUID, Vec3d>   PHANTOM_POSITIONS  = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> PHANTOM_ENTITY_IDS = new ConcurrentHashMap<>();
    public static final Map<UUID, Float>   PHANTOM_YAWS       = new ConcurrentHashMap<>();

    // ─── Portal state per-player ─────────────────────────────────────────────

    public static final Map<UUID, Boolean> PORTAL_IS_LIT     = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> PORTAL_STATE_TICKS = new ConcurrentHashMap<>();

    // ─── Gloom Void escalation ───────────────────────────────────────────────

    public static final Map<UUID, Integer> VOID_ESCALATION_TICKS = new ConcurrentHashMap<>();

    // ─── Wall violation tracking ─────────────────────────────────────────────

    public static final Map<UUID, Long>    WALL_MESSAGE_COOLDOWNS = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> WALL_VIOLATION_COUNT   = new ConcurrentHashMap<>();
    /** Players who have already received the pumpkin mask hint */
    public static final java.util.Set<UUID> PUMPKIN_HINT_SENT =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    // ─── Mace fall-distance tracking (for mace kill 50-blocks achievement) ──

    public static final Map<UUID, Float> LAST_MACE_ATTACK_FALL_DISTANCE = new ConcurrentHashMap<>();

    // ─── Food eating counter (for 30-food → happiness cell mechanic) ─────────

    public static final Map<UUID, Integer> FOOD_EATEN_BASELINE = new ConcurrentHashMap<>();

    // ─── Cleanup ────────────────────────────────────────────────────────────

    /** Remove all per-player data on disconnect. */
    public static void onDisconnect(UUID uuid) {
        FLASHLIGHT.remove(uuid);
        AMBIENT.remove(uuid);
        SECTOR.remove(uuid);
        PHANTOM.remove(uuid);
        MUSIC.remove(uuid);
        SMOKE.remove(uuid);
        DEAD_SCENARIO.remove(uuid);
        CHAT_ECHO.remove(uuid);
        STATIC.remove(uuid);
        DEJA_VU.remove(uuid);
        PLAYER_LIGHT_POSITIONS.remove(uuid);
        PHANTOM_LIGHT_POSITIONS.remove(uuid);
        COMMAND_SPAWNED_LIGHTS.remove(uuid);
        PHANTOM_POSITIONS.remove(uuid);
        PHANTOM_ENTITY_IDS.remove(uuid);
        PHANTOM_YAWS.remove(uuid);
        PORTAL_IS_LIT.remove(uuid);
        PORTAL_STATE_TICKS.remove(uuid);
        VOID_ESCALATION_TICKS.remove(uuid);
        WALL_MESSAGE_COOLDOWNS.remove(uuid);
        WALL_VIOLATION_COUNT.remove(uuid);
        PUMPKIN_HINT_SENT.remove(uuid);
        LAST_MACE_ATTACK_FALL_DISTANCE.remove(uuid);
        FOOD_EATEN_BASELINE.remove(uuid);
    }

    /** Initialize portal state for a new player entering Gloom Void. */
    public static void initPlayerVoidPortal(UUID uuid, net.minecraft.util.math.random.Random random) {
        PORTAL_IS_LIT.put(uuid, true);
        PORTAL_STATE_TICKS.put(uuid, 1200 + random.nextInt(800));
    }
}
