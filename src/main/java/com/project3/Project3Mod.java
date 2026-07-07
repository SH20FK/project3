package com.project3;

import com.project3.block.ProducerBlock;
import com.project3.block.entity.ProducerBlockEntity;
import java.util.UUID;
import com.project3.command.Project3Command;
import com.project3.network.CameraRotatePayload;
import com.project3.network.OpenInventoryPayload;
import com.project3.achievement.AchievementManager;
import com.project3.state.Project3State;
import net.minecraft.stat.Stat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.world.rule.GameRules;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main server-side entry point for Project3.
 *
 * Registers:
 *  - ProducerBlock + ProducerBlockEntity
 *  - Network payloads (CameraRotate, ShowTutorial, HideTutorial)
 *  - Commands (/p3)
 *  - Server tick event (world-border wall, quest tick)
 *  - UseBlockCallback (Nether portal lock & End portal frame lock)
 */
public class Project3Mod implements ModInitializer {

    public static final String MODID = "p3";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public static final Identifier OPEN_INVENTORY_STAT_ID = Identifier.of("minecraft", "open_inventory");
    public static final Identifier GIVE_ALLAY_FLOWER_STAT_ID = Identifier.of("p3", "give_allay_flower");
    public static final Identifier MACE_KILL_50_BLOCKS_STAT_ID = Identifier.of("p3", "mace_kill_50_blocks");
    public static final Identifier SHOOT_FIREWORK_CROSSBOW_STAT_ID = Identifier.of("p3", "shoot_firework_crossbow");
    public static final java.util.Map<java.util.UUID, Float> LAST_MACE_ATTACK_FALL_DISTANCE = new java.util.concurrent.ConcurrentHashMap<>();
    public static final RegistryKey<World> GLOOM_VOID_WORLD_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, Identifier.of("p3", "gloom_void"));
    /** Cooldown map for wall penalty messages to avoid chat spam. Key = player UUID, value = last penalty time millis */
    private static final java.util.Map<java.util.UUID, Long> WALL_MESSAGE_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long WALL_MESSAGE_COOLDOWN_MS = 5_000L;
    /** Tracks wall violation count per player for graduated penalties */
    private static final java.util.Map<java.util.UUID, Integer> WALL_VIOLATION_COUNT = new java.util.concurrent.ConcurrentHashMap<>();
    /** Tracks players who have received the pumpkin mask hint */
    private static final java.util.Set<java.util.UUID> PUMPKIN_HINT_SENT = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static final java.util.Map<java.util.UUID, Integer> FLASHLIGHT_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> AMBIENT_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> SECTOR_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> PHANTOM_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> DEAD_SCENARIO_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> CHAT_ECHO_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> STATIC_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> DEJA_VU_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> MUSIC_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> SMOKE_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, net.minecraft.util.math.BlockPos> PLAYER_LIGHT_POSITIONS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Boolean> PORTAL_IS_LIT = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> PORTAL_STATE_TICKS = new java.util.concurrent.ConcurrentHashMap<>();

    // Virtual client-side phantom tracker maps
    public static final java.util.Map<java.util.UUID, net.minecraft.util.math.Vec3d> PHANTOM_POSITIONS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Integer> PHANTOM_ENTITY_IDS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<java.util.UUID, Float> PHANTOM_YAWS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Tracks the server-side LIGHT block placed near each player's phantom for glow effect */
    public static final java.util.Map<java.util.UUID, net.minecraft.util.math.BlockPos> PHANTOM_LIGHT_POSITIONS = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Commands spawned lights
    public static final java.util.Map<java.util.UUID, java.util.List<net.minecraft.util.math.BlockPos>> COMMAND_SPAWNED_LIGHTS = new java.util.concurrent.ConcurrentHashMap<>();

    // Gloom Void escalation tracking
    public static final java.util.Map<java.util.UUID, Integer> VOID_ESCALATION_TICKS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void initializePlayerVoidPortal(java.util.UUID uuid, net.minecraft.util.math.random.Random random) {
        PORTAL_IS_LIT.put(uuid, true);
        PORTAL_STATE_TICKS.put(uuid, 1200 + random.nextInt(800));
    }

    // ─── Scheduled Tasks Schedulers (zero listener leak) ───────────────────
    public static class ScheduledTask {
        public int remaining;
        public final Runnable action;
        public ScheduledTask(int remaining, Runnable action) {
            this.remaining = remaining;
            this.action = action;
        }
    }
    public static final java.util.List<ScheduledTask> SCHEDULED_TASKS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void schedule(int delayTicks, Runnable action) {
        SCHEDULED_TASKS.add(new ScheduledTask(delayTicks, action));
    }

    // ─── Block & BlockEntity ────────────────────────────────────────────────

    public static final ProducerBlock PRODUCER_BLOCK = new ProducerBlock();
    public static BlockEntityType<ProducerBlockEntity> PRODUCER_BLOCK_ENTITY_TYPE;

    public static final com.project3.block.PhantomBlock PHANTOM_BLOCK = new com.project3.block.PhantomBlock();
    public static BlockEntityType<com.project3.block.entity.PhantomBlockEntity> PHANTOM_BLOCK_ENTITY_TYPE;

    // Screen handler type for Producer Block GUI
    public static net.minecraft.screen.ScreenHandlerType<com.project3.block.entity.ProducerScreenHandler> PRODUCER_SCREEN_HANDLER;

    public static final net.minecraft.block.Block VOID_GLASS = new com.project3.block.VoidGlassBlock(net.minecraft.block.AbstractBlock.Settings.create()
        .registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK, net.minecraft.util.Identifier.of(MODID, "void_glass")))
        .strength(-1.0f, 3600000.0f)
        .dropsNothing()
        .allowsSpawning(net.minecraft.block.Blocks::never)
        .nonOpaque()
        .blockVision(net.minecraft.block.Blocks::never)
        .suffocates(net.minecraft.block.Blocks::never)
        .solidBlock(net.minecraft.block.Blocks::never)
        .sounds(net.minecraft.sound.BlockSoundGroup.GLASS));

    public static final net.minecraft.block.Block DEAD_SPACE = new com.project3.block.DeadSpaceBlock(net.minecraft.block.AbstractBlock.Settings.create()
        .registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK, net.minecraft.util.Identifier.of(MODID, "dead_space")))
        .strength(-1.0f, 3600000.0f)
        .mapColor(net.minecraft.block.MapColor.BLACK)
        .dropsNothing()
        .allowsSpawning(net.minecraft.block.Blocks::never)
        .solidBlock(net.minecraft.block.Blocks::never)
        .nonOpaque()
        .blockVision(net.minecraft.block.Blocks::never));

    public static final com.project3.item.AIChronometerItem AI_CHRONOMETER = new com.project3.item.AIChronometerItem(new net.minecraft.item.Item.Settings().registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM, net.minecraft.util.Identifier.of("p3", "ai_chronometer"))).maxCount(1));
    public static final com.project3.item.AIDumpAnalyzerItem AI_DUMP_ANALYZER = new com.project3.item.AIDumpAnalyzerItem(new net.minecraft.item.Item.Settings().registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM, net.minecraft.util.Identifier.of("p3", "ai_dump_analyzer"))).maxCount(1));
    public static final net.minecraft.item.Item CALMING_AMULET = new com.project3.item.CalmingAmuletItem(new net.minecraft.item.Item.Settings().registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM, net.minecraft.util.Identifier.of("p3", "calming_amulet"))).maxCount(1).maxDamage(500));

    public static final net.minecraft.world.gen.feature.Feature<net.minecraft.world.gen.feature.DefaultFeatureConfig> PRODUCER_BLOCK_FEATURE =
            new com.project3.worldgen.ProducerBlockFeature(net.minecraft.world.gen.feature.DefaultFeatureConfig.CODEC);



    public enum Act {
        NOT_STARTED,
        I,
        II,
        III,
        IV
    }

    public static Act getAct(Project3State state) {
        if (!state.isSeasonStarted()) {
            return Act.NOT_STARTED;
        }
        long elapsedMs = state.getElapsedMs();
        if (elapsedMs < 72L * 3600 * 1000L) {
            return Act.I;
        } else if (elapsedMs < 168L * 3600 * 1000L) {
            return Act.II;
        } else if (elapsedMs < 240L * 3600 * 1000L) {
            return Act.III;
        } else {
            return Act.IV;
        }
    }



    public static boolean isWearingPumpkin(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.CARVED_PUMPKIN);
    }

    // ─── Calibration ───────────────────────────────────────────────────────
    public static int calibrationTicksLeft = -1;
    public static final Object CALIBRATION_LOCK = new Object();

    public static String getProgressBar(double percent) {
        int totalBars = 20;
        int filledBars = (int) (percent / 100.0 * totalBars);
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < filledBars; i++) {
            sb.append("█");
        }
        sb.append("§7");
        for (int i = filledBars; i < totalBars; i++) {
            sb.append("░");
        }
        return sb.toString();
    }

    // ─── Achievement Manager ───────────────────────────────────────────────

    public static final AchievementManager ACHIEVEMENT_MANAGER = new AchievementManager();

    public static AchievementManager getAchievementManager() {
        return ACHIEVEMENT_MANAGER;
    }

    // ─── Timers ─────────────────────────────────────────────────────────────

    /** 72 hours in milliseconds — Nether lock duration */
    private static final long NETHER_LOCK_MS = 72L * 3_600_000L;
    /** 240 hours in milliseconds — End lock duration */
    private static final long END_LOCK_MS    = 240L * 3_600_000L;

    /** Invisible wall threshold */
    private static final int WALL_THRESHOLD = 20_000;

    @Override
    public void onInitialize() {
        LOGGER.info("Project3 initializing…");

        // ── Register Block ─────────────────────────────────────────────────
        Registry.register(Registries.BLOCK, Identifier.of(MODID, "producer_block"), PRODUCER_BLOCK);
        Registry.register(Registries.ITEM,  Identifier.of(MODID, "producer_block"),
                new net.minecraft.item.BlockItem(PRODUCER_BLOCK,
                        new net.minecraft.item.Item.Settings().registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM, net.minecraft.util.Identifier.of(MODID, "producer_block")))));



        Registry.register(Registries.BLOCK, Identifier.of(MODID, "phantom_block"), PHANTOM_BLOCK);
        Registry.register(Registries.ITEM,  Identifier.of(MODID, "phantom_block"),
                new net.minecraft.item.BlockItem(PHANTOM_BLOCK,
                        new net.minecraft.item.Item.Settings().registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM, net.minecraft.util.Identifier.of(MODID, "phantom_block")))));

        Registry.register(Registries.BLOCK, Identifier.of(MODID, "void_glass"), VOID_GLASS);
        Registry.register(Registries.ITEM,  Identifier.of(MODID, "void_glass"),
                new net.minecraft.item.BlockItem(VOID_GLASS,
                        new net.minecraft.item.Item.Settings().registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM, net.minecraft.util.Identifier.of(MODID, "void_glass")))));

        Registry.register(Registries.BLOCK, Identifier.of(MODID, "dead_space"), DEAD_SPACE);
        Registry.register(Registries.ITEM,  Identifier.of(MODID, "dead_space"),
                new net.minecraft.item.BlockItem(DEAD_SPACE,
                        new net.minecraft.item.Item.Settings().registryKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM, net.minecraft.util.Identifier.of(MODID, "dead_space")))));

        Registry.register(Registries.ITEM, Identifier.of(MODID, "ai_chronometer"), AI_CHRONOMETER);
        Registry.register(Registries.ITEM, Identifier.of(MODID, "ai_dump_analyzer"), AI_DUMP_ANALYZER);
        Registry.register(Registries.ITEM, Identifier.of(MODID, "calming_amulet"), CALMING_AMULET);

        // ── Register BlockEntityType ───────────────────────────────────────
        PRODUCER_BLOCK_ENTITY_TYPE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MODID, "producer_block"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(ProducerBlockEntity::new, PRODUCER_BLOCK).build()
        );

        PHANTOM_BLOCK_ENTITY_TYPE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MODID, "phantom_block"),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(com.project3.block.entity.PhantomBlockEntity::new, PHANTOM_BLOCK).build()
        );

        // ── Register Screen Handler ──────────────────────────────────────
        PRODUCER_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(MODID, "producer_block"),
                new net.minecraft.screen.ScreenHandlerType<com.project3.block.entity.ProducerScreenHandler>(
                    (syncId, playerInv) -> new com.project3.block.entity.ProducerScreenHandler(syncId, playerInv, new net.minecraft.inventory.SimpleInventory(2), net.minecraft.util.math.BlockPos.ORIGIN),
                    net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES
                )
        );

        // ── Register Worldgen Feature ──────────────────────────────────────
        Registry.register(Registries.FEATURE, Identifier.of(MODID, "producer_block"), PRODUCER_BLOCK_FEATURE);

        // Producer blocks are placed exclusively via the CHUNK_LOAD listener below
        // (which respects season state). The worldgen feature is registered for
        // data-pack compatibility but intentionally not bound to any biome.

        // ── Register Network Payloads (S→C) ───────────────────────────────
        PayloadTypeRegistry.playS2C().register(CameraRotatePayload.ID, CameraRotatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.AchievementSyncPayload.ID, com.project3.network.AchievementSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.PlayerStateSyncPayload.ID, com.project3.network.PlayerStateSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.ChunkReloadPayload.ID, com.project3.network.ChunkReloadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.ShaderFlashPayload.ID, com.project3.network.ShaderFlashPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.SpawnPhantomPayload.ID, com.project3.network.SpawnPhantomPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.RemovePhantomPayload.ID, com.project3.network.RemovePhantomPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.PhantomHeadSnapPayload.ID, com.project3.network.PhantomHeadSnapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.SpawnStatuePayload.ID, com.project3.network.SpawnStatuePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.FogTargetPayload.ID, com.project3.network.FogTargetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.ParanoiaPayload.ID, com.project3.network.ParanoiaPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.DreadPayload.ID, com.project3.network.DreadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.project3.network.OpenProducerScreenPayload.ID, com.project3.network.OpenProducerScreenPayload.CODEC);

        // Register custom open_inventory stat registry key and C2S network payload
        Registry.register(Registries.CUSTOM_STAT, OPEN_INVENTORY_STAT_ID, OPEN_INVENTORY_STAT_ID);
        net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(OPEN_INVENTORY_STAT_ID);

        Registry.register(Registries.CUSTOM_STAT, GIVE_ALLAY_FLOWER_STAT_ID, GIVE_ALLAY_FLOWER_STAT_ID);
        net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(GIVE_ALLAY_FLOWER_STAT_ID);

        Registry.register(Registries.CUSTOM_STAT, MACE_KILL_50_BLOCKS_STAT_ID, MACE_KILL_50_BLOCKS_STAT_ID);
        net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(MACE_KILL_50_BLOCKS_STAT_ID);

        Registry.register(Registries.CUSTOM_STAT, SHOOT_FIREWORK_CROSSBOW_STAT_ID, SHOOT_FIREWORK_CROSSBOW_STAT_ID);
        net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(SHOOT_FIREWORK_CROSSBOW_STAT_ID);

        PayloadTypeRegistry.playC2S().register(OpenInventoryPayload.ID, OpenInventoryPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.project3.network.AdminToolUsePayload.ID, com.project3.network.AdminToolUsePayload.CODEC);
        
        ServerPlayNetworking.registerGlobalReceiver(com.project3.network.AdminToolUsePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                com.project3.network.AdminToolUseReceiver.handle(payload, player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenInventoryPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                Stat<?> stat = net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(OPEN_INVENTORY_STAT_ID);
                player.incrementStat(stat);
            });
        });

        // ── Register Commands ──────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register(Project3Command::register);

        // ── Sync Achievements on player join ────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Project3State state = Project3State.getOrCreate(server);
            ACHIEVEMENT_MANAGER.syncAdvancements(handler.player, state);
            ACHIEVEMENT_MANAGER.syncActiveAchievement(handler.player, state);
            syncPlayerState(handler.player, state);

            // Welcome message with progress
            ServerPlayerEntity player = handler.player;
            int completed = state.getCompletedAchievements(player.getUuid()).size();
            int total = 75;
            long happiness = state.getHappinessTicksLeft(player.getUuid());
            boolean isGloom = state.isGloomPermanent(player.getUuid()) || state.getGloomTicksLeft(player.getUuid()) > 0;

            player.sendMessage(Text.literal("§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
            player.sendMessage(Text.literal("§6§lProject3 §7| §fДобро пожаловать, " + player.getName().getString()), false);
            player.sendMessage(Text.literal("§7Прогресс: §a" + completed + "§7/§f" + total + " ачивок"), false);
            if (happiness > 0) {
                int minutes = (int) (happiness / 1200);
                player.sendMessage(Text.literal("§7Состояние: §a§lСчастье §7(§f" + minutes + " мин§7)"), false);
            } else if (isGloom) {
                player.sendMessage(Text.literal("§7Состояние: §4§lУныние"), false);
            } else {
                player.sendMessage(Text.literal("§7Состояние: §7Нейтрально"), false);
            }
            player.sendMessage(Text.literal("§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            try {
                BlockPos lightPos = PLAYER_LIGHT_POSITIONS.remove(player.getUuid());
                if (lightPos != null) {
                    net.minecraft.world.World entityWorld = player.getEntityWorld();
                    if (entityWorld instanceof ServerWorld world && world.getBlockState(lightPos).isOf(Blocks.LIGHT)) {
                        world.setBlockState(lightPos, Blocks.AIR.getDefaultState());
                    }
                }
                // Remove phantom glow light block on disconnect
                BlockPos phantomLight = PHANTOM_LIGHT_POSITIONS.remove(player.getUuid());
                if (phantomLight != null) {
                    net.minecraft.world.World entityWorld = player.getEntityWorld();
                    if (entityWorld instanceof ServerWorld world && world.getBlockState(phantomLight).isOf(Blocks.LIGHT)) {
                        world.setBlockState(phantomLight, Blocks.AIR.getDefaultState());
                    }
                }
                PHANTOM_POSITIONS.remove(player.getUuid());
                Integer eid = PHANTOM_ENTITY_IDS.remove(player.getUuid());
                if (eid != null) {
                    ServerPlayNetworking.send(player, new com.project3.network.RemovePhantomPayload(eid));
                }
                PHANTOM_YAWS.remove(player.getUuid());

                // Remove command spawned lights on disconnect
                java.util.List<BlockPos> cmdLights = COMMAND_SPAWNED_LIGHTS.remove(player.getUuid());
                if (cmdLights != null) {
                    net.minecraft.world.World entityWorld = player.getEntityWorld();
                    if (entityWorld instanceof ServerWorld world) {
                        for (BlockPos pos : cmdLights) {
                            if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.LIGHT)) {
                                world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error cleaning up player data on disconnect: {}", player.getName().getString(), e);
            }
            com.project3.dread.DreadManager.onDisconnect(player.getUuid());
            com.project3.dread.ShadowMerchant.onDisconnect(player.getUuid());
            VOID_ESCALATION_TICKS.remove(player.getUuid());
        });

        // ── Server Started Event to restore World Border based on Season State ──
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Project3State state = Project3State.getOrCreate(server);
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                net.minecraft.world.border.WorldBorder border = overworld.getWorldBorder();
                BlockPos spawnPos = overworld.getSpawnPoint().getPos();
                double spawnX = spawnPos.getX() + 0.5;
                double spawnZ = spawnPos.getZ() + 0.5;

                if (!state.isSeasonStarted()) {
                    // Season not started: reset border to default vanilla size
                    border.setCenter(0.0, 0.0);
                    border.setSize(5.9999968E7);
                } else {
                    // Season started: center at spawn and enforce correct size/interpolation
                    border.setCenter(spawnX, spawnZ);
                    border.setWarningBlocks(3);
                    border.setSafeZone(1.0);
                    long elapsed = System.currentTimeMillis() - state.getSeasonStartTime();
                    if (elapsed < 2000000L) {
                        long remaining = 2000000L - elapsed;
                        border.interpolateSize(border.getSize(), 42000.0, remaining, net.minecraft.util.Util.getMeasuringTimeMs());
                    } else {
                        border.setSize(42000.0);
                    }
                }
            }
        });

        // ── Chunk load event to place producer blocks dynamically ──────────
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;
            Project3State state = Project3State.getOrCreate(world.getServer());
            if (!state.isSeasonStarted()) return;

            int cx = chunk.getPos().x;
            int cz = chunk.getPos().z;

            if (state.hasGeneratedProducer(cx, cz)) return;

            int blockX = cx * 16;
            int blockZ = cz * 16;
            boolean inStripX = (Math.abs(blockX) >= 19_800 && Math.abs(blockX) <= 20_200);
            boolean inStripZ = (Math.abs(blockZ) >= 19_800 && Math.abs(blockZ) <= 20_200);

            if (!(inStripX || inStripZ)) return;

            // Mark before scheduling so a rapid reload cannot enqueue a second task.
            state.markGeneratedProducer(cx, cz);

            java.util.Random random = new java.util.Random((long)cx * 341873128712L + (long)cz * 132897987541L + world.getSeed());
            if (random.nextInt(40) != 0) return;

            int rx = blockX + random.nextInt(16);
            int rz = blockZ + random.nextInt(16);
            MinecraftServer server = world.getServer();

            // Schedule placement on the server main thread to avoid chunk-loading deadlock.
            schedule(1, () -> {
                ServerWorld overworld = server.getOverworld();
                int topY = overworld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, rx, rz);
                BlockPos checkPos = new BlockPos(rx, topY, rz);

                while (checkPos.getY() > overworld.getBottomY()) {
                    BlockState blockState = overworld.getBlockState(checkPos);
                    Block block = blockState.getBlock();
                    if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT
                            || block == Blocks.PODZOL || block == Blocks.MYCELIUM || block == Blocks.SAND
                            || block == Blocks.RED_SAND || block == Blocks.GRAVEL || block == Blocks.CLAY
                            || block == Blocks.MOSS_BLOCK || block == Blocks.MUD || block == Blocks.STONE) {
                        overworld.setBlockState(checkPos, PRODUCER_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                        break;
                    }
                    checkPos = checkPos.down();
                }
            });
        });

        // ── Attempt to break Producer Block Callback ───────────────────────
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (world.getBlockState(pos).isOf(PRODUCER_BLOCK)) {
                if (world.getRandom().nextInt(20) == 0) {
                    player.sendMessage(Text.literal("§c[Система]: §eУважаемый житель! Зафиксировано несанкционированное вмешательство в работу муниципального оборудования. Объект является госсобственностью. (Код: ERR_STATE_PROPERTY_DAMAGE_99)"), false);
                }
            }
            return ActionResult.PASS;
        });

        // ── Block use callbacks: Nether portal and End portal ──
        registerBlockUseCallbacks();
 
        // ── Module 2 + 3 + 6: Server tick ─────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Tick scheduled tasks safely and clean up finished ones
            java.util.List<ScheduledTask> toRemove = new java.util.ArrayList<>();
            for (ScheduledTask task : SCHEDULED_TASKS) {
                task.remaining--;
                if (task.remaining <= 0) {
                    try {
                        task.action.run();
                    } catch (Exception e) {
                        LOGGER.error("Error executing scheduled task", e);
                    }
                    toRemove.add(task);
                }
            }
            SCHEDULED_TASKS.removeAll(toRemove);

            // Tick custom virtual NPCs and record/track online player frames & history
            try {
                com.project3.entity.PhantomReplicator.tickActiveNpcs(server);
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    com.project3.entity.PhantomReplicator.tickRecordingAndHistory(player);
                }
            } catch (Exception e) {
                LOGGER.error("Error in virtual phantom tick", e);
            }

            // Calibration processing
            if (calibrationTicksLeft > 0) {
                calibrationTicksLeft--;
                double percent = (1200 - calibrationTicksLeft) / 12.0;
                String progressBar = getProgressBar(percent);
                Text actionbarText = Text.literal("§e[ КАЛИБРОВКА: " + String.format("%.1f", percent) + "% ] " + progressBar);
                
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.sendMessage(actionbarText, true);
                    if (calibrationTicksLeft % 20 == 0) {
                        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.3f, 1.2f);
                    }
                }

                if (calibrationTicksLeft == 0) {
                    calibrationTicksLeft = -1;
                    Project3State state = Project3State.getOrCreate(server);
                    state.setSeasonStartTime(System.currentTimeMillis());
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        grantHappiness(player, state, 144000L); // 2 hours of Happiness on season start
                    }
                    ServerWorld overworld = server.getOverworld();
                    if (overworld != null) {
                        net.minecraft.world.border.WorldBorder border = overworld.getWorldBorder();
                        border.interpolateSize(border.getSize(), 42000.0, 2000000L, net.minecraft.util.Util.getMeasuringTimeMs()); // 2000 seconds in milliseconds
                    }
                }
            }

            Project3State state = Project3State.getOrCreate(server);
            ACHIEVEMENT_MANAGER.tick(server, state);

            if (!state.isSeasonStarted()) return;

            // Tick players' Happiness, Gloom, Unnamed Effect, and Sync
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // Pumpkin mask hint - send once when player first equips pumpkin
                if (server.getTicks() % 20 == 0 && isWearingPumpkin(player) && PUMPKIN_HINT_SENT.add(player.getUuid())) {
                    player.sendMessage(Text.literal("§6[Подсказка]: §fТы носишь резной тыквенный шлем. Фантомы не могут тебя видеть вблизи."), false);
                    player.sendMessage(Text.literal("§7Но будь осторожен — за пределами 16 блоков они атакуют всех без разбора."), false);
                }
                // Clear hint flag if player removes pumpkin
                if (!isWearingPumpkin(player)) {
                    PUMPKIN_HINT_SENT.remove(player.getUuid());
                }

                long happiness = state.getHappinessTicksLeft(player.getUuid());
                if (happiness > 0) {
                    state.setHappinessTicksLeft(player.getUuid(), happiness - 1);
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 40, 0, true, false, true));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, true, false, true));
                    state.setGloomPermanent(player.getUuid(), false);
                    state.setGloomTicksLeft(player.getUuid(), 0L);
                    // Reset gloom depth when happiness is restored
                    if (state.getGloomDepthTicks(player.getUuid()) > 0) {
                        state.setGloomDepthTicks(player.getUuid(), 0L);
                    }

                    // Fix #15: only run XP magnet every 2 ticks to reduce entity query load
                    if (server.getTicks() % 2 == 0) {
                        double radius = 6.0;
                        Box box = player.getBoundingBox().expand(radius);
                        java.util.List<net.minecraft.entity.ExperienceOrbEntity> orbs = ((ServerWorld) player.getEntityWorld()).getEntitiesByClass(
                                net.minecraft.entity.ExperienceOrbEntity.class, box, orb -> true
                        );
                        for (net.minecraft.entity.ExperienceOrbEntity orb : orbs) {
                            double dx = player.getX() - orb.getX();
                            double dy = (player.getY() + 0.5) - orb.getY();
                            double dz = player.getZ() - orb.getZ();
                            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            if (dist > 0.1) {
                                double speed = 0.2;
                                orb.setVelocity(orb.getVelocity().add(dx / dist * speed, dy / dist * speed, dz / dist * speed));
                            }
                        }
                    }

                    // Friendly mobs follow player (radius 10)
                    if (server.getTicks() % 10 == 0) {
                        Box mobBox = player.getBoundingBox().expand(10.0);
                        java.util.List<net.minecraft.entity.passive.PassiveEntity> passives = ((ServerWorld) player.getEntityWorld()).getEntitiesByClass(
                                net.minecraft.entity.passive.PassiveEntity.class, mobBox, passive -> passive.isAlive()
                        );
                        for (net.minecraft.entity.passive.PassiveEntity passive : passives) {
                            passive.getNavigation().startMovingTo(player, 1.25);
                        }
                    }

                    if (happiness == 1) {
                        state.setGloomPermanent(player.getUuid(), true);
                        player.sendMessage(Text.literal("§cСчастье покинуло вас... Вы чувствуете глубокое уныние."), false);
                        player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.6f);
                        player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);

                        // Title message
                        player.networkHandler.sendPacket(new TitleS2CPacket(
                                Text.literal("СЧАСТЬЕ ПОКИНУЛО ВАС").formatted(Formatting.DARK_RED)
                        ));
                        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                                Text.literal("Вы чувствуете глубокое уныние...")
                        ));

                        // Add temporary transition effects
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false));
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, false));

                        // Camera shake effect: schedule a few ticks of camera rotation
                        for (int i = 0; i < 10; i++) {
                            final float shakeYaw = (i % 2 == 0 ? 1.0f : -1.0f) * (10 - i) * 0.3f;
                            final float shakePitch = (i % 2 == 0 ? -0.5f : 0.5f) * (10 - i) * 0.2f;
                            schedule(i, () -> {
                                if (((ServerWorld) player.getEntityWorld()).getServer().getPlayerManager().getPlayer(player.getUuid()) != null) {
                                    ServerPlayNetworking.send(player, new com.project3.network.CameraRotatePayload(shakeYaw, shakePitch));
                                }
                            });
                        }
                    } else if (happiness == 1200) {
                        // 1 minute warning
                        player.sendMessage(Text.literal("§e[Система]§r: §7Счастье угасает... Осталась минута."), false);
                        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.5f, 0.7f);
                    } else if (happiness == 200) {
                        // 10 second warning
                        player.sendMessage(Text.literal("§c[Система]§r: §7Счастье вот-вот исчезнет!"), false);
                        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.8f, 0.5f);
                    }
                } else {
                    // No happiness! Force permanent gloom
                    state.setGloomPermanent(player.getUuid(), true);
                    state.setGloomTicksLeft(player.getUuid(), 0L);
                    state.addGloomDepthTicks(player.getUuid(), 1L);
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.UNLUCK, 40, 0, true, false, true));
                }

                if (state.isUnnamedEffectActive(player.getUuid())) {
                    if (player.getRandom().nextInt(600) == 0) {
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 100, 0, true, false, true));
                    }
                    if (player.getRandom().nextInt(1200) == 0) {
                        double dx = player.getX() + (player.getRandom().nextDouble() - 0.5) * 4.0;
                        double dy = player.getY() + (player.getRandom().nextInt(3) - 1);
                        double dz = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 4.0;
                        ServerWorld world = (ServerWorld) player.getEntityWorld();
                        player.teleport(world, dx, dy, dz, java.util.Set.of(), player.getYaw(), player.getPitch(), true);
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    }
                    if (server.getTicks() % 20 == 0) {
                        ServerWorld world = (ServerWorld) player.getEntityWorld();
                        for (ServerPlayerEntity other : world.getPlayers()) {
                            if (other != player && other.squaredDistanceTo(player) <= 64.0) {
                                other.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, true, true));
                            }
                        }
                    }
                }

                net.minecraft.world.World playerWorld = player.getEntityWorld();
                if (playerWorld instanceof ServerWorld serverWorld && serverWorld.getRegistryKey() == GLOOM_VOID_WORLD_KEY) {
                    VOID_ESCALATION_TICKS.merge(player.getUuid(), 1, Integer::sum);
                    tickGloomVoidPlayer(player, serverWorld);
                } else if (playerWorld instanceof ServerWorld serverWorld) {
                    VOID_ESCALATION_TICKS.remove(player.getUuid());
                    BlockPos lightPos = PLAYER_LIGHT_POSITIONS.remove(player.getUuid());
                    if (lightPos != null) {
                        if (serverWorld.getBlockState(lightPos).isOf(Blocks.LIGHT)) {
                            serverWorld.setBlockState(lightPos, Blocks.AIR.getDefaultState());
                        }
                    }
                    PHANTOM_POSITIONS.remove(player.getUuid());
                    Integer eid = PHANTOM_ENTITY_IDS.remove(player.getUuid());
                    if (eid != null) {
                        ServerPlayNetworking.send(player, new com.project3.network.RemovePhantomPayload(eid));
                    }
                    PHANTOM_YAWS.remove(player.getUuid());
                }

                // Calming Amulet tick — check offhand
                ItemStack offhand = player.getOffHandStack();
                if (!offhand.isEmpty() && offhand.getItem() instanceof com.project3.item.CalmingAmuletItem) {
                    if (server.getTicks() % 200 == 0) { // every 10 seconds
                        com.project3.item.CalmingAmuletItem.tickAmulet(player, offhand);
                    }
                }

                // Dread overload check
                com.project3.dread.DreadManager.checkOverload(player);

                if (server.getTicks() % 20 == 0) {
                    syncPlayerState(player, state);
                    // Sync dread to client
                    int dread = com.project3.dread.DreadManager.getDread(player);
                    int threshold = com.project3.dread.DreadManager.getThreshold(player);
                    ServerPlayNetworking.send(player, new com.project3.network.DreadPayload(dread, threshold));
                }
            }

            // Dread decay tick
            com.project3.dread.DreadManager.tickDecay(server);

            // Shadow Merchant tick
            com.project3.dread.ShadowMerchant.tickAll(server);

            // Tick World Oddities based on progressLevel (0 to 5)
            if (server.getTicks() % 100 == 0 && state.getProgressLevel() > 0) {
                int level = state.getProgressLevel();
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    net.minecraft.util.math.random.Random rand = player.getRandom();
                    
                    if (rand.nextFloat() < 0.005f * level) {
                        player.setOnFireFor(3 + level * 2);
                        player.sendMessage(Text.literal("§cВы внезапно загорелись!"), false);
                        player.playSound(SoundEvents.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
                    }

                    if (rand.nextFloat() < 0.002f * level) {
                        ServerPlayNetworking.send(player, new com.project3.network.ChunkReloadPayload());
                        player.sendMessage(Text.literal("§7[Система] Произошла перезагрузка чанков."), false);
                    }

                    if (rand.nextFloat() < 0.05f * level) {
                        spawnPhantomBlockNear(player);
                    }

                    if (rand.nextFloat() < 0.01f * level) {
                        deleteBlockNear(player);
                    }

                    if (rand.nextFloat() < 0.05f * level) {
                        rotGrassNear(player);
                    }

                    // Spawn Stalker NPC at higher progress levels (3-5)
                    if (level >= 3 && rand.nextFloat() < 0.003f * level) {
                        com.project3.entity.PhantomReplicator.spawnStalker(player);
                    }

                    // Dead Scenario — your past self appears (level 2+)
                    if (level >= 2) {
                        int dsCooldown = DEAD_SCENARIO_COOLDOWNS.computeIfAbsent(player.getUuid(), u -> rand.nextInt(3600) + 2400);
                        if (dsCooldown > 0) {
                            DEAD_SCENARIO_COOLDOWNS.put(player.getUuid(), dsCooldown - 100);
                        } else {
                            com.project3.entity.PhantomReplicator.spawnDeadScenario(player);
                            DEAD_SCENARIO_COOLDOWNS.put(player.getUuid(), rand.nextInt(3600) + 2400);
                        }
                    }

                    // Chat Echo — corrupted messages from your name (level 3+)
                    if (level >= 3) {
                        int ceCooldown = CHAT_ECHO_COOLDOWNS.computeIfAbsent(player.getUuid(), u -> rand.nextInt(4800) + 3600);
                        if (ceCooldown > 0) {
                            CHAT_ECHO_COOLDOWNS.put(player.getUuid(), ceCooldown - 100);
                        } else {
                            com.project3.entity.PhantomReplicator.spawnChatEcho(player);
                            CHAT_ECHO_COOLDOWNS.put(player.getUuid(), rand.nextInt(4800) + 3600);
                        }
                    }

                    // Static — frozen figure appears behind you (level 4+)
                    if (level >= 4) {
                        int stCooldown = STATIC_COOLDOWNS.computeIfAbsent(player.getUuid(), u -> rand.nextInt(6000) + 4800);
                        if (stCooldown > 0) {
                            STATIC_COOLDOWNS.put(player.getUuid(), stCooldown - 100);
                        } else {
                            com.project3.entity.PhantomReplicator.spawnStaticNpc(player);
                            STATIC_COOLDOWNS.put(player.getUuid(), rand.nextInt(6000) + 4800);
                        }
                    }

                    // Deja Vu — time loop, teleport back + screamer (level 5 only)
                    if (level >= 5) {
                        int dvCooldown = DEJA_VU_COOLDOWNS.computeIfAbsent(player.getUuid(), u -> rand.nextInt(7200) + 6000);
                        if (dvCooldown > 0) {
                            DEJA_VU_COOLDOWNS.put(player.getUuid(), dvCooldown - 100);
                        } else {
                            com.project3.entity.PhantomReplicator.triggerDejaVu(player);
                            DEJA_VU_COOLDOWNS.put(player.getUuid(), rand.nextInt(7200) + 6000);
                        }
                    }

                    // Corrupted biome effects at level 4-5
                    if (level >= 4) {
                        applyCorruptedBiomeEffects(player, level);
                    }
                }
            }

            ServerWorld overworld = server.getOverworld();
            Act act = getAct(state);

            // 1. Boundary Check
            BlockPos spawnPos = overworld.getSpawnPoint().getPos();
            int spawnX = spawnPos.getX();
            int spawnZ = spawnPos.getZ();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getEntityWorld() == overworld) {
                    double x = player.getX();
                    double z = player.getZ();
                    if (Math.abs(x - spawnX) >= WALL_THRESHOLD || Math.abs(z - spawnZ) >= WALL_THRESHOLD) {
                        applyWallPenalty(player, overworld);
                    }
                }
            }
        });

        // ── UseEntityCallback for Allay flower interaction & player shearing ──
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (entity instanceof net.minecraft.entity.passive.AllayEntity allay) {
                if (player instanceof ServerPlayerEntity spe) {
                    ItemStack stack = player.getStackInHand(hand);
                    if (!stack.isEmpty() && stack.isIn(net.minecraft.registry.tag.ItemTags.FLOWERS)) {
                        if (allay.getStackInHand(Hand.MAIN_HAND).isEmpty()) {
                            spe.incrementStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(GIVE_ALLAY_FLOWER_STAT_ID));
                        }
                    }
                }
            } else if (entity instanceof ServerPlayerEntity targetPlayer) {
                if (player instanceof ServerPlayerEntity spe) {
                    ItemStack stack = spe.getStackInHand(hand);
                    if (stack.isOf(Items.SHEARS)) {
                        if (spe.getItemCooldownManager().isCoolingDown(stack)) {
                            return ActionResult.FAIL;
                        }
                        
                        // Cooldown on shears (30s = 600 ticks)
                        spe.getItemCooldownManager().set(stack, 600);
                        
                        // Break shears
                        stack.decrement(1);
                        spe.getEntityWorld().playSound(null, spe.getX(), spe.getY(), spe.getZ(),
                                SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        
                        // Drop player head
                        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                        head.set(net.minecraft.component.DataComponentTypes.PROFILE,
                                net.minecraft.component.type.ProfileComponent.ofStatic(targetPlayer.getGameProfile()));
                        net.minecraft.entity.ItemEntity itemEntity = new net.minecraft.entity.ItemEntity(
                                world, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(), head
                        );
                        world.spawnEntity(itemEntity);
                        
                        // Shearing sound
                        world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                                SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        
                        return ActionResult.SUCCESS;
                    }
                }
            }
            return ActionResult.PASS;
        });

        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity spe) {
                if (spe.getMainHandStack().isOf(Items.MACE)) {
                    LAST_MACE_ATTACK_FALL_DISTANCE.put(spe.getUuid(), (float) spe.fallDistance);
                    java.util.UUID uuid = spe.getUuid();
                    schedule(20, () -> LAST_MACE_ATTACK_FALL_DISTANCE.remove(uuid));
                }
            }
            return ActionResult.PASS;
        });

        // ── ServerLivingEntityEvents.AFTER_DEATH to detect 50-block Mace kill ──
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getAttacker() instanceof ServerPlayerEntity player) {
                Float fallDist = LAST_MACE_ATTACK_FALL_DISTANCE.remove(player.getUuid());
                if (fallDist == null) {
                    fallDist = (float) player.fallDistance;
                }
                if (player.getMainHandStack().isOf(Items.MACE) && fallDist >= 50.0f) {
                    player.incrementStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(MACE_KILL_50_BLOCKS_STAT_ID));
                }
            }
        });

        // ── ServerEntityEvents.ENTITY_LOAD to detect Crossbow firework shoot ──
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.entity.projectile.FireworkRocketEntity firework) {
                if (firework.getOwner() instanceof ServerPlayerEntity player) {
                    if (player.isHolding(Items.CROSSBOW)) {
                        player.incrementStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(SHOOT_FIREWORK_CROSSBOW_STAT_ID));
                    }
                }
            }
        });

        // ── Block break callback for Gloom ore loss ──
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, blockState, blockEntity) -> {
            if (world.isClient()) return true;
            if (world.getRegistryKey() == GLOOM_VOID_WORLD_KEY && !player.isCreative()) return false;
            if (!(player instanceof ServerPlayerEntity spe)) return true;
            
            Project3State p3State = Project3State.getOrCreate(((ServerWorld) spe.getEntityWorld()).getServer());
            boolean hasGloom = p3State.getGloomTicksLeft(spe.getUuid()) > 0 || p3State.isGloomPermanent(spe.getUuid());
            
            if (hasGloom && isOreBlock(blockState)) {
                if (world.getRandom().nextFloat() < 0.02f) { // 2% chance
                    BlockState replacement;
                    if (blockState.isOf(Blocks.DEEPSLATE_COAL_ORE) || blockState.isOf(Blocks.DEEPSLATE_COPPER_ORE) || 
                        blockState.isOf(Blocks.DEEPSLATE_IRON_ORE) || blockState.isOf(Blocks.DEEPSLATE_GOLD_ORE) || 
                        blockState.isOf(Blocks.DEEPSLATE_REDSTONE_ORE) || blockState.isOf(Blocks.DEEPSLATE_LAPIS_ORE) || 
                        blockState.isOf(Blocks.DEEPSLATE_DIAMOND_ORE) || blockState.isOf(Blocks.DEEPSLATE_EMERALD_ORE)) {
                        replacement = Blocks.DEEPSLATE.getDefaultState();
                    } else if (blockState.isOf(Blocks.NETHER_GOLD_ORE) || blockState.isOf(Blocks.NETHER_QUARTZ_ORE)) {
                        replacement = Blocks.NETHERRACK.getDefaultState();
                    } else {
                        replacement = Blocks.STONE.getDefaultState();
                    }
                    
                    world.setBlockState(pos, replacement, Block.NOTIFY_ALL);
                    world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    spe.sendMessage(Text.literal("§cУныние обратило добытую руду в простой камень..."), false);
                    return false; // Cancel block break & drops
                }
            }
            return true;
        });

        // ── AttackEntityCallback for Unnamed effect blindness ──
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity spe) {
                Project3State p3State = Project3State.getOrCreate(((ServerWorld) spe.getEntityWorld()).getServer());
                if (p3State.isUnnamedEffectActive(spe.getUuid())) {
                    if (entity instanceof net.minecraft.entity.LivingEntity target) {
                        if (world.getRandom().nextFloat() < 0.25f) {
                            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, true, true));
                        }
                    }
                }
            }
            return ActionResult.PASS;
        });

        LOGGER.info("Project3 initialized.");
    }

    // ─── Module 1 ─────────────────────────────────────────────────────────────

    private void registerBlockUseCallbacks() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.getRegistryKey() == GLOOM_VOID_WORLD_KEY && !player.isCreative()) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (world.getRegistryKey() == GLOOM_VOID_WORLD_KEY && !player.isCreative()) return ActionResult.FAIL;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            BlockState clickedState = world.getBlockState(pos);
            Block clickedBlock = clickedState.getBlock();
            ItemStack heldStack = serverPlayer.getStackInHand(hand);

            Project3State state = Project3State.getOrCreate(sw.getServer());
            Act act = getAct(state);

            // 1. Nether portal creation/activation lock
            boolean isFlintSteel = heldStack.isOf(Items.FLINT_AND_STEEL) || heldStack.isOf(Items.FIRE_CHARGE);
            if (isFlintSteel && clickedBlock == Blocks.OBSIDIAN) {
                boolean locked = false;
                if (!serverPlayer.isCreative()) {
                    if (!state.isSeasonStarted() || calibrationTicksLeft > 0) {
                        locked = true;
                    } else if (act == Act.I && !state.isNetherForceUnlocked()) {
                        locked = true;
                    }
                }

                if (locked) {
                    sw.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0f, false, World.ExplosionSourceType.NONE);
                    serverPlayer.damage(sw, sw.getDamageSources().explosion(null, null), 4.0f);
                    
                    // Low-pitch warp travel sound and anvil crash sound
                    sw.playSound(null, pos, SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, 1.0f, 0.5f);
                    sw.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    
                    // Spiral portal particles vortex
                    double centerX = pos.getX() + 0.5;
                    double centerY = pos.getY() + 0.5;
                    double centerZ = pos.getZ() + 0.5;
                    for (int i = 0; i < 150; i++) {
                        double theta = i * 0.2;
                        double radius = 0.1 + i * 0.02;
                        double yOffset = (i * 0.03) - 2.0; // from -2.0 to 2.5 height
                        double px = centerX + Math.cos(theta) * radius;
                        double py = centerY + yOffset;
                        double pz = centerZ + Math.sin(theta) * radius;
                        sw.spawnParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                    }

                    if (!state.isSeasonStarted() || calibrationTicksLeft > 0) {
                        serverPlayer.sendMessage(Text.literal("§c[Система] Портал в Незер заблокирован. Сезон не начат."), false);
                    } else {
                        serverPlayer.sendMessage(Text.literal("§c[Система] Портал в Незер заблокирован на первые 72 часа сезона."), false);
                    }
                    
                    // Force client update on offset block (where client predicts fire block)
                    BlockPos offsetPos = pos.offset(hitResult.getSide());
                    sw.updateListeners(offsetPos, sw.getBlockState(offsetPos), sw.getBlockState(offsetPos), 3);
                    
                    return ActionResult.FAIL;
                }
            }

            // 2. End portal frame Ender Eye insertion lock
            boolean isEnderEye = heldStack.isOf(Items.ENDER_EYE);
            if (isEnderEye && clickedBlock == Blocks.END_PORTAL_FRAME) {
                boolean locked = false;
                if (!serverPlayer.isCreative()) {
                    if (!state.isSeasonStarted() || calibrationTicksLeft > 0) {
                        locked = true;
                    } else if ((act == Act.I || act == Act.II || act == Act.III) && !state.isEndForceUnlocked()) {
                        locked = true;
                    }
                }

                if (locked) {
                    sw.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    if (!state.isSeasonStarted() || calibrationTicksLeft > 0) {
                        serverPlayer.sendMessage(Text.literal("§c[Система] Портал в Энд заблокирован. Сезон не начат."), false);
                    } else {
                        serverPlayer.sendMessage(Text.literal("§c[Система] Портал в Энд заблокирован на первые 10 дней (240 часов) сезона."), false);
                    }
                    
                    // Force client update on frame block (where client predicts eye frame)
                    sw.updateListeners(pos, clickedState, clickedState, 3);
                    
                    return ActionResult.FAIL;
                }
            }

            return ActionResult.PASS;
        });
    }

    // ─── Module 2 ─────────────────────────────────────────────────────────────

    private static void applyWallPenalty(ServerPlayerEntity player, ServerWorld overworld) {
        long now = System.currentTimeMillis();
        long lastPenalty = WALL_MESSAGE_COOLDOWNS.getOrDefault(player.getUuid(), 0L);
        if (now - lastPenalty < WALL_MESSAGE_COOLDOWN_MS) {
            return;
        }
        WALL_MESSAGE_COOLDOWNS.put(player.getUuid(), now);

        // Track violation count for graduated penalties
        int violations = WALL_VIOLATION_COUNT.getOrDefault(player.getUuid(), 0) + 1;
        WALL_VIOLATION_COUNT.put(player.getUuid(), violations);

        // First offense: warning only (no teleport, no debuffs)
        if (violations == 1) {
            player.sendMessage(Text.literal("§c[Система]: §eВнимание! Вы приближаетесь к границе сектора. Нарушение паспортного режима повлечёт за собой принудительную депортацию."), false);
            player.networkHandler.sendPacket(new TitleS2CPacket(
                    Text.literal("§e⚠ ВНИМАНИЕ").formatted(Formatting.YELLOW)
            ));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("§7Вы приближаетесь к границе сектора")
            ));
            overworld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1.0f, 0.5f);
            return;
        }

        // Second+ offense: full penalty
        // Teleport to world spawn (safe Y ground height)
        // Validate spawn is within world border to prevent teleport loops
        BlockPos spawn = overworld.getSpawnPoint().getPos();
        net.minecraft.world.border.WorldBorder border = overworld.getWorldBorder();
        double spawnX = spawn.getX() + 0.5;
        double spawnZ = spawn.getZ() + 0.5;
        
        // If spawn is outside or near the border, use center (0, 0) instead
        if (!border.contains(spawnX, spawnZ) || border.getDistanceInsideBorder(spawnX, spawnZ) < 100) {
            spawnX = 0.5;
            spawnZ = 0.5;
            spawn = new BlockPos(0, spawn.getY(), 0);
        }
        
        int topY = overworld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        double spawnY = Math.max(overworld.getBottomY() + 2, topY);
        player.teleport(overworld, spawnX, spawnY, spawnZ,
                java.util.Set.of(), player.getYaw(), player.getPitch(), true);

        // Set hunger to 0
        player.getHungerManager().setFoodLevel(0);

        // Debuffs: Slowness IV (10s), Weakness II (10s), Darkness (10s)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,  200, 3, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,   200, 1, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,   200, 0, false, true));

        // Scary Elder Guardian sound effect on boundary violation
        overworld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 1.0f, 0.5f);

        // System warning message
        player.sendMessage(Text.literal("§c[Система]: §eВнимание! Вы покинули согласованный жилой сектор. Ваше нахождение за пределами зоны несанкционировано. Зафиксировано нарушение паспортного режима. (Код: 0xBORDER_LIMIT_EXCEEDED)"), false);

        // Title message
        player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("ВЫДВОРЕНИЕ ИЗ СЕКТОРА").formatted(Formatting.RED)
        ));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                Text.literal("§4Нарушение паспортного режима.")
        ));
    }

    private static final long GLOOM_DEPTH_THRESHOLD_3 = 24000L;  // 20 min → state 4
    private static final long GLOOM_DEPTH_THRESHOLD_4 = 72000L;  // 60 min → state 5

    public static int computeStateIndex(ServerPlayerEntity player, Project3State state) {
        if (!state.isSeasonStarted()) return 0;
        UUID uuid = player.getUuid();
        if (state.isUnnamedEffectActive(uuid)) return 6;
        long happiness = state.getHappinessTicksLeft(uuid);
        if (happiness > state.MAX_HAPPINESS_TICKS / 2) return 1;
        if (happiness > 0) return 2;
        long gloomDepth = state.getGloomDepthTicks(uuid);
        boolean inVoid = player.getEntityWorld() instanceof ServerWorld sw
            && sw.getRegistryKey() == GLOOM_VOID_WORLD_KEY;
        if (gloomDepth < GLOOM_DEPTH_THRESHOLD_3) return 3;
        if (inVoid && gloomDepth >= GLOOM_DEPTH_THRESHOLD_4) return 5;
        return 4;
    }

    public static void syncPlayerState(ServerPlayerEntity player, Project3State state) {
        int stateIndex = computeStateIndex(player, state);
        ServerPlayNetworking.send(player, new com.project3.network.PlayerStateSyncPayload(
                state.getHappinessTicksLeft(player.getUuid()),
                state.isGloomPermanent(player.getUuid()),
                state.getGloomTicksLeft(player.getUuid()),
                state.isUnnamedEffectActive(player.getUuid()),
                state.getProgressLevel(),
                stateIndex
        ));
        // Sync paranoia level based on progressLevel (0 = no paranoia, 5 = max paranoia)
        int paranoiaLevel = state.getProgressLevel();
        ServerPlayNetworking.send(player, new com.project3.network.ParanoiaPayload(paranoiaLevel));
    }

    // Maximum happiness duration: 1 hour (120000 ticks)
    private static final long MAX_HAPPINESS_TICKS = com.project3.state.Project3State.MAX_HAPPINESS_TICKS;

    public static void grantHappiness(ServerPlayerEntity player, Project3State state, long ticks) {
        long current = state.getHappinessTicksLeft(player.getUuid());
        long newTotal = Math.min(current + ticks, MAX_HAPPINESS_TICKS);
        state.setHappinessTicksLeft(player.getUuid(), newTotal);
        state.setGloomPermanent(player.getUuid(), false);
        state.setGloomTicksLeft(player.getUuid(), 0L);
        state.setGloomDepthTicks(player.getUuid(), 0L);
        player.removeStatusEffect(StatusEffects.UNLUCK);
        syncPlayerState(player, state);
    }

    public static void grantGloom(ServerPlayerEntity player, Project3State state, long ticks) {
        state.setHappinessTicksLeft(player.getUuid(), 0L);
        state.setGloomTicksLeft(player.getUuid(), ticks);
        state.setGloomPermanent(player.getUuid(), false);
        player.removeStatusEffect(StatusEffects.LUCK);
        syncPlayerState(player, state);
    }

    private static void spawnPhantomBlockNear(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        // Never spawn PhantomBlocks in the Gloom Void — they replace floor and trap players
        if (world.getRegistryKey() == GLOOM_VOID_WORLD_KEY) return;
        net.minecraft.util.math.random.Random rand = player.getRandom();
        int rx = player.getBlockX() + rand.nextInt(11) - 5;
        int ry = player.getBlockY() + rand.nextInt(5) - 2;
        int rz = player.getBlockZ() + rand.nextInt(11) - 5;
        BlockPos pos = new BlockPos(rx, ry, rz);
        BlockState state = world.getBlockState(pos);
        
        // Allow replacing solid blocks OR air (floating phantom illusion)
        if (state.isOf(com.project3.Project3Mod.PHANTOM_BLOCK) || state.isOf(com.project3.Project3Mod.PRODUCER_BLOCK)
            || state.hasBlockEntity() || state.isOf(Blocks.BEDROCK)) {
            return;
        }

        BlockState replacedState;
        if (!state.isAir()) {
            // Solid block - use the existing block as replaced state
            replacedState = state;
        } else {
            // Air - find a nearby solid block to mimic
            replacedState = null;
            for (BlockPos neighbor : new BlockPos[]{pos.down(), pos.north(), pos.south(), pos.east(), pos.west(), pos.up()}) {
                BlockState ns = world.getBlockState(neighbor);
                if (!ns.isAir() && !ns.isOf(com.project3.Project3Mod.PHANTOM_BLOCK) && !ns.isOf(com.project3.Project3Mod.PRODUCER_BLOCK)
                        && ns.getFluidState().isEmpty() && !ns.hasBlockEntity() && !ns.isOf(Blocks.BEDROCK)) {
                    replacedState = ns;
                    break;
                }
            }
            if (replacedState == null) {
                replacedState = Blocks.STONE.getDefaultState();
            }
        }
 
        world.setBlockState(pos, com.project3.Project3Mod.PHANTOM_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(pos) instanceof com.project3.block.entity.PhantomBlockEntity pbe) {
            pbe.setReplacedState(replacedState);
        }
    }
 
    private static void deleteBlockNear(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        net.minecraft.util.math.random.Random rand = player.getRandom();
        int rx = player.getBlockX() + rand.nextInt(9) - 4;
        int ry = player.getBlockY() + rand.nextInt(5) - 2;
        int rz = player.getBlockZ() + rand.nextInt(9) - 4;
        BlockPos pos = new BlockPos(rx, ry, rz);
        BlockState state = world.getBlockState(pos);
        if (!state.isAir() && !state.isOf(Blocks.BEDROCK) && !state.isOf(com.project3.Project3Mod.PRODUCER_BLOCK) && !state.isOf(com.project3.Project3Mod.PHANTOM_BLOCK) && !state.hasBlockEntity()) {
            Block block = state.getBlock();
            if (block != Blocks.CHEST && block != Blocks.FURNACE && block != Blocks.BLAST_FURNACE && block != Blocks.SMOKER 
                && block != Blocks.CRAFTING_TABLE && block != Blocks.BARREL && block != Blocks.SHULKER_BOX 
                && block != Blocks.DISPENSER && block != Blocks.DROPPER && block != Blocks.HOPPER) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 0.5f, 1.0f);
            }
        }
    }
 
    private static void rotGrassNear(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        net.minecraft.util.math.random.Random rand = player.getRandom();
        int rx = player.getBlockX() + rand.nextInt(9) - 4;
        int ry = player.getBlockY() + rand.nextInt(5) - 2;
        int rz = player.getBlockZ() + rand.nextInt(9) - 4;
        BlockPos pos = new BlockPos(rx, ry, rz);
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.MYCELIUM)) {
            world.setBlockState(pos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        } else if (state.isOf(Blocks.FLOWERING_AZALEA)) {
            world.setBlockState(pos, Blocks.DEAD_BUSH.getDefaultState(), Block.NOTIFY_LISTENERS);
        } else if (state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    private static boolean isOreBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE ||
               block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE ||
               block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE ||
               block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE ||
               block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE ||
               block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE ||
               block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE ||
               block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE ||
               block == Blocks.NETHER_GOLD_ORE || block == Blocks.NETHER_QUARTZ_ORE;
    }

    public static boolean isHoldingLightSource(ServerPlayerEntity player) {
        for (net.minecraft.util.Hand hand : net.minecraft.util.Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isEmpty()) {
                net.minecraft.item.Item item = stack.getItem();
                String name = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
                if (name.contains("torch") || name.contains("lantern") || name.contains("glowstone") || 
                    name.contains("campfire") || name.contains("sea_lantern") || name.contains("shroomlight") || 
                    name.contains("pearlescent_froglight") || name.contains("verdant_froglight") || 
                    name.contains("ochre_froglight") || name.contains("crying_obsidian") || 
                    name.contains("beacon") || name.contains("conduit") || name.contains("jack_o_lantern")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Applies corrupted biome effects based on progress level.
     * Level 4: Leaf decay, grass death
     * Level 5: All of above + sculk growth in caves
     */
    private static void applyCorruptedBiomeEffects(ServerPlayerEntity player, int level) {
        if (level < 4) return;
        
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        net.minecraft.util.math.random.Random rand = player.getRandom();
        
        
        // Leaf decay and grass death in a 16-block radius
        int radius = 16;
        for (int i = 0; i < 5; i++) { // Process 5 random blocks per tick
            int rx = player.getBlockX() + rand.nextInt(radius * 2 + 1) - radius;
            int ry = player.getBlockY() + rand.nextInt(9) - 4;
            int rz = player.getBlockZ() + rand.nextInt(radius * 2 + 1) - radius;
            BlockPos pos = new BlockPos(rx, ry, rz);
            BlockState state = world.getBlockState(pos);
            
            // Leaf decay
            if (state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
                if (rand.nextFloat() < 0.3f) { // 30% chance per leaf block
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    // Spawn particles
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, 
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        3, 0.2, 0.2, 0.2, 0.01);
                }
            }
            
            // Grass/flower death
            if (state.isOf(Blocks.SHORT_GRASS) || state.isOf(Blocks.TALL_GRASS) || 
                state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN) ||
                state.isOf(Blocks.POPPY) || state.isOf(Blocks.DANDELION) ||
                state.isOf(Blocks.BLUE_ORCHID) || state.isOf(Blocks.ALLIUM) ||
                state.isOf(Blocks.AZURE_BLUET) || state.isOf(Blocks.RED_TULIP) ||
                state.isOf(Blocks.ORANGE_TULIP) || state.isOf(Blocks.WHITE_TULIP) ||
                state.isOf(Blocks.PINK_TULIP) || state.isOf(Blocks.OXEYE_DAISY) ||
                state.isOf(Blocks.CORNFLOWER) || state.isOf(Blocks.LILY_OF_THE_VALLEY)) {
                if (rand.nextFloat() < 0.5f) { // 50% chance per plant
                    world.setBlockState(pos, Blocks.DEAD_BUSH.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        
        // Level 5: Sculk growth in caves (below Y=60)
        if (level >= 5 && world.getTime() % 200 == 0) {
            for (int i = 0; i < 2; i++) {
                int rx = player.getBlockX() + rand.nextInt(21) - 10;
                int ry = Math.min(player.getBlockY() - 5, 60); // Below player and below Y=60
                int rz = player.getBlockZ() + rand.nextInt(21) - 10;
                BlockPos pos = new BlockPos(rx, ry, rz);
                
                // Find cave floor (stone/deepslate)
                while (pos.getY() > world.getBottomY() && 
                       !world.getBlockState(pos).isOf(Blocks.STONE) && 
                       !world.getBlockState(pos).isOf(Blocks.DEEPSLATE)) {
                    pos = pos.down();
                }
                
                if (pos.getY() > world.getBottomY() && 
                    world.getBlockState(pos.up()).isAir()) {
                    // Place sculk
                    world.setBlockState(pos.up(), Blocks.SCULK.getDefaultState(), Block.NOTIFY_LISTENERS);
                    // Sculk veins around
                    if (rand.nextFloat() < 0.5f) {
                        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                            if (rand.nextFloat() < 0.3f) {
                                BlockPos veinPos = pos.up().offset(dir);
                                if (world.getBlockState(veinPos).isAir()) {
                                    world.setBlockState(veinPos, Blocks.SCULK.getDefaultState(), Block.NOTIFY_LISTENERS);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void tickGloomVoidPlayer(ServerPlayerEntity player, ServerWorld playerWorld) {
        if (!player.isAlive()) return;

        // Escalation level based on time spent in Gloom Void this session
        int voidTicks = VOID_ESCALATION_TICKS.getOrDefault(player.getUuid(), 0);
        int escalationLevel = 0;
        if (voidTicks > 3600) escalationLevel = 4;  // 3+ minutes
        else if (voidTicks > 2400) escalationLevel = 3;  // 2 minutes
        else if (voidTicks > 1200) escalationLevel = 2;  // 1 minute
        else if (voidTicks > 600) escalationLevel = 1;   // 30 seconds

        // Failsafe: if player falls below Y=55, teleport them back to the portal safety!
        if (player.getY() < 55.0) {
            player.teleport(playerWorld, player.getX(), 64.0, player.getZ(), java.util.Set.of(), player.getYaw(), player.getPitch(), true);
            player.setVelocity(0, 0, 0);
        }

        // 1. Flashlight Failure / Interruption
        boolean holdingLight = isHoldingLightSource(player);
        if (holdingLight) {
            int flTicks = FLASHLIGHT_COOLDOWNS.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(600) + 600);
            if (flTicks > 0) {
                FLASHLIGHT_COOLDOWNS.put(player.getUuid(), flTicks - 1);
            } else {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 50, 0, false, false, true));
                player.playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f, 0.5f);
                schedule(50, () -> {
                    if (player.isAlive()) {
                        player.playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f, 0.8f);
                    }
                });
                FLASHLIGHT_COOLDOWNS.put(player.getUuid(), player.getRandom().nextInt(600) + 600);
            }
        } else {
            FLASHLIGHT_COOLDOWNS.remove(player.getUuid());
        }

        // Dynamic held light source placement
        BlockPos oldLightPos = PLAYER_LIGHT_POSITIONS.get(player.getUuid());
        BlockPos targetLightPos = null;

        boolean shouldLight = holdingLight && !player.hasStatusEffect(StatusEffects.DARKNESS);
        if (shouldLight) {
            BlockPos eyePos = player.getBlockPos().up();
            BlockState eyeState = playerWorld.getBlockState(eyePos);
            if (eyeState.isAir() || eyeState.isOf(Blocks.LIGHT)) {
                targetLightPos = eyePos;
            } else {
                BlockPos feetPos = player.getBlockPos();
                BlockState feetState = playerWorld.getBlockState(feetPos);
                if (feetState.isAir() || feetState.isOf(Blocks.LIGHT)) {
                    targetLightPos = feetPos;
                }
            }
        }

        if (oldLightPos != null && !oldLightPos.equals(targetLightPos)) {
            if (playerWorld.getBlockState(oldLightPos).isOf(Blocks.LIGHT)) {
                playerWorld.setBlockState(oldLightPos, Blocks.AIR.getDefaultState());
            }
            PLAYER_LIGHT_POSITIONS.remove(player.getUuid());
        }

        if (targetLightPos != null && !targetLightPos.equals(oldLightPos)) {
            playerWorld.setBlockState(targetLightPos, Blocks.LIGHT.getDefaultState()
                .with(net.minecraft.block.LightBlock.LEVEL_15, 15));
            PLAYER_LIGHT_POSITIONS.put(player.getUuid(), targetLightPos);
        }

        // 2. Ambient Sounds / Glitches — faster at higher escalation
        int ambBaseDelay = Math.max(1200 - escalationLevel * 200, 400);
        final int ambDelayFinal = ambBaseDelay;
        int ambTicks = AMBIENT_COOLDOWNS.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(ambDelayFinal) + ambDelayFinal);
        if (ambTicks > 0) {
            AMBIENT_COOLDOWNS.put(player.getUuid(), ambTicks - 1);
        } else {
            int soundType = player.getRandom().nextInt(3);
            if (soundType == 0) {
                net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0f).normalize();
                double bx = player.getX() - look.x * 1.5;
                double bz = player.getZ() - look.z * 1.5;
                double by = player.getY();
                playerWorld.playSound(null, bx, by, bz, SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 1.0f, 0.9f);
                schedule(5, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, bx, by, bz, SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 1.0f, 0.85f);
                    }
                });
                schedule(10, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, bx, by, bz, SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 1.0f, 0.95f);
                    }
                });
            } else if (soundType == 1) {
                net.minecraft.util.math.Vec3d randOffset = new net.minecraft.util.math.Vec3d((player.getRandom().nextDouble() - 0.5) * 8.0, 
                                             (player.getRandom().nextDouble() - 0.5) * 2.0, 
                                             (player.getRandom().nextDouble() - 0.5) * 8.0);
                net.minecraft.util.math.Vec3d soundPos = new net.minecraft.util.math.Vec3d(player.getX(), player.getY(), player.getZ()).add(randOffset);
                schedule(15, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, soundPos.x, soundPos.y, soundPos.z, 
                            SoundEvents.BLOCK_STONE_BREAK, SoundCategory.MASTER, 2.0f, 0.2f);
                    }
                });
            } else {
                playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(), 
                    SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.MASTER, 1.0f, 1.5f);
                schedule(2, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(), 
                            SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1.2f, 1.3f);
                    }
                });
                schedule(4, () -> {
                    if (player.isAlive()) {
                        playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(), 
                            SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.MASTER, 1.5f, 0.1f);
                    }
                });
            }
            AMBIENT_COOLDOWNS.put(player.getUuid(), player.getRandom().nextInt(1200) + 1200);
        }

        // 2b. Distorted ambient music (played at very low pitch to sound eerie)
        int musicTicks = MUSIC_COOLDOWNS.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(3600) + 2400);
        if (musicTicks > 0) {
            MUSIC_COOLDOWNS.put(player.getUuid(), musicTicks - 1);
        } else {
            // Pick one of several ambient sounds and play it slowed down via packet
            SoundEvent[] distortedSounds = {
                SoundEvents.MUSIC_DISC_13.value(),
                SoundEvents.MUSIC_DISC_CAT.value(),
                SoundEvents.AMBIENT_CAVE.value()
            };
            SoundEvent chosen = distortedSounds[player.getRandom().nextInt(distortedSounds.length)];
            // Deliver via direct packet to set a very low pitch (0.40f) inaccessible via world.playSound
            net.minecraft.util.math.Vec3d pos = new net.minecraft.util.math.Vec3d(player.getX(), player.getY(), player.getZ());
            PlaySoundS2CPacket musicPacket = new PlaySoundS2CPacket(
                net.minecraft.registry.Registries.SOUND_EVENT.getEntry(chosen),
                SoundCategory.MASTER,
                pos.x, pos.y, pos.z,
                0.18f, // volume — very quiet, barely audible
                0.40f, // pitch — drastically slowed down
                player.getRandom().nextLong()
            );
            player.networkHandler.sendPacket(musicPacket);
            MUSIC_COOLDOWNS.put(player.getUuid(), player.getRandom().nextInt(4800) + 3600);
        }

        // 2c. Ambient smoke particles drifting upward around the player
        int smokeTicks = SMOKE_COOLDOWNS.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(40) + 20);
        if (smokeTicks > 0) {
            SMOKE_COOLDOWNS.put(player.getUuid(), smokeTicks - 1);
        } else {
            // Spawn 2-4 wisps of smoke at random positions around player
            int smokeCount = player.getRandom().nextInt(3) + 2;
            for (int i = 0; i < smokeCount; i++) {
                double ox = (player.getRandom().nextDouble() - 0.5) * 10.0;
                double oy = (player.getRandom().nextDouble()) * 1.5 - 0.5;
                double oz = (player.getRandom().nextDouble() - 0.5) * 10.0;
                playerWorld.spawnParticles(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    player.getX() + ox,
                    player.getY() + oy,
                    player.getZ() + oz,
                    1, 0.0, 0.0, 0.0, 0.005
                );
            }
            SMOKE_COOLDOWNS.put(player.getUuid(), player.getRandom().nextInt(30) + 15);
        }

        // 2d. ENHANCED HORROR AMBIENCE

        // Footsteps BEHIND the player (sounds like someone following)
        if (playerWorld.getTime() % 80 == 0 && player.getRandom().nextFloat() < 0.3f) {
            net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0f).normalize();
            // Place sound BEHIND player
            double fx = player.getX() - look.x * 2.0;
            double fz = player.getZ() - look.z * 2.0;
            double fy = player.getY();
            playerWorld.playSound(null, fx, fy, fz, SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 0.6f, 0.7f);
            schedule(8, () -> {
                if (player.isAlive()) {
                    playerWorld.playSound(null, fx + look.x * 0.5, fy, fz + look.z * 0.5,
                        SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 0.5f, 0.65f);
                }
            });
        }

        // Breathing sounds (player hears their own terrified breathing)
        if (playerWorld.getTime() % 120 == 0 && player.getRandom().nextFloat() < 0.2f) {
            player.playSound(SoundEvents.ENTITY_PLAYER_BREATH, 0.8f, 0.4f);
        }

        // Distant screams (very quiet, very far away)
        if (playerWorld.getTime() % 300 == 0 && player.getRandom().nextFloat() < 0.15f) {
            net.minecraft.util.math.Vec3d offset = new net.minecraft.util.math.Vec3d(
                (player.getRandom().nextDouble() - 0.5) * 40.0, 0,
                (player.getRandom().nextDouble() - 0.5) * 40.0);
            net.minecraft.util.math.Vec3d screamPos = player.getEntityPos().add(offset);
            PlaySoundS2CPacket screamPacket = new PlaySoundS2CPacket(
                net.minecraft.registry.Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_ENDERMAN_SCREAM),
                SoundCategory.MASTER,
                screamPos.x, screamPos.y, screamPos.z,
                0.3f,  // quiet
                0.4f,  // very low pitch = terrifying
                player.getRandom().nextLong()
            );
            player.networkHandler.sendPacket(screamPacket);
        }

        // Heartbeat (subtle, low rhythm, increases tension — faster at higher escalation)
        int hbInterval = 30 - escalationLevel * 4;
        if (hbInterval < 10) hbInterval = 10;
        float hbChance = 0.08f + escalationLevel * 0.05f;
        if (hbChance > 0.3f) hbChance = 0.3f;
        float hbPitch = 0.6f + escalationLevel * 0.15f;
        if (hbPitch > 1.2f) hbPitch = 1.2f;
        if (playerWorld.getTime() % hbInterval == 0 && player.getRandom().nextFloat() < hbChance) {
            float hbVolume = 0.15f + escalationLevel * 0.08f;
            if (hbVolume > 0.5f) hbVolume = 0.5f;
            PlaySoundS2CPacket heartPacket = new PlaySoundS2CPacket(
                net.minecraft.registry.Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_WARDEN_HEARTBEAT),
                SoundCategory.MASTER,
                player.getX(), player.getY(), player.getZ(),
                hbVolume,
                hbPitch,
                player.getRandom().nextLong()
            );
            player.networkHandler.sendPacket(heartPacket);
        }

        // Random door creak (creepy wooden sounds) — more frequent at escalation
        int creakInterval = 250 - escalationLevel * 30;
        if (creakInterval < 100) creakInterval = 100;
        if (playerWorld.getTime() % creakInterval == 0 && player.getRandom().nextFloat() < 0.12f + escalationLevel * 0.04f) {
            net.minecraft.util.math.Vec3d creakOffset = new net.minecraft.util.math.Vec3d(
                (player.getRandom().nextDouble() - 0.5) * 16.0, 0,
                (player.getRandom().nextDouble() - 0.5) * 16.0);
            net.minecraft.util.math.Vec3d creakPos = player.getEntityPos().add(creakOffset);
            playerWorld.playSound(null, creakPos.x, creakPos.y, creakPos.z,
                SoundEvents.BLOCK_FENCE_GATE_OPEN, SoundCategory.MASTER, 0.4f, 0.3f);
        }

        // Whisper System - random horror messages in Gloom Void (more frequent at higher escalation)
        int whisperInterval = 200 - escalationLevel * 30;
        if (whisperInterval < 80) whisperInterval = 80;
        float whisperChance = 0.15f + escalationLevel * 0.08f;
        if (whisperChance > 0.5f) whisperChance = 0.5f;
        if (playerWorld.getTime() % whisperInterval == 0 && player.getRandom().nextFloat() < whisperChance) {
            String[] whispers = {
                "§8[???]: §7Помогите...",
                "§8[???]: §7Оно здесь...",
                "§8[???]: §7Не поворачивайся...",
                "§8[???]: §7Они следят за тобой...",
                "§8[???]: §7Беги...",
                "§8[???]: §7Ты не один...",
                "§8[???]: §7Слышишь это?",
                "§8[???]: §7Не смотри в темноту...",
                "§8[???]: §7Оно движется...",
                "§8[???]: §7Спасения нет...",
                "§8[???]: §7Мы были такими же...",
                "§8[???]: §7Осторожно позади...",
                "§8[???]: §7Не делай этого...",
                "§8[???]: §7Ты слышишь шаги?",
                "§8[???]: §7Помогите мне..."
            };
            String whisper = whispers[player.getRandom().nextInt(whispers.length)];
            player.sendMessage(Text.literal(whisper), false);
            com.project3.dread.DreadManager.onWhisper(player);
            // Quiet whisper sound
            playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.MASTER, 0.3f, 0.5f);
        }

        // 2e. Shadow Merchant spawn chance (scales with escalation)
        float merchantChance = 0.05f + escalationLevel * 0.03f;
        if (merchantChance > 0.2f) merchantChance = 0.2f;
        if (playerWorld.getTime() % 1200 == 0 && player.getRandom().nextFloat() < merchantChance) {
            com.project3.dread.ShadowMerchant.trySpawn(player);
        }

        // 3. Perception Collapse Trap (Replaced physical walls)
        int sectorTicks = SECTOR_COOLDOWNS.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(600) + 600);
        if (sectorTicks > 0) {
            SECTOR_COOLDOWNS.put(player.getUuid(), sectorTicks - 1);
        } else {
            // Trigger trap
            player.sendMessage(Text.literal("§8[Эхо]: §7Сектор замыкается..."), false);
            com.project3.dread.DreadManager.onGlitch(player);
            playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 2.0f, 0.8f);

            // Apply Slowness IV and Weakness II for 10 seconds (200 ticks)
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 3, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1, false, false, true));

            // Compress fog via network packet to blind player
            ServerPlayNetworking.send(player, new com.project3.network.FogTargetPayload(0.8F));

            schedule(200, () -> {
                if (player.isAlive()) {
                    // Restore fog
                    ServerPlayNetworking.send(player, new com.project3.network.FogTargetPayload(8.0F));
                    playerWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.MASTER, 2.0f, 0.8f);
                    player.sendMessage(Text.literal("§7[Эхо]: §aПуть свободен."), false);
                }
            });

            SECTOR_COOLDOWNS.put(player.getUuid(), player.getRandom().nextInt(600) + 600);
        }

        // 4. Automatic Screamer Sprint loop check in Gloom Void (escalation affects frequency and count)
        int phCooldownBase = Math.max(1200 - escalationLevel * 150, 300);
        final int phCooldownFinal = phCooldownBase;
        int phTicks = PHANTOM_COOLDOWNS.computeIfAbsent(player.getUuid(), uuid -> player.getRandom().nextInt(phCooldownFinal) + phCooldownFinal);
        if (phTicks > 0) {
            PHANTOM_COOLDOWNS.put(player.getUuid(), phTicks - 1);
        } else {
            com.project3.entity.PhantomReplicator.spawnScreamerSprint(player);
            // At higher escalation, spawn a SECOND screamer from opposite direction
            if (escalationLevel >= 2 && player.getRandom().nextBoolean()) {
                com.project3.entity.PhantomReplicator.spawnScreamerSprint(player);
            }
            com.project3.dread.DreadManager.onPhantomSpawn(player);
            PHANTOM_COOLDOWNS.put(player.getUuid(), player.getRandom().nextInt(phCooldownBase) + phCooldownBase);
        }

        // 4b. Escalation-based visual glitches (shader flash, fog spikes)
        if (escalationLevel >= 2 && playerWorld.getTime() % (300 - escalationLevel * 50) == 0 && player.getRandom().nextFloat() < 0.1f * escalationLevel) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new com.project3.network.ShaderFlashPayload());
        }

        // 5. Portal flickering logic
        int portalStateTicks = PORTAL_STATE_TICKS.getOrDefault(player.getUuid(), 1200);
        portalStateTicks--;
        if (portalStateTicks <= 0) {
            boolean isCurrentlyLit = PORTAL_IS_LIT.getOrDefault(player.getUuid(), true);
            boolean newLit = !isCurrentlyLit;
            PORTAL_IS_LIT.put(player.getUuid(), newLit);

            int hash = Math.abs(player.getUuid().hashCode());
            double vx = (hash % 1000) * 1000.0;
            double vy = 64.0;
            double vz = ((hash / 1000) % 1000) * 1000.0;
            BlockPos pPos = new BlockPos((int)vx, (int)vy, (int)vz);

            if (newLit) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        playerWorld.setBlockState(pPos.add(dx, dy, 0), Blocks.NETHER_PORTAL.getDefaultState()
                            .with(net.minecraft.block.NetherPortalBlock.AXIS, net.minecraft.util.math.Direction.Axis.X));
                    }
                }
                playerWorld.playSound(null, pPos, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1.0f, 1.0f);
                player.sendMessage(Text.literal("§a[Система] Связь восстановлена. Портал стабилен."), false);
                portalStateTicks = player.getRandom().nextInt(1200) + 1200;
            } else {
                // Only remove portal if player is NOT standing inside it to prevent trapping
                boolean playerNearPortal = player.getBlockPos().getManhattanDistance(pPos) < 5;
                if (!playerNearPortal) {
                    for (int dx = 0; dx <= 1; dx++) {
                        for (int dy = 1; dy <= 3; dy++) {
                            playerWorld.setBlockState(pPos.add(dx, dy, 0), Blocks.AIR.getDefaultState());
                        }
                    }
                    playerWorld.playSound(null, pPos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.2f, 0.5f);
                    playerWorld.playSound(null, pPos, SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, 0.8f, 0.5f);
                    player.sendMessage(Text.literal("§c[Система] Сигнальный портал нестабилен... Связь с реальностью потеряна."), false);
                    portalStateTicks = player.getRandom().nextInt(200) + 200;
                } else {
                    // Player is near portal, skip unlit phase and keep it lit
                    PORTAL_IS_LIT.put(player.getUuid(), true);
                    portalStateTicks = player.getRandom().nextInt(600) + 600;
                }
            }
        }
        PORTAL_STATE_TICKS.put(player.getUuid(), portalStateTicks);
    }
}
