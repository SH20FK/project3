package com.project3.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public class MixinAbstractFurnaceBlockEntity {

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;craftRecipe(Lnet/minecraft/registry/DynamicRegistryManager;Lnet/minecraft/recipe/RecipeEntry;Lnet/minecraft/recipe/input/SingleStackRecipeInput;Lnet/minecraft/util/collection/DefaultedList;I)Z", shift = At.Shift.AFTER))
    private static void afterCraftRecipe(ServerWorld world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        if (world != null && !world.isClient()) {
            net.minecraft.server.network.ServerPlayerEntity nearestPlayer = null;
            double minDist = 64.0; // 8 blocks squared
            for (net.minecraft.entity.player.PlayerEntity player : world.getPlayers()) {
                if (player instanceof net.minecraft.server.network.ServerPlayerEntity spe) {
                    double dist = spe.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (dist < minDist) {
                        minDist = dist;
                        nearestPlayer = spe;
                    }
                }
            }
            if (nearestPlayer != null) {
                com.project3.state.Project3State pState = com.project3.state.Project3State.getOrCreate(world.getServer());
                if (pState.isUnnamedEffectActive(nearestPlayer.getUuid())) {
                    pState.setUnnamedEffectActive(nearestPlayer.getUuid(), false);
                    nearestPlayer.sendMessage(net.minecraft.text.Text.literal("§a[Система] Безымянный эффект развеялся после переплавки."), false);
                }
            }
        }
    }
}
