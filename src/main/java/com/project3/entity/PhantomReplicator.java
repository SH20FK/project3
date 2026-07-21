package com.project3.entity;

import com.mojang.authlib.GameProfile;
import com.project3.config.ModConfig;
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
import java.util.concurrent.ConcurrentLinkedDeque;

public class PhantomReplicator {

    public static final ConcurrentLinkedDeque<NpcBase> ACTIVE_NPCS = new ConcurrentLinkedDeque<>();

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



    // ─── Position Sync Helper ──────────────────────────────────────────────
    // Uses EntityPositionSyncS2CPacket (absolute position) for fake NPCs.
    // EntityPositionS2CPacket relies on client-side TrackedPosition which is
    // only maintained by the entity tracker — fake NPCs have no tracker,
    // so deltas go stale. EntityPositionSyncS2CPacket sends the full position
    // directly and vanilla uses it for periodic re-syncs.
    public static void syncPosition(ServerPlayerEntity npc, double newX, double newY, double newZ, float yaw, float pitch) {
        npc.refreshPositionAndAngles(newX, newY, newZ, yaw, pitch);
    }

    public static void syncPositionAngles(ServerPlayerEntity npc, Vec3d pos, float yaw, float pitch) {
        syncPosition(npc, pos.x, pos.y, pos.z, yaw, pitch);
    }

    // ─── Ground Finder ─────────────────────────────────────────────────────
    // Finds a valid ground position near the target: walks down from the given
    // position to find a solid block with air above, checking a 3x3 area.
    public static Vec3d findGroundPos(ServerWorld world, Vec3d center, int searchRadius) {
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
    public static Vec3d computeNextStep(ServerWorld world, Vec3d from, Vec3d to, double speed) {
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

    public static void tickActiveNpcs(MinecraftServer server) {
        java.util.Iterator<NpcBase> iterator = ACTIVE_NPCS.iterator();
        while (iterator.hasNext()) {
            NpcBase activeNpc = iterator.next();
            activeNpc.ticksLeft--;
            if (activeNpc.ticksLeft <= 0) {
                destroyNpc(activeNpc);
                iterator.remove();
                continue;
            }

            ServerPlayerEntity npc = activeNpc.npc;
            if (npc == null || npc.isRemoved() || !npc.isAlive() || activeNpc.targetPlayer == null || activeNpc.targetPlayer.isRemoved()) {
                destroyNpc(activeNpc);
                iterator.remove();
                continue;
            }

            Vec3d npcPos = npc.getEntityPos();
            ServerWorld world = (ServerWorld) npc.getEntityWorld();
            if (world == null) {
                destroyNpc(activeNpc);
                iterator.remove();
                continue;
            }

            boolean removed = activeNpc.tick(world, npcPos);
            if (removed) {
                iterator.remove();
            }
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

        ScreamerSprintNpc npcInstance = new ScreamerSprintNpc(npc, targetPlayer, ModConfig.get().screamerTicks);
        npcInstance.speed = ModConfig.get().screamerSpeed;
        npcInstance.gracePeriod = ModConfig.get().screamerGracePeriod;
        npcInstance.runToPos = runToPos;
        ACTIVE_NPCS.add(npcInstance);

        // Silent spawn — subtle buildup, no loud scream
        // Subtle sound in distance
        world.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, 0.3f, 0.6f);
    }

    public static void spawnDeadScenario(ServerPlayerEntity targetPlayer) {
        List<PlayerFrame> cachedFrames = PlayerSessionData.getLastSavedRecording(targetPlayer.getUuid());
        if (cachedFrames == null || cachedFrames.isEmpty()) return;

        ServerWorld world = (ServerWorld) targetPlayer.getEntityWorld();
        PlayerFrame firstFrame = cachedFrames.get(0);

        Vec3d spawnPos = findGroundPos(world, firstFrame.pos, 2);
        ServerPlayerEntity npc = createNpc(targetPlayer, world, spawnPos);
        syncPosition(npc, spawnPos.x, spawnPos.y, spawnPos.z, firstFrame.yaw, firstFrame.pitch);
        npc.setHeadYaw(firstFrame.headYaw);
        npc.setBodyYaw(firstFrame.yaw);

        renderNpc(npc, world, spawnPos);

        DeadScenarioNpc npcInstance = new DeadScenarioNpc(npc, targetPlayer, ModConfig.get().deadScenarioTicks);
        npcInstance.replayFrames = cachedFrames;
        ACTIVE_NPCS.add(npcInstance);
    }

    public static void triggerDejaVu(ServerPlayerEntity targetPlayer) {
        UUID uuid = targetPlayer.getUuid();
        java.util.Deque<Vec3d> posList = PlayerSessionData.getPositionHistory(uuid);
        java.util.Deque<Float> yawList = PlayerSessionData.getYawHistory(uuid);

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

        ChatEchoNpc npcInstance = new ChatEchoNpc(npc, targetPlayer, ModConfig.get().chatEchoTicks);
        npcInstance.chatEchoNextMsg = 100 + targetPlayer.getRandom().nextInt(60);
        ACTIVE_NPCS.add(npcInstance);

        // First message: only target sees it
        String phrase = ChatEchoNpc.GLITCHED_PHRASES[targetPlayer.getRandom().nextInt(ChatEchoNpc.GLITCHED_PHRASES.length)];
        targetPlayer.sendMessage(Text.literal("<" + targetPlayer.getGameProfile().name() + "> " + phrase), false);
        npcInstance.chatEchoMsgCount = 1;
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

        StalkerNpc stalker = new StalkerNpc(npc, targetPlayer, ModConfig.get().stalkerTicks);
        stalker.speed = ModConfig.get().stalkerSpeed;
        stalker.gracePeriod = ModConfig.get().stalkerGracePeriod;
        ACTIVE_NPCS.add(stalker);

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

        ACTIVE_NPCS.add(new StaticNpc(npc, targetPlayer, ModConfig.get().staticTicks));

        world.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 0.3f);
    }

    public static void spawnStaticNpcAt(ServerPlayerEntity target, Vec3d pos) {
        ServerWorld world = (ServerWorld) target.getEntityWorld();
        ServerPlayerEntity npc = createNpc(target, world, pos);
        double dx = target.getX() - pos.x;
        double dz = target.getZ() - pos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        npc.setYaw(yaw);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);
        renderNpc(npc, world, pos);
        ACTIVE_NPCS.add(new StaticNpc(npc, target, 200));
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.3f, 0.1f);
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

        DejaVuNpc npcInstance = new DejaVuNpc(npc, targetPlayer, ModConfig.get().dejaVuTicks);
        npcInstance.speed = ModConfig.get().dejaVuSpeed;
        npcInstance.runToPos = chaseToPos;
        ACTIVE_NPCS.add(npcInstance);

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

    public static void renderNpc(ServerPlayerEntity npc, ServerWorld world, Vec3d pos) {
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

    public static void destroyNpc(NpcBase activeNpc) {
        destroyNpc(activeNpc.npc);
    }

    public static void destroyNpc(ServerPlayerEntity npc) {
        broadcastToViewers(npc, new EntitiesDestroyS2CPacket(npc.getId()));
        broadcastToViewers(npc, new PlayerRemoveS2CPacket(List.of(npc.getUuid())));
        npc.discard();
    }

    public static void broadcastToViewers(ServerPlayerEntity npc, Packet<?> packet) {
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

        NpcBase npcInstance;
        if (type.equalsIgnoreCase("echo")) {
            npcInstance = new ChatEchoNpc(npc, targetPlayer, ModConfig.get().chatEchoTicks);
        } else if (type.equalsIgnoreCase("static")) {
            npcInstance = new StaticNpc(npc, targetPlayer, ModConfig.get().staticTicks);
        } else if (type.equalsIgnoreCase("stalker")) {
            npcInstance = new StalkerNpc(npc, targetPlayer, ModConfig.get().stalkerTicks);
        } else if (type.equalsIgnoreCase("dejavu")) {
            npcInstance = new DejaVuNpc(npc, targetPlayer, ModConfig.get().dejaVuTicks);
        } else {
            npcInstance = new ScreamerSprintNpc(npc, targetPlayer, ModConfig.get().screamerTicks);
        }
        npcInstance.gracePeriod = 20;
        ACTIVE_NPCS.add(npcInstance);

        if (npcInstance instanceof ScreamerSprintNpc) {
            world.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_GHAST_SCREAM, net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
    }

    public static void clearCommandPhantoms(ServerPlayerEntity player) {
        java.util.Iterator<NpcBase> iterator = ACTIVE_NPCS.iterator();
        while (iterator.hasNext()) {
            NpcBase npc = iterator.next();
            if (npc.targetPlayer == player) {
                destroyNpc(npc);
                iterator.remove();
            }
        }
    }
}
