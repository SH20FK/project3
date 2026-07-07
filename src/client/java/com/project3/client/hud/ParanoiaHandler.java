package com.project3.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/**
 * Handles paranoia effects based on progress level.
 * Level 0: No effects
 * Level 1: Occasional name flicker (5% chance)
 * Level 2: Frequent name flicker (15% chance) + occasional garbled chat
 * Level 3: Constant name flicker (30% chance) + garbled chat
 * Level 4: Very frequent flicker (50%) + very garbled chat + screen effects
 * Level 5: Maximum paranoia - everything is unstable
 */
@Environment(EnvType.CLIENT)
public class ParanoiaHandler {

    private static int paranoiaLevel = 0;
    private static long tickCounter = 0;

    public static void setLevel(int level) {
        paranoiaLevel = Math.max(0, Math.min(5, level));
    }

    public static int getLevel() {
        return paranoiaLevel;
    }

    /**
     * Called every client tick to update paranoia effects.
     */
    public static void tick() {
        if (paranoiaLevel <= 0) return;
        tickCounter++;
    }

    /**
     * Returns true if names should flicker this tick based on paranoia level.
     */
    public static boolean shouldFlickerNames() {
        if (paranoiaLevel <= 0) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        
        float chance = switch (paranoiaLevel) {
            case 1 -> 0.05f;
            case 2 -> 0.15f;
            case 3 -> 0.30f;
            case 4 -> 0.50f;
            case 5 -> 0.75f;
            default -> 0.0f;
        };
        
        return client.player.getRandom().nextFloat() < chance;
    }

    /**
     * Returns garbled version of text based on paranoia level.
     * Higher levels = more garbled characters.
     */
    public static Text garbleText(Text original) {
        if (paranoiaLevel <= 1) return original;
        
        String content = original.getString();
        if (content.isEmpty()) return original;
        
        float garbleRatio = switch (paranoiaLevel) {
            case 2 -> 0.10f;
            case 3 -> 0.25f;
            case 4 -> 0.40f;
            case 5 -> 0.60f;
            default -> 0.0f;
        };
        
        char[] chars = content.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == ' ' || chars[i] == '\n') continue;
            if (Math.random() < garbleRatio) {
                // Replace with random glitch character
                chars[i] = getRandomGlitchChar();
            }
        }
        
        return Text.literal(new String(chars)).setStyle(original.getStyle());
    }

    /**
     * Returns true if armor stands should rotate toward player.
     */
    public static boolean shouldRotateArmorStands() {
        return paranoiaLevel >= 3;
    }

    /**
     * Returns screen shake intensity (0 = none, 1 = max).
     */
    public static float getScreenShakeIntensity() {
        if (paranoiaLevel <= 2) return 0.0f;
        return (paranoiaLevel - 2) / 3.0f; // 0.33 at level 3, 0.67 at level 4, 1.0 at level 5
    }

    private static char getRandomGlitchChar() {
        // Cyrillic and special characters that look like latin but aren't
        char[] glitchChars = {
            'а', 'е', 'о', 'р', 'с', 'у', 'х', // Cyrillic looking like latin
            '×', '÷', '±', '∞', '∑', '∏', '√',
            '█', '▓', '▒', '░', '▐', '▌',
            '╔', '╗', '╚', '╝', '║', '═',
            '↑', '↓', '←', '→', '↔', '↕',
            '♦', '♣', '♠', '♥', '▀', '▄'
        };
        return glitchChars[(int) (Math.random() * glitchChars.length)];
    }
}
