package com.project3.entity;

import com.project3.Project3Mod;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

public class ScreamerSprintNpc extends NpcBase {

    public ScreamerSprintNpc(ServerPlayerEntity npc, ServerPlayerEntity targetPlayer, int ticksLeft) {
        super(npc, targetPlayer, ticksLeft);
        this.speed = 0.4;
        this.gracePeriod = 20;
    }

    @Override
    public boolean tick(ServerWorld world, Vec3d npcPos) {
        ServerPlayerEntity target = targetPlayer;
        Vec3d targetPos = (runToPos != null) ? runToPos : target.getEntityPos();
        Vec3d dir = targetPos.subtract(npcPos);
        double distance = dir.length();
        double distToPlayer = target.getEntityPos().squaredDistanceTo(npcPos);

        npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, targetPos);
        double currentSpeed = (distance < 12.0) ? speed * 2.5 : speed;

        if (distance > 0.1) {
            Vec3d newPos = PhantomReplicator.computeNextStep(world, npcPos, targetPos, currentSpeed);
            if (!newPos.equals(npcPos)) {
                PhantomReplicator.syncPositionAngles(npc, newPos, npc.getYaw(), npc.getPitch());
            }
        }

        npc.setSprinting(true);
        PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
        PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));

        int footstepInterval = (distance < 12.0) ? 2 : 4;
        if (ticksLeft % footstepInterval == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.8f, 0.9f);
        }

        if (distToPlayer < 20.0 * 20.0 && ticksLeft % (distToPlayer < 10.0 * 10.0 ? 5 : 10) == 0) {
            float hbVol = (float) Math.min(1.0, 1.0 - Math.sqrt(distToPlayer) / 30.0) + 0.3f;
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, hbVol, 1.8f);
        }

        boolean shouldVanish = false;
        if (gracePeriod <= 0) {
            if (runToPos != null) {
                double distToRunTarget = npcPos.squaredDistanceTo(runToPos);
                if (distToRunTarget < 3.0) shouldVanish = true;
            } else if (distToPlayer < 3.0 * 3.0) {
                shouldVanish = true;
            }
            if (!shouldVanish) {
                Vec3d lookVec = target.getRotationVec(1.0f).normalize();
                Vec3d toNpc = npcPos.subtract(target.getEntityPos()).normalize();
                if (lookVec.dotProduct(toNpc) > 0.98) {
                    shouldVanish = true;
                }
            }
        }

        if (shouldVanish) {
            Project3Mod.schedule(0, () -> {
                if (target.isAlive() && target.networkHandler != null) {
                    ServerPlayNetworking.send(target, new com.project3.network.CameraRotatePayload(
                            (target.getRandom().nextFloat() - 0.5f) * 6.0f,
                            (target.getRandom().nextFloat() - 0.5f) * 3.0f));
                }
            });
            PhantomReplicator.destroyNpc(this);
            world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                    SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, 2.0f, 0.8f);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    npcPos.x, npcPos.y + 1.0, npcPos.z, 40, 0.3, 0.3, 0.3, 0.1);
            return true;
        } else {
            gracePeriod--;
        }
        return false;
    }
}
