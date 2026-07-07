package com.project3.achievement;

import net.minecraft.util.Formatting;

/**
 * Represents the rarity of an achievement, affecting its frame color and toast.
 */
public enum AchievementRarity {
    COMMON("Обычный", Formatting.GRAY),
    UNCOMMON("Необычный", Formatting.GREEN),
    RARE("Редкий", Formatting.AQUA),
    EPIC("Эпический", Formatting.LIGHT_PURPLE),
    LEGENDARY("Легендарный", Formatting.GOLD);

    private final String displayName;
    private final Formatting formatting;

    AchievementRarity(String displayName, Formatting formatting) {
        this.displayName = displayName;
        this.formatting = formatting;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Formatting getFormatting() {
        return formatting;
    }

    public static AchievementRarity fromString(String name) {
        if (name == null) return COMMON;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}
