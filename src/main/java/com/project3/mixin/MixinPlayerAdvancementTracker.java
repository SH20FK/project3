package com.project3.mixin;

import com.project3.Project3Mod;
import com.project3.achievement.AchievementDefinition;
import com.project3.achievement.PlayerAdvancementTrackerAccessor;
import com.project3.state.Project3State;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;

@Mixin(PlayerAdvancementTracker.class)
public class MixinPlayerAdvancementTracker implements PlayerAdvancementTrackerAccessor {

    @Shadow
    private ServerPlayerEntity owner;

    @Shadow
    private boolean dirty;

    @Override
    public void project3$setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    private static final ThreadLocal<Boolean> IN_REDIRECT = ThreadLocal.withInitial(() -> false);

    @Redirect(
        method = "sendUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
        )
    )
    private void redirectSendPacket(ServerPlayNetworkHandler handler, Packet<?> packet) {
        if (IN_REDIRECT.get()) {
            handler.sendPacket(packet);
            return;
        }
        IN_REDIRECT.set(true);
        try {
            if (packet instanceof AdvancementUpdateS2CPacket updatePacket) {
                ServerPlayerEntity player = this.owner;
                if (player != null) {
                    Project3State state = Project3State.getOrCreate(((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getServer());
                    Set<String> completed = state.getCompletedAchievements(player.getUuid());

                    // Build the set of allowed IDs (always root, plus completed ones, plus active locked ones)
                    Set<Identifier> allowedIds = new HashSet<>();
                    allowedIds.add(Identifier.of("p3", "root"));

                    var achievements = com.project3.Project3Mod.ACHIEVEMENT_MANAGER.getAchievements();
                    for (AchievementDefinition def : achievements) {
                        String id = def.getId();
                        if (completed.contains(id)) {
                            allowedIds.add(Identifier.of("p3", id));
                        } else {
                            // Check if parent is completed
                            String parentId = def.getParentId();
                            if (parentId.equals("root") || parentId.equals("p3:root") || completed.contains(parentId)) {
                                allowedIds.add(Identifier.of("p3", id));
                            }
                        }
                    }

                    // Filter toEarn list
                    List<AdvancementEntry> filteredToEarn = new ArrayList<>();
                    for (AdvancementEntry entry : updatePacket.getAdvancementsToEarn()) {
                        Identifier id = entry.id();
                        if (id.getNamespace().equals("p3")) {
                            if (allowedIds.contains(id)) {
                                filteredToEarn.add(entry);
                            }
                        } else {
                            filteredToEarn.add(entry);
                        }
                    }

                    // Filter toSetProgress map
                    Map<Identifier, AdvancementProgress> filteredProgress = new HashMap<>();
                    for (Map.Entry<Identifier, AdvancementProgress> entry : updatePacket.getAdvancementsToProgress().entrySet()) {
                        Identifier id = entry.getKey();
                        if (id.getNamespace().equals("p3")) {
                            if (allowedIds.contains(id)) {
                                filteredProgress.put(id, entry.getValue());
                            }
                        } else {
                            filteredProgress.put(id, entry.getValue());
                        }
                    }

                    // If shouldClearCurrent is true, we must ensure ALL allowed p3 advancements are present in filteredToEarn and filteredProgress
                    if (updatePacket.shouldClearCurrent()) {
                        var loader = ((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getServer().getAdvancementLoader();
                        var tracker = player.getAdvancementTracker();
                        for (Identifier id : allowedIds) {
                            AdvancementEntry entry = loader.get(id);
                            if (entry != null) {
                                // Add to filteredToEarn if not already present
                                boolean alreadyInEarn = false;
                                for (AdvancementEntry existing : filteredToEarn) {
                                    if (existing.id().equals(id)) {
                                        alreadyInEarn = true;
                                        break;
                                    }
                                }
                                if (!alreadyInEarn) {
                                    filteredToEarn.add(entry);
                                }

                                // Add to filteredProgress if not already present
                                if (!filteredProgress.containsKey(id)) {
                                    filteredProgress.put(id, tracker.getProgress(entry));
                                }
                            }
                        }
                    }

                    // Construct filtered packet
                    packet = new AdvancementUpdateS2CPacket(
                        updatePacket.shouldClearCurrent(),
                        filteredToEarn,
                        updatePacket.getAdvancementIdsToRemove(),
                        filteredProgress,
                        updatePacket.shouldShowToast()
                    );
                }
            }
            handler.sendPacket(packet);
        } finally {
            IN_REDIRECT.set(false);
        }
    }
}
