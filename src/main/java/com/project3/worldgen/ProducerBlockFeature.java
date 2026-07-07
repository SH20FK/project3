package com.project3.worldgen;

import com.mojang.serialization.Codec;
import com.project3.Project3Mod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

public class ProducerBlockFeature extends Feature<DefaultFeatureConfig> {

    public ProducerBlockFeature(Codec<DefaultFeatureConfig> configCodec) {
        super(configCodec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos pos = context.getOrigin();

        // Ensure we are in the Overworld
        if (world.toServerWorld().getRegistryKey() != World.OVERWORLD) {
            return false;
        }

        int x = pos.getX();
        int z = pos.getZ();

        // Check if inside the border strip zone (19800 to 20200)
        boolean inStripX = (Math.abs(x) >= 19_800 && Math.abs(x) <= 20_200);
        boolean inStripZ = (Math.abs(z) >= 19_800 && Math.abs(z) <= 20_200);
        if (!inStripX && !inStripZ) {
            return false;
        }

        // 1/40 chance per chunk column
        Random random = context.getRandom();
        if (random.nextInt(40) != 0) {
            return false;
        }

        // Find the top heightmap position (MOTION_BLOCKING)
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        BlockPos.Mutable checkPos = new BlockPos.Mutable(x, topY, z);
        boolean foundGround = false;

        // Scan down to find a valid ground block to replace
        while (checkPos.getY() > world.getBottomY()) {
            BlockState blockState = world.getBlockState(checkPos);
            Block block = blockState.getBlock();
            
            // Check if block is a valid ground block (grass, dirt, podzol, clay, sand, gravel, mud, moss)
            if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT
                    || block == Blocks.PODZOL || block == Blocks.MYCELIUM || block == Blocks.SAND
                    || block == Blocks.RED_SAND || block == Blocks.GRAVEL || block == Blocks.CLAY
                    || block == Blocks.MOSS_BLOCK || block == Blocks.MUD) {
                foundGround = true;
                break;
            }
            
            // Move down to search
            checkPos.move(net.minecraft.util.math.Direction.DOWN);
        }

        if (foundGround) {
            // Replace the ground block so it doesn't protrude above the terrain
            world.setBlockState(checkPos, Project3Mod.PRODUCER_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
            return true;
        }

        return false;
    }
}
