package com.project3.mixin;

import com.project3.Project3Client;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (Project3Client.GLITCHED_STATUE_IDS.contains(target.getId())) {
            Project3Client.GLITCHED_STATUE_IDS.remove(target.getId());

            target.discard();

            net.minecraft.client.world.ClientWorld world = net.minecraft.client.MinecraftClient.getInstance().world;
            var pos = target.getEntityPos();
            var rand = world.getRandom();
            for (int i = 0; i < 40; i++) {
                double px = pos.x + (rand.nextDouble() - 0.5) * 1.2;
                double py = pos.y + 0.5 + rand.nextDouble() * 1.5;
                double pz = pos.z + (rand.nextDouble() - 0.5) * 1.2;

                var block = (i % 3 == 0) ? Blocks.MAGENTA_CONCRETE :
                            (i % 3 == 1) ? Blocks.BLACK_CONCRETE :
                                           Blocks.OBSIDIAN;

                net.minecraft.client.MinecraftClient.getInstance().particleManager.addParticle(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, block.getDefaultState()),
                        px, py, pz,
                        (rand.nextDouble() - 0.5) * 0.1,
                        rand.nextDouble() * 0.1,
                        (rand.nextDouble() - 0.5) * 0.1
                );
            }

            ci.cancel();
        }
    }
}
