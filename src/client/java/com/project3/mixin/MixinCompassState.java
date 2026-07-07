package com.project3.mixin;

import com.project3.Project3Mod;
import net.minecraft.client.render.item.property.numeric.CompassState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.HeldItemContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

/**
 * Mixin that scrambles the compass needle rotation when near a ProducerBlock.
 */
@Mixin(CompassState.class)
public class MixinCompassState {

    private static final Random RANDOM = new Random();
    private static final int SCRAMBLE_RADIUS = 16;

    private static float cachedAngle = 0.0f;
    private static long lastUpdateTick = -1L;
    
    // Cache for nearest producer block to avoid iterating all blocks every frame
    private static BlockPos cachedNearestProducer = null;
    private static double cachedNearestDistSq = Double.MAX_VALUE;
    private static Vec3d lastPlayerPos = null;

    @Inject(method = "getAngle", at = @At("RETURN"), cancellable = true)
    private void onGetAngle(ItemStack stack, @Nullable ClientWorld world, int seed, @Nullable HeldItemContext context, CallbackInfoReturnable<Float> cir) {
        if (world != null && context != null && context.getEntity() != null) {
            LivingEntity entity = context.getEntity();
            Vec3d entityPos = context.getEntityPos();
            long currentTick = world.getTime();

            // Only recalculate if player moved more than 2 blocks or cache is stale
            boolean needsRecalc = lastPlayerPos == null 
                || entityPos.distanceTo(lastPlayerPos) > 2.0
                || currentTick - lastUpdateTick > 20;
            
            if (needsRecalc) {
                lastPlayerPos = entityPos;
                cachedNearestProducer = null;
                cachedNearestDistSq = Double.MAX_VALUE;
                
                double radiusSq = SCRAMBLE_RADIUS * SCRAMBLE_RADIUS;
                for (BlockPos pos : com.project3.block.entity.ProducerBlockEntity.CLIENT_PRODUCERS) {
                    double distSq = pos.getSquaredDistance(entityPos);
                    if (distSq < cachedNearestDistSq) {
                        cachedNearestDistSq = distSq;
                        cachedNearestProducer = pos;
                    }
                }
            }

            // Check if any ProducerBlock is within SCRAMBLE_RADIUS
            boolean nearProducer = cachedNearestProducer != null && cachedNearestDistSq <= SCRAMBLE_RADIUS * SCRAMBLE_RADIUS;

            if (nearProducer) {
                // If wearing pumpkin, do not scramble
                boolean isMasked = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isOf(net.minecraft.item.Items.CARVED_PUMPKIN);
                if (isMasked) {
                    return;
                }

                if (currentTick - lastUpdateTick >= 5) {
                    cachedAngle = RANDOM.nextFloat();
                    lastUpdateTick = currentTick;
                }
                cir.setReturnValue(cachedAngle);
            }
        }
    }
}
