package com.project3.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import static com.project3.Project3Mod.MODID;

public final class ModTags {

    private ModTags() {}

    public static final TagKey<Item> LIGHT_SOURCES = TagKey.of(RegistryKeys.ITEM, Identifier.of(MODID, "light_sources"));

}
