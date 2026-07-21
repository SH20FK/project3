package com.project3.entity;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public abstract class NpcBase {
    public final ServerPlayerEntity npc;
    public final ServerPlayerEntity targetPlayer;
    public int ticksLeft;
    public double speed = 0.25;
    public List<PhantomReplicator.PlayerFrame> replayFrames;
    public int replayIndex = 0;
    public Vec3d runToPos;
    public int gracePeriod = 40;
    public boolean hasTriggeredScare = false;

    protected NpcBase(ServerPlayerEntity npc, ServerPlayerEntity targetPlayer, int ticksLeft) {
        this.npc = npc;
        this.targetPlayer = targetPlayer;
        this.ticksLeft = ticksLeft;
    }

    public abstract boolean tick(ServerWorld world, Vec3d npcPos);
}
