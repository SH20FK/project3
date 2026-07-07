package com.project3.network;

import com.project3.entity.PhantomReplicator;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class AdminToolUseReceiver {

    public static void handle(AdminToolUsePayload payload, ServerPlayerEntity admin) {
        // Enforce operator permission level check to prevent client-side packet spoofing exploits
        // GAMEMASTERS_CHECK corresponds to level 2 (op)
        ServerWorld adminWorld = (ServerWorld) admin.getEntityWorld();
        if (adminWorld == null) return;
        
        // Use the same permission check pattern as the original code
        if (adminWorld.getServer().getPermissionLevel(new net.minecraft.server.PlayerConfigEntry(admin.getUuid(), admin.getGameProfile().name())).getLevel().ordinal() < 2) return;

        if (payload.targetPlayerUuid() != null) {
            ServerPlayerEntity targetPlayer = adminWorld.getServer().getPlayerManager().getPlayer(payload.targetPlayerUuid());
            if (targetPlayer == null) return;

            if (payload.itemType() == 1) { // Chronometer
                if (payload.actionType() == 0) {
                    PhantomReplicator.spawnScreamerSprint(targetPlayer);
                } else if (payload.actionType() == 1) {
                    PhantomReplicator.spawnDeadScenario(targetPlayer);
                } else if (payload.actionType() == 2) {
                    PhantomReplicator.triggerDejaVu(targetPlayer);
                }
            } else if (payload.itemType() == 2) { // Dump Analyzer
                if (payload.actionType() == 0) {
                    PhantomReplicator.spawnChatEcho(targetPlayer);
                }
            }
        } else if (payload.targetBlockPos() != null && payload.itemType() == 2 && payload.actionType() == 3) { // Shift+Right click block with Dump Analyzer
            PhantomReplicator.spawnFrozenScreenshot(admin, payload.targetBlockPos());
        }
    }
}
