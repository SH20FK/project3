package com.project3.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;

public class HappinessEffect extends StatusEffect {

    public HappinessEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x55FF55);
    }

    @Override
    public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, amplifier, true, false, false));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, amplifier, true, false, false));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 40, amplifier, true, false, false));
        return true;
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }
}
