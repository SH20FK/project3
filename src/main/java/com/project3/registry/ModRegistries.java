package com.project3.registry;

import com.project3.block.DeadSpaceBlock;
import com.project3.block.PhantomBlock;
import com.project3.block.ProducerBlock;
import com.project3.block.VoidGlassBlock;
import com.project3.block.entity.PhantomBlockEntity;
import com.project3.block.entity.ProducerBlockEntity;
import com.project3.item.AIChronometerItem;
import com.project3.item.AIDumpAnalyzerItem;
import com.project3.item.CalmingAmuletItem;
import com.project3.worldgen.ProducerBlockFeature;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSoundGroup;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

import static com.project3.Project3Mod.MODID;

/**
 * Handles registration of all blocks, block entities, items, and world-gen features.
 * Call {@link #registerAll()} once from {@code Project3Mod.onInitialize()}.
 */
public final class ModRegistries {

    private ModRegistries() {}

    // ─── Blocks ──────────────────────────────────────────────────────────────

    public static final ProducerBlock PRODUCER_BLOCK = new ProducerBlock();

    public static final PhantomBlock PHANTOM_BLOCK = new PhantomBlock();

    public static final Block VOID_GLASS = new VoidGlassBlock(
            AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MODID, "void_glass")))
                    .strength(-1.0f, 3600000.0f)
                    .dropsNothing()
                    .allowsSpawning(Blocks::never)
                    .nonOpaque()
                    .blockVision(Blocks::never)
                    .suffocates(Blocks::never)
                    .solidBlock(Blocks::never)
                    .sounds(BlockSoundGroup.GLASS));

    public static final Block DEAD_SPACE = new DeadSpaceBlock(
            AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MODID, "dead_space")))
                    .strength(-1.0f, 3600000.0f)
                    .mapColor(MapColor.BLACK)
                    .dropsNothing()
                    .allowsSpawning(Blocks::never)
                    .solidBlock(Blocks::never)
                    .nonOpaque()
                    .blockVision(Blocks::never));

    // ─── Block Entity Types ───────────────────────────────────────────────────

    public static BlockEntityType<ProducerBlockEntity> PRODUCER_BLOCK_ENTITY_TYPE;
    public static BlockEntityType<PhantomBlockEntity>  PHANTOM_BLOCK_ENTITY_TYPE;

    // ─── Items ────────────────────────────────────────────────────────────────

    public static final AIChronometerItem AI_CHRONOMETER = new AIChronometerItem(
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MODID, "ai_chronometer")))
                    .maxCount(1));

    public static final AIDumpAnalyzerItem AI_DUMP_ANALYZER = new AIDumpAnalyzerItem(
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MODID, "ai_dump_analyzer")))
                    .maxCount(1));

    public static final Item CALMING_AMULET = new CalmingAmuletItem(
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MODID, "calming_amulet")))
                    .maxCount(1)
                    .maxDamage(500));

    // ─── World-gen Features ───────────────────────────────────────────────────

    public static final Feature<DefaultFeatureConfig> PRODUCER_BLOCK_FEATURE =
            new ProducerBlockFeature(DefaultFeatureConfig.CODEC);

    // ─── Registration ─────────────────────────────────────────────────────────

    public static void registerAll() {
        // Blocks
        registerBlock("producer_block", PRODUCER_BLOCK);
        registerBlock("phantom_block",  PHANTOM_BLOCK);
        registerBlock("void_glass",     VOID_GLASS);
        registerBlock("dead_space",     DEAD_SPACE);

        // Items (standalone)
        Registry.register(Registries.ITEM, Identifier.of(MODID, "ai_chronometer"),  AI_CHRONOMETER);
        Registry.register(Registries.ITEM, Identifier.of(MODID, "ai_dump_analyzer"), AI_DUMP_ANALYZER);
        Registry.register(Registries.ITEM, Identifier.of(MODID, "calming_amulet"),   CALMING_AMULET);

        // Block Entity Types
        PRODUCER_BLOCK_ENTITY_TYPE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MODID, "producer_block"),
                FabricBlockEntityTypeBuilder.create(ProducerBlockEntity::new, PRODUCER_BLOCK).build());

        PHANTOM_BLOCK_ENTITY_TYPE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MODID, "phantom_block"),
                FabricBlockEntityTypeBuilder.create(PhantomBlockEntity::new, PHANTOM_BLOCK).build());

        // World-gen Feature
        Registry.register(Registries.FEATURE, Identifier.of(MODID, "producer_block"), PRODUCER_BLOCK_FEATURE);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Registers a block and its corresponding BlockItem under the same id. */
    private static void registerBlock(String name, Block block) {
        Identifier id = Identifier.of(MODID, name);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id,
                new BlockItem(block, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, id))));
    }
}
