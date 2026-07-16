package com.project3.mixin;

import com.project3.Project3Mod;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JukeboxBlock.class)
public class MixinJukeboxBlock {

    @Inject(
        method = "onUseWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/JukeboxBlock;setRecord(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/item/ItemStack;)V"
        )
    )
    private void onInsertDisc(ItemStack stack, net.minecraft.block.BlockState state, World world, BlockPos pos, PlayerEntity user, Hand hand, net.minecraft.util.hit.BlockHitResult hit, CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        if (user instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.incrementStat(Stats.CUSTOM.getOrCreateStat(Project3Mod.PLAY_MUSIC_DISC_STAT_ID));
        }
    }
}
