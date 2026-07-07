package com.project3.achievement;

import net.minecraft.util.Identifier;

/**
 * Defines a custom achievement with id, title, description, rarity, trigger, and optional special effect.
 */
public class AchievementDefinition {

    private final String id;
    private final String parentId;
    private final String title;
    private final String description;
    private final AchievementRarity rarity;
    private final AchievementTrigger trigger;
    private final String specialEffect; // optional: "burn_item", "kick_error", "spawn_particles", etc.

    public AchievementDefinition(String id, String title, String description,
                                  AchievementRarity rarity, AchievementTrigger trigger) {
        this(id, "root", title, description, rarity, trigger, null);
    }

    public AchievementDefinition(String id, String parentId, String title, String description,
                                  AchievementRarity rarity, AchievementTrigger trigger) {
        this(id, parentId, title, description, rarity, trigger, null);
    }

    public AchievementDefinition(String id, String title, String description,
                                  AchievementRarity rarity, AchievementTrigger trigger,
                                  String specialEffect) {
        this(id, "root", title, description, rarity, trigger, specialEffect);
    }

    public AchievementDefinition(String id, String parentId, String title, String description,
                                  AchievementRarity rarity, AchievementTrigger trigger,
                                  String specialEffect) {
        this.id = id;
        this.parentId = parentId;
        this.title = title;
        this.description = description;
        this.rarity = rarity;
        this.trigger = trigger;
        this.specialEffect = specialEffect;
    }

    public String getId() { return id; }
    public String getParentId() { return parentId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public AchievementRarity getRarity() { return rarity; }
    public AchievementTrigger getTrigger() { return trigger; }
    public String getSpecialEffect() { return specialEffect; }
    public boolean hasSpecialEffect() { return specialEffect != null && !specialEffect.isEmpty(); }

    /**
     * Auto-detect a trigger from the description text.
     * Parses keywords like "добыть", "убить", "скрафтить", "съесть" etc.
     */
    public static AchievementTrigger autoDetectTrigger(String description) {
        String lower = description.toLowerCase();

        // Extract number from description
        int amount = 1;
        java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("(\\d+)");
        java.util.regex.Matcher numMatcher = numPattern.matcher(lower);
        if (numMatcher.find()) {
            amount = Integer.parseInt(numMatcher.group(1));
        }

        // Keywords mapping
        if (lower.contains("добыть") || lower.contains("получить") || lower.contains("подобрать")) {
            String itemId = extractItemId(lower);
            return new AchievementTrigger(AchievementTrigger.Type.INVENTORY_ITEM, itemId, amount);
        }
        if (lower.contains("убить") || lower.contains("победить")) {
            String entityId = extractEntityId(lower);
            return new AchievementTrigger(AchievementTrigger.Type.KILL_ENTITY, entityId, amount);
        }
        if (lower.contains("скрафтить") || lower.contains("создать")) {
            String itemId = extractItemId(lower);
            return new AchievementTrigger(AchievementTrigger.Type.CRAFT_ITEM, itemId, amount);
        }
        if (lower.contains("съесть") || lower.contains("отведать") || lower.contains("поешьте")) {
            String itemId = extractItemId(lower);
            return new AchievementTrigger(AchievementTrigger.Type.USE_ITEM, itemId, amount);
        }
        if (lower.contains("использовать") || lower.contains("послушать") || lower.contains("прокатиться")) {
            String itemId = extractItemId(lower);
            return new AchievementTrigger(AchievementTrigger.Type.USE_ITEM, itemId, amount);
        }
        if (lower.contains("открыть")) {
            String itemId = extractItemId(lower);
            return new AchievementTrigger(AchievementTrigger.Type.USE_ITEM, itemId, amount);
        }
        if (lower.contains("найти") || lower.contains("обнаружить")) {
            // Location-based triggers default to custom for now
            return new AchievementTrigger(AchievementTrigger.Type.CUSTOM, "location", 1);
        }
        if (lower.contains("зачаровать")) {
            return new AchievementTrigger(AchievementTrigger.Type.CUSTOM, "enchant", amount);
        }
        if (lower.contains("приручить")) {
            return new AchievementTrigger(AchievementTrigger.Type.CUSTOM, "tame", 1);
        }
        if (lower.contains("обмундировать")) {
            return new AchievementTrigger(AchievementTrigger.Type.CUSTOM, "trim", 1);
        }

        // Default fallback
        return new AchievementTrigger(AchievementTrigger.Type.CUSTOM, "manual", 1);
    }

    private static String extractItemId(String lower) {
        // Simple keyword-to-item mapping
        if (lower.contains("дерево") || lower.contains("древесина")) return "minecraft:oak_log";
        if (lower.contains("верстак")) return "minecraft:crafting_table";
        if (lower.contains("булыжник")) return "minecraft:cobblestone";
        if (lower.contains("уголь")) return "minecraft:coal";
        if (lower.contains("медная руда") || lower.contains("меди")) return "minecraft:copper_ingot";
        if (lower.contains("хлеб")) return "minecraft:bread";
        if (lower.contains("щит")) return "minecraft:shield";
        if (lower.contains("лазурит")) return "minecraft:lapis_lazuli";
        if (lower.contains("редстоун") && lower.contains("факел")) return "minecraft:redstone_torch";
        if (lower.contains("алмазная мотыга")) return "minecraft:diamond_hoe";
        if (lower.contains("аметист")) return "minecraft:amethyst_shard";
        if (lower.contains("пластинка")) return "minecraft:music_disc_13";
        if (lower.contains("изумрудная руда")) return "minecraft:emerald_ore";
        if (lower.contains("цветущая азалия")) return "minecraft:flowering_azalea";
        if (lower.contains("тнт")) return "minecraft:tnt";
        if (lower.contains("морковь")) return "minecraft:carrot";
        if (lower.contains("зачарованное золотое яблоко")) return "minecraft:enchanted_golden_apple";
        if (lower.contains("губка")) return "minecraft:wet_sponge";
        if (lower.contains("осколок эха")) return "minecraft:echo_shard";
        if (lower.contains("молот")) return "minecraft:mace";
        if (lower.contains("алмазов")) return "minecraft:diamond";
        if (lower.contains("алмазный блок")) return "minecraft:diamond_block";
        if (lower.contains("незерит")) return "minecraft:netherite_ingot";
        if (lower.contains("адский камень") || lower.contains("незеррак")) return "minecraft:netherrack";
        if (lower.contains("кварц")) return "minecraft:quartz";
        if (lower.contains("светокамень")) return "minecraft:glowstone";
        if (lower.contains("золотой самородок")) return "minecraft:gold_nugget";
        if (lower.contains("пурпур")) return "minecraft:purpur_block";
        if (lower.contains("камень края") || lower.contains("энд стоун")) return "minecraft:end_stone";
        if (lower.contains("грязь")) return "minecraft:dirt";
        if (lower.contains("кровать")) return "minecraft:red_bed";
        return "minecraft:stone";
    }

    private static String extractEntityId(String lower) {
        if (lower.contains("зомби")) return "minecraft:zombie";
        if (lower.contains("ифрит") || lower.contains("блейз")) return "minecraft:blaze";
        if (lower.contains("эндермен")) return "minecraft:enderman";
        if (lower.contains("шалкер")) return "minecraft:shulker";
        if (lower.contains("дракон")) return "minecraft:ender_dragon";
        if (lower.contains("слизень")) return "minecraft:slime";
        if (lower.contains("варден")) return "minecraft:warden";
        if (lower.contains("враг")) return "minecraft:zombie";
        if (lower.contains("мирных")) return "minecraft:sheep";
        return "minecraft:zombie";
    }

    public String getIconItemId() {
        if (trigger == null) return "minecraft:book";
        return trigger.getIconItemId();
    }

    @Override
    public String toString() {
        return "Achievement[" + id + "] " + title + " (" + rarity.getDisplayName() + ")";
    }
}
