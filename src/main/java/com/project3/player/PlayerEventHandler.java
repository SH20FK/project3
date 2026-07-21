package com.project3.player;

import com.project3.Project3Mod;
import com.project3.dread.DreadManager;
import com.project3.network.CameraRotatePayload;
import com.project3.network.RemovePhantomPayload;
import com.project3.registry.ModRegistries;
import com.project3.state.Project3State;
import com.project3.world.CalibrationManager;
import com.project3.world.GloomVoidTickHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Handles connection, disconnection, and ticking for individual players.
 */
public final class PlayerEventHandler {

    private PlayerEventHandler() {}

    public static void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        Project3State state = Project3State.getOrCreate(server);
        Project3Mod.ACHIEVEMENT_MANAGER.syncAdvancements(player, state);
        Project3Mod.ACHIEVEMENT_MANAGER.syncActiveAchievement(player, state);
        PlayerStateManager.syncPlayerState(player, state);

        int completed = state.getCompletedAchievements(player.getUuid()).size();
        int total = 75;
        long happiness = state.getHappinessTicksLeft(player.getUuid());
        boolean isGloom = state.isGloomPermanent(player.getUuid()) || state.getGloomTicksLeft(player.getUuid()) > 0;

        player.sendMessage(Text.literal("§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
        player.sendMessage(Text.literal("§6§lProject3 §7| §fДобро пожаловать, " + player.getName().getString()), false);
        player.sendMessage(Text.literal("§7Прогресс: §a" + completed + "§7/§f" + total + " ачивок"), false);
        if (happiness > 0) {
            int minutes = (int) (happiness / 1200);
            player.sendMessage(Text.literal("§7Состояние: §a§lСчастье §7(§f" + minutes + " мин§7)"), false);
        } else if (isGloom) {
            player.sendMessage(Text.literal("§7Состояние: §4§lУныние"), false);
        } else {
            player.sendMessage(Text.literal("§7Состояние: §7Нейтрально"), false);
        }
        player.sendMessage(Text.literal("§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
        
        // Trigger Nether Corruption reconnect logic if active
        com.project3.event.NetherCorruptionEvent.onPlayerJoin(player);
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player, MinecraftServer server) {
        try {
            BlockPos lightPos = PlayerCooldowns.PLAYER_LIGHT_POSITIONS.remove(player.getUuid());
            if (lightPos != null) {
                if (player.getEntityWorld() instanceof ServerWorld world && world.getBlockState(lightPos).isOf(Blocks.LIGHT)) {
                    world.setBlockState(lightPos, Blocks.AIR.getDefaultState());
                }
            }
            BlockPos phantomLight = PlayerCooldowns.PHANTOM_LIGHT_POSITIONS.remove(player.getUuid());
            if (phantomLight != null) {
                if (player.getEntityWorld() instanceof ServerWorld world && world.getBlockState(phantomLight).isOf(Blocks.LIGHT)) {
                    world.setBlockState(phantomLight, Blocks.AIR.getDefaultState());
                }
            }

            Integer eid = PlayerCooldowns.PHANTOM_ENTITY_IDS.remove(player.getUuid());
            if (eid != null) {
                ServerPlayNetworking.send(player, new RemovePhantomPayload(eid));
            }

            List<BlockPos> cmdLights = PlayerCooldowns.COMMAND_SPAWNED_LIGHTS.remove(player.getUuid());
            if (cmdLights != null) {
                if (player.getEntityWorld() instanceof ServerWorld world) {
                    for (BlockPos pos : cmdLights) {
                        if (world.getBlockState(pos).isOf(Blocks.LIGHT)) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Project3Mod.LOGGER.error("Error cleaning up player data on disconnect: {}", player.getName().getString(), e);
        }
        DreadManager.onDisconnect(player.getUuid());
        PlayerCooldowns.onDisconnect(player.getUuid());
    }

    public static void tickPlayer(ServerPlayerEntity player, MinecraftServer server, Project3State state) {
        if (!Project3Mod.isWearingPumpkin(player)) {
            PlayerCooldowns.PUMPKIN_HINT_SENT.remove(player.getUuid());
        }

        long happiness = state.getHappinessTicksLeft(player.getUuid());
        if (happiness > 0) {
            state.setHappinessTicksLeft(player.getUuid(), happiness - 1);
            player.addStatusEffect(new StatusEffectInstance(ModRegistries.HAPPINESS_EFFECT, 40, 0, true, false, true));
            state.setGloomPermanent(player.getUuid(), false);
            state.setGloomTicksLeft(player.getUuid(), 0L);
            if (state.getGloomDepthTicks(player.getUuid()) > 0) {
                state.setGloomDepthTicks(player.getUuid(), 0L);
            }

            if (server.getTicks() % 2 == 0) {
                double radius = 6.0;
                Box box = player.getBoundingBox().expand(radius);
                var orbs = ((ServerWorld) player.getEntityWorld()).getEntitiesByClass(
                        net.minecraft.entity.ExperienceOrbEntity.class, box, orb -> true
                );
                for (var orb : orbs) {
                    double dx = player.getX() - orb.getX();
                    double dy = (player.getY() + 0.5) - orb.getY();
                    double dz = player.getZ() - orb.getZ();
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > 0.1) {
                        double speed = 0.2;
                        orb.setVelocity(orb.getVelocity().add(dx / dist * speed, dy / dist * speed, dz / dist * speed));
                    }
                }
            }

            if (happiness == 1) {
                state.setGloomPermanent(player.getUuid(), true);
                player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.6f);
                player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);
                player.networkHandler.sendPacket(new TitleS2CPacket(
                        Text.literal("СЧАСТЬЕ ПОКИНУЛО ВАС").formatted(Formatting.DARK_RED)
                ));

                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, false));

                for (int i = 0; i < 10; i++) {
                    final float shakeYaw = (i % 2 == 0 ? 1.0f : -1.0f) * (10 - i) * 0.3f;
                    final float shakePitch = (i % 2 == 0 ? -0.5f : 0.5f) * (10 - i) * 0.2f;
                    Project3Mod.schedule(i, () -> {
                        if (((ServerWorld) player.getEntityWorld()).getServer().getPlayerManager().getPlayer(player.getUuid()) != null) {
                            ServerPlayNetworking.send(player, new CameraRotatePayload(shakeYaw, shakePitch));
                        }
                    });
                }
            } else if (happiness == 1200) {
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.5f, 0.7f);
            } else if (happiness == 200) {
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.8f, 0.5f);
            }
        } else {
            state.setGloomPermanent(player.getUuid(), true);
            state.setGloomTicksLeft(player.getUuid(), 0L);
            state.addGloomDepthTicks(player.getUuid(), 1L);
            player.addStatusEffect(new StatusEffectInstance(ModRegistries.GLOOM_EFFECT, 40, 0, true, false, true));
        }

        if (state.isUnnamedEffectActive(player.getUuid())) {
            if (player.getRandom().nextInt(600) == 0) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 100, 0, true, false, true));
            }
            if (player.getRandom().nextInt(1200) == 0) {
                double dx = player.getX() + (player.getRandom().nextDouble() - 0.5) * 4.0;
                double dy = player.getY() + (player.getRandom().nextInt(3) - 1);
                double dz = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 4.0;
                ServerWorld world = (ServerWorld) player.getEntityWorld();
                player.teleport(world, dx, dy, dz, java.util.Set.of(), player.getYaw(), player.getPitch(), true);
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
            if (server.getTicks() % 20 == 0) {
                ServerWorld world = (ServerWorld) player.getEntityWorld();
                for (ServerPlayerEntity other : world.getPlayers()) {
                    if (other != player && other.squaredDistanceTo(player) <= 64.0) {
                        other.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, true, true));
                    }
                }
            }
        }

        if (player.getEntityWorld() instanceof ServerWorld serverWorld) {
            if (serverWorld.getRegistryKey() == Project3Mod.GLOOM_VOID_WORLD_KEY) {
                PlayerCooldowns.VOID_ESCALATION_TICKS.merge(player.getUuid(), 1, Integer::sum);
                GloomVoidTickHandler.tick(player, serverWorld);
            } else {
                PlayerCooldowns.VOID_ESCALATION_TICKS.remove(player.getUuid());
                BlockPos lightPos = PlayerCooldowns.PLAYER_LIGHT_POSITIONS.remove(player.getUuid());
                if (lightPos != null) {
                    if (serverWorld.getBlockState(lightPos).isOf(Blocks.LIGHT)) {
                        serverWorld.setBlockState(lightPos, Blocks.AIR.getDefaultState());
                    }
                }
                PlayerCooldowns.PHANTOM_POSITIONS.remove(player.getUuid());
                Integer eid = PlayerCooldowns.PHANTOM_ENTITY_IDS.remove(player.getUuid());
                if (eid != null) {
                    ServerPlayNetworking.send(player, new RemovePhantomPayload(eid));
                }
                PlayerCooldowns.PHANTOM_YAWS.remove(player.getUuid());
            }
        }

        DreadManager.checkOverload(player);

        if (server.getTicks() % 20 == 0) {
            PlayerStateManager.syncPlayerState(player, state);
            int dread = DreadManager.getDread(player);
            int threshold = DreadManager.getThreshold(player);
            ServerPlayNetworking.send(player, new com.project3.network.DreadPayload(dread, threshold));
        }
    }
}
