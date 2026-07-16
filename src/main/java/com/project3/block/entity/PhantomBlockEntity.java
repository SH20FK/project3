package com.project3.block.entity;

import com.project3.registry.ModRegistries;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class PhantomBlockEntity extends BlockEntity {

    private BlockState replacedState = Blocks.STONE.getDefaultState();

    public PhantomBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.PHANTOM_BLOCK_ENTITY_TYPE, pos, state);
    }

    public BlockState getReplacedState() {
        return replacedState;
    }

    public void setReplacedState(BlockState state) {
        this.replacedState = state;
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    protected void writeData(net.minecraft.storage.WriteView view) {
        super.writeData(view);
        if (replacedState != null) {
            view.put("replaced_state", BlockState.CODEC, replacedState);
        }
    }

    @Override
    protected void readData(net.minecraft.storage.ReadView view) {
        super.readData(view);
        replacedState = view.read("replaced_state", BlockState.CODEC).orElse(Blocks.STONE.getDefaultState());
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
