package com.project3.entity;

import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class StaticNpc extends NpcBase {

    public StaticNpc(ServerPlayerEntity npc, ServerPlayerEntity targetPlayer, int ticksLeft) {
        super(npc, targetPlayer, ticksLeft);
    }

    @Override
    public boolean tick(ServerWorld world, Vec3d npcPos) {
        ServerPlayerEntity target = targetPlayer;
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

        if (isBeingLookedAt && distToPlayer < 12.0 && ticksLeft % 100 == 0 && !hasTriggeredScare) {
            hasTriggeredScare = true;
            Vec3d behindPos = targetPos.subtract(lookVec.multiply(3.0));
            Vec3d safeBehind = PhantomReplicator.findGroundPos(world, behindPos, 2);
            PhantomReplicator.spawnStaticNpcAt(target, safeBehind);
            target.sendMessage(Text.literal("§7..."), false);
        }

        if (!isBeingLookedAt && distToPlayer > 4.0) {
            Vec3d newPos = PhantomReplicator.computeNextStep(world, npcPos, targetPos, 0.15);
            if (!newPos.equals(npcPos)) {
                PhantomReplicator.syncPositionAngles(npc, newPos, yaw, pitch);
            }
        }

        if (isBeingLookedAt && distToPlayer < 12.0 && ticksLeft % 5 == 0) {
            int finalYaw = (int) yaw;
            int finalPitch = (int) pitch;
            com.project3.Project3Mod.schedule(0, () -> {
                if (target.isAlive() && target.networkHandler != null) {
                    float driftYaw = (finalYaw - target.getYaw()) * 0.02f;
                    float driftPitch = (finalPitch - target.getPitch()) * 0.02f;
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target,
                            new com.project3.network.CameraRotatePayload(
                                    driftYaw * 0.5f, driftPitch * 0.3f));
                }
            });
        }

        if (isBeingLookedAt && ticksLeft % 30 == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 0.4f, 0.1f);
        }

        if (!isBeingLookedAt && distToPlayer < 20 && ticksLeft % 40 == 0) {
            float volume = (float) (0.2 + (20.0 - distToPlayer) / 20.0 * 0.8);
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, volume, 0.15f);
        }

        if (ticksLeft % 10 == 0) {
            world.spawnParticles(ParticleTypes.MYCELIUM,
                    npcPos.x, npcPos.y + 1.5, npcPos.z, 1, 0.3, 0.5, 0.3, 0.01);
        }
        if (ticksLeft % 3 == 0 && world.random.nextFloat() < 0.1f) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    npcPos.x + (world.random.nextDouble() - 0.5) * 0.5,
                    npcPos.y + 1.0 + world.random.nextDouble() * 0.5,
                    npcPos.z + (world.random.nextDouble() - 0.5) * 0.5,
                    1, 0, 0, 0, 0.01);
        }

        if (isBeingLookedAt && distToPlayer < 3.0) {
            PhantomReplicator.destroyNpc(this);
            world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                    SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 0.5f);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                    npcPos.x, npcPos.y + 1.0, npcPos.z, 30, 0.3, 0.5, 0.3, 0.05);
            return true;
        } else {
            PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
            PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
            var dirtyEntries = npc.getDataTracker().getDirtyEntries();
            if (dirtyEntries != null) {
                PhantomReplicator.broadcastToViewers(npc, new EntityTrackerUpdateS2CPacket(npc.getId(), dirtyEntries));
            }
        }
        return false;
    }
}
