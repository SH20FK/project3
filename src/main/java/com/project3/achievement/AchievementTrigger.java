package com.project3.achievement;

import com.project3.state.Project3State;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/**
 * Defines how an achievement is triggered and checked.
 */
public class AchievementTrigger {

    public enum Type {
        INVENTORY_ITEM,   // Have item(s) in inventory
        KILL_ENTITY,        // Kill entity type(s)
        USE_ITEM,           // Use/eat item (by stat)
        CRAFT_ITEM,         // Craft item (by stat)
        MINE_BLOCK,         // Mine block (by stat)
        BREAK_BLOCK,        // Break item (by stat)
        STAT,               // Any vanilla stat
        CUSTOM              // Custom check handled elsewhere
    }

    private final Type type;
    private final String target;      // item id, entity id, or stat name
    private final int threshold;        // amount required
    private final boolean exact;        // if true, requires exact amount (not >=)

    // ─── Static caches to avoid O(N) registry iteration every tick ──────────

    /** All monster entity types, cached once at class load. */
    private static final List<EntityType<?>> MONSTER_TYPES;
    /** All food items, cached once at class load. */
    private static final List<Item> FOOD_ITEMS;
    /** All music disc items, cached once at class load. */
    private static final Set<Item> MUSIC_DISCS;
    /** All terracotta item paths, cached once at class load. */
    private static final Set<String> TERRACOTTA_PATHS;

    static {
        List<EntityType<?>> monsters = new ArrayList<>();
        List<Item> foods = new ArrayList<>();
        Set<Item> discs = new LinkedHashSet<>();
        Set<String> terracotta = new LinkedHashSet<>();

        for (EntityType<?> t : Registries.ENTITY_TYPE) {
            if (t.getSpawnGroup() == SpawnGroup.MONSTER) {
                monsters.add(t);
            }
        }
        for (Item item : Registries.ITEM) {
            ItemStack def = item.getDefaultStack();
            if (def.contains(net.minecraft.component.DataComponentTypes.FOOD)) {
                foods.add(item);
            }
            if (def.contains(net.minecraft.component.DataComponentTypes.JUKEBOX_PLAYABLE)) {
                discs.add(item);
            }
            String path = Registries.ITEM.getId(item).getPath();
            if (path.equals("terracotta") || path.endsWith("_terracotta")) {
                terracotta.add(path);
            }
        }

        MONSTER_TYPES = Collections.unmodifiableList(monsters);
        FOOD_ITEMS = Collections.unmodifiableList(foods);
        MUSIC_DISCS = Collections.unmodifiableSet(discs);
        TERRACOTTA_PATHS = Collections.unmodifiableSet(terracotta);
    }

    /** Returns total food items used (eaten) by the player across all food types. */
    public static int getTotalFoodEaten(ServerPlayerEntity player) {
        int total = 0;
        for (Item item : FOOD_ITEMS) {
            total += player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));
        }
        return total;
    }

    public AchievementTrigger(Type type, String target, int threshold) {
        this(type, target, threshold, false);
    }

    public AchievementTrigger(Type type, String target, int threshold, boolean exact) {
        this.type = type;
        this.target = target;
        this.threshold = threshold;
        this.exact = exact;
    }

    public Type getType() { return type; }
    public String getTarget() { return target; }
    public int getThreshold() { return threshold; }
    public boolean isExact() { return exact; }

    /**
     * Check if the given player has met this trigger's condition.
     */
    public boolean check(ServerPlayerEntity player) {
        return switch (type) {
            case INVENTORY_ITEM -> checkInventory(player);
            case KILL_ENTITY -> checkKill(player);
            case USE_ITEM -> checkItemStat(player, Stats.USED);
            case CRAFT_ITEM -> checkItemStat(player, Stats.CRAFTED);
            case MINE_BLOCK -> checkBlockStat(player, Stats.MINED);
            case BREAK_BLOCK -> checkItemStat(player, Stats.BROKEN);
            case STAT -> checkGenericStat(player);
            case CUSTOM -> checkCustom(player);
        };
    }

    private boolean checkCustom(ServerPlayerEntity player) {
        String[] parts = target.split(":");
        String name = parts[0];
        return switch (name) {
            case "any_log" -> {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        if (stack.isIn(net.minecraft.registry.tag.ItemTags.LOGS)) {
                            yield true;
                        }
                        String path = Registries.ITEM.getId(stack.getItem()).getPath();
                        if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae")) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            case "open_inventory" -> {
                yield safeCustomStatCheck(player, com.project3.Project3Mod.OPEN_INVENTORY_STAT_ID, threshold);
            }
            case "all_tools" -> {
                boolean hasSword = false;
                boolean hasShovel = false;
                boolean hasPickaxe = false;
                boolean hasAxe = false;
                boolean hasHoe = false;
                boolean hasIronSpear = false;
                Item ironSpear = Registries.ITEM.get(Identifier.of("minecraft", "iron_spear"));
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (stack.isEmpty()) continue;
                    Item item = stack.getItem();
                    if (item.getRegistryEntry().isIn(net.minecraft.registry.tag.ItemTags.SWORDS)) hasSword = true;
                    else if (item.getRegistryEntry().isIn(net.minecraft.registry.tag.ItemTags.SHOVELS)) hasShovel = true;
                    else if (item.getRegistryEntry().isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)) hasPickaxe = true;
                    else if (item.getRegistryEntry().isIn(net.minecraft.registry.tag.ItemTags.AXES)) hasAxe = true;
                    else if (item.getRegistryEntry().isIn(net.minecraft.registry.tag.ItemTags.HOES)) hasHoe = true;
                    if (item == ironSpear) hasIronSpear = true;
                }
                yield hasSword && hasShovel && hasPickaxe && hasAxe && hasHoe && hasIronSpear;
            }
            case "smelt_cobbled_deepslate" -> {
                yield player.getInventory().count(Items.DEEPSLATE) >= threshold;
            }
            case "find_mineshaft" -> {
                yield isPlayerInStructure(player, net.minecraft.registry.RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of("minecraft", "mineshaft"))) ||
                      isPlayerInStructure(player, net.minecraft.registry.RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of("minecraft", "mineshaft_badlands")));
            }
            case "ride_minecart" -> {
                yield player.getVehicle() instanceof net.minecraft.entity.vehicle.MinecartEntity;
            }
            // Fix #1: properly exit all 3 nested loops using labelled break logic (via boolean flag)
            case "find_spawner" -> {
                BlockPos pos = player.getBlockPos();
                boolean found = false;
                outer:
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            if (player.getEntityWorld().getBlockState(pos.add(dx, dy, dz)).isOf(Blocks.SPAWNER)) {
                                found = true;
                                break outer;
                            }
                        }
                    }
                }
                yield found;
            }
            case "play_music_disc" -> {
                yield safeCustomStatCheck(player, com.project3.Project3Mod.PLAY_MUSIC_DISC_STAT_ID, threshold);
            }
            case "touch_bedrock" -> {
                BlockPos pos = player.getBlockPos();
                boolean found = false;
                outer:
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (player.getEntityWorld().getBlockState(pos.add(dx, dy, dz)).isOf(Blocks.BEDROCK)) {
                                found = true;
                                break outer;
                            }
                        }
                    }
                }
                yield found;
            }
            case "brush_suspicious" -> {
                yield safeCustomStatCheck(player, Identifier.of("minecraft", "clean_block"), threshold);
            }
            case "ride_pig" -> {
                yield player.getVehicle() instanceof net.minecraft.entity.passive.PigEntity &&
                      (player.getMainHandStack().isOf(Items.CARROT_ON_A_STICK) || player.getOffHandStack().isOf(Items.CARROT_ON_A_STICK));
            }
            case "trim_armor" -> {
                int trimmedCount = 0;
                for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{net.minecraft.entity.EquipmentSlot.FEET, net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.HEAD}) {
                    ItemStack armor = player.getEquippedStack(slot);
                    if (!armor.isEmpty() && armor.contains(net.minecraft.component.DataComponentTypes.TRIM)) {
                        trimmedCount++;
                    }
                }
                yield trimmedCount >= 4;
            }
            // The required level is evaluated in AchievementManager using the
            // pre-enchant experience level and the enchant-item stat delta.
            case "enchant_level_30" -> false;
            case "tame_cat" -> {
                List<net.minecraft.entity.passive.CatEntity> cats = player.getEntityWorld().getEntitiesByClass(
                        net.minecraft.entity.passive.CatEntity.class,
                        player.getBoundingBox().expand(16.0),
                        cat -> cat.isTamed() && cat.getOwnerReference() != null && player.getUuid().equals(cat.getOwnerReference().getUuid())
                );
                yield !cats.isEmpty();
            }
            case "silk_touch" -> {
                if (!(player.getEntityWorld() instanceof ServerWorld sw)) yield false;
                var registryOpt = sw.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
                if (registryOpt.isEmpty()) yield false;
                var registry = registryOpt.get();
                var silkTouchOpt = registry.getEntry(net.minecraft.enchantment.Enchantments.SILK_TOUCH.getValue());
                if (silkTouchOpt.isEmpty()) yield false;
                var silkTouch = silkTouchOpt.get();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (stack.isEmpty()) continue;
                    if (net.minecraft.enchantment.EnchantmentHelper.getLevel(silkTouch, stack) > 0) {
                        yield true;
                    }
                }
                yield false;
            }
            case "open_trial_chamber" -> {
                yield isPlayerInStructure(player, net.minecraft.registry.RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of("minecraft", "trial_chambers")));
            }
            case "open_buried_treasure" -> {
                yield isPlayerInStructure(player, net.minecraft.registry.RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of("minecraft", "buried_treasure")));
            }
            case "find_igloo_lab" -> {
                yield isPlayerInStructure(player, net.minecraft.registry.RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of("minecraft", "igloo"))) && player.getY() < 60;
            }
            case "find_mansion" -> {
                yield isPlayerInStructure(player, net.minecraft.registry.RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of("minecraft", "mansion")));
            }
            case "player_head" -> {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    if (player.getInventory().getStack(i).isOf(Items.PLAYER_HEAD)) {
                        yield true;
                    }
                }
                yield false;
            }
            case "kill_monster" -> {
                int count = 0;
                for (EntityType<?> t : MONSTER_TYPES) {
                    count += player.getStatHandler().getStat(Stats.KILLED.getOrCreateStat(t));
                }
                yield count >= threshold;
            }
            case "kill_passive" -> {
                int count = 0;
                EntityType<?>[] passiveTypes = {
                    EntityType.SHEEP, EntityType.COW, EntityType.PIG, EntityType.CHICKEN,
                    EntityType.RABBIT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
                    EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.GOAT, EntityType.CAT,
                    EntityType.WOLF, EntityType.PARROT, EntityType.OCELOT, EntityType.FROG,
                    EntityType.SNIFFER, EntityType.CAMEL, EntityType.ARMADILLO, EntityType.BAT,
                    EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.AXOLOTL, EntityType.BEE,
                    EntityType.DOLPHIN, EntityType.FOX, EntityType.PANDA, EntityType.POLAR_BEAR,
                    EntityType.STRIDER, EntityType.TURTLE, EntityType.VILLAGER, EntityType.WANDERING_TRADER
                };
                for (EntityType<?> t : passiveTypes) {
                    count += player.getStatHandler().getStat(Stats.KILLED.getOrCreateStat(t));
                }
                yield count >= threshold;
            }
            case "mine_emerald" -> {
                int count = player.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.EMERALD_ORE)) +
                            player.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.DEEPSLATE_EMERALD_ORE));
                yield count >= threshold;
            }
            case "eat_food" -> {
                int total = 0;
                for (Item item : FOOD_ITEMS) {
                    total += player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));
                }
                yield total >= threshold;
            }
            case "rename_item" -> {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty() && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)) {
                        yield true;
                    }
                }
                yield false;
            }
            case "dolphin_grace" -> {
                yield player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.DOLPHINS_GRACE);
            }
            case "rename_entity" -> {
                Stat<?> stat = Stats.USED.getOrCreateStat(Items.NAME_TAG);
                yield player.getStatHandler().getStat(stat) >= threshold;
            }
            case "all_woods" -> {
                boolean hasOak = false, hasSpruce = false, hasBirch = false, hasJungle = false;
                boolean hasAcacia = false, hasDarkOak = false, hasMangrove = false, hasCherry = false;
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (stack.isEmpty()) continue;
                    Item item = stack.getItem();
                    if (item == Items.OAK_LOG || item == Items.OAK_WOOD) hasOak = true;
                    else if (item == Items.SPRUCE_LOG || item == Items.SPRUCE_WOOD) hasSpruce = true;
                    else if (item == Items.BIRCH_LOG || item == Items.BIRCH_WOOD) hasBirch = true;
                    else if (item == Items.JUNGLE_LOG || item == Items.JUNGLE_WOOD) hasJungle = true;
                    else if (item == Items.ACACIA_LOG || item == Items.ACACIA_WOOD) hasAcacia = true;
                    else if (item == Items.DARK_OAK_LOG || item == Items.DARK_OAK_WOOD) hasDarkOak = true;
                    else if (item == Items.MANGROVE_LOG || item == Items.MANGROVE_WOOD) hasMangrove = true;
                    else if (item == Items.CHERRY_LOG || item == Items.CHERRY_WOOD) hasCherry = true;
                }
                yield hasOak && hasSpruce && hasBirch && hasJungle && hasAcacia && hasDarkOak && hasMangrove && hasCherry;
            }
            case "all_music_discs" -> {
                if (MUSIC_DISCS.isEmpty()) yield false;
                Set<Item> foundDiscs = new HashSet<>();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty() && MUSIC_DISCS.contains(stack.getItem())) {
                        foundDiscs.add(stack.getItem());
                    }
                }
                yield foundDiscs.size() >= MUSIC_DISCS.size();
            }
            case "suicide" -> {
                int deaths;
                try {
                    deaths = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));
                } catch (Exception e) {
                    deaths = 0;
                }
                yield deaths >= threshold && !player.isRemoved() && player.isAlive() && player.getHealth() > 0;
            }
            case "give_allay_flower" -> {
                yield safeCustomStatCheck(player, com.project3.Project3Mod.GIVE_ALLAY_FLOWER_STAT_ID, threshold);
            }
            case "activate_conduit" -> {
                yield player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.CONDUIT_POWER);
            }
            case "pottery_sherds" -> {
                Set<Item> uniqueSherds = new HashSet<>();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        Item item = stack.getItem();
                        String path = Registries.ITEM.getId(item).getPath();
                        if (path.endsWith("_pottery_sherd")) {
                            uniqueSherds.add(item);
                        }
                    }
                }
                yield uniqueSherds.size() >= threshold;
            }
            case "mob_head" -> {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        Item item = stack.getItem();
                        if (item == Items.WITHER_SKELETON_SKULL ||
                            item == Items.SKELETON_SKULL ||
                            item == Items.ZOMBIE_HEAD ||
                            item == Items.CREEPER_HEAD ||
                            item == Items.PIGLIN_HEAD ||
                            item == Items.DRAGON_HEAD) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            case "meet_mooshroom" -> {
                List<net.minecraft.entity.passive.MooshroomEntity> list = player.getEntityWorld().getEntitiesByClass(
                        net.minecraft.entity.passive.MooshroomEntity.class,
                        player.getBoundingBox().expand(16.0),
                        entity -> true
                );
                yield !list.isEmpty();
            }
            case "experience_level_45" -> {
                yield player.experienceLevel >= 45;
            }
            case "mace_kill_50_blocks" -> {
                yield safeCustomStatCheck(player, com.project3.Project3Mod.MACE_KILL_50_BLOCKS_STAT_ID, threshold);
            }
            case "use_any_potion" -> {
                int count = player.getStatHandler().getStat(Stats.USED.getOrCreateStat(Items.POTION)) +
                            player.getStatHandler().getStat(Stats.USED.getOrCreateStat(Items.SPLASH_POTION)) +
                            player.getStatHandler().getStat(Stats.USED.getOrCreateStat(Items.LINGERING_POTION));
                yield count >= threshold;
            }
            case "four_banner_patterns" -> {
                Set<Item> uniquePatterns = new HashSet<>();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        Item item = stack.getItem();
                        String path = Registries.ITEM.getId(item).getPath();
                        if (path.endsWith("_banner_pattern")) {
                            uniquePatterns.add(item);
                        }
                    }
                }
                yield uniquePatterns.size() >= threshold;
            }
            case "all_corals" -> {
                boolean hasTube = false, hasBrain = false, hasBubble = false, hasFire = false, hasHorn = false;
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        String path = Registries.ITEM.getId(stack.getItem()).getPath();
                        if (path.contains("tube_coral")) hasTube = true;
                        else if (path.contains("brain_coral")) hasBrain = true;
                        else if (path.contains("bubble_coral")) hasBubble = true;
                        else if (path.contains("fire_coral")) hasFire = true;
                        else if (path.contains("horn_coral")) hasHorn = true;
                    }
                }
                yield hasTube && hasBrain && hasBubble && hasFire && hasHorn;
            }
            case "play_instrument" -> {
                Stat<?> stat = Stats.USED.getOrCreateStat(Items.GOAT_HORN);
                yield player.getStatHandler().getStat(stat) >= threshold;
            }
            case "summon_snow_golem" -> {
                List<net.minecraft.entity.passive.SnowGolemEntity> golems = player.getEntityWorld().getEntitiesByClass(
                        net.minecraft.entity.passive.SnowGolemEntity.class,
                        player.getBoundingBox().expand(8.0),
                        golem -> true
                );
                yield !golems.isEmpty();
            }
            case "shoot_firework_crossbow" -> {
                yield safeCustomStatCheck(player, com.project3.Project3Mod.SHOOT_FIREWORK_CROSSBOW_STAT_ID, threshold);
            }
            case "all_terracotta" -> {
                Set<String> foundTerracotta = new HashSet<>();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        String path = Registries.ITEM.getId(stack.getItem()).getPath();
                        if (TERRACOTTA_PATHS.contains(path)) {
                            foundTerracotta.add(path);
                        }
                    }
                }
                yield foundTerracotta.size() >= TERRACOTTA_PATHS.size();
            }
            case "craft_all_rails_minecarts" -> {
                Item[] items = {
                    Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL,
                    Items.MINECART, Items.CHEST_MINECART, Items.FURNACE_MINECART, Items.TNT_MINECART, Items.HOPPER_MINECART
                };
                for (Item item : items) {
                    if (player.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(item)) <= 0) {
                        yield false;
                    }
                }
                yield true;
            }
            case "trim_different_armor" -> {
                Set<Identifier> uniquePatterns = new HashSet<>();
                for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{net.minecraft.entity.EquipmentSlot.FEET, net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.HEAD}) {
                    ItemStack armor = player.getEquippedStack(slot);
                    if (!armor.isEmpty()) {
                        net.minecraft.item.equipment.trim.ArmorTrim trim = armor.get(net.minecraft.component.DataComponentTypes.TRIM);
                        if (trim != null) {
                            trim.pattern().getKey().ifPresent(key -> uniquePatterns.add(key.getValue()));
                        }
                    }
                }
                yield uniquePatterns.size() >= 4;
            }
            case "all_fish_buckets" -> {
                Item[] required = {
                    Items.COD_BUCKET, Items.SALMON_BUCKET, Items.TROPICAL_FISH_BUCKET, Items.PUFFERFISH_BUCKET, Items.AXOLOTL_BUCKET, Items.TADPOLE_BUCKET
                };
                for (Item item : required) {
                    if (player.getInventory().count(item) <= 0) {
                        yield false;
                    }
                }
                yield true;
            }
            case "has_mending" -> {
                if (!(player.getEntityWorld() instanceof ServerWorld sw)) yield false;
                var registryOpt = sw.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
                if (registryOpt.isEmpty()) yield false;
                var registry = registryOpt.get();
                var mendingOpt = registry.getEntry(net.minecraft.enchantment.Enchantments.MENDING.getValue());
                if (mendingOpt.isEmpty()) yield false;
                var mending = mendingOpt.get();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        if (net.minecraft.enchantment.EnchantmentHelper.getLevel(mending, stack) > 0) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            case "trade_all_professions" -> {
                if (player.getEntityWorld() == null || player.getEntityWorld().isClient()) yield false;
                Project3State state = Project3State.getOrCreate(((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getServer());
                Set<String> traded = state.getTradedProfessions(player.getUuid());
                int validCount = 0;
                for (String p : traded) {
                    if (!p.equals("minecraft:nitwit") && !p.equals("minecraft:none")) {
                        validCount++;
                    }
                }
                yield validCount >= 13;
            }
            default -> false;
        };
    }

    private boolean isPlayerInStructure(ServerPlayerEntity player, net.minecraft.registry.RegistryKey<net.minecraft.world.gen.structure.Structure> structureKey) {
        if (!(player.getEntityWorld() instanceof ServerWorld sw)) return false;
        var registryOpt = sw.getRegistryManager().getOptional(RegistryKeys.STRUCTURE);
        if (registryOpt.isEmpty()) return false;
        var registry = registryOpt.get();
        var structure = registry.get(structureKey);
        if (structure == null) return false;
        net.minecraft.structure.StructureStart start = sw.getStructureAccessor().getStructureAt(player.getBlockPos(), structure);
        return start != null && start.hasChildren();
    }

    private boolean checkInventory(ServerPlayerEntity player) {
        // Fix #2: guard against null from Identifier.tryParse
        Identifier id = Identifier.tryParse(target);
        if (id == null) return false;
        Item item = Registries.ITEM.get(id);
        if (item == null) return false;
        int count = player.getInventory().count(item);
        return exact ? count == threshold : count >= threshold;
    }

    private boolean checkKill(ServerPlayerEntity player) {
        Identifier id = Identifier.tryParse(target);
        if (id == null) return false;
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(id);
        if (entityType == null) return false;
        Stat<?> stat = Stats.KILLED.getOrCreateStat(entityType);
        int value = player.getStatHandler().getStat(stat);
        return exact ? value == threshold : value >= threshold;
    }

    private boolean checkItemStat(ServerPlayerEntity player, StatType<Item> statType) {
        Identifier id = Identifier.tryParse(target);
        if (id == null) return false;
        Item item = Registries.ITEM.get(id);
        if (item == null) return false;
        Stat<?> stat = statType.getOrCreateStat(item);
        int value = player.getStatHandler().getStat(stat);
        return exact ? value == threshold : value >= threshold;
    }

    private boolean checkBlockStat(ServerPlayerEntity player, StatType<Block> statType) {
        Identifier id = Identifier.tryParse(target);
        if (id == null) return false;
        Block block = Registries.BLOCK.get(id);
        if (block == null) return false;
        Stat<?> stat = statType.getOrCreateStat(block);
        int value = player.getStatHandler().getStat(stat);
        return exact ? value == threshold : value >= threshold;
    }

    @SuppressWarnings("unchecked")
    private boolean checkGenericStat(ServerPlayerEntity player) {
        Identifier statId = Identifier.tryParse(target);
        if (statId == null) return false;
        StatType<?> statType = Stats.CUSTOM;
        Stat<?> stat = ((StatType<Object>) statType).getOrCreateStat(statId);
        int value = player.getStatHandler().getStat(stat);
        return exact ? value == threshold : value >= threshold;
    }

    /**
     * Parse a trigger from string arguments.
     * Fix #26: robust parser that handles targets with colons (e.g. "minecraft:oak_log").
     * Format: "TYPE:target:amount" or "TYPE:target:amount:exact"
     * Where type is one of the Type enum values. If not found, defaults to CUSTOM.
     */
    public static AchievementTrigger parse(String str) {
        if (str == null || str.isEmpty()) {
            return new AchievementTrigger(Type.CUSTOM, "", 1);
        }
        int firstColon = str.indexOf(':');
        if (firstColon == -1) {
            return new AchievementTrigger(Type.CUSTOM, str, 1);
        }
        String typeStr = str.substring(0, firstColon);
        Type type;
        try {
            type = Type.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = Type.CUSTOM;
        }

        String rest = str.substring(firstColon + 1);

        // For CUSTOM type, the whole rest is the target (no trailing amount expected from external format)
        // For other types: last numeric token is threshold, optional "true"/"false" before it is exact flag
        // Robust parsing: walk from the end

        String[] tokens = rest.split(":");

        if (tokens.length == 1) {
            return new AchievementTrigger(type, rest, 1, false);
        }

        // Try last token as exact flag
        String lastToken = tokens[tokens.length - 1];
        boolean hasExact = lastToken.equalsIgnoreCase("true") || lastToken.equalsIgnoreCase("false");
        boolean exact = hasExact && Boolean.parseBoolean(lastToken);

        int thresholdIdx = hasExact ? tokens.length - 2 : tokens.length - 1;
        if (thresholdIdx < 1) {
            return new AchievementTrigger(type, rest, 1, false);
        }

        String possibleThreshold = tokens[thresholdIdx];
        int threshold = 1;
        boolean hasThreshold = false;
        try {
            threshold = Integer.parseInt(possibleThreshold);
            hasThreshold = true;
        } catch (NumberFormatException e) {
            // not a number — no threshold token
        }

        // Target is everything before the threshold (and optional exact flag)
        int targetEndIdx = hasThreshold ? thresholdIdx : thresholdIdx + 1;
        StringBuilder targetBuilder = new StringBuilder();
        for (int i = 0; i < targetEndIdx; i++) {
            if (i > 0) targetBuilder.append(':');
            targetBuilder.append(tokens[i]);
        }

        String target = targetBuilder.toString();
        if (target.isEmpty()) {
            target = rest;
        }

        return new AchievementTrigger(type, target, threshold, exact);
    }

    /**
     * Fix #35: derive cumulative flag from the trigger type/name automatically,
     * avoiding a manually maintained whitelist that can drift out of sync.
     */
    public boolean isStatCumulative() {
        if (type == Type.CUSTOM) {
            if (target == null || target.isEmpty()) return false;
            String[] parts = target.split(":");
            String name = parts[0];
            // All custom triggers that work via stats (not inventory-snapshot) are cumulative
            return switch (name) {
                case "open_inventory",
                     "play_music_disc",
                     "brush_suspicious",
                     "enchant_level_30",
                     "kill_monster",
                     "kill_passive",
                     "mine_emerald",
                     "eat_food",
                     "rename_entity",
                     "give_allay_flower",
                     "mace_kill_50_blocks",
                     "shoot_firework_crossbow",
                     "use_any_potion",
                     "play_instrument" -> true;
                default -> false;
            };
        }
        return type != Type.INVENTORY_ITEM;
    }

    public int getCurrentValue(ServerPlayerEntity player) {
        return switch (type) {
            case KILL_ENTITY -> {
                Identifier id = Identifier.tryParse(target);
                EntityType<?> entityType = id == null ? null : Registries.ENTITY_TYPE.get(id);
                yield entityType == null ? 0 : player.getStatHandler().getStat(Stats.KILLED.getOrCreateStat(entityType));
            }
            case USE_ITEM -> {
                Identifier id = Identifier.tryParse(target);
                Item item = id == null ? null : Registries.ITEM.get(id);
                yield item == null ? 0 : player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));
            }
            case CRAFT_ITEM -> {
                Identifier id = Identifier.tryParse(target);
                Item item = id == null ? null : Registries.ITEM.get(id);
                yield item == null ? 0 : player.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(item));
            }
            case MINE_BLOCK -> {
                Identifier id = Identifier.tryParse(target);
                Block block = id == null ? null : Registries.BLOCK.get(id);
                yield block == null ? 0 : player.getStatHandler().getStat(Stats.MINED.getOrCreateStat(block));
            }
            case BREAK_BLOCK -> {
                Identifier id = Identifier.tryParse(target);
                Item item = id == null ? null : Registries.ITEM.get(id);
                yield item == null ? 0 : player.getStatHandler().getStat(Stats.BROKEN.getOrCreateStat(item));
            }
            case STAT -> {
                Identifier statId = Identifier.tryParse(target);
                if (statId == null) yield 0;
                Stat<Identifier> stat = Stats.CUSTOM.getOrCreateStat(statId);
                yield player.getStatHandler().getStat(stat);
            }
            case CUSTOM -> {
                String[] parts = target.split(":");
                String name = parts[0];
                yield switch (name) {
                    case "open_inventory" -> {
                        yield safeCustomStat(player, com.project3.Project3Mod.OPEN_INVENTORY_STAT_ID);
                    }
                    case "play_music_disc" -> {
                        yield safeCustomStat(player, com.project3.Project3Mod.PLAY_MUSIC_DISC_STAT_ID);
                    }
                    case "brush_suspicious" -> {
                        yield safeCustomStat(player, Identifier.of("minecraft", "clean_block"));
                    }
                    case "enchant_level_30" -> {
                        yield safeCustomStat(player, Identifier.of("minecraft", "enchant_item"));
                    }
                    // Fix #11: use cached MONSTER_TYPES
                    case "kill_monster" -> {
                        int count = 0;
                        for (EntityType<?> t : MONSTER_TYPES) {
                            count += player.getStatHandler().getStat(Stats.KILLED.getOrCreateStat(t));
                        }
                        yield count;
                    }
                    case "kill_passive" -> {
                        int count = 0;
                        EntityType<?>[] passiveTypes = {
                            EntityType.SHEEP, EntityType.COW, EntityType.PIG, EntityType.CHICKEN,
                            EntityType.RABBIT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
                            EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.GOAT, EntityType.CAT,
                            EntityType.WOLF, EntityType.PARROT, EntityType.OCELOT, EntityType.FROG,
                            EntityType.SNIFFER, EntityType.CAMEL, EntityType.ARMADILLO, EntityType.BAT,
                            EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.AXOLOTL, EntityType.BEE,
                            EntityType.DOLPHIN, EntityType.FOX, EntityType.PANDA, EntityType.POLAR_BEAR,
                            EntityType.STRIDER, EntityType.TURTLE, EntityType.VILLAGER, EntityType.WANDERING_TRADER
                        };
                        for (EntityType<?> t : passiveTypes) {
                            count += player.getStatHandler().getStat(Stats.KILLED.getOrCreateStat(t));
                        }
                        yield count;
                    }
                    case "mine_emerald" -> player.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.EMERALD_ORE)) +
                                            player.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.DEEPSLATE_EMERALD_ORE));
                    // Fix #11: use cached FOOD_ITEMS
                    case "eat_food" -> {
                        int total = 0;
                        for (Item item : FOOD_ITEMS) {
                            total += player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));
                        }
                        yield total;
                    }
                    case "rename_entity" -> {
                        Stat<?> stat = Stats.USED.getOrCreateStat(Items.NAME_TAG);
                        yield player.getStatHandler().getStat(stat);
                    }
                    case "give_allay_flower" -> {
                        yield safeCustomStat(player, com.project3.Project3Mod.GIVE_ALLAY_FLOWER_STAT_ID);
                    }
                    case "mace_kill_50_blocks" -> {
                        yield safeCustomStat(player, com.project3.Project3Mod.MACE_KILL_50_BLOCKS_STAT_ID);
                    }
                    case "shoot_firework_crossbow" -> {
                        yield safeCustomStat(player, com.project3.Project3Mod.SHOOT_FIREWORK_CROSSBOW_STAT_ID);
                    }
                    case "use_any_potion" -> player.getStatHandler().getStat(Stats.USED.getOrCreateStat(Items.POTION)) +
                                             player.getStatHandler().getStat(Stats.USED.getOrCreateStat(Items.SPLASH_POTION)) +
                                             player.getStatHandler().getStat(Stats.USED.getOrCreateStat(Items.LINGERING_POTION));
                    case "play_instrument" -> {
                        Stat<?> stat = Stats.USED.getOrCreateStat(Items.GOAT_HORN);
                        yield player.getStatHandler().getStat(stat);
                    }
                    default -> 0;
                };
            }
            default -> 0;
        };
    }

    private static int safeCustomStat(ServerPlayerEntity player, Identifier statId) {
        try {
            Stat<?> stat = Stats.CUSTOM.getOrCreateStat(statId);
            return player.getStatHandler().getStat(stat);
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean safeCustomStatCheck(ServerPlayerEntity player, Identifier statId, int threshold) {
        try {
            Stat<?> stat = Stats.CUSTOM.getOrCreateStat(statId);
            return player.getStatHandler().getStat(stat) >= threshold;
        } catch (Exception e) {
            return false;
        }
    }

    public String getIconItemId() {
        return switch (type) {
            case INVENTORY_ITEM, CRAFT_ITEM, USE_ITEM, BREAK_BLOCK -> target;
            case MINE_BLOCK -> target;
            case KILL_ENTITY -> {
                if (target.contains("warden") || target.contains("dragon") || target.contains("wither")) {
                    yield "minecraft:netherite_sword";
                }
                yield "minecraft:iron_sword";
            }
            case STAT -> "minecraft:book";
            case CUSTOM -> {
                String[] parts = target.split(":");
                yield switch (parts[0]) {
                    case "any_log" -> "minecraft:oak_log";
                    case "open_inventory" -> "minecraft:chest";
                    case "all_tools" -> "minecraft:iron_pickaxe";
                    case "smelt_cobbled_deepslate" -> "minecraft:deepslate";
                    case "find_mineshaft", "ride_minecart" -> "minecraft:minecart";
                    case "find_spawner" -> "minecraft:spawner";
                    case "play_music_disc", "all_music_discs" -> "minecraft:music_disc_5";
                    case "touch_bedrock" -> "minecraft:bedrock";
                    case "brush_suspicious" -> "minecraft:brush";
                    case "ride_pig" -> "minecraft:carrot_on_a_stick";
                    case "trim_armor" -> "minecraft:armor_trim_smithing_template";
                    case "enchant_level_30" -> "minecraft:enchanted_book";
                    case "tame_cat" -> "minecraft:cod";
                    case "silk_touch" -> "minecraft:enchanted_book";
                    case "open_trial_chamber" -> "minecraft:trial_key";
                    case "open_buried_treasure" -> "minecraft:chest";
                    case "find_igloo_lab" -> "minecraft:packed_ice";
                    case "find_mansion" -> "minecraft:dark_oak_planks";
                    case "player_head" -> "minecraft:player_head";
                    case "kill_monster" -> "minecraft:iron_sword";
                    case "kill_passive" -> "minecraft:cooked_beef";
                    case "mine_emerald" -> "minecraft:emerald_ore";
                    case "eat_food" -> "minecraft:cooked_chicken";
                    case "rename_item" -> "minecraft:name_tag";
                    case "dolphin_grace" -> "minecraft:heart_of_the_sea";
                    case "rename_entity" -> "minecraft:name_tag";
                    case "all_woods" -> "minecraft:oak_log";
                    case "suicide" -> "minecraft:totem_of_undying";
                    case "give_allay_flower" -> "minecraft:allium";
                    case "activate_conduit" -> "minecraft:conduit";
                    case "pottery_sherds" -> "minecraft:arms_up_pottery_sherd";
                    case "mob_head" -> "minecraft:wither_skeleton_skull";
                    case "meet_mooshroom" -> "minecraft:mushroom_stew";
                    case "mace_kill_50_blocks" -> "minecraft:mace";
                    case "use_any_potion" -> "minecraft:potion";
                    case "four_banner_patterns" -> "minecraft:flower_banner_pattern";
                    case "all_corals" -> "minecraft:tube_coral";
                    case "play_instrument" -> "minecraft:goat_horn";
                    case "summon_snow_golem" -> "minecraft:carved_pumpkin";
                    case "shoot_firework_crossbow" -> "minecraft:crossbow";
                    case "all_terracotta" -> "minecraft:terracotta";
                    case "craft_all_rails_minecarts" -> "minecraft:rail";
                    case "trim_different_armor" -> "minecraft:diamond_chestplate";
                    case "all_fish_buckets" -> "minecraft:cod_bucket";
                    case "has_mending" -> "minecraft:enchanted_book";
                    case "trade_all_professions" -> "minecraft:emerald";
                    case "experience_level_45" -> "minecraft:experience_bottle";
                    default -> "minecraft:book";
                };
            }
        };
    }
}
