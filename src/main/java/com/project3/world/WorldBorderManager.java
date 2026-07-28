package com.project3.world;

import com.project3.network.BorderEffectPayload;
import com.project3.player.PlayerCooldowns;
import com.project3.state.Project3State;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic irregular world border powered by layered noise.
 * Crossing the border triggers an escalating series of effects:
 *
 * Stage 0 (0-40 ticks, 0-2s)   — Smoke/bubble particles, creepy ambience
 * Stage 1 (40-120 ticks, 2-6s) — Fog closes in, Darkness effect
 * Stage 2 (120-200 ticks, 6-10s) — Blindness, Weakness, screen desaturation
 * Stage 3 (200+ ticks, 10-14s) — Teleport to spawn with 1 HP, food drain, Unnamed
 *
 * The timer NEVER resets — once you cross the line, the countdown has begun.
 */
public final class WorldBorderManager {

    private WorldBorderManager() {}

    public static final double BASE_RADIUS = 15_800.0;
    public static final double NOISE_AMPLITUDE = 600.0;
    /** Used externally by CalibrationManager — total playable span. */
    public static final double BORDER_DIAMETER = (BASE_RADIUS + NOISE_AMPLITUDE) * 2.0;
    public static final long PENALTY_COOLDOWN_MS = 2_000L;

    // Stages (in server ticks) — shortened for faster escalation
    private static final int STAGE_1_PARTICLES   = 40;
    private static final int STAGE_2_FOG          = 120;
    private static final int STAGE_3_CORRUPTION   = 200;
    private static final int STAGE_4_TELEPORT     = 280;

    /** Per-player accumulated violation ticks (never resets to 0 except on teleport). */
    private static final Map<UUID, Integer> BORDER_VIOLATION_TICKS = new ConcurrentHashMap<>();

    // ─── Noise ───────────────────────────────────────────────────────────────

    private static final double[] NOISE_TABLE = new double[1024];
    private static final long SEED = 0xB0B0B0B0B0B0L;

    static {
        java.util.Random rng = new java.util.Random(SEED);
        for (int i = 0; i < NOISE_TABLE.length; i++) {
            NOISE_TABLE[i] = (rng.nextDouble() - 0.5) * 2.0;
        }
    }

    private static double smoothNoise(double x, double y) {
        double angle = Math.atan2(y, x);
        if (angle < 0) angle += Math.PI * 2;
        double pos = angle / (Math.PI * 2) * NOISE_TABLE.length;
        int idx = (int) pos;
        double frac = pos - idx;
        int idx0 = Math.floorMod(idx, NOISE_TABLE.length);
        int idx1 = Math.floorMod(idx + 1, NOISE_TABLE.length);
        double v0 = NOISE_TABLE[idx0];
        double v1 = NOISE_TABLE[idx1];
        double t = frac * frac * (3 - 2 * frac);
        return v0 + (v1 - v0) * t;
    }

    public static double getMaxRadius(double spawnX, double spawnZ, double px, double pz) {
        double dx = px - spawnX;
        double dz = pz - spawnZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.01) return BASE_RADIUS + NOISE_AMPLITUDE;
        double nx = dx / dist;
        double nz = dz / dist;

        double n1 = smoothNoise(nx, nz);
        double n2 = smoothNoise(nx * 2.5 + 100.0, nz * 2.5 + 100.0) * 0.5;
        double n3 = smoothNoise(nx * 6.0 + 200.0, nz * 6.0 + 200.0) * 0.25;

        double noise = (n1 + n2 + n3) / (1.0 + 0.5 + 0.25);
        return BASE_RADIUS + noise * NOISE_AMPLITUDE;
    }

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
                // Huge vanilla border — all shape logic is custom
                border.setCenter(spawnX, spawnZ);
                border.setWarningBlocks(0);
                border.setSafeZone(0);
                border.setSize((BASE_RADIUS + NOISE_AMPLITUDE + 2000.0) * 2.0);
            }
        }
    }

    // ─── Tick — called every server tick ─────────────────────────────────────

    public static void checkPlayers(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;

        BlockPos spawnPos = overworld.getSpawnPoint().getPos();
        double spawnX = spawnPos.getX() + 0.5;
        double spawnZ = spawnPos.getZ() + 0.5;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() == overworld) {
                double d = distanceToBorder(spawnX, spawnZ, player.getX(), player.getZ());
                UUID uuid = player.getUuid();

                if (d < 0) {
                    // ── Outside border: accumulate ticks ──────────────────
                    int ticks = BORDER_VIOLATION_TICKS.getOrDefault(uuid, 0) + 1;
                    BORDER_VIOLATION_TICKS.put(uuid, ticks);
                    applyStage(player, overworld, spawnX, spawnZ, ticks);
                } else if (BORDER_VIOLATION_TICKS.containsKey(uuid)) {
                    // Player is inside, but has active violations — keep sending effect state
                    // so the client doesn't clean up. The timer does NOT reset.
                    int ticks = BORDER_VIOLATION_TICKS.getOrDefault(uuid, 0);
                    if (ticks > 0) {
                        syncClient(player, ticks);
                    }
                }
            }
        }
    }

    // ─── Stage Application ───────────────────────────────────────────────────

    private static void applyStage(ServerPlayerEntity player, ServerWorld world,
                                   double spawnX, double spawnZ, int ticks) {
        // Always sync tick count to client
        syncClient(player, ticks);

        // ── Stage 0: Particles + ambient ──────────────────────────────────
        if (ticks < STAGE_1_PARTICLES) {
            spawnStageParticles(player, world, ticks, 0);
        }
        // ── Stage 1: Fog creeping in ──────────────────────────────────────
        else if (ticks < STAGE_2_FOG) {
            spawnStageParticles(player, world, ticks, 1);
            int severity = (ticks - STAGE_1_PARTICLES) * 2 / (STAGE_2_FOG - STAGE_1_PARTICLES);
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.DARKNESS, 100, Math.min(severity, 2), false, false, false));
        }
        // ── Stage 2: Corruption ───────────────────────────────────────────
        else if (ticks < STAGE_3_CORRUPTION) {
            spawnStageParticles(player, world, ticks, 2);
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.DARKNESS, 100, 3, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 100, 1, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.WEAKNESS, 100, 1, false, false, false));
        }
        // ── Stage 3: Imminent collapse → teleport ─────────────────────────
        else {
            int intensity = Math.min((ticks - STAGE_3_CORRUPTION) / 10 + 1, 3);
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 100, intensity, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.WEAKNESS, 100, 2, false, false, false));

            if (ticks >= STAGE_4_TELEPORT) {
                teleportToSpawn(player, world, spawnX, spawnZ);
            }
        }
    }

    // ─── Particles ───────────────────────────────────────────────────────────

    private static void spawnStageParticles(ServerPlayerEntity player, ServerWorld world,
                                            int ticks, int stage) {
        var rand = world.random;
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        boolean inWater = world.getBlockState(player.getBlockPos()).getFluidState()
                .isIn(net.minecraft.registry.tag.FluidTags.WATER);
        boolean inLava = world.getBlockState(player.getBlockPos()).getFluidState()
                .isIn(net.minecraft.registry.tag.FluidTags.LAVA);

        // Spawn rate increases with stage
        int rate = Math.max(1, 4 - stage);
        if (rand.nextInt(rate) != 0) return;

        // In water — boiling bubbles
        if (inWater) {
            world.spawnParticles(
                    ParticleTypes.BUBBLE_COLUMN_UP,
                    px + (rand.nextDouble() - 0.5) * 2.0,
                    py + 0.2,
                    pz + (rand.nextDouble() - 0.5) * 2.0,
                    3, 0.5, 0.3, 0.5, 0.02
            );
            world.spawnParticles(
                    ParticleTypes.SPLASH,
                    px + (rand.nextDouble() - 0.5) * 2.0,
                    py + 1.0,
                    pz + (rand.nextDouble() - 0.5) * 2.0,
                    2, 0.3, 0.2, 0.3, 0.01
            );
        } else if (inLava) {
            // Lava — thick smoke and embers
            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    px + (rand.nextDouble() - 0.5) * 2.0,
                    py + 0.5,
                    pz + (rand.nextDouble() - 0.5) * 2.0,
                    2, 0.5, 0.3, 0.5, 0.01
            );
            world.spawnParticles(
                    ParticleTypes.LAVA,
                    px + (rand.nextDouble() - 0.5) * 1.5,
                    py + 0.5,
                    pz + (rand.nextDouble() - 0.5) * 1.5,
                    1, 0.2, 0.2, 0.2, 0.01
            );
        } else {
            // On land — eerie smoke
            world.spawnParticles(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    px + (rand.nextDouble() - 0.5) * 3.0,
                    py + 0.2,
                    pz + (rand.nextDouble() - 0.5) * 3.0,
                    1, 0.3, 0.1, 0.3, 0.015
            );
            if (stage >= 1) {
                world.spawnParticles(
                        ParticleTypes.MYCELIUM,
                        px + (rand.nextDouble() - 0.5) * 4.0,
                        py + 0.1,
                        pz + (rand.nextDouble() - 0.5) * 4.0,
                        1, 0.4, 0.1, 0.4, 0.005
                );
            }
            if (stage >= 2) {
                world.spawnParticles(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        px + (rand.nextDouble() - 0.5) * 2.5,
                        py + 0.5 + rand.nextDouble() * 0.5,
                        pz + (rand.nextDouble() - 0.5) * 2.5,
                        1, 0.2, 0.1, 0.2, 0.02
                );
            }
        }

        // Stage 2+: ambient distortion particles
        if (stage >= 2 && rand.nextInt(2) == 0) {
            world.spawnParticles(
                    ParticleTypes.ASH,
                    px + (rand.nextDouble() - 0.5) * 5.0,
                    py + 1.0 + rand.nextDouble() * 2.0,
                    pz + (rand.nextDouble() - 0.5) * 5.0,
                    1, 0.2, 0.1, 0.2, 0.01
            );
        }

        // Subtle sound cues (sparingly)
        if (rand.nextInt(100) == 0) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMBIENT_CRIMSON_FOREST_LOOP.value(),
                    SoundCategory.MASTER, 0.3f, 0.8f + rand.nextFloat() * 0.4f);
        }
        if (rand.nextInt(200) == 0) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP.value(),
                    SoundCategory.MASTER, 0.2f, 0.5f + rand.nextFloat() * 0.5f);
        }
    }

    // ─── Client Sync ─────────────────────────────────────────────────────────

    private static void syncClient(ServerPlayerEntity player, int ticks) {
        // Send every 10 ticks to avoid packet spam
        if (ticks % 10 == 0 || ticks == 1) {
            ServerPlayNetworking.send(player, new BorderEffectPayload(ticks));
        }
    }

    // ─── Teleport ────────────────────────────────────────────────────────────

    private static void teleportToSpawn(ServerPlayerEntity player, ServerWorld overworld,
                                        double spawnX, double spawnZ) {
        UUID uuid = player.getUuid();

        // Use the spawn point's Y directly — spawn chunks are always loaded,
        // so this won't trigger chunk generation, and the Y is guaranteed valid.
        BlockPos spawnPos = overworld.getSpawnPoint().getPos();
        double ty = spawnPos.getY() + 1.0;

        player.teleport(overworld, spawnPos.getX() + 0.5, ty, spawnPos.getZ() + 0.5,
                java.util.Set.of(), player.getYaw(), player.getPitch(), true);

        // Reset violation ticks
        BORDER_VIOLATION_TICKS.remove(uuid);
        syncClient(player, 0);

        // Consequences
        player.getHungerManager().setFoodLevel(2);
        player.getHungerManager().setSaturationLevel(0.0f);
        player.setHealth(1.0f);
        player.experienceLevel = 0;
        player.experienceProgress = 0.0f;

        Project3State state = Project3State.getOrCreate(overworld.getServer());
        state.setUnnamedEffectActive(uuid, true);
        com.project3.player.PlayerStateManager.syncPlayerState(player, state);

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.DARKNESS, 300, 2, false, false, false));
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 200, 3, false, false, false));

        // Flash sound + shake
        overworld.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 1.0f, 0.4f);
        overworld.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 0.8f, 0.3f);
    }

    // ─── Public helper ───────────────────────────────────────────────────────

    /** Called when a player disconnects — clean up tracking. */
    public static void onDisconnect(UUID uuid) {
        BORDER_VIOLATION_TICKS.remove(uuid);
    }
}
