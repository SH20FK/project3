package com.project3.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

public class AIDumpAnalyzerItem extends Item {
    public AIDumpAnalyzerItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, net.minecraft.component.type.TooltipDisplayComponent component, java.util.function.Consumer<Text> tooltipConsumer, TooltipType type) {
        tooltipConsumer.accept(Text.literal("§7Анализ дампа памяти Системы."));
        tooltipConsumer.accept(Text.literal("§8[ПКМ на игроке] §eЗапустить Chat Echo"));
        tooltipConsumer.accept(Text.literal("§8[Shift+ПКМ на блоке] §eЗапустить Frozen Screenshot"));
    }
}
