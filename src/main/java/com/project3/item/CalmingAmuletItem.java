package com.project3.item;

import com.project3.dread.DreadManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Calming Amulet — reduces Dread over time when held in offhand.
 * Durability 200, loses 1 durability per 10 seconds of use.
 */
public class CalmingAmuletItem extends Item {

    public CalmingAmuletItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return ActionResult.PASS;
        if (user instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.literal("§6[Амулет]§r: Активен. Держи в offhand для снижения страха (-3/10 сек)."), false);
        }
        return ActionResult.SUCCESS;
    }

    public static boolean tickAmulet(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CalmingAmuletItem)) return false;

        DreadManager.addDread(player, -3);

        stack.damage(1, player, EquipmentSlot.OFFHAND);

        if (stack.getDamage() >= stack.getMaxDamage()) {
            player.sendMessage(Text.literal("§8[Амулет]§r: §cАмулет рассыпался в прах..."), false);
            player.playSound(net.minecraft.sound.SoundEvents.ENTITY_ITEM_BREAK.value(), 1.0f, 1.0f);
            stack.decrement(1);
        }

        return true;
    }
}
