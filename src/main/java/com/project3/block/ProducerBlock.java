package com.project3.block;

import com.mojang.serialization.MapCodec;
import com.project3.block.entity.ProducerBlockEntity;
import com.project3.block.entity.ProducerScreenHandler;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Producer Block — an indestructible black block that spawns near the world border.
 * It causes disturbing effects on nearby players.
 */
public class ProducerBlock extends BlockWithEntity {

    public static final MapCodec<ProducerBlock> CODEC = createCodec(settings -> new ProducerBlock());

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    public ProducerBlock() {
        super(Settings.create()
                .registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK, net.minecraft.util.Identifier.of("p3", "producer_block")))
                .strength(-1.0f, 3_600_000.0f)   // Unbreakable like bedrock
                .sounds(BlockSoundGroup.STONE)
                .luminance(state -> 2)             // Slight glow so it renders dark-ish
                .nonOpaque()
        );
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ProducerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) return null;
        if (type == com.project3.Project3Mod.PRODUCER_BLOCK_ENTITY_TYPE) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<ProducerBlockEntity>) ProducerBlockEntity::serverTick;
        }
        return null;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        if (world.getBlockEntity(pos) instanceof ProducerBlockEntity be) {
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, p) -> new ProducerScreenHandler(syncId, playerInv, be.getProcessingInventory(), pos),
                    Text.literal("ПРОДЮСЕР-СТАНЦИЯ")
            ));
        }
        return ActionResult.SUCCESS;
    }
}
