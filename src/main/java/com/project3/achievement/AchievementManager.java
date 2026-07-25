package com.project3.achievement;

import com.project3.state.Project3State;
import com.project3.network.CameraRotatePayload;
import com.project3.network.AchievementSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

/**
 * Manages a linear chain of achievements.
 * Each player has a current achievement index; completing it unlocks the next.
 * Achievements are displayed as vanilla Minecraft toasts with custom rarity colors.
 */
public class AchievementManager {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("p3/achievements");

    private final List<AchievementDefinition> achievements = new ArrayList<>();
    private final Map<UUID, AchievementSyncPayload> lastSentPayloads = new java.util.concurrent.ConcurrentHashMap<>();
    private int tickAccum = 0;
    // ONLY used for ach_29 (enchant_level_30): prevent re-triggering until next enchant.
    private final Map<UUID, Boolean> lastEnchantProcessed = new HashMap<>();

    public AchievementManager() {
        registerDefaultAchievements();
    }

    // ─── Registration ───────────────────────────────────────────────────────

    private void registerDefaultAchievements() {
        // Overworld achievements (1-75)
        addAchievement("ach_01", "Оно парит", "Добыть 1 блок любой древесины.", AchievementRarity.COMMON, "custom:any_log:1");
        addAchievement("ach_02", "Это просто", "Открыть инвентарь.", AchievementRarity.COMMON, "custom:open_inventory:1");
        addAchievement("ach_03", "Дело мастера боится", "Создать верстак.", AchievementRarity.COMMON, "inventory_item:minecraft:crafting_table:1");
        addAchievement("ach_04", "Работа мечты", "Добыть 15 блоков булыжника.", AchievementRarity.COMMON, "inventory_item:minecraft:cobblestone:15");
        addAchievement("ach_05", "Фулл-хаус", "Получить меч, лопату, кирку, топор, мотыгу и железное копьё.", AchievementRarity.UNCOMMON, "custom:all_tools:1");
        addAchievement("ach_06", "Знаток сплавов", "Добыть 10 ед. необработанной меди.", AchievementRarity.COMMON, "inventory_item:minecraft:raw_copper:10");
        addAchievement("ach_07", "Большой чёрный уголь", "Создать или получить угольный блок.", AchievementRarity.RARE, "inventory_item:minecraft:coal_block:1", "burn_item");
        addAchievement("ach_08", "Наконец-то", "Съесть любую пищу.", AchievementRarity.COMMON, "custom:eat_food:1");
        addAchievement("ach_09", "Неуязвимый", "Создать щит.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:shield:1");
        addAchievement("ach_10", "Главный монстр", "Победить 10 враждебных существ.", AchievementRarity.COMMON, "custom:kill_monster:10");
        addAchievement("ach_11", "Я ничего не трогал", "Переплавить дроблёный сланец в глубинный сланец.", AchievementRarity.UNCOMMON, "custom:smelt_cobbled_deepslate:1");
        addAchievement("ach_12", "Увеличение запаса маны", "Добыть 64 ед. лазурита.", AchievementRarity.COMMON, "inventory_item:minecraft:lapis_lazuli:64");
        addAchievement("ach_13", "Да как его призвать...", "Создать 32 факела из красного камня.", AchievementRarity.COMMON, "inventory_item:minecraft:redstone_torch:32");
        addAchievement("ach_14", "Я это точно использую", "Создать алмазную мотыгу.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:diamond_hoe:1");
        addAchievement("ach_15", "Кипяток", "Набрать ведро лавы.", AchievementRarity.COMMON, "inventory_item:minecraft:lava_bucket:1");
        addAchievement("ach_16", "Тут нужен клининг", "Найти заброшенную шахту.", AchievementRarity.UNCOMMON, "custom:find_mineshaft:1");
        addAchievement("ach_17", "Старик", "Прокатиться в вагонетке.", AchievementRarity.COMMON, "custom:ride_minecart:1");
        addAchievement("ach_18", "Это Бетховен", "Послушать любую музыкальную пластинку.", AchievementRarity.UNCOMMON, "custom:play_music_disc:1");
        addAchievement("ach_19", "Где тут ад?", "Добраться до коренной породы.", AchievementRarity.UNCOMMON, "custom:touch_bedrock:1");
        addAchievement("ach_20", "Красивая безделушка", "Добыть аметистовый осколок.", AchievementRarity.COMMON, "inventory_item:minecraft:amethyst_shard:1");
        addAchievement("ach_21", "Настоящий ценитель", "Добыть изумрудную руду.", AchievementRarity.UNCOMMON, "custom:mine_emerald:1");
        addAchievement("ach_22", "Зелень цветёт", "Получить цветущую азалию.", AchievementRarity.COMMON, "inventory_item:minecraft:flowering_azalea:1");
        addAchievement("ach_23", "Резня", "Победить 40 дружелюбных существ.", AchievementRarity.UNCOMMON, "custom:kill_passive:40");
        addAchievement("ach_24", "Шаг к индустриализации", "Получить 64 ед. древесного угля.", AchievementRarity.COMMON, "inventory_item:minecraft:charcoal:64", "burn_item");
        addAchievement("ach_25", "Главное не привязаться", "Переименовать любой предмет на наковальне.", AchievementRarity.COMMON, "custom:rename_item:1");
        addAchievement("ach_26", "Камикадзе", "Получить 15 блоков динамита.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:tnt:15");
        addAchievement("ach_27", "С ветерком", "Оседлать свинью и управлять ею с помощью удочки с морковью.", AchievementRarity.COMMON, "custom:ride_pig:1");
        addAchievement("ach_28", "Амфибия", "Получить эффект «Грация дельфина».", AchievementRarity.UNCOMMON, "custom:dolphin_grace:1");
        addAchievement("ach_29", "Are you a wizard?", "Зачаровать предмет на 30-м уровне опыта.", AchievementRarity.EPIC, "custom:enchant_level_30:1");
        addAchievement("ach_30", "Да! Да! Да!", "Приручить кошку.", AchievementRarity.COMMON, "custom:tame_cat:1");
        addAchievement("ach_31", "Чудовище Франкенштейна", "Создать снежного голема.", AchievementRarity.UNCOMMON, "custom:summon_snow_golem:1");
        addAchievement("ach_32", "Откуда берутся дети", "Найти рассадник монстров.", AchievementRarity.RARE, "custom:find_spawner:1");
        addAchievement("ach_33", "Твёрдое состояние воды", "Набрать ведро рыхлого снега.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:powder_snow_bucket:1");
        addAchievement("ach_34", "Миллионер", "Добыть 64 алмаза.", AchievementRarity.RARE, "inventory_item:minecraft:diamond:64");
        addAchievement("ach_35", "Рабовладелец", "Дать имя существу с помощью бирки.", AchievementRarity.UNCOMMON, "custom:rename_entity:1");
        addAchievement("ach_36", "С праздником, тварь", "Выстрелить пиротехнической ракетой из арбалета.", AchievementRarity.UNCOMMON, "custom:shoot_firework_crossbow:1");
        addAchievement("ach_37", "Скользкий тип", "Победить слизня.", AchievementRarity.COMMON, "kill_entity:minecraft:slime:1");
        addAchievement("ach_38", "Обожжённая земля", "Получить все виды терракоты.", AchievementRarity.RARE, "custom:all_terracotta:1");
        addAchievement("ach_39", "Беззаконие", "Очистить подозрительный песок или подозрительный гравий щёткой.", AchievementRarity.RARE, "custom:brush_suspicious:1");
        addAchievement("ach_40", "Корень проблемы", "Добыть свисающие корни.", AchievementRarity.COMMON, "inventory_item:minecraft:hanging_roots:1");
        addAchievement("ach_41", "Ставлю на этом крест", "Найти зарытый клад.", AchievementRarity.UNCOMMON, "custom:open_buried_treasure:1");
        addAchievement("ach_42", "Сломанный телефон", "Добыть осколок эха.", AchievementRarity.RARE, "inventory_item:minecraft:echo_shard:1");
        addAchievement("ach_43", "Пир для косолапого", "Создать блок мёда.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:honey_block:1");
        addAchievement("ach_44", "Братья наши большие", "Создать волчью броню.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:wolf_armor:1");
        addAchievement("ach_45", "Утри слёзы", "Добыть плачущий обсидиан.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:crying_obsidian:1");
        addAchievement("ach_46", "Машиностроение", "Скрафтить все виды рельсов и вагонетку.", AchievementRarity.RARE, "custom:craft_all_rails_minecarts:1");
        addAchievement("ach_47", "Заслуженная награда", "Открыть хранилище ключом испытаний.", AchievementRarity.EPIC, "custom:open_trial_chamber:1");
        addAchievement("ach_48", "Ужасное предчувствие", "Получить зловещий флакон.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:ominous_bottle:1");
        addAchievement("ach_49", "Местный дровосек", "Добыть все виды древесины.", AchievementRarity.COMMON, "custom:all_woods:1");
        addAchievement("ach_50", "Синтез", "Добыть голову любого игрока.", AchievementRarity.LEGENDARY, "custom:player_head:1", "kick_error");
        addAchievement("ach_51", "Прикосновение Бога", "Получить инструмент с зачарованием «Шёлковое касание».", AchievementRarity.EPIC, "custom:silk_touch:1");
        addAchievement("ach_52", "Последний писк моды", "Надеть броню, украшенную четырьмя разными кузнечными шаблонами.", AchievementRarity.EPIC, "custom:trim_different_armor:1");
        addAchievement("ach_53", "Добротный улов", "Поймать все виды рыб и аксолотля в ведро.", AchievementRarity.RARE, "custom:all_fish_buckets:1");
        addAchievement("ach_54", "Болванка на рукояти", "Создать булаву.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:mace:1");
        addAchievement("ach_55", "Лучшая мастерская", "Получить инструмент с зачарованием «Починка».", AchievementRarity.RARE, "custom:has_mending:1");
        addAchievement("ach_56", "Разнорабочий", "Поторговать с сельскими жителями всех профессий.", AchievementRarity.EPIC, "custom:trade_all_professions:1");
        addAchievement("ach_57", "Не зельевар", "Выпить любое зелье.", AchievementRarity.COMMON, "custom:use_any_potion:1");
        addAchievement("ach_58", "Спаситель", "Победить разорителя.", AchievementRarity.RARE, "kill_entity:minecraft:ravager:1");
        addAchievement("ach_59", "Трафарет", "Получить 4 любых узора флага.", AchievementRarity.UNCOMMON, "custom:four_banner_patterns:1");
        addAchievement("ach_60", "Миллиардер", "Собрать 20 алмазных блоков.", AchievementRarity.EPIC, "inventory_item:minecraft:diamond_block:20");
        addAchievement("ach_61", "Морской деликатес", "Добыть все виды кораллов.", AchievementRarity.RARE, "custom:all_corals:1");
        addAchievement("ach_62", "Хлюп-хлюп", "Добыть мокрую губку.", AchievementRarity.UNCOMMON, "inventory_item:minecraft:wet_sponge:1");
        addAchievement("ach_63", "Чистая кварта", "Дунуть в козий рог.", AchievementRarity.UNCOMMON, "custom:play_instrument:1");

        // Linear achievements 64-75
        addAchievement("ach_64", "ach_63", "Романтическая свобода", "Вручить спешику любой цветок.", AchievementRarity.UNCOMMON, "custom:give_allay_flower:1");
        addAchievement("ach_65", "ach_64", "Отблеск науки", "Найти подвал в иглу.", AchievementRarity.RARE, "custom:find_igloo_lab:1");
        addAchievement("ach_66", "ach_65", "Слиться с водой", "Активировать проводник.", AchievementRarity.RARE, "custom:activate_conduit:1");
        addAchievement("ach_67", "ach_66", "Реставратор", "Собрать 10 уникальных глиняных черепков.", AchievementRarity.EPIC, "custom:pottery_sherds:10");
        addAchievement("ach_68", "ach_67", "Трофей", "Добыть голову любого существа.", AchievementRarity.RARE, "custom:mob_head:1");
        addAchievement("ach_69", "ach_68", "Кордицепс", "Встретить грибную корову.", AchievementRarity.UNCOMMON, "custom:meet_mooshroom:1");
        addAchievement("ach_70", "ach_69", "Вилка", "Получить трезубец.", AchievementRarity.RARE, "inventory_item:minecraft:trident:1");
        addAchievement("ach_71", "ach_70", "Зловещий муравейник", "Найти лесной особняк.", AchievementRarity.UNCOMMON, "custom:find_mansion:1");
        addAchievement("ach_72", "ach_71", "Небесная кара", "Победить противника булавой, упав с высоты не менее 50 блоков.", AchievementRarity.EPIC, "custom:mace_kill_50_blocks:1", "mace_kill_effect");
        addAchievement("ach_73", "ach_72", "Меломан", "Собрать все музыкальные пластинки.", AchievementRarity.EPIC, "custom:all_music_discs:1");
        addAchievement("ach_74", "ach_73", "Опытный", "Достичь 45-го уровня опыта.", AchievementRarity.UNCOMMON, "custom:experience_level_45:1");
        addAchievement("ach_75", "ach_74", "Не слышу", "Победить хранителя.", AchievementRarity.LEGENDARY, "kill_entity:minecraft:warden:1");
    }

    public void addAchievement(String id, String title, String description,
                                AchievementRarity rarity, String triggerStr) {
        String parentId = achievements.isEmpty() ? "root" : achievements.get(achievements.size() - 1).getId();
        addAchievement(id, parentId, title, description, rarity, triggerStr, null);
    }

    public void addAchievement(String id, String parentId, String title, String description,
                                AchievementRarity rarity, String triggerStr) {
        addAchievement(id, parentId, title, description, rarity, triggerStr, null);
    }

    public void addAchievement(String id, String title, String description,
                                AchievementRarity rarity, String triggerStr, String specialEffect) {
        String parentId = achievements.isEmpty() ? "root" : achievements.get(achievements.size() - 1).getId();
        addAchievement(id, parentId, title, description, rarity, triggerStr, specialEffect);
    }

    public void addAchievement(String id, String parentId, String title, String description,
                                AchievementRarity rarity, String triggerStr, String specialEffect) {
        AchievementTrigger trigger = AchievementTrigger.parse(triggerStr);
        achievements.add(new AchievementDefinition(id, parentId, title, description, rarity, trigger, specialEffect));
    }

    public void addAchievement(AchievementDefinition def) {
        achievements.add(def);
    }

    public boolean removeAchievement(String id) {
        return achievements.removeIf(a -> a.getId().equals(id));
    }

    public List<AchievementDefinition> getAchievements() {
        return Collections.unmodifiableList(achievements);
    }

    public int getAchievementCount() {
        return achievements.size();
    }

    public AchievementDefinition getAchievement(int index) {
        if (index < 0 || index >= achievements.size()) return null;
        return achievements.get(index);
    }

    public AchievementDefinition getAchievementById(String id) {
        return achievements.stream().filter(a -> a.getId().equals(id)).findFirst().orElse(null);
    }

    // ─── Tick & Check ─────────────────────────────────────────────────────────

    public void tick(MinecraftServer server, Project3State state) {
        if (++tickAccum < 20) return;
        tickAccum = 0;

        lastSentPayloads.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                checkPlayer(player, state);
            } catch (Exception e) {
                LOGGER.error("Error checking achievements for player {}: {}", player.getName().getString(), e.getMessage(), e);
            }
            syncActiveAchievement(player, state);
        }
    }

    private void checkPlayer(ServerPlayerEntity player, Project3State state) {
        UUID uuid = player.getUuid();

        Set<String> completed = state.getCompletedAchievements(uuid);

        // Fix #45: guard against out-of-bounds achievement index
        int currentIndex = state.getCurrentAchievementIndex(player.getUuid());
        if (currentIndex > achievements.size()) {
            LOGGER.warn("Player {} has achievement index {} which exceeds list size {}; resetting.",
                    player.getName().getString(), currentIndex, achievements.size());
            state.setAchievementIndex(player.getUuid(), achievements.size());
        }

        // Optimization: only check achievements whose parent is completed
        // This avoids iterating through the entire tree when most parents aren't done
        for (AchievementDefinition achievement : achievements) {
            String id = achievement.getId();
            if (completed.contains(id)) {
                continue;
            }

            String parentId = achievement.getParentId();
            boolean parentCompleted = parentId.equals("root") || parentId.equals("p3:root") || completed.contains(parentId);

            if (!parentCompleted) {
                continue; // Skip if parent not completed - can't trigger yet
            }

            boolean triggered;
            // This trigger uses a mixin (MixinEnchantmentScreenHandler) that stores the
            // player's level at the moment they took an enchanted item. We read that flag
            // instead of using the advancement-tracker-triggering stat system.
            if (achievement.getTrigger().getType() == AchievementTrigger.Type.CUSTOM
                    && achievement.getTrigger().getTarget().equals("enchant_level_30")) {
                int enchantLevel = state.getPlayerEnchantLevelAtTake(uuid);
                triggered = enchantLevel >= 30 && !lastEnchantProcessed.getOrDefault(uuid, false);
                if (triggered || enchantLevel > 0) {
                    state.clearPlayerEnchantLevelAtTake(uuid);
                    lastEnchantProcessed.put(uuid, true);
                }
            } else if (achievement.getTrigger().isStatCumulative()) {
                Integer baseline = state.getAchievementBaseline(player.getUuid(), id);
                int currentValue = achievement.getTrigger().getCurrentValue(player);
                if (baseline == null) {
                    state.setAchievementBaseline(player.getUuid(), id, currentValue);
                    triggered = false;
                } else {
                    triggered = (currentValue - baseline) >= achievement.getTrigger().getThreshold();
                }
            } else {
                triggered = achievement.getTrigger().check(player);
            }

            if (triggered) {
                completeAchievement(player, state, achievement);
            }
        }

    }

    private int getLevelReward(int questNum, net.minecraft.world.World world) {
        int group = (questNum - 1) / 15;
        if (world.getRegistryKey() == net.minecraft.world.World.NETHER) {
            return 2 * (group + 1);
        } else if (world.getRegistryKey() == net.minecraft.world.World.END) {
            return 4 + 2 * group;
        } else {
            return group + 1;
        }
    }

    public void completeAchievement(ServerPlayerEntity player, Project3State state, AchievementDefinition achievement) {
        int questNum = state.getCurrentAchievementIndex(player.getUuid()) + 1;

        state.completeAchievement(player.getUuid(), achievement.getId());
        state.advanceAchievementIndex(player.getUuid());

        // Fix #54: log achievement completion
        LOGGER.info("[Project3] Player {} completed achievement '{}' ({})",
                player.getName().getString(), achievement.getId(), achievement.getTitle());
        ((ServerWorld) player.getEntityWorld()).playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
                
        // Spawn green particles
        ((ServerWorld) player.getEntityWorld()).spawnParticles(
                net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER, 
                player.getX(), player.getY() + 1.0, player.getZ(), 
                20, 0.5, 0.5, 0.5, 0.1);
                
        // Custom chat message
        net.minecraft.text.MutableText msg = net.minecraft.text.Text.literal("Выполнена задача: " + achievement.getTitle())
            .formatted(net.minecraft.util.Formatting.GREEN)
            .styled(style -> style.withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(
                net.minecraft.text.Text.literal(achievement.getDescription()).formatted(net.minecraft.util.Formatting.GRAY)
            )));
        ((net.minecraft.server.world.ServerWorld)player.getEntityWorld()).getServer().getPlayerManager().broadcast(msg, false);

        ItemStack cookies = new ItemStack(Items.COOKIE, 10);
        player.getInventory().offerOrDrop(cookies);

        int xpLevels = getLevelReward(questNum, player.getEntityWorld());
        player.addExperienceLevels(xpLevels);

        com.project3.player.PlayerStateManager.grantHappiness(player, state, 72000L);
        state.setUnnamedEffectActive(player.getUuid(), false);

        grantVanillaAdvancement(player, achievement.getId());

        if (achievement.hasSpecialEffect()) {
            applySpecialEffect(player, achievement);
        }

        syncAdvancements(player, state);
        syncActiveAchievement(player, state);
        com.project3.player.PlayerStateManager.syncPlayerState(player, state);

        // Season End Ceremony: all achievements completed
        int newIndex = state.getCurrentAchievementIndex(player.getUuid());
        if (newIndex >= achievements.size()) {
            triggerSeasonEndCeremony(player, state);
        }
    }

    private void triggerSeasonEndCeremony(ServerPlayerEntity player, Project3State state) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Fireworks celebration
        for (int i = 0; i < 20; i++) {
            int delay = i * 5;
            com.project3.Project3Mod.schedule(delay, () -> {
                if (player.isAlive() && !player.isRemoved()) {
                    double fx = player.getX() + (player.getRandom().nextDouble() - 0.5) * 16;
                    double fy = player.getY() + 5 + player.getRandom().nextDouble() * 10;
                    double fz = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 16;
                    net.minecraft.entity.EntityType.FIREWORK_ROCKET.spawn(world,
                            net.minecraft.util.math.BlockPos.ofFloored(fx, fy, fz),
                            net.minecraft.entity.SpawnReason.EVENT);
                }
            });
        }

        // Grant permanent reward: remove all debuffs, give max happiness
        player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.UNLUCK);
        player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.DARKNESS);
        player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
        player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
        player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS);

        // Permanent Speed I and Luck as reward (hidden from inventory)
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.SPEED, -1, 0, false, false, false));
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.LUCK, -1, 0, false, false, false));

        // Max happiness
        com.project3.player.PlayerStateManager.grantHappiness(player, state, 120000L);

        LOGGER.info("[Project3] Player {} completed ALL achievements - Season End Ceremony triggered",
                player.getName().getString());
    }

    public void syncActiveAchievement(ServerPlayerEntity player, Project3State state) {
        if (!state.isSeasonStarted()) {
            AchievementSyncPayload empty = new AchievementSyncPayload("", "", "", "", 0, 0, 0, 0);
            AchievementSyncPayload last = lastSentPayloads.get(player.getUuid());
            if (last == null || !last.equals(empty)) {
                ServerPlayNetworking.send(player, empty);
                lastSentPayloads.put(player.getUuid(), empty);
            }
            return;
        }
        int index = state.getCurrentAchievementIndex(player.getUuid());
        int total = achievements.size();
        
        AchievementSyncPayload payload;
        if (index >= total) {
            payload = new AchievementSyncPayload("", "", "", "", 0, 0, total, total);
        } else {
            AchievementDefinition active = achievements.get(index);
            AchievementTrigger trigger = active.getTrigger();
            
            int currentValue = 0;
            int targetValue = trigger.getThreshold();
            
            if (trigger.isStatCumulative()) {
                Integer baseline = state.getAchievementBaseline(player.getUuid(), active.getId());
                int val = trigger.getCurrentValue(player);
                if (baseline != null) {
                    currentValue = val - baseline;
                }
            } else {
                if (trigger.getType() == AchievementTrigger.Type.INVENTORY_ITEM) {
                    net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(
                            net.minecraft.util.Identifier.tryParse(trigger.getTarget()));
                    if (item != null) {
                        currentValue = player.getInventory().count(item);
                    }
                } else {
                    currentValue = trigger.check(player) ? 1 : 0;
                }
            }
            
            payload = new AchievementSyncPayload(
                    active.getId(),
                    active.getTitle(),
                    active.getDescription(),
                    active.getIconItemId(),
                    currentValue,
                    targetValue,
                    index,
                    total
            );
        }

        AchievementSyncPayload last = lastSentPayloads.get(player.getUuid());
        if (last == null || !last.equals(payload)) {
            ServerPlayNetworking.send(player, payload);
            lastSentPayloads.put(player.getUuid(), payload);
        }
    }

    public void grantVanillaAdvancement(ServerPlayerEntity player, String id) {
        var loader = ((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getServer().getAdvancementLoader();
        var entry = loader.get(net.minecraft.util.Identifier.of("p3", id));
        if (entry != null) {
            var tracker = player.getAdvancementTracker();
            var progress = tracker.getProgress(entry);
            if (!progress.isDone()) {
                for (String criterion : progress.getUnobtainedCriteria()) {
                    tracker.grantCriterion(entry, criterion);
                }
            }
        }
    }

    public void syncAdvancementsToMatchIndex(ServerPlayerEntity player, int index) {
        syncAdvancements(player, Project3State.getOrCreate(((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getServer()));
    }

    public void syncAdvancements(ServerPlayerEntity player, Project3State state) {
        grantVanillaAdvancement(player, "root");

        var loader = ((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getServer().getAdvancementLoader();
        var tracker = player.getAdvancementTracker();
        Set<String> completed = state.getCompletedAchievements(player.getUuid());

        for (int i = 0; i < achievements.size(); i++) {
            String id = achievements.get(i).getId();
            var entry = loader.get(net.minecraft.util.Identifier.of("p3", id));
            if (entry != null) {
                var progress = tracker.getProgress(entry);
                if (completed.contains(id)) {
                    if (!progress.isDone()) {
                        for (String criterion : progress.getUnobtainedCriteria()) {
                            tracker.grantCriterion(entry, criterion);
                        }
                    }
                } else {
                    if (progress.isDone()) {
                        for (String criterion : progress.getObtainedCriteria()) {
                            tracker.revokeCriterion(entry, criterion);
                        }
                    }
                }
            }
        }

        if (tracker instanceof PlayerAdvancementTrackerAccessor accessor) {
            accessor.project3$setDirty(true);
            tracker.sendUpdate(player, false);
        }
    }

    private void applySpecialEffect(ServerPlayerEntity player, AchievementDefinition achievement) {
        String effect = achievement.getSpecialEffect();
        switch (effect) {
            case "mace_kill_effect" -> {
                player.setOnFireFor(5);
                player.networkHandler.sendPacket(new TitleS2CPacket(
                        Text.literal("critical error").formatted(Formatting.RED, Formatting.BOLD)
                ));
            }
            case "burn_item" -> {
                if (achievement.getTrigger().getType() == AchievementTrigger.Type.INVENTORY_ITEM) {
                    String target = achievement.getTrigger().getTarget();
                    net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(
                            net.minecraft.util.Identifier.tryParse(target));
                    if (item != null) {
                        player.getInventory().remove(item1 -> item1.isOf(item), achievement.getTrigger().getThreshold(), player.getInventory());
                        if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                            sw.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                        }
                        player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
                    }
                }
            }
            case "kick_error" -> {
                player.getInventory().remove(stack -> stack.isOf(Items.PLAYER_HEAD), 1, player.getInventory());
                 if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                }
                player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
                player.setOnFireFor(1);

                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§c1100101 1110010 1110010 1101111 1110010")));
                player.sendMessage(Text.literal("§c1100101 1110010 1110010 1101111 1110010"), false);

                com.project3.Project3Mod.schedule(20, () -> {
                    if (player.networkHandler != null) {
                        player.networkHandler.disconnect(Text.literal("Недопустимые данные игрока"));
                    }
                });
            }
        }
    }

    public void resetPlayer(UUID uuid, Project3State state) {
        state.resetAchievements(uuid);
    }

    public void forceNext(UUID uuid, Project3State state) {
        state.advanceAchievementIndex(uuid);
    }

    public String getProgressText(UUID uuid, Project3State state) {
        int current = state.getCurrentAchievementIndex(uuid);
        int total = achievements.size();
        if (current >= total) {
            return "Все достижения выполнены! (" + total + "/" + total + ")";
        }
        AchievementDefinition next = achievements.get(current);
        return "Текущее: " + next.getTitle() + " (" + (current + 1) + "/" + total + ")";
    }
}
