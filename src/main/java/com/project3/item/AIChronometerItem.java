package com.project3.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

public class AIChronometerItem extends Item {
    public AIChronometerItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, net.minecraft.component.type.TooltipDisplayComponent component, java.util.function.Consumer<Text> tooltipConsumer, TooltipType type) {
        tooltipConsumer.accept(Text.literal("§7Управление временем и фантомами."));
        tooltipConsumer.accept(Text.literal("§8[ПКМ на игроке] §eЗапустить Screamer Sprint"));
        tooltipConsumer.accept(Text.literal("§8[Shift+ПКМ на игроке] §eЗапустить Dead Scenario"));
        tooltipConsumer.accept(Text.literal("§8[Ctrl+ПКМ на игроке] §eЗапустить Déjà Vu"));
    }
}
