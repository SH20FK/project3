package com.project3.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.project3.Project3Mod;
import com.project3.network.PhantomHeadSnapPayload;
import com.project3.network.ShaderFlashPayload;
import com.project3.network.SpawnStatuePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class PhantomReplicator {

    public static final List<ActiveNpc> ACTIVE_NPCS = new CopyOnWriteArrayList<>();

    public static final java.util.Map<UUID, List<PlayerFrame>> CURRENT_RECORDING = new ConcurrentHashMap<>();
    public static final java.util.Map<UUID, List<PlayerFrame>> LAST_SAVED_RECORDING = new ConcurrentHashMap<>();
    public static final java.util.Map<UUID, Integer> RECORDING_TIMER = new ConcurrentHashMap<>();

    public static final java.util.Map<UUID, java.util.Deque<Vec3d>> POSITION_HISTORY = new ConcurrentHashMap<>();
    public static final java.util.Map<UUID, java.util.Deque<Float>> YAW_HISTORY = new ConcurrentHashMap<>();

    private static final String[] GLITCHED_PHRASES = {
        "§c▞▚█ СИСТЕМА ПЕРЕГРУЖЕНА █▚▞",
        "§4П▞О▚М█О▞Г█И▚Т█Е",
        "§7я █▞▚ виж█у ▚▞█ т█еб▞я...",
        "§cОНО СМОТРИТ НА МЕНЯ ▞▚▞▚",
        "§4Ф▞А▚Й█Л С█Т█Е█Р█Т",
        "§8[ДАННЫЕ ПОВРЕЖДЕНЫ] █▞▚█",
        "§cВЫХОДА НЕТ ВЫХОДА НЕТ ВЫХОДА НЕТ",
        "§4С█И█С█Т█Е█М█А  У█М█И█Р█А█Е█Т",
        "§cГДЕ МОЕ ТЕЛО?! ▞▚█",
        "§eН█А█З█А█Д  Н█Е  С█М█О█Т█Р█И"
    };

    private static final String[] ECHO_SYSTEM_MSGS = {
        "§7[Сервер] §fВыполняется резервное копирование данных игрока %s...",
        "§c[Ошибка] §fНе удалось прочитать файл %s.dat — повреждена контрольная сумма",
        "§e[Предупреждение] §fАномальная активность в памяти игрока %s",
        "§4[Критическая ошибка] §fОбнаружено 2 (два) активных экземпляра игрока %s",
        "§7[Система] §fСинхронизация данных: %s — 0x%X байт повреждено",
        "§4[СБОЙ] §fСущность игрока %s десинхронизирована. Рекомендуется перезагрузка",
        "§8[Лог] §f%s: множественный вход в систему — возможен дубликат",
        "§c[ОШИБКА: 0x7F4A] §fОбнаружена временная аномалия в потоке памяти игрока %s"
    };

    public static class PlayerFrame {
        public final Vec3d pos;
        public final float yaw;
        public final float pitch;
        public final float headYaw;
        public final EntityPose pose;
        public final boolean isSprinting;
        public final ItemStack mainHandItem;
        public final ItemStack offHandItem;
        public final ItemStack helmet;
        public final ItemStack chestplate;
        public final ItemStack leggings;
        public final ItemStack boots;
        public final boolean isSwinging;

        public PlayerFrame(ServerPlayerEntity player) {
            this.pos = player.getEntityPos();
            this.yaw = player.getYaw();
            this.pitch = player.getPitch();
            this.headYaw = player.getHeadYaw();
            this.pose = player.getPose();
            this.isSprinting = player.isSprinting();
            this.mainHandItem = player.getEquippedStack(EquipmentSlot.MAINHAND).copy();
            this.offHandItem = player.getEquippedStack(EquipmentSlot.OFFHAND).copy();
            this.helmet = player.getEquippedStack(EquipmentSlot.HEAD).copy();
            this.chestplate = player.getEquippedStack(EquipmentSlot.CHEST).copy();
            this.leggings = player.getEquippedStack(EquipmentSlot.LEGS).copy();
            this.boots = player.getEquippedStack(EquipmentSlot.FEET).copy();
            this.isSwinging = player.handSwinging;
        }
    }

    public static class ActiveNpc {
        public final ServerPlayerEntity npc;
        public final ServerPlayerEntity targetPlayer;
        public final NpcType type;
        public int ticksLeft;
        
        public double speed = 0.25;
        public List<PlayerFrame> replayFrames;
        public int replayIndex = 0;
        public Vec3d runToPos;
        public int gracePeriod = 40;

        public boolean stalkerWasVisible = false;
        public int stalkerEyeContactTicks = 0;
        public int stalkerBlinkTimer = 0;
        public int chatEchoMsgCount = 0;
        public int chatEchoNextMsg = 0;
        public int dejaVuLoops = 0;
        public int dejaVuMaxLoops = 3;
        public boolean hasTriggeredScare = false;

        public enum NpcType {
            SCREAMER_SPRINT,
            DEAD_SCENARIO,
            CHAT_ECHO,
            STATIC,
            STALKER,
            DEJA_VU
        }

        public ActiveNpc(ServerPlayerEntity npc, ServerPlayerEntity targetPlayer, NpcType type, int ticksLeft) {
            this.npc = npc;
            this.targetPlayer = targetPlayer;
            this.type = type;
            this.ticksLeft = ticksLeft;
        }
    }

    // ─── Position Sync Helper ──────────────────────────────────────────────
    // Uses EntityPositionSyncS2CPacket (absolute position) for fake NPCs.
    // EntityPositionS2CPacket relies on client-side TrackedPosition which is
    // only maintained by the entity tracker — fake NPCs have no tracker,
    // so deltas go stale. EntityPositionSyncS2CPacket sends the full position
    // directly and vanilla uses it for periodic re-syncs.
    private static void syncPosition(ServerPlayerEntity npc, double newX, double newY, double newZ, float yaw, float pitch) {
        npc.refreshPositionAndAngles(newX, newY, newZ, yaw, pitch);
    }

    private static void syncPositionAngles(ServerPlayerEntity npc, Vec3d pos, float yaw, float pitch) {
        syncPosition(npc, pos.x, pos.y, pos.z, yaw, pitch);
    }

    // ─── Ground Finder ─────────────────────────────────────────────────────
    // Finds a valid ground position near the target: walks down from the given
    // position to find a solid block with air above, checking a 3x3 area.
    private static Vec3d findGroundPos(ServerWorld world, Vec3d center, int searchRadius) {
        BlockPos base = BlockPos.ofFloored(center);
        // Search downward from center Y to find ground
        for (int dy = -3; dy >= -15; dy--) {
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos check = base.add(dx, dy, dz);
                    BlockPos above = check.up();
                    BlockPos feet = above.up();
                    BlockState below = world.getBlockState(check);
                    BlockState atFeet = world.getBlockState(above);
                    BlockState atHead = world.getBlockState(feet);
                    if (!below.isAir() && below.isSolidBlock(world, check)
                            && atFeet.isAir() && atHead.isAir()) {
                        return new Vec3d(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
                    }
                }
            }
        }
        // Fallback: return center adjusted to nearest solid block below
        for (int dy = 0; dy >= -20; dy--) {
            BlockPos check = base.down(-dy);
            if (!world.getBlockState(check).isAir() && world.getBlockState(check).isSolidBlock(world, check)
                    && world.getBlockState(check.up()).isAir()) {
                return new Vec3d(check.getX() + 0.5, check.getY() + 1, check.getZ() + 0.5);
            }
        }
        return center;
    }

    // ─── Pathfinding: move toward target avoiding solid blocks ──────────────
    // Simple greedy pathfinding: try direct path, then horizontal offset, then jump
    private static Vec3d computeNextStep(ServerWorld world, Vec3d from, Vec3d to, double speed) {
        Vec3d dir = to.subtract(from);
        double dist = dir.length();
        if (dist < 0.1) return from;

        Vec3d moveDir = dir.normalize().multiply(Math.min(speed, dist));
        Vec3d candidate = from.add(moveDir);

        // Check if the candidate position is clear (feet + head)
        BlockPos feetPos = BlockPos.ofFloored(candidate.x, candidate.y, candidate.z);
        BlockPos headPos = feetPos.up();
        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(headPos);

        // Direct path is clear
        if ((feetState.isAir() || !feetState.isSolidBlock(world, feetPos))
                && (headState.isAir() || !headState.isSolidBlock(world, headPos))) {
            // Check if there's ground beneath
            BlockState belowFeet = world.getBlockState(feetPos.down());
            if (!belowFeet.isAir() && belowFeet.isSolidBlock(world, feetPos.down())) {
                return candidate;
            }
        }

        // Try horizontal-only movement (stay at same Y)
        Vec3d horizontalMove = new Vec3d(moveDir.x, 0, moveDir.z).normalize().multiply(Math.min(speed, dist));
        Vec3d horizCandidate = from.add(horizontalMove);
        BlockPos hFeet = BlockPos.ofFloored(horizCandidate.x, horizCandidate.y, horizCandidate.z);
        BlockPos hHead = hFeet.up();
        BlockState hFeetState = world.getBlockState(hFeet);
        BlockState hHeadState = world.getBlockState(hHead);
        BlockState hBelow = world.getBlockState(hFeet.down());

        if ((hFeetState.isAir() || !hFeetState.isSolidBlock(world, hFeet))
                && (hHeadState.isAir() || !hHeadState.isSolidBlock(world, hHead))
                && (!hBelow.isAir() && hBelow.isSolidBlock(world, hFeet.down()))) {
            return horizCandidate;
        }

        // Try stepping up 1 block
        Vec3d stepUp = from.add(moveDir.x, 1.0, moveDir.z);
        BlockPos sFeet = BlockPos.ofFloored(stepUp.x, stepUp.y, stepUp.z);
        BlockPos sHead = sFeet.up();
        BlockState sFeetState = world.getBlockState(sFeet);
        BlockState sHeadState = world.getBlockState(sHead);
        BlockState sBelow = world.getBlockState(sFeet.down());

        if ((sFeetState.isAir() || !sFeetState.isSolidBlock(world, sFeet))
                && (sHeadState.isAir() || !sHeadState.isSolidBlock(world, sHead))
                && (!sBelow.isAir() && sBelow.isSolidBlock(world, sFeet.down()))) {
            return stepUp;
        }

        // Try stepping down 1 block
        Vec3d stepDown = from.add(moveDir.x, -1.0, moveDir.z);
        BlockPos dFeet = BlockPos.ofFloored(stepDown.x, stepDown.y, stepDown.z);
        BlockPos dHead = dFeet.up();
        BlockState dFeetState = world.getBlockState(dFeet);
        BlockState dHeadState = world.getBlockState(dHead);
        BlockState dBelow = world.getBlockState(dFeet.down());

        if ((dFeetState.isAir() || !dFeetState.isSolidBlock(world, dFeet))
                && (dHeadState.isAir() || !dHeadState.isSolidBlock(world, dHead))
                && (!dBelow.isAir() && dBelow.isSolidBlock(world, dFeet.down()))) {
            return stepDown;
        }

        return from;
    }

    // ─── Ticking & Recording Logic ──────────────────────────────────────────

    public static void tickRecordingAndHistory(ServerPlayerEntity player) {
        if (player.isSpectator()) return;

        UUID uuid = player.getUuid();

        int ticks = RECORDING_TIMER.computeIfAbsent(uuid, k -> 0);
        ticks++;
        if (ticks >= 1200) {
            ticks = 0;
        }
        RECORDING_TIMER.put(uuid, ticks);

        if (ticks < 300) {
            List<PlayerFrame> list = CURRENT_RECORDING.computeIfAbsent(uuid, k -> new ArrayList<>());
            list.add(new PlayerFrame(player));
        } else if (ticks == 300) {
            List<PlayerFrame> list = CURRENT_RECORDING.remove(uuid);
            if (list != null && !list.isEmpty()) {
                LAST_SAVED_RECORDING.put(uuid, list);
            }
        }

        java.util.Deque<Vec3d> posList = POSITION_HISTORY.computeIfAbsent(uuid, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        java.util.Deque<Float> yawList = YAW_HISTORY.computeIfAbsent(uuid, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());

        posList.addLast(player.getEntityPos());
        yawList.addLast(player.getYaw());

        if (posList.size() > 300) {
            posList.pollFirst();
        }
        if (yawList.size() > 300) {
            yawList.pollFirst();
        }
    }

    public static void tickActiveNpcs(MinecraftServer server) {
        java.util.Iterator<ActiveNpc> iterator = ACTIVE_NPCS.iterator();
        while (iterator.hasNext()) {
            ActiveNpc activeNpc = iterator.next();
            activeNpc.ticksLeft--;
            if (activeNpc.ticksLeft <= 0) {
                destroyNpc(activeNpc);
                ACTIVE_NPCS.remove(activeNpc);
                continue;
            }

            ServerPlayerEntity npc = activeNpc.npc;
            if (npc == null || npc.isRemoved() || !npc.isAlive() || activeNpc.targetPlayer == null || activeNpc.targetPlayer.isRemoved()) {
                destroyNpc(activeNpc);
                ACTIVE_NPCS.remove(activeNpc);
                continue;
            }

            Vec3d npcPos = npc.getEntityPos();
            ServerWorld world = (ServerWorld) npc.getEntityWorld();
            if (world == null) {
                destroyNpc(activeNpc);
                ACTIVE_NPCS.remove(activeNpc);
                continue;
            }

            switch (activeNpc.type) {
                case SCREAMER_SPRINT -> tickScreamerSprint(activeNpc, npc, npcPos, world);
                case STALKER -> tickStalker(activeNpc, npc, npcPos, world);
                case DEAD_SCENARIO -> tickDeadScenario(activeNpc, npc, npcPos, world);
                case CHAT_ECHO -> tickChatEcho(activeNpc, npc, npcPos, world);
                case STATIC -> tickStatic(activeNpc, npc, npcPos, world);
                case DEJA_VU -> tickDejaVu(activeNpc, npc, npcPos, world);
            }
        }
    }

    // ─── Screamer Sprint Tick ───────────────────────────────────────────────
    private static void tickScreamerSprint(ActiveNpc activeNpc, ServerPlayerEntity npc, Vec3d npcPos, ServerWorld world) {
        Vec3d target = (activeNpc.runToPos != null) ? activeNpc.runToPos : activeNpc.targetPlayer.getEntityPos();
        Vec3d dir = target.subtract(npcPos);
        double distance = dir.length();
        double distToPlayer = activeNpc.targetPlayer.getEntityPos().squaredDistanceTo(npcPos);

        npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target);
        double currentSpeed = (distance < 12.0) ? activeNpc.speed * 2.5 : activeNpc.speed;

        if (distance > 0.1) {
            Vec3d newPos = computeNextStep(world, npcPos, target, currentSpeed);
            if (!newPos.equals(npcPos)) {
                syncPositionAngles(npc, newPos, npc.getYaw(), npc.getPitch());
            }
        }

        npc.setSprinting(true);
        broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
        broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));

        // Footstep sounds — running on different surfaces
        int footstepInterval = (distance < 12.0) ? 2 : 4;
        if (activeNpc.ticksLeft % footstepInterval == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.8f, 0.9f);
        }

        // Heartbeat on target — gets faster as screamer gets closer
        if (distToPlayer < 20.0 * 20.0 && activeNpc.ticksLeft % (distToPlayer < 10.0 * 10.0 ? 5 : 10) == 0) {
            float hbVol = (float) Math.min(1.0, 1.0 - Math.sqrt(distToPlayer) / 30.0) + 0.3f;
            world.playSound(null, activeNpc.targetPlayer.getX(), activeNpc.targetPlayer.getY(), activeNpc.targetPlayer.getZ(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, hbVol, 1.8f);
        }

        // Run PAST player, not vanish on contact
        boolean shouldVanish = false;
        if (activeNpc.gracePeriod <= 0) {
            // Vanishes after passing through player (distance past them)
            if (activeNpc.runToPos != null) {
                double distToRunTarget = npcPos.squaredDistanceTo(activeNpc.runToPos);
                if (distToRunTarget < 3.0) shouldVanish = true;
            } else if (distToPlayer < 3.0 * 3.0) {
                // Ran through player — continue past, then vanish
                shouldVanish = true;
            }
            // Also vanish if player looks directly at it (eye contact = too predictable)
            if (!shouldVanish) {
                Vec3d lookVec = activeNpc.targetPlayer.getRotationVec(1.0f).normalize();
                Vec3d toNpc = npcPos.subtract(activeNpc.targetPlayer.getEntityPos()).normalize();
                if (lookVec.dotProduct(toNpc) > 0.98) {
                    shouldVanish = true;
                }
            }
        }

        if (shouldVanish) {
            ServerPlayerEntity vanishTarget = activeNpc.targetPlayer;
            // Camera shake on vanish
            Project3Mod.schedule(0, () -> {
                if (vanishTarget.isAlive() && vanishTarget.networkHandler != null) {
                    ServerPlayNetworking.send(vanishTarget, new com.project3.network.CameraRotatePayload(
                            (vanishTarget.getRandom().nextFloat() - 0.5f) * 6.0f,
                            (vanishTarget.getRandom().nextFloat() - 0.5f) * 3.0f));
                }
            });
            destroyNpc(activeNpc);
            world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                    SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, 2.0f, 0.8f);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    npcPos.x, npcPos.y + 1.0, npcPos.z, 40, 0.3, 0.3, 0.3, 0.1);
            ACTIVE_NPCS.remove(activeNpc);
        } else {
            activeNpc.gracePeriod--;
        }
    }

    // ─── Stalker Tick ───────────────────────────────────────────────────────
    private static void tickStalker(ActiveNpc activeNpc, ServerPlayerEntity npc, Vec3d npcPos, ServerWorld world) {
        Vec3d target = activeNpc.targetPlayer.getEntityPos();
        Vec3d dir = target.subtract(npcPos);
        double distToPlayer = dir.length();
        activeNpc.stalkerBlinkTimer++;

        Vec3d lookVec = activeNpc.targetPlayer.getRotationVec(1.0f).normalize();
        Vec3d toStalker = npcPos.subtract(activeNpc.targetPlayer.getEntityPos()).normalize();
        double dot = lookVec.dotProduct(toStalker);
        boolean isBeingLookedAt = dot > 0.95;

        if (isBeingLookedAt) {
            activeNpc.stalkerWasVisible = true;
            activeNpc.stalkerEyeContactTicks++;
            npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target);

            // Creepy head tilt after 2 seconds of eye contact
            if (activeNpc.stalkerEyeContactTicks > 40 && activeNpc.stalkerEyeContactTicks % 20 == 0) {
                float tiltAmount = (activeNpc.stalkerEyeContactTicks % 80 == 0) ? 30.0f : -30.0f;
                npc.setHeadYaw(npc.getHeadYaw() + tiltAmount);
                npc.setBodyYaw(npc.getBodyYaw() + tiltAmount);
                world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                        SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.HOSTILE, 0.3f, 0.1f);
            }

            // Reach during eye contact → jumpscare
            if (distToPlayer < 2.5 && !activeNpc.hasTriggeredScare) {
                activeNpc.hasTriggeredScare = true;
                triggerStalkerJumpscare(activeNpc);
                return;
            }

            broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
            broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
        } else {
            activeNpc.stalkerWasVisible = false;
            activeNpc.stalkerEyeContactTicks = 0;

            if (distToPlayer > 4.0) {
                // Fast approach when not looked at (speed * 3 = ~1.05)
                double approachSpeed = (distToPlayer > 15.0) ? activeNpc.speed * 3.0 : activeNpc.speed * 1.8;
                Vec3d newPos = computeNextStep(world, npcPos, target, approachSpeed);
                if (!newPos.equals(npcPos)) {
                    npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target);
                    syncPositionAngles(npc, newPos, npc.getYaw(), npc.getPitch());
                }

                // Occasional blink teleport (weeping angel style) - close gap instantly
                if (distToPlayer < 20.0 && activeNpc.stalkerBlinkTimer > 40 && world.random.nextFloat() < 0.01f) {
                    Vec3d blinkTarget = target.subtract(toStalker.multiply(4.0));
                    Vec3d blinkPos = findGroundPos(world, blinkTarget, 2);
                    if (blinkPos.squaredDistanceTo(target) < distToPlayer * distToPlayer) {
                        syncPositionAngles(npc, blinkPos, npc.getYaw(), npc.getPitch());
                        world.playSound(null, blinkPos.x, blinkPos.y, blinkPos.z,
                                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.3f, 2.0f);
                        activeNpc.stalkerBlinkTimer = 0;
                    }
                }
            } else {
                // Close enough - trigger scare
                if (!activeNpc.hasTriggeredScare) {
                    activeNpc.hasTriggeredScare = true;
                    triggerStalkerJumpscare(activeNpc);
                    return;
                }
                // After scare, teleport far away for another approach
                double theta = world.random.nextDouble() * 2 * Math.PI;
                double farDist = 25.0 + world.random.nextDouble() * 15.0;
                Vec3d farPos = target.add(Math.cos(theta) * farDist, 0, Math.sin(theta) * farDist);
                Vec3d safeFarPos = findGroundPos(world, farPos, 5);
                syncPositionAngles(npc, safeFarPos, npc.getYaw(), npc.getPitch());
                activeNpc.hasTriggeredScare = false;
                activeNpc.gracePeriod = 60;
            }

            npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target);
            broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
            broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
        }

        // Creepy breathing - gets louder closer
        if (activeNpc.ticksLeft % 15 == 0 && distToPlayer < 30) {
            float volume = (float) (0.4 + Math.max(0, 1.0 - distToPlayer / 30.0) * 1.6);
            float pitch = 0.3f + (float)(distToPlayer / 30.0) * 0.3f;
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, volume, pitch);
        }

        // Creaking sound when close and invisible
        if (!isBeingLookedAt && distToPlayer < 10.0 && activeNpc.ticksLeft % 30 == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_DOWN, SoundCategory.HOSTILE, 0.6f, 0.2f);
        }
    }

    private static void triggerStalkerJumpscare(ActiveNpc activeNpc) {
        ServerPlayerEntity target = activeNpc.targetPlayer;
        ServerWorld world = (ServerWorld) target.getEntityWorld();
        Vec3d npcPos = activeNpc.npc.getEntityPos();

        // Damage + blindness + nausea
        target.damage(world, world.getDamageSources().magic(), 4.0f);
        target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.BLINDNESS, 100, 0, false, false, true));
        target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.NAUSEA, 60, 1, false, false, true));

        // Camera shake
        Project3Mod.schedule(0, () -> {
            if (target.isAlive() && target.networkHandler != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target,
                        new com.project3.network.CameraRotatePayload(
                                (target.getRandom().nextFloat() - 0.5f) * 12.0f,
                                (target.getRandom().nextFloat() - 0.5f) * 6.0f));
            }
        });

        // Shader flash
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target,
                new com.project3.network.ShaderFlashPayload());

        // Loud scream sound AT target position (feels like it's in their head)
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.5f, 1.2f);

        // Stalker disappears with smoke
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                npcPos.x, npcPos.y + 1.0, npcPos.z, 60, 0.5, 0.5, 0.5, 0.15);
        world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.3f);
        destroyNpc(activeNpc);
        ACTIVE_NPCS.remove(activeNpc);
    }

    // ─── Dead Scenario Tick ─────────────────────────────────────────────────
    private static void tickDeadScenario(ActiveNpc activeNpc, ServerPlayerEntity npc, Vec3d npcPos, ServerWorld world) {
        ServerPlayerEntity target = activeNpc.targetPlayer;

        if (activeNpc.replayFrames != null && !activeNpc.replayFrames.isEmpty()) {
            int totalFrames = activeNpc.replayFrames.size();
            int frameTicks = activeNpc.ticksLeft > 200 ? 200 : activeNpc.ticksLeft;
            float lifePercent = 1.0f - ((float) frameTicks / 200.0f);

            boolean shouldPause = (lifePercent > 0.24f && lifePercent < 0.30f) ||
                                  (lifePercent > 0.74f && lifePercent < 0.80f);

            // Random head snap during replay (10% chance at certain intervals)
            boolean headSnap = !shouldPause && activeNpc.ticksLeft % 30 == 0 && world.random.nextFloat() < 0.3f;

            if (shouldPause) {
                npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getEntityPos());
                broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
                broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));

                if (activeNpc.ticksLeft % 4 == 0) {
                    world.spawnParticles(ParticleTypes.END_ROD,
                            npcPos.x, npcPos.y + 1.6, npcPos.z, 1, 0.1, 0.1, 0.1, 0.01);
                }
            } else if (headSnap) {
                // Head snap toward player during replay
                npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getEntityPos());
                float snapYaw = npc.getHeadYaw() + 180.0f;
                npc.setHeadYaw(snapYaw);
                npc.setBodyYaw(snapYaw);
                broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (snapYaw * 256.0F / 360.0F)));
                broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
                world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                        SoundEvents.BLOCK_BONE_BLOCK_STEP, SoundCategory.HOSTILE, 0.3f, 0.2f);
            } else {
                activeNpc.replayIndex = Math.min(frameTicks * totalFrames / 200, totalFrames - 1);
                PlayerFrame frame = activeNpc.replayFrames.get(activeNpc.replayIndex);

                syncPosition(npc, frame.pos.x, frame.pos.y, frame.pos.z, frame.yaw, frame.pitch);
                npc.setHeadYaw(frame.headYaw);
                npc.setBodyYaw(frame.yaw);
                npc.setPose(frame.pose);
                npc.setSprinting(frame.isSprinting);

                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack targetStack = frame.mainHandItem;
                    if (slot == EquipmentSlot.OFFHAND) targetStack = frame.offHandItem;
                    else if (slot == EquipmentSlot.HEAD) targetStack = frame.helmet;
                    else if (slot == EquipmentSlot.CHEST) targetStack = frame.chestplate;
                    else if (slot == EquipmentSlot.LEGS) targetStack = frame.leggings;
                    else if (slot == EquipmentSlot.FEET) targetStack = frame.boots;
                    npc.equipStack(slot, targetStack);
                }

                broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
                broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
                var dirtyEntries = npc.getDataTracker().getDirtyEntries();
                if (dirtyEntries != null) {
                    broadcastToViewers(npc, new EntityTrackerUpdateS2CPacket(npc.getId(), dirtyEntries));
                }

                if (frame.isSwinging) {
                    broadcastToViewers(npc, new EntityAnimationS2CPacket(npc, EntityAnimationS2CPacket.SWING_MAIN_HAND));
                }
            }
        }

        if (activeNpc.ticksLeft <= 30 && activeNpc.ticksLeft % 5 == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 0.8f, 0.3f);
        }

        if (activeNpc.ticksLeft == 1) {
            // Final scare: head snap toward player before vanishing
            npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getEntityPos());
            broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
            broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));

            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 0.5f, 0.3f);

            for (int i = 0; i < 40; i++) {
                double px = npcPos.x + (world.random.nextDouble() - 0.5) * 1.2;
                double py = npcPos.y + 0.5 + world.random.nextDouble() * 1.5;
                double pz = npcPos.z + (world.random.nextDouble() - 0.5) * 1.2;

                var block = (i % 3 == 0) ? Blocks.MAGENTA_CONCRETE :
                            (i % 3 == 1) ? Blocks.BLACK_CONCRETE :
                                           Blocks.OBSIDIAN;

                world.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, block.getDefaultState()),
                        px, py, pz, 1,
                        (world.random.nextDouble() - 0.5) * 0.1,
                        world.random.nextDouble() * 0.1,
                        (world.random.nextDouble() - 0.5) * 0.1,
                        0.05
                );
            }
            destroyNpc(activeNpc);
            ACTIVE_NPCS.remove(activeNpc);
        }
    }

    // ─── Chat Echo Tick ─────────────────────────────────────────────────────
    private static void tickChatEcho(ActiveNpc activeNpc, ServerPlayerEntity npc, Vec3d npcPos, ServerWorld world) {
        ServerPlayerEntity target = activeNpc.targetPlayer;
        if (activeNpc.gracePeriod > 0) {
            activeNpc.gracePeriod--;
        }

        if (activeNpc.speed != 999.0) {
            float lifePercent = 1.0f - ((float) activeNpc.ticksLeft / 600.0f);
            float targetYaw = (float) Math.toDegrees(Math.atan2(
                    target.getZ() - npcPos.z,
                    target.getX() - npcPos.x)) - 90.0f;
            float backYaw = targetYaw + 180.0f;

            float currentYaw = backYaw + (targetYaw - backYaw) * Math.min(lifePercent * 1.5f, 1.0f);
            npc.setYaw(currentYaw);
            npc.setHeadYaw(currentYaw);
            npc.setBodyYaw(currentYaw);

            broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
            broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));

            if (activeNpc.chatEchoMsgCount < 5 && activeNpc.ticksLeft < (600 - activeNpc.chatEchoNextMsg)) {
                activeNpc.chatEchoMsgCount++;
                activeNpc.chatEchoNextMsg = 160 + world.random.nextInt(80);

                // 50% chance: glitched chat message (target ONLY), 50% chance: fake system message
                if (world.random.nextBoolean()) {
                    String phrase = GLITCHED_PHRASES[world.random.nextInt(GLITCHED_PHRASES.length)];
                    target.sendMessage(Text.literal("<" + target.getGameProfile().name() + "> " + phrase), false);
                } else {
                    String sysMsg = ECHO_SYSTEM_MSGS[world.random.nextInt(ECHO_SYSTEM_MSGS.length)];
                    String formatted;
                    if (sysMsg.contains("%s")) {
                        long corruptBytes = (long)(world.random.nextDouble() * 0xFFFF);
                        formatted = String.format(sysMsg, target.getGameProfile().name(), corruptBytes);
                    } else {
                        formatted = String.format(sysMsg, target.getGameProfile().name());
                    }
                    target.sendMessage(Text.literal(formatted), false);
                }

                world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                        SoundEvents.ENTITY_ITEM_FRAME_BREAK, SoundCategory.HOSTILE, 0.6f, 1.5f);
            }

            boolean vanish = false;
            if (activeNpc.gracePeriod <= 0) {
                double distToPlayer = target.getEntityPos().squaredDistanceTo(npcPos);
                vanish = distToPlayer < 4.0 * 4.0;
                if (!vanish) {
                    Vec3d lookVec = target.getRotationVec(1.0f).normalize();
                    Vec3d toNpc = npcPos.subtract(target.getEntityPos()).normalize();
                    if (lookVec.dotProduct(toNpc) > 0.95) {
                        vanish = true;
                    }
                }
            }

            if (vanish) {
                activeNpc.speed = 999.0;
                activeNpc.ticksLeft = 12;

                world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                        SoundEvents.ENTITY_SKELETON_HURT, SoundCategory.HOSTILE, 2.0f, 0.4f);
                world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                        SoundEvents.ENTITY_ITEM_FRAME_BREAK, SoundCategory.HOSTILE, 1.5f, 0.5f);

                // Head snap only for the target player
                if (target.networkHandler != null) {
                    ServerPlayNetworking.send(target, new PhantomHeadSnapPayload(npc.getId()));
                }
            }
        } else if (activeNpc.ticksLeft == 1) {
            destroyNpc(activeNpc);
            world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 1.5f, 0.2f);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    npcPos.x, npcPos.y + 1.0, npcPos.z, 60, 0.5, 0.5, 0.5, 0.1);
            ACTIVE_NPCS.remove(activeNpc);
        }
    }

    // ─── Static Tick ────────────────────────────────────────────────────────
    private static void tickStatic(ActiveNpc activeNpc, ServerPlayerEntity npc, Vec3d npcPos, ServerWorld world) {
        ServerPlayerEntity target = activeNpc.targetPlayer;
        npc.setSprinting(false);
        Vec3d targetPos = target.getEntityPos();
        Vec3d dir = targetPos.subtract(npcPos);
        double distToPlayer = dir.length();

        double dx = targetPos.x - npcPos.x;
        double dy = targetPos.y + target.getStandingEyeHeight() - (npcPos.y + npc.getStandingEyeHeight());
        double dz = targetPos.z - npcPos.z;
        double dh = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));

        npc.setYaw(yaw);
        npc.setPitch(pitch);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);

        Vec3d lookVec = target.getRotationVec(1.0f).normalize();
        Vec3d toStatic = npcPos.subtract(targetPos).normalize();
        double dot = lookVec.dotProduct(toStatic);
        boolean isBeingLookedAt = dot > 0.95;

        // When looked at: spawn a SECOND copy behind the player
        if (isBeingLookedAt && distToPlayer < 12.0 && activeNpc.ticksLeft % 100 == 0 && !activeNpc.hasTriggeredScare) {
            activeNpc.hasTriggeredScare = true;
            Vec3d behindPos = targetPos.subtract(lookVec.multiply(3.0));
            Vec3d safeBehind = findGroundPos(world, behindPos, 2);
            spawnStaticNpcAt(target, safeBehind);
            target.sendMessage(Text.literal("§7..."), false);  // subtle text cue
        }

        if (!isBeingLookedAt && distToPlayer > 4.0) {
            Vec3d newPos = computeNextStep(world, npcPos, targetPos, 0.15);
            if (!newPos.equals(npcPos)) {
                syncPositionAngles(npc, newPos, yaw, pitch);
            }
        }

        // Camera slowly pulled toward statue when looked at
        if (isBeingLookedAt && distToPlayer < 12.0 && activeNpc.ticksLeft % 5 == 0) {
            Project3Mod.schedule(0, () -> {
                if (target.isAlive() && target.networkHandler != null) {
                    float driftYaw = (yaw - target.getYaw()) * 0.02f;
                    float driftPitch = (pitch - target.getPitch()) * 0.02f;
                    ServerPlayNetworking.send(target, new com.project3.network.CameraRotatePayload(
                            driftYaw * 0.5f, driftPitch * 0.3f));
                }
            });
        }

        // Creaking sounds when looked at
        if (isBeingLookedAt && activeNpc.ticksLeft % 30 == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 0.4f, 0.1f);
        }

        // Breathing when not looked at
        if (!isBeingLookedAt && distToPlayer < 20 && activeNpc.ticksLeft % 40 == 0) {
            float volume = (float) (0.2 + (20.0 - distToPlayer) / 20.0 * 0.8);
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, volume, 0.15f);
        }

        // Glitched particles (replaced electric spark with smoke and end rod particles for more subtlety)
        if (activeNpc.ticksLeft % 10 == 0) {
            world.spawnParticles(ParticleTypes.MYCELIUM,
                    npcPos.x, npcPos.y + 1.5, npcPos.z, 1, 0.3, 0.5, 0.3, 0.01);
        }
        if (activeNpc.ticksLeft % 3 == 0 && world.random.nextFloat() < 0.1f) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    npcPos.x + (world.random.nextDouble() - 0.5) * 0.5,
                    npcPos.y + 1.0 + world.random.nextDouble() * 0.5,
                    npcPos.z + (world.random.nextDouble() - 0.5) * 0.5,
                    1, 0, 0, 0, 0.01);
        }

        // When player gets close and looks at it: disappear with scare
        if (isBeingLookedAt && distToPlayer < 3.0) {
            destroyNpc(activeNpc);
            world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                    SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 0.5f);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                    npcPos.x, npcPos.y + 1.0, npcPos.z, 30, 0.3, 0.5, 0.3, 0.05);
            ACTIVE_NPCS.remove(activeNpc);
        } else {
            broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
            broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
            var dirtyEntries = npc.getDataTracker().getDirtyEntries();
            if (dirtyEntries != null) {
                broadcastToViewers(npc, new EntityTrackerUpdateS2CPacket(npc.getId(), dirtyEntries));
            }
        }
    }

    private static void spawnStaticNpcAt(ServerPlayerEntity target, Vec3d pos) {
        ServerWorld world = (ServerWorld) target.getEntityWorld();
        ServerPlayerEntity npc = createNpc(target, world, pos);
        double dx = target.getX() - pos.x;
        double dz = target.getZ() - pos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        npc.setYaw(yaw);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);
        renderNpc(npc, world, pos);
        ActiveNpc clone = new ActiveNpc(npc, target, ActiveNpc.NpcType.STATIC, 200);
        ACTIVE_NPCS.add(clone);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.3f, 0.1f);
    }

    // ─── Deja Vu Tick ───────────────────────────────────────────────────────
    private static void tickDejaVu(ActiveNpc activeNpc, ServerPlayerEntity npc, Vec3d npcPos, ServerWorld world) {
        Vec3d target = activeNpc.runToPos;
        if (target == null) {
            destroyNpc(activeNpc);
            ACTIVE_NPCS.remove(activeNpc);
            return;
        }

        npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target);

        if (activeNpc.ticksLeft > 0.1) {
            Vec3d newPos = computeNextStep(world, npcPos, target, activeNpc.speed);
            if (!newPos.equals(npcPos)) {
                npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target);
                syncPositionAngles(npc, newPos, npc.getYaw(), npc.getPitch());
            }
        }

        npc.setSprinting(true);

        broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
        broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));

        float hbVolume = 1.0f + activeNpc.dejaVuLoops * 0.5f;
        if (activeNpc.ticksLeft % 8 == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, hbVolume, 0.8f);
        }

        boolean vanish = false;
        double distToPlayer = activeNpc.targetPlayer.getEntityPos().squaredDistanceTo(npcPos);
        if (distToPlayer < 3.0 * 3.0) {
            vanish = true;
        }
        if (activeNpc.gracePeriod <= 0) {
            Vec3d lookVec = activeNpc.targetPlayer.getRotationVec(1.0f).normalize();
            Vec3d toNpc = npcPos.subtract(activeNpc.targetPlayer.getEntityPos()).normalize();
            double dotProduct = lookVec.dotProduct(toNpc);
            if (dotProduct > 0.98) vanish = true;
        } else {
            activeNpc.gracePeriod--;
        }

        if (vanish) {
            destroyNpc(activeNpc);
            world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 2.0f, 0.2f);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    npcPos.x, npcPos.y + 1.0, npcPos.z, 80, 0.5, 0.5, 0.5, 0.15);
            ACTIVE_NPCS.remove(activeNpc);
        }
    }

    // ─── Trigger Specific Actions ───────────────────────────────────────────

    public static void spawnScreamerSprint(ServerPlayerEntity targetPlayer) {
        spawnScreamerSprintChase(targetPlayer, null);
    }

    public static void spawnScreamerSprintChase(ServerPlayerEntity targetPlayer, Vec3d runToPos) {
        ServerWorld world = (ServerWorld) targetPlayer.getEntityWorld();

        double theta = targetPlayer.getRandom().nextDouble() * 2 * Math.PI;
        double px = targetPlayer.getX() + Math.cos(theta) * 8.0;
        double pz = targetPlayer.getZ() + Math.sin(theta) * 8.0;
        double py = targetPlayer.getY();
        Vec3d spawnPos = findGroundPos(world, new Vec3d(px, py, pz), 3);

        ServerPlayerEntity npc = createNpc(targetPlayer, world, spawnPos);

        double dx = targetPlayer.getX() - spawnPos.x;
        double dz = targetPlayer.getZ() - spawnPos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        npc.setYaw(yaw);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);

        renderNpc(npc, world, spawnPos);

        ActiveNpc activeNpc = new ActiveNpc(npc, targetPlayer, ActiveNpc.NpcType.SCREAMER_SPRINT, 200);
        activeNpc.speed = 0.4;
        activeNpc.runToPos = runToPos;
        activeNpc.gracePeriod = 20;
        ACTIVE_NPCS.add(activeNpc);

        // Silent spawn — subtle buildup, no loud scream
        // Subtle sound in distance
        world.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, 0.3f, 0.6f);
    }

    public static void spawnDeadScenario(ServerPlayerEntity targetPlayer) {
        List<PlayerFrame> cachedFrames = LAST_SAVED_RECORDING.get(targetPlayer.getUuid());
        if (cachedFrames == null || cachedFrames.isEmpty()) return;

        ServerWorld world = (ServerWorld) targetPlayer.getEntityWorld();
        PlayerFrame firstFrame = cachedFrames.get(0);

        Vec3d spawnPos = findGroundPos(world, firstFrame.pos, 2);
        ServerPlayerEntity npc = createNpc(targetPlayer, world, spawnPos);
        syncPosition(npc, spawnPos.x, spawnPos.y, spawnPos.z, firstFrame.yaw, firstFrame.pitch);
        npc.setHeadYaw(firstFrame.headYaw);
        npc.setBodyYaw(firstFrame.yaw);

        renderNpc(npc, world, spawnPos);

        ActiveNpc activeNpc = new ActiveNpc(npc, targetPlayer, ActiveNpc.NpcType.DEAD_SCENARIO, 200);
        activeNpc.replayFrames = cachedFrames;
        ACTIVE_NPCS.add(activeNpc);
    }

    public static void triggerDejaVu(ServerPlayerEntity targetPlayer) {
        UUID uuid = targetPlayer.getUuid();
        java.util.Deque<Vec3d> posList = POSITION_HISTORY.get(uuid);
        java.util.Deque<Float> yawList = YAW_HISTORY.get(uuid);

        if (posList == null || posList.isEmpty()) return;

        Vec3d targetPos = posList.peekFirst();
        float targetYaw = yawList != null && !yawList.isEmpty() ? yawList.peekFirst() : targetPlayer.getYaw();

        ServerPlayNetworking.send(targetPlayer, new ShaderFlashPayload());

        Vec3d fromPos = targetPlayer.getEntityPos();

        Project3Mod.schedule(10, () -> {
            targetPlayer.teleport((ServerWorld) targetPlayer.getEntityWorld(), targetPos.x, targetPos.y, targetPos.z, java.util.Set.of(), targetYaw, targetPlayer.getPitch(), true);
            spawnScreamerSprintChase(targetPlayer, fromPos);
        });
    }

    public static void spawnChatEcho(ServerPlayerEntity targetPlayer) {
        ServerWorld world = (ServerWorld) targetPlayer.getEntityWorld();

        Vec3d look = targetPlayer.getRotationVec(1.0f).normalize();
        double px = targetPlayer.getX() - look.x * 8.0;
        double pz = targetPlayer.getZ() - look.z * 8.0;
        double py = targetPlayer.getY();
        Vec3d spawnPos = findGroundPos(world, new Vec3d(px, py, pz), 3);

        double dx = targetPlayer.getX() - spawnPos.x;
        double dz = targetPlayer.getZ() - spawnPos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float backYaw = yaw + 180.0f;

        ServerPlayerEntity npc = createNpc(targetPlayer, world, spawnPos);
        npc.setYaw(backYaw);
        npc.setHeadYaw(backYaw);
        npc.setBodyYaw(backYaw);

        renderNpc(npc, world, spawnPos);

        ActiveNpc activeNpc = new ActiveNpc(npc, targetPlayer, ActiveNpc.NpcType.CHAT_ECHO, 600);
        activeNpc.chatEchoNextMsg = 100 + targetPlayer.getRandom().nextInt(60);
        ACTIVE_NPCS.add(activeNpc);

        // First message: only target sees it
        String phrase = GLITCHED_PHRASES[targetPlayer.getRandom().nextInt(GLITCHED_PHRASES.length)];
        targetPlayer.sendMessage(Text.literal("<" + targetPlayer.getGameProfile().name() + "> " + phrase), false);
        activeNpc.chatEchoMsgCount = 1;
    }

    public static void spawnFrozenScreenshot(ServerPlayerEntity targetPlayer, BlockPos blockPos) {
        ServerWorld world = (ServerWorld) targetPlayer.getEntityWorld();

        double px = blockPos.getX() + 0.5;
        double py = blockPos.getY() + 1.0;
        double pz = blockPos.getZ() + 0.5;

        int entityId = -150000 - targetPlayer.getRandom().nextInt(50000);

        ServerPlayNetworking.send(targetPlayer, new SpawnStatuePayload(
            entityId,
            px, py, pz,
            targetPlayer.getYaw(),
            targetPlayer.getPitch(),
            targetPlayer.getHeadYaw(),
            targetPlayer.getBodyYaw(),
            targetPlayer.getPose().name(),
            targetPlayer.getUuid(),
            targetPlayer.getGameProfile().name()
        ));

        if (world.getBlockState(blockPos.up()).isAir()) {
            world.setBlockState(blockPos.up(), Blocks.LIGHT.getDefaultState().with(net.minecraft.block.LightBlock.LEVEL_15, 6));
            com.project3.player.PlayerCooldowns.COMMAND_SPAWNED_LIGHTS.computeIfAbsent(targetPlayer.getUuid(), uuid -> new ArrayList<>()).add(blockPos.up());
        }
    }

    public static void spawnStalker(ServerPlayerEntity targetPlayer) {
        ServerWorld world = (ServerWorld) targetPlayer.getEntityWorld();

        double theta = targetPlayer.getRandom().nextDouble() * 2 * Math.PI;
        double distance = 25.0 + targetPlayer.getRandom().nextDouble() * 10.0;
        double px = targetPlayer.getX() + Math.cos(theta) * distance;
        double pz = targetPlayer.getZ() + Math.sin(theta) * distance;
        double py = targetPlayer.getY();
        Vec3d spawnPos = findGroundPos(world, new Vec3d(px, py, pz), 5);

        ServerPlayerEntity npc = createNpc(targetPlayer, world, spawnPos);

        double dx = targetPlayer.getX() - spawnPos.x;
        double dz = targetPlayer.getZ() - spawnPos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        npc.setYaw(yaw);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);

        renderNpc(npc, world, spawnPos);

        ActiveNpc activeNpc = new ActiveNpc(npc, targetPlayer, ActiveNpc.NpcType.STALKER, 900);
        activeNpc.speed = 0.35;
        activeNpc.gracePeriod = 80;
        ACTIVE_NPCS.add(activeNpc);

        // Silent spawn — no sound, no warning
    }

    public static void spawnStaticNpc(ServerPlayerEntity targetPlayer) {
        ServerWorld world = (ServerWorld) targetPlayer.getEntityWorld();

        Vec3d look = targetPlayer.getRotationVec(1.0f).normalize();
        double distance = 10.0 + targetPlayer.getRandom().nextDouble() * 5.0;
        double px = targetPlayer.getX() - look.x * distance;
        double pz = targetPlayer.getZ() - look.z * distance;
        double py = targetPlayer.getY();
        Vec3d spawnPos = findGroundPos(world, new Vec3d(px, py, pz), 3);

        ServerPlayerEntity npc = createNpc(targetPlayer, world, spawnPos);

        double dx = targetPlayer.getX() - spawnPos.x;
        double dz = targetPlayer.getZ() - spawnPos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        npc.setYaw(yaw);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);

        renderNpc(npc, world, spawnPos);

        ActiveNpc activeNpc = new ActiveNpc(npc, targetPlayer, ActiveNpc.NpcType.STATIC, 400);
        ACTIVE_NPCS.add(activeNpc);

        world.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 0.3f);
    }

    public static void spawnDejaVuChase(ServerPlayerEntity targetPlayer, Vec3d chaseToPos) {
        ServerWorld world = (ServerWorld) targetPlayer.getEntityWorld();

        double theta = targetPlayer.getRandom().nextDouble() * 2 * Math.PI;
        double px = targetPlayer.getX() + Math.cos(theta) * 8.0;
        double pz = targetPlayer.getZ() + Math.sin(theta) * 8.0;
        double py = targetPlayer.getY();
        Vec3d spawnPos = findGroundPos(world, new Vec3d(px, py, pz), 3);

        ServerPlayerEntity npc = createNpc(targetPlayer, world, spawnPos);

        double dx = targetPlayer.getX() - spawnPos.x;
        double dz = targetPlayer.getZ() - spawnPos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        npc.setYaw(yaw);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);

        renderNpc(npc, world, spawnPos);

        ActiveNpc activeNpc = new ActiveNpc(npc, targetPlayer, ActiveNpc.NpcType.DEJA_VU, 300);
        activeNpc.speed = 0.45;
        activeNpc.runToPos = chaseToPos;
        ACTIVE_NPCS.add(activeNpc);

        world.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 2.5f, 0.6f);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static GameProfile cloneProfileWithRandomUuid(GameProfile original) {
        return new GameProfile(UUID.randomUUID(), original.name(), original.properties());
    }

    private static ServerPlayerEntity createNpc(ServerPlayerEntity target, ServerWorld world, Vec3d pos) {
        ServerPlayerEntity npc = new ServerPlayerEntity(
                world.getServer(),
                world,
                cloneProfileWithRandomUuid(target.getGameProfile()),
                target.getClientOptions()
        );
        npc.setPosition(pos);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            npc.equipStack(slot, target.getEquippedStack(slot).copy());
        }
        return npc;
    }

    private static void renderNpc(ServerPlayerEntity npc, ServerWorld world, Vec3d pos) {
        if (npc == null || npc.isRemoved() || !npc.isAlive()) return;
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer == null || viewer.networkHandler == null || viewer.isRemoved() || !viewer.isAlive()) continue;
            if (viewer.getEntityPos().squaredDistanceTo(pos) < 64 * 64) {
                viewer.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, npc));
                
                viewer.networkHandler.sendPacket(new EntitySpawnS2CPacket(
                    npc.getId(),
                    npc.getUuid(),
                    npc.getX(),
                    npc.getY(),
                    npc.getZ(),
                    npc.getPitch(),
                    npc.getYaw(),
                    net.minecraft.entity.EntityType.PLAYER,
                    0,
                    net.minecraft.util.math.Vec3d.ZERO,
                    npc.getHeadYaw()
                ));

                List<Pair<EquipmentSlot, ItemStack>> equipmentList = new ArrayList<>();
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    equipmentList.add(Pair.of(slot, npc.getEquippedStack(slot)));
                }
                viewer.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(npc.getId(), equipmentList));

                var dirtyEntries = npc.getDataTracker().getDirtyEntries();
                if (dirtyEntries != null) {
                    viewer.networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(npc.getId(), dirtyEntries));
                }
            }
        }
    }

    private static void destroyNpc(ActiveNpc activeNpc) {
        ServerPlayerEntity npc = activeNpc.npc;
        broadcastToViewers(npc, new EntitiesDestroyS2CPacket(npc.getId()));
        broadcastToViewers(npc, new PlayerRemoveS2CPacket(List.of(npc.getUuid())));
        npc.discard();
    }

    private static void broadcastToViewers(ServerPlayerEntity npc, Packet<?> packet) {
        Vec3d pos = npc.getEntityPos();
        for (ServerPlayerEntity viewer : ((ServerWorld) npc.getEntityWorld()).getPlayers()) {
            if (viewer == null || viewer.networkHandler == null || viewer.isRemoved() || !viewer.isAlive()) continue;
            if (viewer.getEntityPos().squaredDistanceTo(pos) < 64 * 64) {
                viewer.networkHandler.sendPacket(packet);
            }
        }
    }

    public static void spawnCustomPhantom(ServerPlayerEntity targetPlayer, GameProfile skinProfile, String type) {
        ServerWorld world = (ServerWorld) targetPlayer.getEntityWorld();

        GameProfile npcProfile = cloneProfileWithRandomUuid(skinProfile);

        double px, py, pz;
        float yaw;

        if (type.equalsIgnoreCase("screamer")) {
            double theta = targetPlayer.getRandom().nextDouble() * 2 * Math.PI;
            px = targetPlayer.getX() + Math.cos(theta) * 8.0;
            pz = targetPlayer.getZ() + Math.sin(theta) * 8.0;
            py = targetPlayer.getY();
            double dx = targetPlayer.getX() - px;
            double dz = targetPlayer.getZ() - pz;
            yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        } else {
            double yawRad = Math.toRadians(targetPlayer.getYaw());
            px = targetPlayer.getX() - Math.sin(yawRad) * 3.0;
            pz = targetPlayer.getZ() + Math.cos(yawRad) * 3.0;
            py = targetPlayer.getY();
            yaw = targetPlayer.getYaw() + 180.0f;
        }

        Vec3d spawnPos = findGroundPos(world, new Vec3d(px, py, pz), 3);

        ServerPlayerEntity npc = new ServerPlayerEntity(
                ((ServerWorld) targetPlayer.getEntityWorld()).getServer(),
                world,
                npcProfile,
                targetPlayer.getClientOptions()
        );
        npc.setPosition(spawnPos);
        npc.setYaw(yaw);
        npc.setPitch(0.0f);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);

        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
            npc.equipStack(slot, targetPlayer.getEquippedStack(slot).copy());
        }

        renderNpc(npc, world, spawnPos);

        ActiveNpc.NpcType npcType;
        int ticksLeft;
        if (type.equalsIgnoreCase("echo")) {
            npcType = ActiveNpc.NpcType.CHAT_ECHO;
            ticksLeft = 600;
        } else if (type.equalsIgnoreCase("static")) {
            npcType = ActiveNpc.NpcType.STATIC;
            ticksLeft = 400;
        } else if (type.equalsIgnoreCase("stalker")) {
            npcType = ActiveNpc.NpcType.STALKER;
            ticksLeft = 600;
        } else if (type.equalsIgnoreCase("dejavu")) {
            npcType = ActiveNpc.NpcType.DEJA_VU;
            ticksLeft = 300;
        } else {
            npcType = ActiveNpc.NpcType.SCREAMER_SPRINT;
            ticksLeft = 200;
        }

        ActiveNpc activeNpc = new ActiveNpc(npc, targetPlayer, npcType, ticksLeft);
        activeNpc.gracePeriod = 20;
        ACTIVE_NPCS.add(activeNpc);

        if (npcType == ActiveNpc.NpcType.SCREAMER_SPRINT) {
            world.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_GHAST_SCREAM, net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
    }

    public static void clearCommandPhantoms(ServerPlayerEntity player) {
        List<ActiveNpc> toRemove = new ArrayList<>();
        for (ActiveNpc activeNpc : ACTIVE_NPCS) {
            if (activeNpc.targetPlayer == player) {
                destroyNpc(activeNpc);
                toRemove.add(activeNpc);
            }
        }
        ACTIVE_NPCS.removeAll(toRemove);
    }
}
