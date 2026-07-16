package com.project3.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import static com.project3.Project3Mod.MODID;

public class GloomEffect extends StatusEffect {

    public GloomEffect() {
        super(StatusEffectCategory.HARMFUL, 0x555555);
        this.addAttributeModifier(EntityAttributes.MOVEMENT_SPEED, Identifier.of(MODID, "gloom_speed"), -0.15, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(EntityAttributes.ATTACK_DAMAGE, Identifier.of(MODID, "gloom_attack_damage"), -3.0, EntityAttributeModifier.Operation.ADD_VALUE);
        this.addAttributeModifier(EntityAttributes.LUCK, Identifier.of(MODID, "gloom_luck"), -1.0, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
        return true;
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }
}
