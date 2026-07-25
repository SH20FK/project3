package com.project3.mixin;

import com.project3.state.Project3State;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects when a player enchants an item at the enchanting table.
 * Flags the player so ach_29 (enchant_level_30) can be verified.
 */
@Mixin(EnchantmentScreenHandler.class)
public class MixinEnchantmentScreenHandler {

    @Inject(method = "onTakeItemResult", at = @At("HEAD"))
    private void onTakeEnchantedItem(ServerPlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player == null || player.getEntityWorld() == null || player.getEntityWorld().isClient()) return;

        int playerLevel = player.experienceLevel;
        if (playerLevel >= 30) {
            // Store a flag in the player's persistent state indicating they
            // triggered an enchant while at level >= 30.
            var server = ((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getServer();
            if (server != null) {
                Project3State state = Project3State.getOrCreate(server);
                state.setPlayerEnchantedAtHighLevel(player.getUuid(), playerLevel);
            }
        }
    }
}
