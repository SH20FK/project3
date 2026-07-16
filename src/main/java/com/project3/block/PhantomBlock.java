package com.project3.block;

import com.mojang.serialization.MapCodec;
import com.project3.block.entity.PhantomBlockEntity;
import net.minecraft.block.*;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.world.ServerWorld;

public class PhantomBlock extends BlockWithEntity {

    public static final MapCodec<PhantomBlock> CODEC = createCodec(settings -> new PhantomBlock());

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    public PhantomBlock() {
        super(Settings.create()
                .registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK, net.minecraft.util.Identifier.of("p3", "phantom_block")))
                .strength(0.0f, 0.0f) // Instantly breakable
                .nonOpaque()
        );
    }

    @Override
    protected net.minecraft.util.shape.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.BlockView world, BlockPos pos, ShapeContext context) {
        return net.minecraft.util.shape.VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        // Invisible so the default block model doesn't render;
        // rendering will be fully handled by the BlockEntityRenderer.
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PhantomBlockEntity(pos, state);
    }

    private void vanish(World world, BlockPos pos) {
        if (!world.isClient() && world instanceof ServerWorld sw) {
            // Only vanish if still a phantom block (prevents double-vanish)
            if (!sw.getBlockState(pos).isOf(com.project3.registry.ModRegistries.PHANTOM_BLOCK)) return;
            sw.setBlockState(pos, Blocks.AIR.getDefaultState());
            sw.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.05);
            sw.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
    }

    // Fix #6: use onEntityCollision only (onSteppedOn was causing double vanish)
    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean isClient) {
        super.onEntityCollision(state, world, pos, entity, handler, isClient);
        if (entity instanceof PlayerEntity) {
            vanish(world, pos);
        }
    }

    // Fix #6: removed onSteppedOn override — onEntityCollision handles stepping already

    // Fix #5: don't call vanish() in onBreak — it replaces with AIR and then
    // super.onBreak() tries to work with an already-removed block.
    // The block has 0 hardness so it breaks instantly — no extra vanish needed.
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        vanish(world, pos);
        return ActionResult.SUCCESS;
    }

    @Override
    protected void onProjectileHit(World world, BlockState state, BlockHitResult hit, ProjectileEntity projectile) {
        super.onProjectileHit(world, state, hit, projectile);
        vanish(world, hit.getBlockPos());
    }
}
