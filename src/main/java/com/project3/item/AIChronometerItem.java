package com.project3.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

public class AIChronometerItem extends Item {
    public AIChronometerItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, net.minecraft.component.type.TooltipDisplayComponent component, java.util.function.Consumer<Text> tooltipConsumer, TooltipType type) {
    }
}
