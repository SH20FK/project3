package com.project3.world;

import com.project3.player.PlayerCooldowns;
import com.project3.state.Project3State;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Handles world border interpolation and penalties for crossing the border boundary.
 */
public final class WorldBorderManager {

    private WorldBorderManager() {}

    private static final int WALL_THRESHOLD = 15_950;
    private static final long WALL_MESSAGE_COOLDOWN_MS = 5_000L;

    public static void setupBorderOnStart(MinecraftServer server) {
        Project3State state = Project3State.getOrCreate(server);
        ServerWorld overworld = server.getOverworld();
        if (overworld != null) {
            net.minecraft.world.border.WorldBorder border = overworld.getWorldBorder();
            BlockPos spawnPos = overworld.getSpawnPoint().getPos();
            double spawnX = spawnPos.getX() + 0.5;
            double spawnZ = spawnPos.getZ() + 0.5;

            if (!state.isSeasonStarted()) {
                // Season not started: reset border to default vanilla size
                border.setCenter(0.0, 0.0);
                border.setSize(5.9999968E7);
            } else {
                // Season started: center at spawn and enforce correct size/interpolation
                border.setCenter(spawnX, spawnZ);
                border.setWarningBlocks(3);
                border.setSafeZone(1.0);
                long elapsed = System.currentTimeMillis() - state.getSeasonStartTime();
                if (elapsed < 2000000L) {
                    long remaining = 2000000L - elapsed;
                    border.interpolateSize(border.getSize(), 42000.0, remaining, net.minecraft.util.Util.getMeasuringTimeMs());
                } else {
                    border.setSize(42000.0);
                }
            }
        }
    }

    public static void checkPlayers(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;
        
        BlockPos spawnPos = overworld.getSpawnPoint().getPos();
        int spawnX = spawnPos.getX();
        int spawnZ = spawnPos.getZ();
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() == overworld) {
                double x = player.getX();
                double z = player.getZ();
                if (Math.abs(x - spawnX) >= WALL_THRESHOLD || Math.abs(z - spawnZ) >= WALL_THRESHOLD) {
                    applyWallPenalty(player, overworld);
                }
            }
        }
    }

    private static void applyWallPenalty(ServerPlayerEntity player, ServerWorld overworld) {
        long now = System.currentTimeMillis();
        long lastPenalty = PlayerCooldowns.WALL_MESSAGE_COOLDOWNS.getOrDefault(player.getUuid(), 0L);
        if (now - lastPenalty < WALL_MESSAGE_COOLDOWN_MS) {
            return;
        }
        PlayerCooldowns.WALL_MESSAGE_COOLDOWNS.put(player.getUuid(), now);

        // Track violation count for graduated penalties
        int violations = PlayerCooldowns.WALL_VIOLATION_COUNT.getOrDefault(player.getUuid(), 0) + 1;
        PlayerCooldowns.WALL_VIOLATION_COUNT.put(player.getUuid(), violations);

        // First offense: warning only (no teleport, no debuffs)
        if (violations == 1) {
            player.networkHandler.sendPacket(new TitleS2CPacket(
                    Text.literal("§e⚠ ВНИМАНИЕ").formatted(Formatting.YELLOW)
            ));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("§7Вы приближаетесь к границе сектора")
            ));
            overworld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1.0f, 0.5f);
            return;
        }

        // Second+ offense: full penalty
        // Teleport to world spawn (safe Y ground height)
        // Validate spawn is within world border to prevent teleport loops
        BlockPos spawn = overworld.getSpawnPoint().getPos();
        net.minecraft.world.border.WorldBorder border = overworld.getWorldBorder();
        double spawnX = spawn.getX() + 0.5;
        double spawnZ = spawn.getZ() + 0.5;
        
        // If spawn is outside or near the border, use center (0, 0) instead
        if (!border.contains(spawnX, spawnZ) || border.getDistanceInsideBorder(spawnX, spawnZ) < 100) {
            spawnX = 0.5;
            spawnZ = 0.5;
            spawn = new BlockPos(0, spawn.getY(), 0);
        }
        
        int topY = overworld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        double spawnY = Math.max(overworld.getBottomY() + 2, topY);
        player.teleport(overworld, spawnX, spawnY, spawnZ,
                java.util.Set.of(), player.getYaw(), player.getPitch(), true);

        // Set hunger to 0
        player.getHungerManager().setFoodLevel(0);
        
        // Take 50% health damage
        player.damage(overworld, overworld.getDamageSources().magic(), player.getMaxHealth() * 0.5f);
        
        // Give Unnamed effect
        Project3State state = Project3State.getOrCreate(overworld.getServer());
        state.setUnnamedEffectActive(player.getUuid(), true);
        com.project3.player.PlayerStateManager.syncPlayerState(player, state);

        // Debuffs: Slowness IV (10s), Weakness II (10s), Darkness (10s)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,  200, 3, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,   200, 1, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,   200, 0, false, true));

        // Scary Elder Guardian sound effect on boundary violation
        overworld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 1.0f, 0.5f);

        // Title message
        player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("ВЫДВОРЕНИЕ ИЗ СЕКТОРА").formatted(Formatting.RED)
        ));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                Text.literal("§4Нарушение паспортного режима.")
        ));
    }
}
