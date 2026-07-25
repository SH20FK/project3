package com.project3.world;

import com.project3.player.PlayerCooldowns;
import com.project3.state.Project3State;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

/**
 * Dynamic irregular world border powered by layered noise.
 * Instead of a fixed square radius, the maximum allowed distance
 * from spawn varies smoothly with angle, creating an organic,
 * polygonal shape that feels alive.
 */
public final class WorldBorderManager {

    private WorldBorderManager() {}

    /** Base radius around which noise is applied. */
    public static final double BASE_RADIUS = 15_800.0;
    /** Max deviation from BASE_RADIUS at any angle. */
    public static final double NOISE_AMPLITUDE = 600.0;
    /** How far past the limit is still a warning (no penalty) — gives a buffer zone. */
    private static final double WARNING_BUFFER = 100.0;
    /** WorldBorder#setSize — set far beyond the playable area so vanilla doesn't interfere. */
    public static final double VANILLA_BORDER_SIZE = BASE_RADIUS + NOISE_AMPLITUDE + WARNING_BUFFER + 1000.0;
    private static final long PENALTY_COOLDOWN_MS = 5_000L;

    // ─── Noise ───────────────────────────────────────────────────────────────

    private static final double[] NOISE_TABLE = new double[1024];
    private static final long SEED = 0xB0RD3R_SEED;

    static {
        java.util.Random rng = new java.util.Random(SEED);
        for (int i = 0; i < NOISE_TABLE.length; i++) {
            NOISE_TABLE[i] = (rng.nextDouble() - 0.5) * 2.0; // -1..1
        }
    }

    private static double smoothNoise(double x, double y) {
        // Hash angle into the table with smooth interpolation
        double angle = Math.atan2(y, x);
        if (angle < 0) angle += Math.PI * 2;
        double pos = angle / (Math.PI * 2) * NOISE_TABLE.length;
        int idx = (int) pos;
        double frac = pos - idx;
        int idx0 = Math.floorMod(idx, NOISE_TABLE.length);
        int idx1 = Math.floorMod(idx + 1, NOISE_TABLE.length);
        double v0 = NOISE_TABLE[idx0];
        double v1 = NOISE_TABLE[idx1];
        double t = frac * frac * (3 - 2 * frac); // smoothstep
        return v0 + (v1 - v0) * t;
    }

    /** Returns the dynamic max allowed distance from (spawnX, spawnZ) in direction (dx, dz). */
    public static double getMaxRadius(double spawnX, double spawnZ, double px, double pz) {
        double dx = px - spawnX;
        double dz = pz - spawnZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.01) return BASE_RADIUS + NOISE_AMPLITUDE;
        double nx = dx / dist;
        double nz = dz / dist;

        // Multi-octave noise for organic complexity
        double n1 = smoothNoise(nx, nz);
        double n2 = smoothNoise(nx * 2.5 + 100.0, nz * 2.5 + 100.0) * 0.5;
        double n3 = smoothNoise(nx * 6.0 + 200.0, nz * 6.0 + 200.0) * 0.25;

        double noise = (n1 + n2 + n3) / (1.0 + 0.5 + 0.25); // weighted normalise
        return BASE_RADIUS + noise * NOISE_AMPLITUDE;
    }

    /** Returns remaining distance from player to the dynamic border (>0 means inside). */
    public static double distanceToBorder(double spawnX, double spawnZ, double px, double pz) {
        double maxR = getMaxRadius(spawnX, spawnZ, px, pz);
        double dx = px - spawnX;
        double dz = pz - spawnZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return maxR - dist;
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    public static void setupBorderOnStart(MinecraftServer server) {
        Project3State state = Project3State.getOrCreate(server);
        ServerWorld overworld = server.getOverworld();
        if (overworld != null) {
            net.minecraft.world.border.WorldBorder border = overworld.getWorldBorder();
            BlockPos spawnPos = overworld.getSpawnPoint().getPos();
            double spawnX = spawnPos.getX() + 0.5;
            double spawnZ = spawnPos.getZ() + 0.5;

            if (!state.isSeasonStarted()) {
                border.setCenter(0.0, 0.0);
                border.setSize(5.9999968E7);
            } else {
                // Huge vanilla border — all shape logic is custom via checkPlayers()
                border.setCenter(spawnX, spawnZ);
                border.setWarningBlocks(0);
                border.setSafeZone(0);
                border.setSize(VANILLA_BORDER_SIZE * 2.0);
            }
        }
    }

    // ─── Player Checks ───────────────────────────────────────────────────────

    public static void checkPlayers(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;

        BlockPos spawnPos = overworld.getSpawnPoint().getPos();
        double spawnX = spawnPos.getX() + 0.5;
        double spawnZ = spawnPos.getZ() + 0.5;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() == overworld) {
                double d = distanceToBorder(spawnX, spawnZ, player.getX(), player.getZ());
                if (d < 0) {
                    applyPenalty(player, overworld, spawnX, spawnZ);
                }
            }
        }
    }

    // ─── Penalty ─────────────────────────────────────────────────────────────

    private static void applyPenalty(ServerPlayerEntity player, ServerWorld overworld,
                                     double spawnX, double spawnZ) {
        long now = System.currentTimeMillis();
        long lastPenalty = PlayerCooldowns.WALL_MESSAGE_COOLDOWNS.getOrDefault(player.getUuid(), 0L);
        if (now - lastPenalty < PENALTY_COOLDOWN_MS) return;
        PlayerCooldowns.WALL_MESSAGE_COOLDOWNS.put(player.getUuid(), now);

        int violations = PlayerCooldowns.WALL_VIOLATION_COUNT.getOrDefault(player.getUuid(), 0) + 1;
        PlayerCooldowns.WALL_VIOLATION_COUNT.put(player.getUuid(), violations);

        // First offence: sound warning only
        if (violations == 1) {
            overworld.playSound(null, player.getBlockPos(),
                    SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1.0f, 0.5f);
            return;
        }

        // Teleport toward centre
        double dx = player.getX() - spawnX;
        double dz = player.getZ() - spawnZ;
        double angle = Math.atan2(dz, dx);

        // Place player somewhere between spawn and the local border minus a margin
        double localMax = getMaxRadius(spawnX, spawnZ, player.getX(), player.getZ());
        double pullBack = Math.min(localMax * 0.5, 200.0);
        double safeDist = Math.max(localMax - pullBack, 100.0);

        double tx = spawnX + Math.cos(angle) * safeDist;
        double tz = spawnZ + Math.sin(angle) * safeDist;

        BlockPos surfacePos = overworld.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                new BlockPos((int) tx, 0, (int) tz));
        double ty = surfacePos.getY() + 1.0;

        player.teleport(overworld, tx, ty, tz,
                java.util.Set.of(), player.getYaw(), player.getPitch(), true);

        // Penalty effects
        player.getHungerManager().setFoodLevel(0);
        player.damage(overworld, overworld.getDamageSources().magic(), player.getMaxHealth() * 0.5f);

        Project3State state = Project3State.getOrCreate(overworld.getServer());
        state.setUnnamedEffectActive(player.getUuid(), true);
        com.project3.player.PlayerStateManager.syncPlayerState(player, state);

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,  200, 3, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,   200, 1, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,   200, 0, false, true));

        overworld.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 1.0f, 0.5f);
    }
}
