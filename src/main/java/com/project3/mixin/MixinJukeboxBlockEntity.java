package com.project3.mixin;

import com.project3.Project3Mod;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(JukeboxBlockEntity.class)
public class MixinJukeboxBlockEntity {

    @Inject(method = "setDisc", at = @At("HEAD"))
    private void onSetDisc(ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        JukeboxBlockEntity be = (JukeboxBlockEntity) (Object) this;
        if (be.getWorld() == null || be.getWorld().isClient()) return;
        if (!(be.getWorld() instanceof ServerWorld world)) return;

        Box box = Box.of(be.getPos().toCenterPos(), 6.0, 6.0, 6.0);
        List<ServerPlayerEntity> players = world.getEntitiesByClass(
                ServerPlayerEntity.class, box, p -> p.isAlive() && !p.isRemoved());
        if (players.isEmpty()) return;

        ServerPlayerEntity nearest = players.get(0);
        double nearestDist = nearest.squaredDistanceTo(be.getPos().toCenterPos());
        for (ServerPlayerEntity p : players) {
            double d = p.squaredDistanceTo(be.getPos().toCenterPos());
            if (d < nearestDist) {
                nearest = p;
                nearestDist = d;
            }
        }

        nearest.incrementStat(Stats.CUSTOM.getOrCreateStat(Project3Mod.PLAY_MUSIC_DISC_STAT_ID));
    }
}
