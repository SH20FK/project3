package com.project3.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.project3.Project3Mod;
import com.project3.achievement.AchievementDefinition;
import com.project3.achievement.AchievementManager;
import com.project3.achievement.AchievementRarity;
import com.project3.state.Project3State;
import com.project3.world.WorldBorderManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import com.project3.registry.ModRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.particle.ParticleTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Registers the /p3 command with sub-commands: start, status, reset.
 * Requires operator level (permission level 2).
 */
public class Project3Command {

    private static final Random RANDOM = new Random();

    // Nether block radius strip for producer placement
    private static final int BORDER_STRIP_MIN = 19_800;
    private static final int BORDER_STRIP_MAX = 20_200;
    private static final int PRODUCER_CHANCE  = 40; // 1/40 per chunk column

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {

        dispatcher.register(CommandManager.literal("p3")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("start")
                        .executes(ctx -> executeStartWarning(ctx.getSource()))
                        .then(CommandManager.literal("confirm")
                                .executes(ctx -> executeStart(ctx.getSource()))))
                .then(CommandManager.literal("status")
                        .executes(ctx -> executeStatus(ctx.getSource()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> executePlayerStatus(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))
                .then(CommandManager.literal("reset")
                        .executes(ctx -> executeResetWarning(ctx.getSource()))
                        .then(CommandManager.literal("confirm")
                                .executes(ctx -> executeReset(ctx.getSource()))))
                .then(CommandManager.literal("progress")
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(0, 5))
                                .executes(ctx -> executeSetProgress(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))))
                .then(CommandManager.literal("effect")
                        .then(CommandManager.literal("give")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> executeGiveEffect(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))
                        .then(CommandManager.literal("happiness")
                                .then(CommandManager.literal("give")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> executeHappinessGive(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "seconds"))))))
                                .then(CommandManager.literal("clear")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(ctx -> executeHappinessClear(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))))))
                        .then(CommandManager.literal("gloom")
                                .then(CommandManager.literal("give")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> executeGloomGive(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "seconds"))))))
                                .then(CommandManager.literal("clear")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(ctx -> executeGloomClear(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))))))
                )
                .then(CommandManager.literal("phantom")
                        .then(CommandManager.literal("spawn")
                                .then(CommandManager.argument("skin_player", StringArgumentType.word())
                                        .then(CommandManager.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> net.minecraft.command.CommandSource.suggestMatching(java.util.List.of("screamer", "echo", "static", "stalker", "dejavu"), builder))
                                                .executes(ctx -> executePhantomSpawn(ctx.getSource(), StringArgumentType.getString(ctx, "skin_player"), StringArgumentType.getString(ctx, "type"))))
                                        .executes(ctx -> executePhantomSpawn(ctx.getSource(), StringArgumentType.getString(ctx, "skin_player")))))
                        .then(CommandManager.literal("clear")
                                .executes(ctx -> executePhantomClear(ctx.getSource())))
                )
                .then(CommandManager.literal("unlock")
                        .then(CommandManager.literal("nether")
                                .executes(ctx -> executeUnlockNether(ctx.getSource())))
                        .then(CommandManager.literal("end")
                                .executes(ctx -> executeUnlockEnd(ctx.getSource()))))
                .then(CommandManager.literal("lock")
                        .then(CommandManager.literal("nether")
                                .executes(ctx -> executeLockNether(ctx.getSource())))
                        .then(CommandManager.literal("end")
                                .executes(ctx -> executeLockEnd(ctx.getSource()))))
                .then(CommandManager.literal("nethercorruption")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        .executes(ctx -> {
                            com.project3.event.NetherCorruptionEvent.trigger(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(
                                () -> Text.literal("§cNether Corruption Event запущен").formatted(Formatting.RED), false);
                            return 1;
                        })
                        .then(CommandManager.literal("restore")
                                .executes(ctx -> {
                                    com.project3.event.NetherCorruptionEvent.restoreAll(ctx.getSource().getServer());
                                    ctx.getSource().sendFeedback(
                                        () -> Text.literal("§aВосстановление блоков после Nether Corruption запущено").formatted(Formatting.GREEN), false);
                                    return 1;
                                }))
                )
                .then(buildAchievementCommand())
                .then(CommandManager.literal("border")
                        .then(CommandManager.literal("show")
                                .executes(ctx -> executeBorderShow(ctx.getSource())))
                        .then(CommandManager.literal("nearest")
                                .executes(ctx -> executeBorderNearest(ctx.getSource())))
                )
        );

    }

    // ─── achievement sub-command tree ───────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildAchievementCommand() {
        return CommandManager.literal("achievement")
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("title", StringArgumentType.greedyString())
                                        .then(CommandManager.argument("rarity", StringArgumentType.word())
                                                .then(CommandManager.argument("trigger", StringArgumentType.greedyString())
                                                        .executes(ctx -> executeAchievementAdd(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "title"),
                                                                StringArgumentType.getString(ctx, "rarity"),
                                                                StringArgumentType.getString(ctx, "trigger"),
                                                                null))
                                                )
                                                .executes(ctx -> executeAchievementAdd(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "title"),
                                                        StringArgumentType.getString(ctx, "rarity"),
                                                        null,
                                                        null))
                                        )
                                )
                        )
                )
                .then(CommandManager.literal("list")
                        .executes(ctx -> executeAchievementList(ctx.getSource())))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(ctx -> executeAchievementRemove(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("progress")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> executeAchievementProgress(ctx.getSource(),
                                        EntityArgumentType.getPlayer(ctx, "player")))))
                .then(CommandManager.literal("reset")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> executeAchievementReset(ctx.getSource(),
                                        EntityArgumentType.getPlayer(ctx, "player")))))
                .then(CommandManager.literal("next")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> executeAchievementNext(ctx.getSource(),
                                        EntityArgumentType.getPlayer(ctx, "player"), 1))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeAchievementNext(ctx.getSource(),
                                                EntityArgumentType.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    // ─── achievement add ────────────────────────────────────────────────────

    private static int executeAchievementAdd(ServerCommandSource source, String id,
                                              String titleAndDesc, String rarityStr,
                                              String triggerStr, String specialEffect) {
        // titleAndDesc format: "Title" "Description" or just "Title"
        final String title;
        final String description;
        int quoteIdx = titleAndDesc.indexOf('"', 1);
        if (quoteIdx > 0) {
            title = titleAndDesc.substring(0, quoteIdx).replace("\"", "");
            description = titleAndDesc.substring(quoteIdx).replace("\"", "").trim();
        } else {
            title = titleAndDesc;
            description = "";
        }

        AchievementRarity rarity = AchievementRarity.fromString(rarityStr);

        final String finalTriggerStr;
        if (triggerStr == null || triggerStr.isEmpty()) {
            // Auto-detect trigger from description
            finalTriggerStr = AchievementDefinition.autoDetectTrigger(description).toString();
        } else {
            finalTriggerStr = triggerStr;
        }

        AchievementManager manager = Project3Mod.ACHIEVEMENT_MANAGER;
        if (manager.getAchievementById(id) != null) {
            source.sendFeedback(() -> Text.literal("Достижение с ID '" + id + "' уже существует!").formatted(Formatting.RED), false);
            return 0;
        }

        manager.addAchievement(id, title, description, rarity, finalTriggerStr, specialEffect);
        source.sendFeedback(() -> Text.literal("Достижение '" + title + "' добавлено!").formatted(Formatting.GREEN), true);
        return 1;
    }

    // ─── achievement list ─────────────────────────────────────────────────

    private static int executeAchievementList(ServerCommandSource source) {
        AchievementManager manager = Project3Mod.ACHIEVEMENT_MANAGER;
        List<AchievementDefinition> achievements = manager.getAchievements();

        if (achievements.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Нет зарегистрированных достижений.").formatted(Formatting.YELLOW), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§6=== Список достижений ==="), false);
        for (AchievementDefinition ach : achievements) {
            source.sendFeedback(() -> Text.literal(
                    String.format("§7[%s] §r%s §7— %s §8(%s)",
                            ach.getId(), ach.getTitle(), ach.getDescription(), ach.getRarity().getDisplayName())
            ), false);
        }
        return 1;
    }

    // ─── achievement remove ─────────────────────────────────────────────────

    private static int executeAchievementRemove(ServerCommandSource source, String id) {
        AchievementManager manager = Project3Mod.ACHIEVEMENT_MANAGER;
        if (manager.removeAchievement(id)) {
            source.sendFeedback(() -> Text.literal("Достижение '" + id + "' удалено.").formatted(Formatting.GREEN), true);
            return 1;
        } else {
            source.sendFeedback(() -> Text.literal("Достижение '" + id + "' не найдено.").formatted(Formatting.RED), false);
            return 0;
        }
    }

    // ─── achievement progress ───────────────────────────────────────────────

    private static int executeAchievementProgress(ServerCommandSource source, ServerPlayerEntity player) {
        AchievementManager manager = Project3Mod.ACHIEVEMENT_MANAGER;
        Project3State state = Project3State.getOrCreate(source.getServer());
        String text = manager.getProgressText(player.getUuid(), state);
        source.sendFeedback(() -> Text.literal(text).formatted(Formatting.AQUA), false);
        return 1;
    }

    // ─── achievement reset ──────────────────────────────────────────────────

    private static int executeAchievementReset(ServerCommandSource source, ServerPlayerEntity player) {
        AchievementManager manager = Project3Mod.ACHIEVEMENT_MANAGER;
        Project3State state = Project3State.getOrCreate(source.getServer());
        manager.resetPlayer(player.getUuid(), state);
        manager.syncAdvancementsToMatchIndex(player, 0);
        source.sendFeedback(() -> Text.literal("Прогресс достижений игрока " + player.getName().getString() + " сброшен.").formatted(Formatting.GREEN), true);
        return 1;
    }

    // ─── player next ────────────────────────────────────────────────────────

    private static int executePlayerNext(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            AchievementManager manager = Project3Mod.ACHIEVEMENT_MANAGER;
            Project3State state = Project3State.getOrCreate(source.getServer());
            Set<String> completed = state.getCompletedAchievements(player.getUuid());
            
            List<AchievementDefinition> active = new ArrayList<>();
            for (AchievementDefinition def : manager.getAchievements()) {
                if (completed.contains(def.getId())) {
                    continue;
                }
                String parentId = def.getParentId();
                if (parentId.equals("root") || parentId.equals("p3:root") || completed.contains(parentId)) {
                    active.add(def);
                }
            }

            if (!active.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[Текущие задачи]:"), false);
                for (AchievementDefinition def : active) {
                    source.sendFeedback(() -> Text.literal("  - §f" + def.getTitle() + " §7— " + def.getDescription()), false);
                }
            } else {
                source.sendFeedback(() -> Text.literal("§aВсе достижения выполнены!"), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("Эту команду может использовать только игрок.").formatted(Formatting.RED), false);
            return 0;
        }
    }

    // ─── achievement next ───────────────────────────────────────────────────

    private static int executeAchievementNext(ServerCommandSource source, ServerPlayerEntity player, int count) {
        AchievementManager manager = Project3Mod.ACHIEVEMENT_MANAGER;
        Project3State state = Project3State.getOrCreate(source.getServer());
        int completedCount = 0;

        for (int i = 0; i < count; i++) {
            Set<String> completed = state.getCompletedAchievements(player.getUuid());

            // Find the first uncompleted active achievement
            AchievementDefinition activeDef = null;
            for (AchievementDefinition def : manager.getAchievements()) {
                if (completed.contains(def.getId())) {
                    continue;
                }
                String parentId = def.getParentId();
                if (parentId.equals("root") || parentId.equals("p3:root") || completed.contains(parentId)) {
                    activeDef = def;
                    break;
                }
            }

            if (activeDef != null) {
                final AchievementDefinition finalActive = activeDef;
                manager.completeAchievement(player, state, activeDef);

                // Notify player
                player.sendMessage(Text.literal("§6[Админ] §eЗадача §f" + activeDef.getTitle() + " §eбыла принудительно выполнена."), false);
                
                source.sendFeedback(() -> Text.literal("Прогресс игрока " + player.getName().getString() + " продвинут (выполнено " + finalActive.getTitle() + ").").formatted(Formatting.GREEN), true);
                completedCount++;
            } else {
                break;
            }
        }

        if (completedCount == 0) {
            source.sendFeedback(() -> Text.literal("У игрока нет доступных невыполненных задач.").formatted(Formatting.RED), true);
        } else {
            final int finalCompletedCount = completedCount;
            source.sendFeedback(() -> Text.literal("Успешно пропущено задач: " + finalCompletedCount).formatted(Formatting.GREEN), true);
        }

        return completedCount;
    }

    private static int executeSetProgress(ServerCommandSource source, int level) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        state.setProgressLevel(level);
        
        // Sync player state to all online players
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
        com.project3.player.PlayerStateManager.syncPlayerState(player, state);
        }

        source.sendFeedback(() -> Text.literal("Уровень прогресса установлен на: " + level).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int executeGiveEffect(ServerCommandSource source, ServerPlayerEntity player) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        state.setUnnamedEffectActive(player.getUuid(), true);
        com.project3.player.PlayerStateManager.syncPlayerState(player, state);

        source.sendFeedback(() -> Text.literal("Безымянный эффект выдан игроку " + player.getName().getString()).formatted(Formatting.GREEN), true);
        player.sendMessage(Text.literal("§cВы почувствовали странное присутствие... На вас наложен безымянный эффект."), false);
        return 1;
    }

    private static int executeHappinessGive(ServerCommandSource source, ServerPlayerEntity player, int seconds) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        long ticks = (long) seconds * 20L;
        com.project3.player.PlayerStateManager.grantHappiness(player, state, ticks);
        source.sendFeedback(() -> Text.literal("Эффект счастья выдан игроку " + player.getName().getString() + " на " + seconds + " сек.").formatted(Formatting.GREEN), true);
        player.sendMessage(Text.literal("§aВы чувствуете невероятное счастье и прилив сил!"), false);
        player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        return 1;
    }

    private static int executeHappinessClear(ServerCommandSource source, ServerPlayerEntity player) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        state.setHappinessTicksLeft(player.getUuid(), 0L);
        player.removeStatusEffect(ModRegistries.HAPPINESS_EFFECT);
        com.project3.player.PlayerStateManager.syncPlayerState(player, state);
        source.sendFeedback(() -> Text.literal("Эффект счастья снят с игрока " + player.getName().getString()).formatted(Formatting.GREEN), true);
        player.sendMessage(Text.literal("§eОщущение счастья угасло..."), false);
        return 1;
    }

    private static int executeGloomGive(ServerCommandSource source, ServerPlayerEntity player, int seconds) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        long ticks = (long) seconds * 20L;
        com.project3.player.PlayerStateManager.grantGloom(player, state, ticks);
        source.sendFeedback(() -> Text.literal("Эффект уныния выдан игроку " + player.getName().getString() + " на " + seconds + " сек.").formatted(Formatting.GREEN), true);
        player.sendMessage(Text.literal("§cВы почувствовали внезапное уныние и слабость..."), false);
        player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);
        return 1;
    }

    private static int executeGloomClear(ServerCommandSource source, ServerPlayerEntity player) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        state.setGloomTicksLeft(player.getUuid(), 0L);
        state.setGloomPermanent(player.getUuid(), false);
        player.removeStatusEffect(ModRegistries.GLOOM_EFFECT);
        com.project3.player.PlayerStateManager.syncPlayerState(player, state);
        source.sendFeedback(() -> Text.literal("Эффект уныния снят с игрока " + player.getName().getString()).formatted(Formatting.GREEN), true);
        player.sendMessage(Text.literal("§aУныние прошло. Вы чувствуете облегчение."), false);
        player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        return 1;
    }

    // ─── start ───────────────────────────────────────────────────────────────

    private static int executeStart(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        Project3State state = Project3State.getOrCreate(server);

        synchronized (com.project3.world.CalibrationManager.CALIBRATION_LOCK) {
            if (state.isSeasonStarted() || com.project3.world.CalibrationManager.calibrationTicksLeft > 0) {
                source.sendFeedback(() -> Text.literal("Сезон уже запущен или идёт калибровка!").formatted(Formatting.RED), false);
                return 0;
            }

            // Initialize calibration countdown to 1200 ticks (60 seconds)
            com.project3.world.CalibrationManager.calibrationTicksLeft = 1200;
        }

        ServerWorld overworld = server.getOverworld();
        WorldBorder border = overworld.getWorldBorder();
        
        // Find safe spawn coordinates
        BlockPos spawnPos = overworld.getSpawnPoint().getPos();
        int topY = overworld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, spawnPos.getX(), spawnPos.getZ());
        double spawnY = Math.max(overworld.getBottomY() + 2, topY);
        double spawnX = spawnPos.getX() + 0.5;
        double spawnZ = spawnPos.getZ() + 0.5;

        // Center the border at spawn and lock it to size 8 during calibration
        border.setCenter(spawnX, spawnZ);
        border.setWarningBlocks(3);
        border.setSafeZone(1.0);
        border.setSize(8);

        // Teleport all online players to spawn safely
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        for (ServerPlayerEntity player : players) {
            player.teleport(overworld, spawnX, spawnY, spawnZ, java.util.Set.of(), player.getYaw(), player.getPitch(), true);
            
            // System Boot Titles (cinematic effect)
            player.networkHandler.sendPacket(new TitleS2CPacket(
                    Text.literal("§cПРИВЕТСТВУЕМ ВАС!")));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("§eДобро пожаловать в мир!")));
            player.playSound(SoundEvents.EVENT_RAID_HORN.value(), 1.0f, 1.0f);

            // Apply cinematic calibration status effects
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 1200, 4, false, false)); // Slowness V keeps them in place
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1200, 4, false, false)); // Immortality/high resistance during calibration
        }

        // System boot-up status logs are deliberately staged for the cinematic sequence.
        Project3Mod.schedule(0, () -> server.getPlayerManager().broadcast(
                Text.literal("§7[Система] §aЗапускаю систему... OK"), false));
        Project3Mod.schedule(20, () -> server.getPlayerManager().broadcast(
                Text.literal("§7[Система] §aПодготавливаю временную зону... OK"), false));
        Project3Mod.schedule(40, () -> server.getPlayerManager().broadcast(
                Text.literal("§7[Система] §aЦентрирую начальную точку... OK"), false));

        // Schedule welcome messages and border expansion
        Project3Mod.schedule(60, () -> {
            Text sender = Text.literal("[Система]").styled(style -> style.withColor(0xAAAAAA).withItalic(true));
            Text msg = Text.literal(": Добро пожаловать в мир project3 !").styled(style -> style.withColor(0xFFFFFF).withItalic(false));
            Text combined = Text.empty().append(sender).append(msg);
            server.getPlayerManager().broadcast(combined, false);
        });

        Project3Mod.schedule(100, () -> {
            Text sender = Text.literal("[Система]").styled(style -> style.withColor(0xAAAAAA).withItalic(true));
            Text msg = Text.literal(": Приятной игры!").styled(style -> style.withColor(0xFFFFFF).withItalic(false));
            Text combined = Text.empty().append(sender).append(msg);
            server.getPlayerManager().broadcast(combined, false);
        });

        source.sendFeedback(() -> Text.literal("Запущена калибровка сезона (60 сек).").formatted(Formatting.GREEN), true);
        return 1;
    }

    // ─── status ──────────────────────────────────────────────────────────────

    private static int executeStatus(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        Project3State state = Project3State.getOrCreate(server);

        if (!state.isSeasonStarted()) {
            source.sendFeedback(() -> Text.literal("Сезон не начат.").formatted(Formatting.YELLOW), false);
            return 0;
        }

        long elapsedMs = state.getElapsedMs();
        long hours = elapsedMs / 3_600_000L;
        long minutes = (elapsedMs % 3_600_000L) / 60_000L;

        boolean netherLocked = !state.isNetherForceUnlocked() && (elapsedMs < 72L * 3_600_000L);
        boolean endLocked    = !state.isEndForceUnlocked() && (elapsedMs < 240L * 3_600_000L);

        source.sendFeedback(() -> Text.literal(String.format(
                "Сезон идёт: %dч %dм | Ад заблокирован: %s | Край заблокирован: %s",
                hours, minutes,
                netherLocked ? "Да" : "Нет",
                endLocked    ? "Да" : "Нет"
        )).formatted(Formatting.AQUA), false);
        return 1;
    }

    // ─── reset ───────────────────────────────────────────────────────────────

    private static int executeReset(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        Project3State state = Project3State.getOrCreate(server);
        state.reset();
        
        // Reset achievement index for all online players and sync
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            state.resetAchievements(player.getUuid());
            Project3Mod.ACHIEVEMENT_MANAGER.syncAdvancementsToMatchIndex(player, 0);
        }

        // Reset world border on reset
        ServerWorld overworld = server.getOverworld();
        if (overworld != null) {
            WorldBorder border = overworld.getWorldBorder();
            border.setSize(5.9999968E7);
            border.setCenter(0.0, 0.0);
        }

        source.sendFeedback(() -> Text.literal("Сезон сброшен.").formatted(Formatting.RED), true);
        return 1;
    }

    private static int executeStartWarning(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("§c[Внимание]: §eЗапуск сезона обнулит текущее состояние и заблокирует игроков на 60 сек калибровки. Введите §a/p3 start confirm §eдля подтверждения."), false);
        return 1;
    }

    private static int executeResetWarning(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("§c[Внимание]: §eСброс сезона полностью удалит весь прогресс достижений и сбросит все параметры игроков. Введите §a/p3 reset confirm §eдля подтверждения."), false);
        return 1;
    }

    private static int executePlayerStatus(ServerCommandSource source, ServerPlayerEntity target) {
        MinecraftServer server = source.getServer();
        Project3State state = Project3State.getOrCreate(server);

        int index = state.getCurrentAchievementIndex(target.getUuid());
        var achievements = Project3Mod.ACHIEVEMENT_MANAGER.getAchievements();
        final String finalActiveTitle;
        if (index >= 0 && index < achievements.size()) {
            finalActiveTitle = achievements.get(index).getTitle();
        } else if (index >= achievements.size()) {
            finalActiveTitle = "Все завершены! 🎉";
        } else {
            finalActiveTitle = "Нет";
        }

        long happiness = state.getHappinessTicksLeft(target.getUuid());
        boolean permanentGloom = state.isGloomPermanent(target.getUuid());
        long gloomTicks = state.getGloomTicksLeft(target.getUuid());
        boolean unnamedActive = state.isUnnamedEffectActive(target.getUuid());

        source.sendFeedback(() -> Text.literal(String.format(
                "§bСтатус игрока §e%s§b:\n" +
                "§7- §fТекущий индекс квеста: §a%d/%d§f (§e%s§f)\n" +
                "§7- §fВремя Счастья: §a%d сек\n" +
                "§7- §fПостоянное Уныние: §a%s\n" +
                "§7- §fВремя Уныния: §a%d сек\n" +
                "§7- §fБезымянный Эффект: §a%s",
                target.getName().getString(),
                index, achievements.size(), finalActiveTitle,
                happiness / 20,
                permanentGloom ? "Да" : "Нет",
                gloomTicks / 20,
                unnamedActive ? "Да" : "Нет"
        )), false);
        return 1;
    }

    private static int executeUnlockNether(ServerCommandSource source) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        state.setNetherForceUnlocked(true);
        source.sendFeedback(() -> Text.literal("Портал в Незер успешно разблокирован для всех игроков.").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int executeUnlockEnd(ServerCommandSource source) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        state.setEndForceUnlocked(true);
        source.sendFeedback(() -> Text.literal("Портал в Энд успешно разблокирован для всех игроков.").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int executeLockNether(ServerCommandSource source) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        state.setNetherForceUnlocked(false);
        source.sendFeedback(() -> Text.literal("Портал в Незер снова заблокирован согласно таймеру сезона.").formatted(Formatting.RED), true);
        return 1;
    }

    private static int executeLockEnd(ServerCommandSource source) {
        Project3State state = Project3State.getOrCreate(source.getServer());
        state.setEndForceUnlocked(false);
        source.sendFeedback(() -> Text.literal("Портал в Энд снова заблокирован согласно таймеру сезона.").formatted(Formatting.RED), true);
        return 1;
    }

    private static int executePhantomSpawn(ServerCommandSource source, String skinPlayerName) {
        return executePhantomSpawn(source, skinPlayerName, "screamer");
    }

    private static int executePhantomSpawn(ServerCommandSource source, String skinPlayerName, String type) {
        try {
            ServerPlayerEntity executor = source.getPlayerOrThrow();
            MinecraftServer server = source.getServer();

            // Resolve the target skin profile
            UUID skinUuid = null;
            String resolvedName = skinPlayerName;
            com.mojang.authlib.GameProfile targetProfile = null;

            // 1. Check if the player is online
            ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(skinPlayerName);
            if (onlinePlayer != null) {
                targetProfile = onlinePlayer.getGameProfile();
                skinUuid = onlinePlayer.getUuid();
                resolvedName = onlinePlayer.getGameProfile().name();
            } else {
                // 2. Check if the player is cached in the server UserCache
                var profileOpt = server.getApiServices().nameToIdCache().findByName(skinPlayerName);
                if (profileOpt.isPresent()) {
                    var entry = profileOpt.get();
                    targetProfile = new com.mojang.authlib.GameProfile(entry.id(), entry.name());
                    skinUuid = targetProfile.id();
                    resolvedName = targetProfile.name();
                }
            }

            // 3. Fallback to offline UUID if we couldn't find the skin profile
            if (skinUuid == null) {
                skinUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + skinPlayerName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                source.sendFeedback(() -> Text.literal("§6[Предупреждение]: §eИгрок '" + skinPlayerName + "' не найден на сервере / в кэше. Скин может не загрузиться (используется оффлайн UUID)."), false);
            }

            if (targetProfile == null) {
                targetProfile = new com.mojang.authlib.GameProfile(skinUuid, resolvedName);
            }

            // Spawn the custom server-side phantom NPC!
            com.project3.entity.PhantomReplicator.spawnCustomPhantom(executor, targetProfile, type);

            // Also place a dim LIGHT block at phantom feet/spawn area for glow
            double yawRad = Math.toRadians(executor.getYaw());
            double px = executor.getX() - Math.sin(yawRad) * 3.0;
            double py = executor.getY();
            double pz = executor.getZ() + Math.cos(yawRad) * 3.0;
            BlockPos phantomLightPos = new BlockPos((int)Math.floor(px), (int)Math.floor(py), (int)Math.floor(pz));
            ServerWorld world = executor.getEntityWorld();
            if (world.getBlockState(phantomLightPos).isAir()) {
                world.setBlockState(phantomLightPos, net.minecraft.block.Blocks.LIGHT.getDefaultState().with(net.minecraft.block.LightBlock.LEVEL_15, 6));
                com.project3.player.PlayerCooldowns.COMMAND_SPAWNED_LIGHTS.computeIfAbsent(executor.getUuid(), uuid -> new ArrayList<>()).add(phantomLightPos);
            }

            final String finalName = resolvedName;
            final String finalType = type;
            source.sendFeedback(() -> Text.literal("§aФантом со скином §e" + finalName + " §aи типом поведения §b" + finalType + " §aуспешно заспавнен!").formatted(Formatting.GREEN), true);
            source.sendFeedback(() -> Text.literal("§7Используйте §e/p3 phantom clear§7, чтобы убрать всех призванных фантомов."), false);

            return 1;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("Эту команду может использовать только игрок.").formatted(Formatting.RED), false);
            return 0;
        }
    }

    private static int executePhantomClear(ServerCommandSource source) {
        try {
            ServerPlayerEntity executor = source.getPlayerOrThrow();
            
            // Remove server-side command-spawned phantoms
            com.project3.entity.PhantomReplicator.clearCommandPhantoms(executor);

            // Remove light blocks
            List<BlockPos> lights = com.project3.player.PlayerCooldowns.COMMAND_SPAWNED_LIGHTS.remove(executor.getUuid());
            if (lights != null) {
                ServerWorld world = (ServerWorld) executor.getEntityWorld();
                if (world != null) {
                    for (BlockPos pos : lights) {
                        if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.LIGHT)) {
                            world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }

            source.sendFeedback(() -> Text.literal("§aВсе заспавненные фантомы убраны.").formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("Эту команду может использовать только игрок.").formatted(Formatting.RED), false);
            return 0;
        }
    }


    // ─── border show ─────────────────────────────────────────────────────────

    private static int executeBorderShow(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) {
                source.sendFeedback(() -> Text.literal("Команда работает только в обычном мире.").formatted(Formatting.RED), false);
                return 0;
            }

            BlockPos spawnPos = world.getSpawnPoint().getPos();
            double spawnX = spawnPos.getX() + 0.5;
            double spawnZ = spawnPos.getZ() + 0.5;

            // Spawn a ring of particles along the border in all directions
            int points = 72;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points;
                double nx = Math.cos(angle);
                double nz = Math.sin(angle);

                double maxR = WorldBorderManager.getMaxRadius(spawnX, spawnZ,
                        spawnX + nx * 20000.0, spawnZ + nz * 20000.0);
                double bx = spawnX + nx * maxR;
                double bz = spawnZ + nz * maxR;

                BlockPos surfacePos = world.getTopPosition(
                        net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                        new BlockPos((int) bx, 0, (int) bz));
                double by = surfacePos.getY() + 1.0;

                world.spawnParticles(
                        ParticleTypes.FLAME,
                        bx, by, bz,
                        1, 0.1, 0.1, 0.1, 0.01
                );
            }

            source.sendFeedback(() -> Text.literal("Граница отмечена огненными частицами (исчезнут через 5 сек).").formatted(Formatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("Эту команду может использовать только игрок.").formatted(Formatting.RED), false);
            return 0;
        }
    }

    // ─── border nearest ──────────────────────────────────────────────────────

    private static int executeBorderNearest(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) {
                source.sendFeedback(() -> Text.literal("Команда работает только в обычном мире.").formatted(Formatting.RED), false);
                return 0;
            }

            BlockPos spawnPos = world.getSpawnPoint().getPos();
            double spawnX = spawnPos.getX() + 0.5;
            double spawnZ = spawnPos.getZ() + 0.5;

            double px = player.getX();
            double pz = player.getZ();

            double dx = px - spawnX;
            double dz = pz - spawnZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            double angle = Math.atan2(dz, dx);

            // Find distance to border in the player's current direction
            double maxR = WorldBorderManager.getMaxRadius(spawnX, spawnZ, px, pz);
            double remaining = maxR - dist;

            source.sendFeedback(() -> Text.literal(String.format(
                    "§6=== Граница ===\n" +
                    "§7Расстояние от спавна: §f%.0f блоков\n" +
                    "§7Макс. радиус в этом направлении: §f%.0f блоков\n" +
                    "§7До границы: §e%.0f блоков\n" +
                    "§7Направление: §b%s (%.0f°)",
                    dist, maxR, remaining,
                    angleToString(angle), Math.toDegrees(angle)
            )), false);

            // Teleport player to the border edge in their current direction
            if (remaining > 0) {
                double tx = spawnX + dx / dist * maxR;
                double tz = spawnZ + dz / dist * maxR;
                BlockPos surfacePos = world.getTopPosition(
                        net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                        new BlockPos((int) tx, 0, (int) tz));
                double ty = surfacePos.getY() + 1.0;
                player.teleport(world, tx, ty, tz, java.util.Set.of(), player.getYaw(), player.getPitch(), true);
                source.sendFeedback(() -> Text.literal("Телепортированы к ближайшей точке границы.").formatted(Formatting.GREEN), false);
            } else {
                source.sendFeedback(() -> Text.literal("Вы уже за границей!").formatted(Formatting.RED), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("Эту команду может использовать только игрок.").formatted(Formatting.RED), false);
            return 0;
        }
    }

    private static String angleToString(double angle) {
        double deg = Math.toDegrees(angle);
        if (deg < -157.5 || deg >= 157.5) return "Запад";
        if (deg < -112.5) return "Юго-Запад";
        if (deg < -67.5) return "Юг";
        if (deg < -22.5) return "Юго-Восток";
        if (deg < 22.5) return "Восток";
        if (deg < 67.5) return "Северо-Восток";
        if (deg < 112.5) return "Север";
        return "Северо-Запад";
    }
}
