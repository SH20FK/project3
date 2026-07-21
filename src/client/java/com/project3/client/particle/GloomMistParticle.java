package com.project3.client.particle;

import net.minecraft.client.particle.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

public class GloomMistParticle extends BillboardParticle {

    protected GloomMistParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, Sprite sprite) {
        super(world, x, y, z, vx, vy, vz, sprite);
        this.velocityMultiplier = 0.96f;
        this.velocityX = vx + (random.nextDouble() - 0.5) * 0.02;
        this.velocityY = vy + (random.nextDouble() - 0.5) * 0.01;
        this.velocityZ = vz + (random.nextDouble() - 0.5) * 0.02;
        this.scale = 0.6f + random.nextFloat() * 0.8f;
        this.maxAge = 60 + random.nextInt(40);
        this.alpha = 0.6f;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = (float) Math.max(0, 0.6 * (1.0 - (double) age / maxAge));
    }

    @Override
    public ParticleTextureSheet getRenderType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz, Random random) {
            return new GloomMistParticle(world, x, y, z, vx, vy, vz, spriteProvider.getSprite(random));
        }
    }
}
