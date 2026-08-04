package com.project3.player;

import com.project3.Project3Mod;
import com.project3.network.ParanoiaPayload;
import com.project3.network.PlayerStateSyncPayload;
import com.project3.registry.ModRegistries;
import com.project3.state.Project3State;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * Manages player states: Happiness, Gloom, Unnamed Effect, and synchronizes state to the client.
 */
public final class PlayerStateManager {

    private PlayerStateManager() {}

    private static final long GLOOM_DEPTH_THRESHOLD_3 = 24000L;  // 20 min -> state 4
    private static final long GLOOM_DEPTH_THRESHOLD_4 = 72000L;  // 60 min -> state 5

    public static int computeStateIndex(ServerPlayerEntity player, Project3State state) {
        if (!state.isSeasonStarted()) return 0;
        UUID uuid = player.getUuid();
        if (state.isUnnamedEffectActive(uuid)) return 6;
        
        long happiness = state.getHappinessTicksLeft(uuid);
        if (happiness > Project3State.MAX_HAPPINESS_TICKS / 2) return 1;
        if (happiness > 0) return 2;
        
        long gloomDepth = state.getGloomDepthTicks(uuid);
        boolean inVoid = player.getEntityWorld() instanceof ServerWorld sw
                && sw.getRegistryKey() == Project3Mod.GLOOM_VOID_WORLD_KEY;
                
        if (gloomDepth < GLOOM_DEPTH_THRESHOLD_3) return 3;
        if (inVoid && gloomDepth >= GLOOM_DEPTH_THRESHOLD_4) return 5;
        
        return 4;
    }

    public static void syncPlayerState(ServerPlayerEntity player, Project3State state) {
        int stateIndex = computeStateIndex(player, state);
        
        ServerPlayNetworking.send(player, new PlayerStateSyncPayload(
                state.getHappinessTicksLeft(player.getUuid()),
                state.isGloomPermanent(player.getUuid()),
                state.getGloomTicksLeft(player.getUuid()),
                state.isUnnamedEffectActive(player.getUuid()),
                state.getProgressLevel(),
                stateIndex
        ));
        
        // Sync paranoia level based on progressLevel (0 = no paranoia, 5 = max paranoia)
        int paranoiaLevel = state.getProgressLevel();
        ServerPlayNetworking.send(player, new ParanoiaPayload(paranoiaLevel));
    }

    public static void grantHappiness(ServerPlayerEntity player, Project3State state, long ticks) {
        long current = state.getHappinessTicksLeft(player.getUuid());
        long newTotal = Math.min(current + ticks, Project3State.MAX_HAPPINESS_TICKS);
        
        state.setHappinessTicksLeft(player.getUuid(), newTotal);
        state.setGloomPermanent(player.getUuid(), false);
        state.setGloomTicksLeft(player.getUuid(), 0L);
        state.setGloomDepthTicks(player.getUuid(), 0L);
        state.setUnnamedEffectActive(player.getUuid(), false);
        player.removeStatusEffect(ModRegistries.GLOOM_EFFECT);
        
        syncPlayerState(player, state);
    }

    public static void grantGloom(ServerPlayerEntity player, Project3State state, long ticks) {
        state.setHappinessTicksLeft(player.getUuid(), 0L);
        state.setGloomTicksLeft(player.getUuid(), ticks);
        state.setGloomPermanent(player.getUuid(), false);
        player.removeStatusEffect(ModRegistries.HAPPINESS_EFFECT);
        
        syncPlayerState(player, state);
    }
}
