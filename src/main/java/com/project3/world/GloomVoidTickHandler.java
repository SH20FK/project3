package com.project3.world;

import com.project3.Project3Mod;
import com.project3.dread.DreadManager;
import com.project3.dread.ShadowMerchant;
import com.project3.entity.PhantomReplicator;
import com.project3.network.FogTargetPayload;
import com.project3.network.ShaderFlashPayload;
import com.project3.player.PlayerCooldowns;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Handles all logic for players ticking inside the Gloom Void dimension.
 */
public final class GloomVoidTickHandler {

    private GloomVoidTickHandler() {}

    public static void tick(ServerPlayerEntity player, ServerWorld playerWorld) {
        if (!player.isAlive()) return;

        // Escalation level based on time spent in Gloom Void this session
        int voidTicks = PlayerCooldowns.VOID_ESCALATION_TICKS.getOrDefault(player.getUuid(), 0);
        int escalationLevel = 0;
        if (voidTicks > 3600) escalationLevel = 4;  // 3+ minutes
        else if (voidTicks > 2400) escalationLevel = 3;  // 2 minutes
        else if (voidTicks > 1200) escalationLevel = 2;  // 1 minute
        else if (voidTicks > 600) escalationLevel = 1;   // 30 seconds

        // Failsafe: if player falls below Y=55, teleport them back to the portal safety!
        if (player.getY() < 55.0) {
            player.teleport(playerWorld, player.getX(), 64.0, player.getZ(), java.util.Set.of(), player.getYaw(), player.getPitch(), true);
            player.setVelocity(0, 0, 0);
        }

        // 1. Flashlight Failure / Interruption
        boolean holdingLight = GloomVoidTickHandler.isHoldingLightSource(player);
        if (holdingLight) {
            int flTicks = PlayerCooldowns.FLASHLIGHT.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(600) + 600);
            if (flTicks > 0) {
                PlayerCooldowns.FLASHLIGHT.put(player.getUuid(), flTicks - 1);
            } else {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 50, 0, false, false, true));
                player.playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f, 0.5f);
                Project3Mod.schedule(50, () -> {
                    if (player.isAlive()) {
                        player.playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f, 0.8f);
                    }
                });
                PlayerCooldowns.FLASHLIGHT.put(player.getUuid(), player.getRandom().nextInt(600) + 600);
            }
        } else {
            PlayerCooldowns.FLASHLIGHT.remove(player.getUuid());
        }

        // Dynamic held light source placement
        BlockPos oldLightPos = PlayerCooldowns.PLAYER_LIGHT_POSITIONS.get(player.getUuid());
        BlockPos targetLightPos = null;

        boolean shouldLight = holdingLight && !player.hasStatusEffect(StatusEffects.DARKNESS);
        if (shouldLight) {
            BlockPos eyePos = player.getBlockPos().up();
            BlockState eyeState = playerWorld.getBlockState(eyePos);
            if (eyeState.isAir() || eyeState.isOf(Blocks.LIGHT)) {
                targetLightPos = eyePos;
            } else {
                BlockPos feetPos = player.getBlockPos();
                BlockState feetState = playerWorld.getBlockState(feetPos);
                if (feetState.isAir() || feetState.isOf(Blocks.LIGHT)) {
                    targetLightPos = feetPos;
                }
            }
        }

        if (oldLightPos != null && !oldLightPos.equals(targetLightPos)) {
            if (playerWorld.getBlockState(oldLightPos).isOf(Blocks.LIGHT)) {
                playerWorld.setBlockState(oldLightPos, Blocks.AIR.getDefaultState());
            }
            PlayerCooldowns.PLAYER_LIGHT_POSITIONS.remove(player.getUuid());
        }

        if (targetLightPos != null && !targetLightPos.equals(oldLightPos)) {
            playerWorld.setBlockState(targetLightPos, Blocks.LIGHT.getDefaultState()
                .with(net.minecraft.block.LightBlock.LEVEL_15, 15));
            PlayerCooldowns.PLAYER_LIGHT_POSITIONS.put(player.getUuid(), targetLightPos);
        }

        // 2. Ambient Sounds / Glitches — faster at higher escalation
        int ambBaseDelay = Math.max(1200 - escalationLevel * 200, 400);
        final int ambDelayFinal = ambBaseDelay;
        int ambTicks = PlayerCooldowns.AMBIENT.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(ambDelayFinal) + ambDelayFinal);
        if (ambTicks > 0) {
            PlayerCooldowns.AMBIENT.put(player.getUuid(), ambTicks - 1);
        } else {
            int soundType = player.getRandom().nextInt(3);
            if (soundType == 0) {
                Vec3d look = player.getRotationVec(1.0f).normalize();
                double bx = player.getX() - look.x * 1.5;
                double bz = player.getZ() - look.z * 1.5;
                double by = player.getY();
                playerWorld.playSound(null, bx, by, bz, SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 1.0f, 0.9f);
                Project3Mod.schedule(5, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, bx, by, bz, SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 1.0f, 0.85f);
                    }
                });
                Project3Mod.schedule(10, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, bx, by, bz, SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 1.0f, 0.95f);
                    }
                });
            } else if (soundType == 1) {
                Vec3d randOffset = new Vec3d((player.getRandom().nextDouble() - 0.5) * 8.0, 
                                             (player.getRandom().nextDouble() - 0.5) * 2.0, 
                                             (player.getRandom().nextDouble() - 0.5) * 8.0);
                Vec3d soundPos = new Vec3d(player.getX(), player.getY(), player.getZ()).add(randOffset);
                Project3Mod.schedule(15, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, soundPos.x, soundPos.y, soundPos.z, 
                            SoundEvents.BLOCK_STONE_BREAK, SoundCategory.MASTER, 2.0f, 0.2f);
                    }
                });
            } else {
                playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(), 
                    SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.MASTER, 1.0f, 1.5f);
                Project3Mod.schedule(2, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(), 
                            SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1.2f, 1.3f);
                    }
                });
                Project3Mod.schedule(4, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(), 
                            SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.MASTER, 1.5f, 0.1f);
                    }
                });
            }
            PlayerCooldowns.AMBIENT.put(player.getUuid(), player.getRandom().nextInt(1200) + 1200);
        }

        // 2b. Distorted ambient music (played at very low pitch to sound eerie)
        int musicTicks = PlayerCooldowns.MUSIC.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(3600) + 2400);
        if (musicTicks > 0) {
            PlayerCooldowns.MUSIC.put(player.getUuid(), musicTicks - 1);
        } else {
            SoundEvent[] distortedSounds = {
                SoundEvents.MUSIC_DISC_13.value(),
                SoundEvents.MUSIC_DISC_CAT.value(),
                SoundEvents.AMBIENT_CAVE.value()
            };
            SoundEvent chosen = distortedSounds[player.getRandom().nextInt(distortedSounds.length)];
            Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
            PlaySoundS2CPacket musicPacket = new PlaySoundS2CPacket(
                net.minecraft.registry.Registries.SOUND_EVENT.getEntry(chosen),
                SoundCategory.MASTER,
                pos.x, pos.y, pos.z,
                0.18f, // volume
                0.40f, // pitch
                player.getRandom().nextLong()
            );
            player.networkHandler.sendPacket(musicPacket);
            PlayerCooldowns.MUSIC.put(player.getUuid(), player.getRandom().nextInt(4800) + 3600);
        }

        // 2c. Ambient smoke particles drifting upward around the player
        int smokeTicks = PlayerCooldowns.SMOKE.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(40) + 20);
        if (smokeTicks > 0) {
            PlayerCooldowns.SMOKE.put(player.getUuid(), smokeTicks - 1);
        } else {
            int smokeCount = player.getRandom().nextInt(3) + 2;
            for (int i = 0; i < smokeCount; i++) {
                double ox = (player.getRandom().nextDouble() - 0.5) * 10.0;
                double oy = (player.getRandom().nextDouble()) * 1.5 - 0.5;
                double oz = (player.getRandom().nextDouble() - 0.5) * 10.0;
                playerWorld.spawnParticles(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    player.getX() + ox,
                    player.getY() + oy,
                    player.getZ() + oz,
                    1, 0.0, 0.0, 0.0, 0.005
                );
            }
            PlayerCooldowns.SMOKE.put(player.getUuid(), player.getRandom().nextInt(30) + 15);
        }

        // 2d. ENHANCED HORROR AMBIENCE
        if (playerWorld.getTime() % 80 == 0 && player.getRandom().nextFloat() < 0.3f) {
            Vec3d look = player.getRotationVec(1.0f).normalize();
            double fx = player.getX() - look.x * 2.0;
            double fz = player.getZ() - look.z * 2.0;
            double fy = player.getY();
            playerWorld.playSound(null, fx, fy, fz, SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 0.6f, 0.7f);
            Project3Mod.schedule(8, () -> {
                if (player.isAlive()) {
                    playerWorld.playSound(null, fx + look.x * 0.5, fy, fz + look.z * 0.5,
                        SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 0.5f, 0.65f);
                }
            });
        }

        if (playerWorld.getTime() % 120 == 0 && player.getRandom().nextFloat() < 0.2f) {
            player.playSound(SoundEvents.ENTITY_PLAYER_BREATH, 0.8f, 0.4f);
        }

        if (playerWorld.getTime() % 300 == 0 && player.getRandom().nextFloat() < 0.15f) {
            Vec3d offset = new Vec3d(
                (player.getRandom().nextDouble() - 0.5) * 40.0, 0,
                (player.getRandom().nextDouble() - 0.5) * 40.0);
            Vec3d screamPos = player.getEntityPos().add(offset);
            PlaySoundS2CPacket screamPacket = new PlaySoundS2CPacket(
                net.minecraft.registry.Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_ENDERMAN_SCREAM),
                SoundCategory.MASTER,
                screamPos.x, screamPos.y, screamPos.z,
                0.3f, 0.4f,
                player.getRandom().nextLong()
            );
            player.networkHandler.sendPacket(screamPacket);
        }

        // Heartbeat
        int hbInterval = 30 - escalationLevel * 4;
        if (hbInterval < 10) hbInterval = 10;
        float hbChance = 0.08f + escalationLevel * 0.05f;
        if (hbChance > 0.3f) hbChance = 0.3f;
        float hbPitch = 0.6f + escalationLevel * 0.15f;
        if (hbPitch > 1.2f) hbPitch = 1.2f;
        if (playerWorld.getTime() % hbInterval == 0 && player.getRandom().nextFloat() < hbChance) {
            float hbVolume = 0.15f + escalationLevel * 0.08f;
            if (hbVolume > 0.5f) hbVolume = 0.5f;
            PlaySoundS2CPacket heartPacket = new PlaySoundS2CPacket(
                net.minecraft.registry.Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_WARDEN_HEARTBEAT),
                SoundCategory.MASTER,
                player.getX(), player.getY(), player.getZ(),
                hbVolume, hbPitch,
                player.getRandom().nextLong()
            );
            player.networkHandler.sendPacket(heartPacket);
        }

        // Door creak
        int creakInterval = 250 - escalationLevel * 30;
        if (creakInterval < 100) creakInterval = 100;
        if (playerWorld.getTime() % creakInterval == 0 && player.getRandom().nextFloat() < 0.12f + escalationLevel * 0.04f) {
            Vec3d creakOffset = new Vec3d(
                (player.getRandom().nextDouble() - 0.5) * 16.0, 0,
                (player.getRandom().nextDouble() - 0.5) * 16.0);
            Vec3d creakPos = player.getEntityPos().add(creakOffset);
            playerWorld.playSound(null, creakPos.x, creakPos.y, creakPos.z,
                SoundEvents.BLOCK_FENCE_GATE_OPEN, SoundCategory.MASTER, 0.4f, 0.3f);
        }

        // 2e. Shadow Merchant spawn
        float merchantChance = 0.05f + escalationLevel * 0.03f;
        if (merchantChance > 0.2f) merchantChance = 0.2f;
        if (playerWorld.getTime() % 1200 == 0 && player.getRandom().nextFloat() < merchantChance) {
            ShadowMerchant.trySpawn(player);
        }

        // 3. Perception Collapse Trap
        int sectorTicks = PlayerCooldowns.SECTOR.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(600) + 600);
        if (sectorTicks > 0) {
            PlayerCooldowns.SECTOR.put(player.getUuid(), sectorTicks - 1);
        } else {
            DreadManager.onGlitch(player);
            playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 2.0f, 0.8f);

            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 3, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1, false, false, true));

            ServerPlayNetworking.send(player, new FogTargetPayload(0.8F));

            Project3Mod.schedule(200, () -> {
                if (player.isAlive()) {
                    ServerPlayNetworking.send(player, new FogTargetPayload(8.0F));
                    playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.MASTER, 2.0f, 0.8f);
                }
            });

            PlayerCooldowns.SECTOR.put(player.getUuid(), player.getRandom().nextInt(600) + 600);
        }

        // 4. Automatic Screamer
        int phCooldownBase = Math.max(1200 - escalationLevel * 150, 300);
        final int phCooldownFinal = phCooldownBase;
        int phTicks = PlayerCooldowns.PHANTOM.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(phCooldownFinal) + phCooldownFinal);
        if (phTicks > 0) {
            PlayerCooldowns.PHANTOM.put(player.getUuid(), phTicks - 1);
        } else {
            PhantomReplicator.spawnScreamerSprint(player);
            if (escalationLevel >= 2 && player.getRandom().nextBoolean()) {
                PhantomReplicator.spawnScreamerSprint(player);
            }
            DreadManager.onPhantomSpawn(player);
            PlayerCooldowns.PHANTOM.put(player.getUuid(), player.getRandom().nextInt(phCooldownBase) + phCooldownBase);
        }

        // 4b. Visual glitches
        if (escalationLevel >= 2 && playerWorld.getTime() % (300 - escalationLevel * 50) == 0 && player.getRandom().nextFloat() < 0.1f * escalationLevel) {
            ServerPlayNetworking.send(player, new ShaderFlashPayload());
        }

        // 5. Portal flickering
        int portalStateTicks = PlayerCooldowns.PORTAL_STATE_TICKS.getOrDefault(player.getUuid(), 1200);
        portalStateTicks--;
        if (portalStateTicks <= 0) {
            boolean isCurrentlyLit = PlayerCooldowns.PORTAL_IS_LIT.getOrDefault(player.getUuid(), true);
            boolean newLit = !isCurrentlyLit;
            PlayerCooldowns.PORTAL_IS_LIT.put(player.getUuid(), newLit);

            int hash = Math.abs(player.getUuid().hashCode());
            double vx = (hash % 1000) * 1000.0;
            double vy = 64.0;
            double vz = ((hash / 1000) % 1000) * 1000.0;
            BlockPos pPos = new BlockPos((int)vx, (int)vy, (int)vz);

            if (newLit) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        playerWorld.setBlockState(pPos.add(dx, dy, 0), Blocks.NETHER_PORTAL.getDefaultState()
                            .with(net.minecraft.block.NetherPortalBlock.AXIS, net.minecraft.util.math.Direction.Axis.X));
                    }
                }
                playerWorld.playSound(null, pPos, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1.0f, 1.0f);
                portalStateTicks = player.getRandom().nextInt(1200) + 1200;
            } else {
                boolean playerNearPortal = player.getBlockPos().getManhattanDistance(pPos) < 5;
                if (!playerNearPortal) {
                    for (int dx = 0; dx <= 1; dx++) {
                        for (int dy = 1; dy <= 3; dy++) {
                            playerWorld.setBlockState(pPos.add(dx, dy, 0), Blocks.AIR.getDefaultState());
                        }
                    }
                    playerWorld.playSound(null, pPos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.2f, 0.5f);
                    playerWorld.playSound(null, pPos, SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, 0.8f, 0.5f);
                    portalStateTicks = player.getRandom().nextInt(200) + 200;
                } else {
                    PlayerCooldowns.PORTAL_IS_LIT.put(player.getUuid(), true);
                    portalStateTicks = player.getRandom().nextInt(600) + 600;
                }
            }
        }
        PlayerCooldowns.PORTAL_STATE_TICKS.put(player.getUuid(), portalStateTicks);
    }

    public static boolean isHoldingLightSource(ServerPlayerEntity player) {
        for (net.minecraft.util.Hand hand : net.minecraft.util.Hand.values()) {
            net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            if (!stack.isEmpty() && stack.isIn(com.project3.registry.ModTags.LIGHT_SOURCES)) {
                return true;
            }
        }
        return false;
    }
}
