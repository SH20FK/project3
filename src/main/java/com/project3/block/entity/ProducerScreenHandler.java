package com.project3.block.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Screen handler for Producer Block processing GUI.
 * Slot 0 = input, Slot 1 = output.
 * Uses data slots for automatic progress/zone sync.
 */
public class ProducerScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    public final BlockPos blockPos;
    public int progress = 0;
    public int maxProgress = 100;
    public int zone = 0;

    public ProducerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos blockPos) {
        super(com.project3.Project3Mod.PRODUCER_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.blockPos = blockPos;

        // Slot 0: Input
        this.addSlot(new Slot(inventory, 0, 26, 35));

        // Slot 1: Output (read-only)
        this.addSlot(new Slot(inventory, 1, 134, 35) {
            @Override
            public boolean canInsert(ItemStack stack) { return false; }
        });

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // Data slots for auto-sync (progress, maxProgress, zone)
        this.addProperties(new net.minecraft.screen.PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> ProducerScreenHandler.this.progress;
                    case 1 -> ProducerScreenHandler.this.maxProgress;
                    case 2 -> ProducerScreenHandler.this.zone;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> ProducerScreenHandler.this.progress = value;
                    case 1 -> ProducerScreenHandler.this.maxProgress = value;
                    case 2 -> ProducerScreenHandler.this.zone = value;
                }
            }

            @Override
            public int size() {
                return 3;
            }
        });
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (blockPos == null) return true;
        return player.squaredDistanceTo(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasStack()) {
            ItemStack stack = slot.getStack();
            original = stack.copy();

            if (slotIndex <= 1) {
                // Output/input → player inventory
                if (!this.insertItem(stack, 2, 38, true)) return ItemStack.EMPTY;
            } else {
                // Player → input slot
                if (!this.insertItem(stack, 0, 1, false)) return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return original;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public void setMaxProgress(int max) {
        this.maxProgress = max;
    }

    public void setZone(int zone) {
        this.zone = zone;
    }
}
