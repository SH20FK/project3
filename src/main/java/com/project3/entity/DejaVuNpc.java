package com.project3.entity;

import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

public class DejaVuNpc extends NpcBase {
    public int dejaVuLoops = 0;
    public int dejaVuMaxLoops = 3;

    public DejaVuNpc(ServerPlayerEntity npc, ServerPlayerEntity targetPlayer, int ticksLeft) {
        super(npc, targetPlayer, ticksLeft);
        this.speed = 0.45;
    }

    @Override
    public boolean tick(ServerWorld world, Vec3d npcPos) {
        Vec3d target = runToPos;
        if (target == null) {
            PhantomReplicator.destroyNpc(this);
            return true;
        }

        npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target);

        if (ticksLeft > 0.1) {
            Vec3d newPos = PhantomReplicator.computeNextStep(world, npcPos, target, speed);
            if (!newPos.equals(npcPos)) {
                npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target);
                PhantomReplicator.syncPositionAngles(npc, newPos, npc.getYaw(), npc.getPitch());
            }
        }

        npc.setSprinting(true);

        PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
        PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));

        float hbVolume = 1.0f + dejaVuLoops * 0.5f;
        if (ticksLeft % 8 == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, hbVolume, 0.8f);
        }

        boolean vanish = false;
        double distToPlayer = targetPlayer.getEntityPos().squaredDistanceTo(npcPos);
        if (distToPlayer < 3.0 * 3.0) {
            vanish = true;
        }
        if (gracePeriod <= 0) {
            Vec3d lookVec = targetPlayer.getRotationVec(1.0f).normalize();
            Vec3d toNpc = npcPos.subtract(targetPlayer.getEntityPos()).normalize();
            double dotProduct = lookVec.dotProduct(toNpc);
            if (dotProduct > 0.98) vanish = true;
        } else {
            gracePeriod--;
        }

        if (vanish) {
            PhantomReplicator.destroyNpc(this);
            world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 2.0f, 0.2f);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    npcPos.x, npcPos.y + 1.0, npcPos.z, 80, 0.5, 0.5, 0.5, 0.15);
            return true;
        }
        return false;
    }
}
