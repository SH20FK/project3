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

public class StalkerNpc extends NpcBase {
    public boolean stalkerWasVisible = false;
    public int stalkerEyeContactTicks = 0;
    public int stalkerBlinkTimer = 0;

    public StalkerNpc(ServerPlayerEntity npc, ServerPlayerEntity targetPlayer, int ticksLeft) {
        super(npc, targetPlayer, ticksLeft);
        this.speed = 0.35;
        this.gracePeriod = 80;
    }

    @Override
    public boolean tick(ServerWorld world, Vec3d npcPos) {
        ServerPlayerEntity target = targetPlayer;
        Vec3d targetPos = target.getEntityPos();
        Vec3d dir = targetPos.subtract(npcPos);
        double distToPlayer = dir.length();
        stalkerBlinkTimer++;

        Vec3d lookVec = target.getRotationVec(1.0f).normalize();
        Vec3d toStalker = npcPos.subtract(target.getEntityPos()).normalize();
        double dot = lookVec.dotProduct(toStalker);
        boolean isBeingLookedAt = dot > 0.95;

        if (isBeingLookedAt) {
            stalkerWasVisible = true;
            stalkerEyeContactTicks++;
            npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, targetPos);

            if (stalkerEyeContactTicks > 40 && stalkerEyeContactTicks % 20 == 0) {
                float tiltAmount = (stalkerEyeContactTicks % 80 == 0) ? 30.0f : -30.0f;
                npc.setHeadYaw(npc.getHeadYaw() + tiltAmount);
                npc.setBodyYaw(npc.getBodyYaw() + tiltAmount);
                world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                        SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.HOSTILE, 0.3f, 0.1f);
            }

            if (distToPlayer < 2.5 && !hasTriggeredScare) {
                hasTriggeredScare = true;
                return triggerJumpscare(world);
            }

            PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
            PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
        } else {
            stalkerWasVisible = false;
            stalkerEyeContactTicks = 0;

            if (distToPlayer > 4.0) {
                double approachSpeed = (distToPlayer > 15.0) ? speed * 3.0 : speed * 1.8;
                Vec3d newPos = PhantomReplicator.computeNextStep(world, npcPos, targetPos, approachSpeed);
                if (!newPos.equals(npcPos)) {
                    npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, targetPos);
                    PhantomReplicator.syncPositionAngles(npc, newPos, npc.getYaw(), npc.getPitch());
                }

                if (distToPlayer < 20.0 && stalkerBlinkTimer > 40 && world.random.nextFloat() < 0.01f) {
                    Vec3d blinkTarget = targetPos.subtract(toStalker.multiply(4.0));
                    Vec3d blinkPos = PhantomReplicator.findGroundPos(world, blinkTarget, 2);
                    if (blinkPos.squaredDistanceTo(targetPos) < distToPlayer * distToPlayer) {
                        PhantomReplicator.syncPositionAngles(npc, blinkPos, npc.getYaw(), npc.getPitch());
                        world.playSound(null, blinkPos.x, blinkPos.y, blinkPos.z,
                                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.3f, 2.0f);
                        stalkerBlinkTimer = 0;
                    }
                }
            } else {
                if (!hasTriggeredScare) {
                    hasTriggeredScare = true;
                    return triggerJumpscare(world);
                }
                double theta = world.random.nextDouble() * 2 * Math.PI;
                double farDist = 25.0 + world.random.nextDouble() * 15.0;
                Vec3d farPos = targetPos.add(Math.cos(theta) * farDist, 0, Math.sin(theta) * farDist);
                Vec3d safeFarPos = PhantomReplicator.findGroundPos(world, farPos, 5);
                PhantomReplicator.syncPositionAngles(npc, safeFarPos, npc.getYaw(), npc.getPitch());
                hasTriggeredScare = false;
                gracePeriod = 60;
            }

            npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, targetPos);
            PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
            PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
        }

        if (ticksLeft % 15 == 0 && distToPlayer < 30) {
            float volume = (float) (0.4 + Math.max(0, 1.0 - distToPlayer / 30.0) * 1.6);
            float pitch = 0.3f + (float)(distToPlayer / 30.0) * 0.3f;
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, volume, pitch);
        }

        if (!isBeingLookedAt && distToPlayer < 10.0 && ticksLeft % 30 == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_DOWN, SoundCategory.HOSTILE, 0.6f, 0.2f);
        }
        return false;
    }

    private boolean triggerJumpscare(ServerWorld world) {
        ServerPlayerEntity target = targetPlayer;
        Vec3d npcPos = npc.getEntityPos();

        target.damage(world, world.getDamageSources().magic(), 4.0f);
        target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.BLINDNESS, 100, 0, false, false, true));
        target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.NAUSEA, 60, 1, false, false, true));

        Project3Mod.schedule(0, () -> {
            if (target.isAlive() && target.networkHandler != null) {
                ServerPlayNetworking.send(target,
                        new com.project3.network.CameraRotatePayload(
                                (target.getRandom().nextFloat() - 0.5f) * 12.0f,
                                (target.getRandom().nextFloat() - 0.5f) * 6.0f));
            }
        });

        ServerPlayNetworking.send(target, new com.project3.network.ShaderFlashPayload());

        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.5f, 1.2f);

        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                npcPos.x, npcPos.y + 1.0, npcPos.z, 60, 0.5, 0.5, 0.5, 0.15);
        world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.3f);
        PhantomReplicator.destroyNpc(this);
        return true;
    }
}
