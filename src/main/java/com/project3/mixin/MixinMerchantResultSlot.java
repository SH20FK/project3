package com.project3.mixin;

import com.project3.state.Project3State;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.village.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TradeOutputSlot.class)
public class MixinMerchantResultSlot {

    @Shadow @Final private Merchant merchant;

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onTakeHead(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!player.getEntityWorld().isClient() && merchant instanceof VillagerEntity villager) {
            net.minecraft.util.Identifier professionId = net.minecraft.registry.Registries.VILLAGER_PROFESSION.getId(
                    villager.getVillagerData().profession().value()
            );
            if (professionId == null) return;
            String profession = professionId.toString();
            if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                Project3State state = Project3State.getOrCreate(sw.getServer());
                state.addTradedProfession(player.getUuid(), profession);
            }
        }
    }
}
