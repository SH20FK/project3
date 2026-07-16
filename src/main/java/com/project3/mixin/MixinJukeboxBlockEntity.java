package com.project3.mixin;

import com.project3.Project3Mod;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
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

        BlockPos pos = be.getPos();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        Box box = new Box(cx - 3.0, cy - 3.0, cz - 3.0, cx + 3.0, cy + 3.0, cz + 3.0);
        List<ServerPlayerEntity> players = world.getEntitiesByClass(
                ServerPlayerEntity.class, box, p -> p.isAlive() && !p.isRemoved());
        if (players.isEmpty()) return;

        ServerPlayerEntity nearest = players.get(0);
        double nearestDist = nearest.squaredDistanceTo(cx, cy, cz);
        for (int i = 1; i < players.size(); i++) {
            ServerPlayerEntity p = players.get(i);
            double d = p.squaredDistanceTo(cx, cy, cz);
            if (d < nearestDist) {
                nearest = p;
                nearestDist = d;
            }
        }

        nearest.incrementStat(Stats.CUSTOM.getOrCreateStat(Project3Mod.PLAY_MUSIC_DISC_STAT_ID));
    }
}
