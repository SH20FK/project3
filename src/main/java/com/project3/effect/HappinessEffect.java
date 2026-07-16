package com.project3.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Custom happiness effect that combines Speed, Strength, and Luck.
 * Applied when player is in the happiness state.
 */
public class HappinessEffect extends StatusEffect {

    public HappinessEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x55FF55);
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, amplifier, true, false, true));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, amplifier, true, false, true));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 40, amplifier, true, false, true));
    }

    @Override
    public boolean shouldApplyUpdateEffect(LivingEntity entity, int amplifier) {
        return true;
    }
}
