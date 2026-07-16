package com.project3.world;

import com.project3.Project3Mod;
import com.project3.entity.PhantomReplicator;
import com.project3.network.ChunkReloadPayload;
import com.project3.player.PlayerCooldowns;
import com.project3.state.Project3State;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Handles all anomalies occurring in the Overworld based on the season progress level.
 */
public final class WorldOdditiesHandler {

    private WorldOdditiesHandler() {}

    public static void tick(MinecraftServer server) {
        Project3State state = Project3State.getOrCreate(server);
        if (server.getTicks() % 100 != 0 || state.getProgressLevel() <= 0) return;

        int level = state.getProgressLevel();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Random rand = player.getRandom();

            if (rand.nextFloat() < 0.005f * level) {
                player.setOnFireFor(3 + level * 2);
                player.playSound(SoundEvents.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
            }

            if (rand.nextFloat() < 0.002f * level) {
                ServerPlayNetworking.send(player, new ChunkReloadPayload());
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
                PhantomReplicator.spawnStalker(player);
            }

            // Dead Scenario — your past self appears (level 2+)
            if (level >= 2) {
                int dsCooldown = PlayerCooldowns.DEAD_SCENARIO.computeIfAbsent(player.getUuid(), u -> rand.nextInt(3600) + 2400);
                if (dsCooldown > 0) {
                    PlayerCooldowns.DEAD_SCENARIO.put(player.getUuid(), dsCooldown - 100);
                } else {
                    PhantomReplicator.spawnDeadScenario(player);
                    PlayerCooldowns.DEAD_SCENARIO.put(player.getUuid(), rand.nextInt(3600) + 2400);
                }
            }

            // Chat Echo — corrupted messages from your name (level 3+)
            if (level >= 3) {
                int ceCooldown = PlayerCooldowns.CHAT_ECHO.computeIfAbsent(player.getUuid(), u -> rand.nextInt(4800) + 3600);
                if (ceCooldown > 0) {
                    PlayerCooldowns.CHAT_ECHO.put(player.getUuid(), ceCooldown - 100);
                } else {
                    PhantomReplicator.spawnChatEcho(player);
                    PlayerCooldowns.CHAT_ECHO.put(player.getUuid(), rand.nextInt(4800) + 3600);
                }
            }

            // Static — frozen figure appears behind you (level 4+)
            if (level >= 4) {
                int stCooldown = PlayerCooldowns.STATIC.computeIfAbsent(player.getUuid(), u -> rand.nextInt(6000) + 4800);
                if (stCooldown > 0) {
                    PlayerCooldowns.STATIC.put(player.getUuid(), stCooldown - 100);
                } else {
                    PhantomReplicator.spawnStaticNpc(player);
                    PlayerCooldowns.STATIC.put(player.getUuid(), rand.nextInt(6000) + 4800);
                }
            }

            // Deja Vu — time loop, teleport back + screamer (level 5 only)
            if (level >= 5) {
                int dvCooldown = PlayerCooldowns.DEJA_VU.computeIfAbsent(player.getUuid(), u -> rand.nextInt(7200) + 6000);
                if (dvCooldown > 0) {
                    PlayerCooldowns.DEJA_VU.put(player.getUuid(), dvCooldown - 100);
                } else {
                    PhantomReplicator.triggerDejaVu(player);
                    PlayerCooldowns.DEJA_VU.put(player.getUuid(), rand.nextInt(7200) + 6000);
                }
            }

            // Corrupted biome effects at level 4-5
            if (level >= 4) {
                applyCorruptedBiomeEffects(player, level);
            }
        }
    }

    private static void spawnPhantomBlockNear(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        if (world.getRegistryKey() == Project3Mod.GLOOM_VOID_WORLD_KEY) return;
        Random rand = player.getRandom();
        int rx = player.getBlockX() + rand.nextInt(11) - 5;
        int rz = player.getBlockZ() + rand.nextInt(11) - 5;

        if (!world.isChunkLoaded(rx >> 4, rz >> 4)) return;

        int ry = world.getTopY(Heightmap.Type.MOTION_BLOCKING, rx, rz);
        BlockPos pos = new BlockPos(rx, ry, rz);
        BlockState state = world.getBlockState(pos);

        if (state.isAir()) return; 

        if (state.isOf(com.project3.registry.ModRegistries.PHANTOM_BLOCK) || state.isOf(com.project3.registry.ModRegistries.PRODUCER_BLOCK)
            || state.hasBlockEntity() || state.isOf(Blocks.BEDROCK)) {
            return;
        }

        world.setBlockState(pos, com.project3.registry.ModRegistries.PHANTOM_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(pos) instanceof com.project3.block.entity.PhantomBlockEntity pbe) {
            pbe.setReplacedState(state);
        }
    }
 
    private static void deleteBlockNear(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Random rand = player.getRandom();
        int rx = player.getBlockX() + rand.nextInt(9) - 4;
        int ry = player.getBlockY() + rand.nextInt(5) - 2;
        int rz = player.getBlockZ() + rand.nextInt(9) - 4;
        if (!world.isChunkLoaded(rx >> 4, rz >> 4)) return;
        BlockPos pos = new BlockPos(rx, ry, rz);
        BlockState state = world.getBlockState(pos);
        if (!state.isAir() && !state.isOf(Blocks.BEDROCK) && !state.isOf(com.project3.registry.ModRegistries.PRODUCER_BLOCK) && !state.isOf(com.project3.registry.ModRegistries.PHANTOM_BLOCK) && !state.hasBlockEntity()) {
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
        Random rand = player.getRandom();
        int rx = player.getBlockX() + rand.nextInt(9) - 4;
        int ry = player.getBlockY() + rand.nextInt(5) - 2;
        int rz = player.getBlockZ() + rand.nextInt(9) - 4;
        if (!world.isChunkLoaded(rx >> 4, rz >> 4)) return;
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

    private static void applyCorruptedBiomeEffects(ServerPlayerEntity player, int level) {
        if (level < 4) return;
        
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Random rand = player.getRandom();
        
        int radius = 16;
        for (int i = 0; i < 5; i++) { 
            int rx = player.getBlockX() + rand.nextInt(radius * 2 + 1) - radius;
            int ry = player.getBlockY() + rand.nextInt(9) - 4;
            int rz = player.getBlockZ() + rand.nextInt(radius * 2 + 1) - radius;
            if (!world.isChunkLoaded(rx >> 4, rz >> 4)) continue;
            BlockPos pos = new BlockPos(rx, ry, rz);
            BlockState state = world.getBlockState(pos);
            
            if (state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
                if (rand.nextFloat() < 0.3f) { 
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, 
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        3, 0.2, 0.2, 0.2, 0.01);
                }
            }
            
            if (state.isOf(Blocks.SHORT_GRASS) || state.isOf(Blocks.TALL_GRASS) || 
                state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN) ||
                state.isOf(Blocks.POPPY) || state.isOf(Blocks.DANDELION) ||
                state.isOf(Blocks.BLUE_ORCHID) || state.isOf(Blocks.ALLIUM) ||
                state.isOf(Blocks.AZURE_BLUET) || state.isOf(Blocks.RED_TULIP) ||
                state.isOf(Blocks.ORANGE_TULIP) || state.isOf(Blocks.WHITE_TULIP) ||
                state.isOf(Blocks.PINK_TULIP) || state.isOf(Blocks.OXEYE_DAISY) ||
                state.isOf(Blocks.CORNFLOWER) || state.isOf(Blocks.LILY_OF_THE_VALLEY)) {
                if (rand.nextFloat() < 0.5f) { 
                    world.setBlockState(pos, Blocks.DEAD_BUSH.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        
        if (level >= 5 && world.getTime() % 200 == 0) {
            for (int i = 0; i < 2; i++) {
                int rx = player.getBlockX() + rand.nextInt(21) - 10;
                int ry = Math.min(player.getBlockY() - 5, 60); 
                int rz = player.getBlockZ() + rand.nextInt(21) - 10;
                if (!world.isChunkLoaded(rx >> 4, rz >> 4)) continue;
                BlockPos pos = new BlockPos(rx, ry, rz);
                
                while (pos.getY() > world.getBottomY() && 
                       !world.getBlockState(pos).isOf(Blocks.STONE) && 
                       !world.getBlockState(pos).isOf(Blocks.DEEPSLATE)) {
                    pos = pos.down();
                }
                
                if (pos.getY() > world.getBottomY() && 
                    world.getBlockState(pos.up()).isAir()) {
                    world.setBlockState(pos.up(), Blocks.SCULK.getDefaultState(), Block.NOTIFY_LISTENERS);
                    if (rand.nextFloat() < 0.5f) {
                        for (Direction dir : Direction.values()) {
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
}
