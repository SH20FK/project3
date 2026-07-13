package com.project3.block.entity;

import com.project3.Project3Mod;
import com.project3.network.CameraRotatePayload;
import com.project3.state.Project3State;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Block entity for the Producer Block.
 * Handles server-tick logic: proximity effects and camera rotation packets.
 */
public class ProducerBlockEntity extends BlockEntity {

    private static final int EFFECT_TICK_INTERVAL = 20;       // 1 second
    private static final int EFFECT_RADIUS = 3;
    private static final int COOLDOWN_TICKS = 600;             // 30 seconds
    private static final int CAMERA_CHANCE = 80;               // 1/80 per tick

    // Fix #4: use net.minecraft.util.math.random.Random — thread-safe Minecraft random
    // Fix #19: wrap tickCounter using modulo to prevent integer overflow
    private int tickCounter = 0;

    // Processing system
    private final SimpleInventory processingInventory = new SimpleInventory(2) {
        @Override
        public void markDirty() {
            ProducerBlockEntity.this.markDirty();
        }
    };
    private int processingProgress = 0;
    private int processingMaxProgress = 0;
    private boolean isProcessing = false;

    // Recipe registry: input item → output item, time in ticks
    private static final Map<Item, Recipe> RECIPES = new HashMap<>();

    static {
        RECIPES.put(Items.COBBLESTONE, new Recipe(Items.STONE, 40));         // 2 sec
        RECIPES.put(Items.SAND, new Recipe(Items.GLASS, 40));                // 2 sec
        RECIPES.put(Items.CLAY_BALL, new Recipe(Items.BRICK, 60));           // 3 sec
        RECIPES.put(Items.RAW_IRON, new Recipe(Items.IRON_INGOT, 100));      // 5 sec
        RECIPES.put(Items.RAW_GOLD, new Recipe(Items.GOLD_INGOT, 100));      // 5 sec
        RECIPES.put(Items.RAW_COPPER, new Recipe(Items.COPPER_INGOT, 80));   // 4 sec
        RECIPES.put(Items.CACTUS, new Recipe(Items.GREEN_DYE, 60));          // 3 sec
        RECIPES.put(Items.KELP, new Recipe(Items.DRIED_KELP, 40));           // 2 sec
        RECIPES.put(Items.OAK_LOG, new Recipe(Items.CHARCOAL, 80));          // 4 sec
        RECIPES.put(Items.GRAVEL, new Recipe(Items.FLINT, 20));              // 1 sec
        RECIPES.put(Items.COAL_BLOCK, new Recipe(Items.DIAMOND, 400));       // 20 sec - rare, requires full coal block
    }

    public record Recipe(Item output, int timeTicks) {}

    /** Static set to track loaded producer block positions on the client for the compass mixin. */
    public static final java.util.Set<BlockPos> CLIENT_PRODUCERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ProducerBlockEntity(BlockPos pos, BlockState state) {
        super(Project3Mod.PRODUCER_BLOCK_ENTITY_TYPE, pos, state);
    }

    public SimpleInventory getProcessingInventory() {
        return processingInventory;
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        if (world != null && world.isClient()) {
            CLIENT_PRODUCERS.add(this.pos);
        }
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        if (this.getWorld() != null && this.getWorld().isClient()) {
            CLIENT_PRODUCERS.remove(this.pos);
        }
    }

    // ─── Tick ────────────────────────────────────────────────────────────────

    /**
     * Called every server tick via ProducerBlock.getTicker().
     */
    public static void serverTick(World world, BlockPos pos, BlockState state, ProducerBlockEntity be) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        // Fix #19: wrap tickCounter to prevent overflow
        be.tickCounter = (be.tickCounter + 1) % (EFFECT_TICK_INTERVAL * 1000);

        var server = serverWorld.getServer();
        if (server == null) return;
        Project3State p3state = Project3State.getOrCreate(server);

        // Fix #13: one entity query with max radius, then filter by distance for inner checks
        double outerRadius = 10.0;
        double warningRadius = 16.0;
        Box outerBox = Box.of(pos.toCenterPos(), warningRadius * 2, warningRadius * 2, warningRadius * 2);
        List<ServerPlayerEntity> allNearPlayers = serverWorld.getEntitiesByClass(
                ServerPlayerEntity.class, outerBox, p -> true);

        // Fix #4: use world's Random (thread-safe)
        Random rand = serverWorld.getRandom();

        for (ServerPlayerEntity player : allNearPlayers) {
            if (Project3Mod.isWearingPumpkin(player)) continue;

            double sqDist = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

            // Warning zone (10-16 blocks): approach warning with sound
            if (sqDist <= warningRadius * warningRadius && sqDist > outerRadius * outerRadius) {
                if (rand.nextInt(100) == 0) {
                    player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.5f, 0.3f);
                }
            }

            // Within 10 blocks: combustion (1/600, checked every 20 ticks) and chunk reload (1/1200)
            if (sqDist <= outerRadius * outerRadius) {
                if (be.tickCounter % 20 == 0 && rand.nextInt(600) == 0) {
                    player.setOnFireFor(4);
                }
                if (be.tickCounter % 20 == 0 && rand.nextInt(1200) == 0) {
                    ServerPlayNetworking.send(player, new com.project3.network.ChunkReloadPayload());
                }
            }

            // Within EFFECT_RADIUS blocks: camera rotation (1/80)
            double effectRadiusSq = (EFFECT_RADIUS * EFFECT_RADIUS);
            if (sqDist <= effectRadiusSq) {
                if (rand.nextInt(CAMERA_CHANCE) == 0) {
                    float deltaYaw = (rand.nextFloat() - 0.5f) * 50.0f;
                    float deltaPitch = (rand.nextFloat() - 0.5f) * 50.0f;
                    ServerPlayNetworking.send(player, new CameraRotatePayload(deltaYaw, deltaPitch));
                }
            }
        }

        // Every EFFECT_TICK_INTERVAL ticks: apply debuff effects in radius
        if (be.tickCounter % EFFECT_TICK_INTERVAL == 0) {
            long currentTick = serverWorld.getTime();

            for (ServerPlayerEntity player : allNearPlayers) {
                if (Project3Mod.isWearingPumpkin(player)) continue;

                double sqDist = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                double effectRadiusSq = (EFFECT_RADIUS * EFFECT_RADIUS);

                if (sqDist <= effectRadiusSq) {
                    long lastCooldown = p3state.getProducerCooldown(player.getUuid());
                    if (currentTick - lastCooldown >= COOLDOWN_TICKS) {
                        applyProducerEffects(player, serverWorld, pos);
                        p3state.setProducerCooldown(player.getUuid(), currentTick);
                    }
                }
            }
        }

        // ── Processing Logic ───────────────────────────────────────────
        be.tickProcessing(serverWorld);
    }

    private void tickProcessing(ServerWorld world) {
        if (isProcessing) {
            processingProgress++;
            if (processingProgress >= processingMaxProgress) {
                // Processing complete — produce output
                ItemStack input = processingInventory.getStack(0);
                ItemStack output = processingInventory.getStack(1);

                if (!input.isEmpty()) {
                    Recipe recipe = RECIPES.get(input.getItem());
                    if (recipe != null) {
                        // Determine zone for bonuses
                        int zone = getZone(world);

                        // Determine output count
                        int count = 1;
                        Random rand = world.getRandom();

                        // Double output bonus
                        float doubleChance = switch (zone) {
                            case 1 -> 0.03f;
                            case 2 -> 0.08f;
                            case 3 -> 0.15f;
                            default -> 0.0f;
                        };
                        if (rand.nextFloat() < doubleChance) {
                            count = 2;
                        }

                        ItemStack result = new ItemStack(recipe.output(), count);

                        // Tier upgrade bonus (rare)
                        float tierChance = switch (zone) {
                            case 2 -> 0.01f;
                            case 3 -> 0.03f;
                            default -> 0.0f;
                        };
                        if (rand.nextFloat() < tierChance) {
                            Item upgraded = getTierUpgrade(recipe.output());
                            if (upgraded != null) {
                                result = new ItemStack(upgraded, count);
                            }
                        }

                        if (output.isEmpty()) {
                            processingInventory.setStack(1, result);
                        } else if (output.isOf(result.getItem()) && output.getCount() + result.getCount() <= output.getMaxCount()) {
                            output.increment(result.getCount());
                        }

                        // Consume input
                        input.decrement(1);

                        // Anomaly check
                        triggerAnomaly(world, zone);
                    }
                }

                isProcessing = false;
                processingProgress = 0;
                processingMaxProgress = 0;
                markDirty();
            }
        } else {
            // Try to start processing
            ItemStack input = processingInventory.getStack(0);
            if (!input.isEmpty()) {
                Recipe recipe = RECIPES.get(input.getItem());
                if (recipe != null) {
                    ItemStack output = processingInventory.getStack(1);
                    if (output.isEmpty() || (output.isOf(recipe.output()) && output.getCount() < output.getMaxCount())) {
                        isProcessing = true;
                        processingProgress = 0;
                        processingMaxProgress = recipe.timeTicks();
                        markDirty();
                    }
                }
            }
        }
    }

    private int getZone(ServerWorld world) {
        if (world.getRegistryKey() != net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,
                net.minecraft.util.Identifier.of("p3", "gloom_void"))) {
            var server = world.getServer();
            if (server == null) return 0;
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) return 0;
            double borderDist = overworld.getWorldBorder().getDistanceInsideBorder(pos.getX(), pos.getZ());
            if (borderDist < 100) return 3;
            if (borderDist < 200) return 2;
            if (borderDist < 300) return 1;
            return 0;
        }
        return 2;
    }

    private Item getTierUpgrade(Item current) {
        if (current == Items.IRON_INGOT) return Items.GOLD_INGOT;
        if (current == Items.GOLD_INGOT) return Items.DIAMOND;
        if (current == Items.RAW_IRON) return Items.RAW_GOLD;
        if (current == Items.RAW_GOLD) return Items.DIAMOND;
        return null;
    }

    private void triggerAnomaly(ServerWorld world, int zone) {
        Random rand = world.getRandom();
        // Find nearest player
        List<ServerPlayerEntity> players = world.getPlayers(p ->
                p.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 16 * 16);
        if (players.isEmpty()) return;
        ServerPlayerEntity target = players.get(0);

        float anomalyBase = switch (zone) {
            case 1 -> 0.03f;
            case 2 -> 0.08f;
            case 3 -> 0.15f;
            default -> 0.0f;
        };
        if (rand.nextFloat() > anomalyBase) return;

        int roll = rand.nextInt(100);
        if (roll < 40) {
            // Phantom spawn
            com.project3.entity.PhantomReplicator.spawnScreamerSprint(target);
            com.project3.dread.DreadManager.onProducerAnomaly(target);
        } else if (roll < 65) {
            // Screen glitch
            ServerPlayNetworking.send(target, new com.project3.network.ShaderFlashPayload());
            com.project3.dread.DreadManager.onProducerAnomaly(target);
        } else if (roll < 85) {
            // Darkness
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0, false, false, true));
            com.project3.dread.DreadManager.onProducerAnomaly(target);
        } else if (roll < 95) {
            // Teleport
            double ox = (rand.nextDouble() - 0.5) * 8;
            double oz = (rand.nextDouble() - 0.5) * 8;
            target.teleport(world, target.getX() + ox, target.getY(), target.getZ() + oz,
                    java.util.Set.of(), target.getYaw(), target.getPitch(), true);
            com.project3.dread.DreadManager.onProducerAnomaly(target);
        } else {
            // Lightning
            com.project3.Project3Mod.schedule(0, () -> {
                var lightning = new LightningEntity(net.minecraft.entity.EntityType.LIGHTNING_BOLT, world);
                lightning.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                lightning.setCosmetic(true);
                world.spawnEntity(lightning);
            });
            com.project3.dread.DreadManager.onProducerAnomaly(target);
        }
    }

    // Fix #20: removed Act parameter — it was never used
    private static void applyProducerEffects(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0));
        world.playSound(null, pos, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    // ─── NBT ─────────────────────────────────────────────────────────────────

    @Override
    protected void writeData(net.minecraft.storage.WriteView view) {
        super.writeData(view);
        view.putInt("ProcessingProgress", processingProgress);
        view.putInt("ProcessingMaxProgress", processingMaxProgress);
        view.putBoolean("IsProcessing", isProcessing);
        view.put("Slot0", ItemStack.CODEC, processingInventory.getStack(0));
        view.put("Slot1", ItemStack.CODEC, processingInventory.getStack(1));
    }

    @Override
    protected void readData(net.minecraft.storage.ReadView view) {
        super.readData(view);
        try {
            processingProgress = view.getInt("ProcessingProgress", 0);
            processingMaxProgress = view.getInt("ProcessingMaxProgress", 0);
            isProcessing = view.getBoolean("IsProcessing", false);
            processingInventory.setStack(0, view.read("Slot0", ItemStack.CODEC).orElse(ItemStack.EMPTY));
            processingInventory.setStack(1, view.read("Slot1", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        } catch (Exception e) {
            Project3Mod.LOGGER.error("Failed to read ProducerBlockEntity data at {}", pos, e);
        }
    }

    // ─── Sync to client ──────────────────────────────────────────────────────

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        // Client doesn't need processing data
        NbtCompound nbt = new NbtCompound();
        return nbt;
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
