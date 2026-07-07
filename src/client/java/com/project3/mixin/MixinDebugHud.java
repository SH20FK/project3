package com.project3.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(DebugHud.class)
public class MixinDebugHud {

    @Shadow @Final private MinecraftClient client;

    @ModifyVariable(method = "drawText", at = @At("HEAD"), argsOnly = true, index = 2)
    private List<String> modifyDebugText(List<String> original) {
        if (client.world != null && client.world.getRegistryKey().getValue().toString().equals("p3:gloom_void")) {
            if (original != null) {
                List<String> corrupted = new ArrayList<>();
                for (String line : original) {
                    corrupted.add(corruptLine(line));
                }
                return corrupted;
            }
        }
        return original;
    }

    private String corruptLine(String original) {
        String lower = original.toLowerCase();
        if (lower.contains("xyz") || lower.contains("block") || lower.contains("chunk") || 
            lower.contains("facing") || lower.contains("biome") || lower.contains("direction") ||
            lower.contains("coord") || lower.contains("position") || lower.contains("shir") || lower.contains("dol")) {
            
            double rand = Math.random();
            if (rand < 0.25) {
                return "§c[DATA CORRUPTED]";
            } else if (rand < 0.5) {
                return "§4SYSTEM_ERROR";
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < original.length(); i++) {
                    char c = original.charAt(i);
                    if (c == ' ' || c == ':') {
                        sb.append(c);
                    } else {
                        String chars = "0123456789!?@#$@%&*";
                        sb.append(chars.charAt((int)(Math.random() * chars.length())));
                    }
                }
                return sb.toString();
            }
        }
        return original;
    }
}
