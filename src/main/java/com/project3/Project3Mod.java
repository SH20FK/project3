package com.project3;

import com.project3.achievement.AchievementManager;
import com.project3.command.Project3Command;
import com.project3.dread.ShadowMerchant;
import com.project3.entity.PhantomReplicator;
import com.project3.player.PlayerCooldowns;
import com.project3.player.PlayerEventHandler;
import com.project3.registry.ModRegistries;
import com.project3.registry.NetworkRegistrar;
import com.project3.state.Project3State;
import com.project3.world.CalibrationManager;
import com.project3.world.WorldBorderManager;
import com.project3.world.WorldOdditiesHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main server-side entry point for Project3.
 * Delegates actual work to domain-specific managers.
 */
public class Project3Mod implements ModInitializer {

    public static final String MODID = "p3";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    
    public static final Identifier OPEN_INVENTORY_STAT_ID = Identifier.of("minecraft", "open_inventory");
    public static final Identifier GIVE_ALLAY_FLOWER_STAT_ID = Identifier.of(MODID, "give_allay_flower");
    public static final Identifier MACE_KILL_50_BLOCKS_STAT_ID = Identifier.of(MODID, "mace_kill_50_blocks");
    public static final Identifier SHOOT_FIREWORK_CROSSBOW_STAT_ID = Identifier.of(MODID, "shoot_firework_crossbow");
    public static final Identifier PLAY_MUSIC_DISC_STAT_ID = Identifier.of(MODID, "play_music_disc");
    
    public static final RegistryKey<World> GLOOM_VOID_WORLD_KEY = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MODID, "gloom_void"));

    public static final AchievementManager ACHIEVEMENT_MANAGER = new AchievementManager();

    // ─── Scheduled Tasks Schedulers (zero listener leak) ───────────────────
    public static class ScheduledTask {
        public int remaining;
        public final Runnable action;
        public ScheduledTask(int remaining, Runnable action) {
            this.remaining = remaining;
            this.action = action;
        }
    }
    private static final List<ScheduledTask> SCHEDULED_TASKS = new CopyOnWriteArrayList<>();

    public static void schedule(int delayTicks, Runnable action) {
        SCHEDULED_TASKS.add(new ScheduledTask(delayTicks, action));
    }

    public static boolean isWearingPumpkin(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.CARVED_PUMPKIN);
    }

    public enum Act {
        NOT_STARTED, I, II, III, IV
    }

    public static Act getAct(Project3State state) {
        if (!state.isSeasonStarted()) return Act.NOT_STARTED;
        long elapsedMs = state.getElapsedMs();
        if (elapsedMs < 72L * 3600 * 1000L) return Act.I;
        if (elapsedMs < 168L * 3600 * 1000L) return Act.II;
        if (elapsedMs < 240L * 3600 * 1000L) return Act.III;
        return Act.IV;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Project3 initializing…");

        ModRegistries.registerAll();
        NetworkRegistrar.registerAll();

        CommandRegistrationCallback.EVENT.register(Project3Command::register);

        // ── Connection Events ────────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerEventHandler.onPlayerJoin(handler.player, server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerEventHandler.onPlayerDisconnect(handler.player, server);
        });

        // ── Server Started Event ─────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTED.register(WorldBorderManager::setupBorderOnStart);

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

            int absX = Math.abs(blockX);
            int absZ = Math.abs(blockZ);

            // Producers spawn in the 15000-16000 zone (approach to the wall)
            int distX = absX - 15_000;
            int distZ = absZ - 15_000;
            boolean inZoneX = distX >= 0 && distX <= 1000;
            boolean inZoneZ = distZ >= 0 && distZ <= 1000;
            if (!(inZoneX || inZoneZ)) return;

            state.markGeneratedProducer(cx, cz);

            int dist = Math.min(Math.max(inZoneX ? distX : distZ, 0), 1000);
            int divisor = 200 - (int)(dist / 1000.0 * 180);

            java.util.Random random = new java.util.Random((long)cx * 341873128712L + (long)cz * 132897987541L + world.getSeed());
            if (random.nextInt(Math.max(divisor, 1)) != 0) return;

            int rx = blockX + random.nextInt(16);
            int rz = blockZ + random.nextInt(16);
            MinecraftServer server = world.getServer();

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
                        overworld.setBlockState(checkPos, ModRegistries.PRODUCER_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                        break;
                    }
                    checkPos = checkPos.down();
                }
            });
        });

        registerBlockUseCallbacks();
        registerEntityCallbacks();

        // ── Server tick ──────────────────────────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<ScheduledTask> toRemove = new ArrayList<>();
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

            try {
                PhantomReplicator.tickActiveNpcs(server);
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    PhantomReplicator.tickRecordingAndHistory(player);
                }
            } catch (Exception e) {
                LOGGER.error("Error in virtual phantom tick", e);
            }

            CalibrationManager.tick(server);
            
            Project3State state = Project3State.getOrCreate(server);
            ACHIEVEMENT_MANAGER.tick(server, state);

            if (!state.isSeasonStarted()) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PlayerEventHandler.tickPlayer(player, server, state);
            }

            com.project3.dread.DreadManager.tickDecay(server);
            ShadowMerchant.tickAll(server);
            WorldOdditiesHandler.tick(server);
            WorldBorderManager.checkPlayers(server);
        });

        LOGGER.info("Project3 initialized.");
    }

    private void registerBlockUseCallbacks() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.getRegistryKey() == GLOOM_VOID_WORLD_KEY && !player.isCreative()) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || !(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (world.getRegistryKey() == GLOOM_VOID_WORLD_KEY && !player.isCreative()) return ActionResult.FAIL;

            BlockPos pos = hitResult.getBlockPos();
            BlockState clickedState = world.getBlockState(pos);
            Block clickedBlock = clickedState.getBlock();
            ItemStack heldStack = serverPlayer.getStackInHand(hand);
            Project3State state = Project3State.getOrCreate(sw.getServer());

            int totalOverlord = ACHIEVEMENT_MANAGER.getAchievementCount();
            boolean overlordCompleted = state.isSeasonStarted() && CalibrationManager.calibrationTicksLeft <= 0 &&
                sw.getServer().getPlayerManager().getPlayerList().stream()
                    .anyMatch(p -> state.getCompletedAchievements(p.getUuid()).size() >= totalOverlord);

            boolean isFlintSteel = heldStack.isOf(Items.FLINT_AND_STEEL) || heldStack.isOf(Items.FIRE_CHARGE);
            if (isFlintSteel && clickedBlock == Blocks.OBSIDIAN) {
                boolean locked = !serverPlayer.isCreative() && !overlordCompleted && !state.isNetherForceUnlocked();
                if (locked) {
                    sw.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0f, false, World.ExplosionSourceType.NONE);
                    serverPlayer.damage(sw, sw.getDamageSources().explosion(null, null), 4.0f);
                    sw.playSound(null, pos, SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, 1.0f, 0.5f);
                    sw.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    double centerX = pos.getX() + 0.5;
                    double centerY = pos.getY() + 0.5;
                    double centerZ = pos.getZ() + 0.5;
                    for (int i = 0; i < 150; i++) {
                        double theta = i * 0.2;
                        double radius = 0.1 + i * 0.02;
                        double yOffset = (i * 0.03) - 2.0;
                        sw.spawnParticles(ParticleTypes.PORTAL, centerX + Math.cos(theta) * radius, centerY + yOffset, centerZ + Math.sin(theta) * radius, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                    BlockPos offsetPos = pos.offset(hitResult.getSide());
                    sw.updateListeners(offsetPos, sw.getBlockState(offsetPos), sw.getBlockState(offsetPos), 3);
                    return ActionResult.FAIL;
                }
            }

            boolean isEnderEye = heldStack.isOf(Items.ENDER_EYE);
            if (isEnderEye && clickedBlock == Blocks.END_PORTAL_FRAME) {
                boolean locked = !serverPlayer.isCreative() && !state.isEndForceUnlocked();
                if (locked) {
                    sw.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    sw.updateListeners(pos, clickedState, clickedState, 3);
                    return ActionResult.FAIL;
                }
            }

            return ActionResult.PASS;
        });
        
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, blockState, blockEntity) -> {
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
                    return false;
                }
            }
            return true;
        });
    }

    private void registerEntityCallbacks() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
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
                        if (spe.getItemCooldownManager().isCoolingDown(stack)) return ActionResult.FAIL;
                        spe.getItemCooldownManager().set(stack, 600);
                        stack.decrement(1);
                        spe.getEntityWorld().playSound(null, spe.getX(), spe.getY(), spe.getZ(),
                                SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                        head.set(net.minecraft.component.DataComponentTypes.PROFILE,
                                net.minecraft.component.type.ProfileComponent.ofStatic(targetPlayer.getGameProfile()));
                        net.minecraft.entity.ItemEntity itemEntity = new net.minecraft.entity.ItemEntity(
                                world, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(), head
                        );
                        world.spawnEntity(itemEntity);
                        world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                                SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        return ActionResult.SUCCESS;
                    }
                }
            }
            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity spe) {
                if (spe.getMainHandStack().isOf(Items.MACE)) {
                    PlayerCooldowns.LAST_MACE_ATTACK_FALL_DISTANCE.put(spe.getUuid(), (float) spe.fallDistance);
                    java.util.UUID uuid = spe.getUuid();
                    schedule(20, () -> PlayerCooldowns.LAST_MACE_ATTACK_FALL_DISTANCE.remove(uuid));
                }
                
                Project3State p3State = Project3State.getOrCreate(((ServerWorld) spe.getEntityWorld()).getServer());
                if (p3State.isUnnamedEffectActive(spe.getUuid()) && entity instanceof net.minecraft.entity.LivingEntity target) {
                    if (world.getRandom().nextFloat() < 0.25f) {
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, true, true));
                    }
                }
            }
            return ActionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getAttacker() instanceof ServerPlayerEntity player) {
                Float fallDist = PlayerCooldowns.LAST_MACE_ATTACK_FALL_DISTANCE.remove(player.getUuid());
                if (fallDist == null) fallDist = (float) player.fallDistance;
                if (player.getMainHandStack().isOf(Items.MACE) && fallDist >= 50.0f) {
                    player.incrementStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(MACE_KILL_50_BLOCKS_STAT_ID));
                }
            }
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.entity.projectile.FireworkRocketEntity firework) {
                if (firework.getOwner() instanceof ServerPlayerEntity player) {
                    if (player.isHolding(Items.CROSSBOW)) {
                        player.incrementStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(SHOOT_FIREWORK_CROSSBOW_STAT_ID));
                    }
                }
            }
        });
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
}
