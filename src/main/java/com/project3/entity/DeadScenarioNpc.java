package com.project3.entity;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

public class DeadScenarioNpc extends NpcBase {

    public DeadScenarioNpc(ServerPlayerEntity npc, ServerPlayerEntity targetPlayer, int ticksLeft) {
        super(npc, targetPlayer, ticksLeft);
    }

    @Override
    public boolean tick(ServerWorld world, Vec3d npcPos) {
        ServerPlayerEntity target = targetPlayer;

        if (replayFrames != null && !replayFrames.isEmpty()) {
            int totalFrames = replayFrames.size();
            int frameTicks = ticksLeft > 200 ? 200 : ticksLeft;
            float lifePercent = 1.0f - ((float) frameTicks / 200.0f);

            boolean shouldPause = (lifePercent > 0.24f && lifePercent < 0.30f) ||
                                  (lifePercent > 0.74f && lifePercent < 0.80f);

            boolean headSnap = !shouldPause && ticksLeft % 30 == 0 && world.random.nextFloat() < 0.3f;

            if (shouldPause) {
                npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getEntityPos());
                PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
                PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));

                if (ticksLeft % 4 == 0) {
                    world.spawnParticles(ParticleTypes.END_ROD,
                            npcPos.x, npcPos.y + 1.6, npcPos.z, 1, 0.1, 0.1, 0.1, 0.01);
                }
            } else if (headSnap) {
                npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getEntityPos());
                float snapYaw = npc.getHeadYaw() + 180.0f;
                npc.setHeadYaw(snapYaw);
                npc.setBodyYaw(snapYaw);
                PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (snapYaw * 256.0F / 360.0F)));
                PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
                world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                        SoundEvents.BLOCK_BONE_BLOCK_STEP, SoundCategory.HOSTILE, 0.3f, 0.2f);
            } else {
                replayIndex = Math.min(frameTicks * totalFrames / 200, totalFrames - 1);
                PhantomReplicator.PlayerFrame frame = replayFrames.get(replayIndex);

                PhantomReplicator.syncPosition(npc, frame.pos.x, frame.pos.y, frame.pos.z, frame.yaw, frame.pitch);
                npc.setHeadYaw(frame.headYaw);
                npc.setBodyYaw(frame.yaw);
                npc.setPose(frame.pose);
                npc.setSprinting(frame.isSprinting);

                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    net.minecraft.item.ItemStack targetStack = frame.mainHandItem;
                    if (slot == EquipmentSlot.OFFHAND) targetStack = frame.offHandItem;
                    else if (slot == EquipmentSlot.HEAD) targetStack = frame.helmet;
                    else if (slot == EquipmentSlot.CHEST) targetStack = frame.chestplate;
                    else if (slot == EquipmentSlot.LEGS) targetStack = frame.leggings;
                    else if (slot == EquipmentSlot.FEET) targetStack = frame.boots;
                    npc.equipStack(slot, targetStack);
                }

                PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));
                PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
                var dirtyEntries = npc.getDataTracker().getDirtyEntries();
                if (dirtyEntries != null) {
                    PhantomReplicator.broadcastToViewers(npc, new EntityTrackerUpdateS2CPacket(npc.getId(), dirtyEntries));
                }

                if (frame.isSwinging) {
                    PhantomReplicator.broadcastToViewers(npc, new EntityAnimationS2CPacket(npc, EntityAnimationS2CPacket.SWING_MAIN_HAND));
                }
            }
        }

        if (ticksLeft <= 30 && ticksLeft % 5 == 0) {
            world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 0.8f, 0.3f);
        }

        if (ticksLeft == 1) {
            npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, target.getEntityPos());
            PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
            PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));

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
            PhantomReplicator.destroyNpc(this);
            return true;
        }
        return false;
    }
}
