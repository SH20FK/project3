package com.project3.particle;

import com.project3.Project3Mod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModParticles {

    public static final SimpleParticleType GLOOM_MIST = FabricParticleTypes.simple();

    private ModParticles() {}

    public static void registerAll() {
        Registry.register(Registries.PARTICLE_TYPE,
                Identifier.of(Project3Mod.MODID, "gloom_mist"),
                GLOOM_MIST);
    }
}
