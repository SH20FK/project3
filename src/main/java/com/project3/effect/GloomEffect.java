package com.project3.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Custom gloom effect that combines Slowness, Weakness, and Unluck.
 * Applied when player is in the gloom state.
 */
public class GloomEffect extends StatusEffect {

    public GloomEffect() {
        super(StatusEffectCategory.HARMFUL, 0x555555);
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, amplifier, true, false, true));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, amplifier, true, false, true));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.UNLUCK, 40, amplifier, true, false, true));
    }

    @Override
    public boolean shouldApplyUpdateEffect(LivingEntity entity, int amplifier) {
        return true;
    }
}
