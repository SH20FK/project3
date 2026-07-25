package com.project3.client.hud;

import com.project3.network.AchievementSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class ActiveAchievementHud {

    private static String currentId = "";
    private static String currentTitle = "";
    private static String currentDescription = "";
    private static String iconItemId = "";
    private static int currentValue = 0;
    private static int targetValue = 0;
    private static int completedCount = 0;
    private static int totalCount = 0;
    private static boolean hasData = false;

    // Toggle visibility with keybind P
    public static boolean panelVisible = true;

    // Player states synced from server
    private static long happinessTicksLeft = 0L;
    private static boolean gloomPermanent = false;
    private static long gloomTicksLeft = 0L;
    private static boolean unnamedEffectActive = false;
    private static int progressLevel = 0;
    private static int stateIndex = 0;

    // ─── Bar Textures (each 32×64, one for each state 1-6) ───────────────
    private static final Identifier[] BAR_TEXTURES = {
        Identifier.of("p3", "textures/hud/texture.png"),   // 1 — happiness full (gold)
        Identifier.of("p3", "textures/hud/texture2.png"),  // 2 — happiness fading (orange)
        Identifier.of("p3", "textures/hud/texture3.png"),  // 3 — entering gloom (cyan)
        Identifier.of("p3", "textures/hud/texture4.png"),  // 4 — gloom deepening (blue)
        Identifier.of("p3", "textures/hud/texture5.png"),  // 5 — deep gloom (purple)
        Identifier.of("p3", "textures/hud/texture6.png")   // 6 — unnamed (dark red)
    };

    // ─── Panel Textures (achievement window) ─────────────────────────
    private static final Identifier WIN_GRAY   = Identifier.of("p3", "textures/hud/texture_window_gray.png");
    private static final Identifier WIN_GREEN  = Identifier.of("p3", "textures/hud/texture_window_green.png");
    private static final Identifier WIN_RED    = Identifier.of("p3", "textures/hud/texture_window_red.png");
    private static final Identifier WIN_PURPLE = Identifier.of("p3", "textures/hud/texture_window_purple.png");

    // Slide animation
    private static float panelAnimOffset = 300f;
    private static int panelState = 0;

    private static int   completeFadeTicks = 0;
    private static float colorAlpha = 0f;
    private static Identifier activeColorTex = WIN_GREEN;

    private static String   prevId = "";
    private static int      prevCompletedCount = 0;
    private static com.project3.network.AchievementSyncPayload pendingPayload = null;

    // ─── Animation State ─────────────────────────────────────────────────
    private static int prevStateIndex = 0;
    private static int transitionTicks = 0;
    private static int transitionType = 0; // 0=none, 1=decay, 2=growth, 3=unnamed
    private static int displayStateIndex = 0;

    private static final int DECAY_DURATION = 15;
    private static final int GROWTH_DURATION = 12;
    private static final int UNNAMED_DURATION = 8;

    // ─── Tick ────────────────────────────────────────────────────────────

    public static void tick() {
        if (happinessTicksLeft > 0) {
            happinessTicksLeft--;
        }
        if (gloomTicksLeft > 0) {
            gloomTicksLeft--;
        }

        float slideSpeed = 18f;

        switch (panelState) {
            case 1 -> {
                panelAnimOffset -= slideSpeed;
                if (panelAnimOffset <= 0f) {
                    panelAnimOffset = 0f;
                    panelState = 2;
                }
            }
            case 3 -> {
                completeFadeTicks--;
                colorAlpha = 1f - (completeFadeTicks / 20f);
                if (completeFadeTicks <= 0) {
                    colorAlpha = 1f;
                    panelState = 4;
                }
            }
            case 4 -> {
                panelAnimOffset += slideSpeed;
                if (panelAnimOffset >= 300f) {
                    panelAnimOffset = 300f;
                    colorAlpha = 0f;
                    if (pendingPayload != null) {
                        applyPayload(pendingPayload);
                        prevId = currentId;
                        pendingPayload = null;
                        panelState = 1;
                        panelAnimOffset = 300f;
                    } else {
                        panelState = 0;
                    }
                }
            }
        }
    }

    // ─── Data updates ──────────────────────────────────────────────────────

    private static void applyPayload(AchievementSyncPayload payload) {
        currentId          = payload.id();
        currentTitle       = payload.title();
        currentDescription = payload.description();
        iconItemId         = payload.iconItemId();
        currentValue       = payload.currentValue();
        targetValue        = payload.targetValue();
        completedCount     = payload.completedCount();
        totalCount         = payload.totalCount();
    }

    public static void update(AchievementSyncPayload payload) {
        boolean isCompleted = payload.completedCount() > prevCompletedCount
                              && prevCompletedCount >= 0
                              && !currentId.isEmpty();
        boolean isNewTask   = !payload.id().equals(prevId) && !payload.id().isEmpty();

        prevCompletedCount = payload.completedCount();

        if (isCompleted && (panelState == 2 || panelState == 1)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                if (client.world.getRegistryKey() == net.minecraft.world.World.NETHER) {
                    activeColorTex = WIN_RED;
                } else if (client.world.getRegistryKey() == net.minecraft.world.World.END) {
                    activeColorTex = WIN_PURPLE;
                } else {
                    activeColorTex = WIN_GREEN;
                }
            } else {
                activeColorTex = WIN_GREEN;
            }
            colorAlpha = 0f;
            completeFadeTicks = 20;
            panelState = 3;
        }

        if (isNewTask) {
            prevId = payload.id();
            if (panelState == 0 || panelState == 4) {
                applyPayload(payload);
                colorAlpha = 0f;
                panelAnimOffset = 300f;
                panelState = 1;
            } else {
                pendingPayload = payload;
                if (panelState == 2 || panelState == 1) {
                    panelState = 4;
                }
            }
        } else {
            applyPayload(payload);
        }

        hasData = true;
    }

    public static void updatePlayerState(com.project3.network.PlayerStateSyncPayload payload) {
        int newStateIndex = payload.stateIndex();

        if (newStateIndex != stateIndex) {
            prevStateIndex = stateIndex;
            stateIndex = newStateIndex;

            if (stateIndex == 6 || prevStateIndex == 6) {
                transitionType = 3;
                transitionTicks = UNNAMED_DURATION;
                if (stateIndex == 6) {
                    displayStateIndex = prevStateIndex; // show old until flash ends
                } else {
                    displayStateIndex = 6; // show 6 until transition ends
                }
            } else if (stateIndex > prevStateIndex) {
                transitionType = 1;
                transitionTicks = DECAY_DURATION;
                displayStateIndex = prevStateIndex;
            } else {
                transitionType = 2;
                transitionTicks = GROWTH_DURATION;
                displayStateIndex = prevStateIndex;
            }
        }

        happinessTicksLeft = payload.happinessTicksLeft();
        gloomPermanent = payload.gloomPermanent();
        gloomTicksLeft = payload.gloomTicksLeft();
        unnamedEffectActive = payload.unnamedEffectActive();
        progressLevel = payload.progressLevel();
    }

    // ─── Main Render ─────────────────────────────────────────────────────

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;
        if (client.currentScreen != null) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // 0. Emitter glitch effect (radius 3)
        if (client.player != null && !client.player.isCreative() && !client.player.isSpectator()) {
            boolean wearingPumpkin = client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isOf(net.minecraft.item.Items.CARVED_PUMPKIN);
            if (!wearingPumpkin) {
                net.minecraft.util.math.BlockPos playerPos = client.player.getBlockPos();
                boolean nearEmitter = false;
                net.minecraft.client.world.ClientWorld world = client.world;
                if (world != null) {
                    outer:
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                if (world.getBlockState(playerPos.add(dx, dy, dz)).isOf(com.project3.registry.ModRegistries.PRODUCER_BLOCK)) {
                                    nearEmitter = true;
                                    break outer;
                                }
                            }
                        }
                    }
                }

                if (nearEmitter) {
                    net.minecraft.util.math.random.Random rand = client.player.getRandom();
                    for (int i = 0; i < 3; i++) {
                        int bandHeight = 2 + rand.nextInt(8);
                        int bandY = rand.nextInt(screenHeight - bandHeight);
                        context.fill(0, bandY, screenWidth, bandY + bandHeight, 0x507A7A7A);
                    }
                }
            }
        }

        // 1. Render active achievements panel (top-right) if data exists and panel is visible
        if (panelVisible && hasData && !currentId.isEmpty() && panelState != 0) {
            int width = 230;
            int x = (int)(screenWidth - width - 5 + panelAnimOffset);
            int y = 28;

            java.util.List<OrderedText> titleLines = client.textRenderer.wrapLines(Text.literal(currentTitle).formatted(Formatting.YELLOW), 190);
            java.util.List<OrderedText> descLines = client.textRenderer.wrapLines(Text.literal(currentDescription).formatted(Formatting.GRAY), 190);

            int height = 32 + titleLines.size() * 10 + descLines.size() * 10 + 11 + 5;

            draw9Slice(context, WIN_GRAY, x, y, width, height, 0xFFFFFFFF);

            if ((panelState == 3 || panelState == 4) && colorAlpha > 0f) {
                int alpha = (int)(colorAlpha * 255);
                draw9Slice(context, activeColorTex, x, y, width, height, (alpha << 24) | 0xFFFFFF);
            }

            ItemStack iconStack = ItemStack.EMPTY;
            if (iconItemId != null && !iconItemId.isEmpty()) {
                Identifier id = Identifier.tryParse(iconItemId);
                if (id != null) {
                    net.minecraft.item.Item item = Registries.ITEM.get(id);
                    if (item != null && item != net.minecraft.item.Items.AIR) {
                        iconStack = new ItemStack(item);
                    }
                }
            }
            if (iconStack.isEmpty()) {
                iconStack = new ItemStack(net.minecraft.item.Items.BOOK);
            }

            // Dark items such as coal blocks disappear against the window texture without
            // an independent icon slot. Draw a high-contrast slot before the item.
            int iconX = x + 7;
            int iconY = y + 7;
            context.fill(iconX, iconY, iconX + 18, iconY + 18, 0xFF050505);
            context.fill(iconX + 1, iconY + 1, iconX + 17, iconY + 17, 0xFF9A9A9A);
            context.fill(iconX + 2, iconY + 2, iconX + 16, iconY + 16, 0xFF303030);
            context.drawItem(iconStack, x + 8, y + 8);

            if (targetValue > 1) {
                String countText = currentValue + "/" + targetValue;
                int countColor = currentValue >= targetValue ? 0xFF55FF55 : 0xFFFF5555;
                int textWidth = client.textRenderer.getWidth(countText);
                int txCount = x + 16 - textWidth / 2;
                context.drawText(client.textRenderer, Text.literal(countText), txCount, y + 26, countColor, true);
            }

            int currentY = y + 8;
            for (OrderedText line : titleLines) {
                context.drawText(client.textRenderer, line, x + 32, currentY, 0xFFFFFFFF, true);
                currentY += 10;
            }

            for (OrderedText line : descLines) {
                context.drawText(client.textRenderer, line, x + 32, currentY, 0xFFFFFFFF, true);
                currentY += 10;
            }

            String progressStr = "Прогресс: " + completedCount + " / " + totalCount;
            context.drawText(client.textRenderer, Text.literal(progressStr).formatted(Formatting.AQUA), x + 32, currentY, 0xFFFFFFFF, true);
            currentY += 11;

            if (totalCount > 0) {
                int barX = x + 32;
                int barY = currentY;
                int barW = width - 40;
                int barH = 5;

                context.fill(barX, barY, barX + barW, barY + barH, 0xFF000000);
                context.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF2A2A2A);

                float progressRatio = (float) completedCount / (float) totalCount;
                int fillW = (int) (progressRatio * (barW - 2));
                if (fillW > 0) {
                    context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, 0xFF55FF55);
                }
            }

            int statusY = y + height + 5;

            boolean isMasked = client.player != null && client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isOf(net.minecraft.item.Items.CARVED_PUMPKIN);
            if (isMasked) {
                context.fill(x, statusY, x + width, statusY + 12, 0xD0101010);

                context.fill(x, statusY, x + width, statusY + 1, 0xFF000000);
                context.fill(x, statusY + 11, x + width, statusY + 12, 0xFF000000);
                context.fill(x, statusY + 1, x + 1, statusY + 11, 0xFF000000);
                context.fill(x + width - 1, statusY + 1, x + width, statusY + 11, 0xFF000000);

                context.fill(x + 1, statusY + 1, x + width - 1, statusY + 2, 0xFF8B8B8B);
                context.fill(x + 1, statusY + 2, x + 2, statusY + 11, 0xFF8B8B8B);
                context.fill(x + 1, statusY + 10, x + width - 1, statusY + 11, 0xFF373737);
                context.fill(x + width - 2, statusY + 2, x + width - 1, statusY + 10, 0xFF373737);

                context.drawText(client.textRenderer, Text.literal("Маскировка").formatted(Formatting.GOLD), x + 6, statusY + 2, 0xFFFFFFFF, true);
                statusY += 14;
            }

            boolean isInvisible = client.player != null && client.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY);
            if (isInvisible) {
                context.fill(x, statusY, x + width, statusY + 12, 0xD0101010);

                context.fill(x, statusY, x + width, statusY + 1, 0xFF000000);
                context.fill(x, statusY + 11, x + width, statusY + 12, 0xFF000000);
                context.fill(x, statusY + 1, x + 1, statusY + 11, 0xFF000000);
                context.fill(x + width - 1, statusY + 1, x + width, statusY + 11, 0xFF000000);

                context.fill(x + 1, statusY + 1, x + width - 1, statusY + 2, 0xFF8B8B8B);
                context.fill(x + 1, statusY + 2, x + 2, statusY + 11, 0xFF8B8B8B);
                context.fill(x + 1, statusY + 10, x + width - 1, statusY + 11, 0xFF373737);
                context.fill(x + width - 2, statusY + 2, x + width - 1, statusY + 10, 0xFF373737);

                context.drawText(client.textRenderer, Text.literal("Скрытие").formatted(Formatting.DARK_AQUA), x + 6, statusY + 2, 0xFFFFFFFF, true);
            }
        }

        // 2. Render vertical bar (bottom-right)
        renderBar(context, client, screenWidth, screenHeight);
    }

    public static void renderInScreen(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        renderBar(context, client, screenWidth, screenHeight);

        renderTooltip(context, client, screenWidth, screenHeight, mouseX, mouseY);
    }

    // ─── Bar Rendering ───────────────────────────────────────────────────

    private static void renderBar(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight) {
        if (stateIndex == 0) return;

        int bx = screenWidth - 42;
        int by = screenHeight - 80;
        int texW = 32;
        int texH = 64;

        if (transitionTicks > 0) {
            float progress = 1.0f - (transitionTicks / (float) getTransitionDuration());
            transitionTicks--;

            switch (transitionType) {
                case 1 -> renderDecayTransition(context, bx, by, texW, texH, progress);
                case 2 -> renderGrowthTransition(context, bx, by, texW, texH, progress);
                case 3 -> renderUnnamedTransition(context, client, bx, by, texW, texH, progress);
            }

            if (transitionTicks <= 0) {
                displayStateIndex = stateIndex;
                transitionType = 0;
            }
        } else {
            // Normal static bar
            if (displayStateIndex >= 1 && displayStateIndex <= 6) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, BAR_TEXTURES[displayStateIndex - 1], bx, by, 0, 0, texW, texH, texW, texH);
            }
        }
    }

    private static int getTransitionDuration() {
        return switch (transitionType) {
            case 1 -> DECAY_DURATION;
            case 2 -> GROWTH_DURATION;
            case 3 -> UNNAMED_DURATION;
            default -> 0;
        };
    }

    private static void renderDecayTransition(DrawContext context, int bx, int by, int texW, int texH, float progress) {
        int oldIdx = prevStateIndex - 1;
        int newIdx = stateIndex - 1;

        if (oldIdx >= 0 && oldIdx < 6) {
            int oldAlpha = (int)((1.0f - Math.min(progress * 1.5f, 1.0f)) * 255) << 24;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, BAR_TEXTURES[oldIdx], bx, by, 0, 0, texW, texH, texW, texH, oldAlpha | 0xFFFFFF);
        }

        if (newIdx >= 0 && newIdx < 6) {
            int newAlpha = (int)(Math.max(progress * 1.5f - 0.5f, 0.0f) * 255) << 24;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, BAR_TEXTURES[newIdx], bx, by, 0, 0, texW, texH, texW, texH, newAlpha | 0xFFFFFF);
        }

        float midFlash = 1.0f - Math.abs(progress - 0.5f) * 4.0f;
        if (midFlash > 0) {
            int flashAlpha = (int) (midFlash * 60);
            if (flashAlpha > 0) {
                context.fill(bx, by, bx + texW, by + texH, (flashAlpha << 24) | 0x400000);
            }
        }
    }

    private static void renderGrowthTransition(DrawContext context, int bx, int by, int texW, int texH, float progress) {
        int oldIdx = prevStateIndex - 1;
        int newIdx = stateIndex - 1;

        if (oldIdx >= 0 && oldIdx < 6) {
            int oldAlpha = (int)((1.0f - progress) * 255) << 24;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, BAR_TEXTURES[oldIdx], bx, by, 0, 0, texW, texH, texW, texH, oldAlpha | 0xFFFFFF);
        }

        if (newIdx >= 0 && newIdx < 6) {
            int newAlpha = (int)(progress * 255) << 24;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, BAR_TEXTURES[newIdx], bx, by, 0, 0, texW, texH, texW, texH, newAlpha | 0xFFFFFF);
        }

        float goldFlash = 1.0f - Math.abs(progress - 0.5f) * 4.0f;
        if (goldFlash > 0) {
            int flashAlpha = (int) (goldFlash * 50);
            if (flashAlpha > 0) {
                context.fill(bx, by, bx + texW, by + texH, (flashAlpha << 24) | 0xFFD700);
            }
        }
    }

    private static void renderUnnamedTransition(DrawContext context, MinecraftClient client, int bx, int by, int texW, int texH, float progress) {
        int remaining = transitionTicks + 1; // ticks left including current

        if (remaining > 5) {
            // Ticks 0-2 (from 8 to 6): purple screen flash on current texture
            float flashIntensity = (remaining - 5) / 3.0f;
            int flashAlpha = (int) (flashIntensity * 100);
            if (prevStateIndex >= 1 && prevStateIndex <= 6) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, BAR_TEXTURES[prevStateIndex - 1], bx, by, 0, 0, texW, texH, texW, texH);
            }
            if (flashAlpha > 0) {
                context.fill(bx, by, bx + texW, by + texH, (flashAlpha << 24) | 0xFF00FF);
            }
        } else if (remaining == 5) {
            // Tick 3: instant switch to new texture (stateIndex, which is 6)
            // Draw texture6 immediately
            if (stateIndex >= 1 && stateIndex <= 6) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, BAR_TEXTURES[stateIndex - 1], bx, by, 0, 0, texW, texH, texW, texH);
            }
        } else {
            // Ticks 4-7 (remaining 4 to 1): X oscillation
            int wobble = (remaining % 2 == 0) ? 2 : -2;
            if (stateIndex >= 1 && stateIndex <= 6) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, BAR_TEXTURES[stateIndex - 1], bx + wobble, by, 0, 0, texW, texH, texW, texH);
            }
        }
    }

    private static final int BORDER = 6;
    private static final int BODY_H = 52;
    private static final int TEX_W = 220;
    private static final int TEX_H = 64;

    private static void draw9Slice(DrawContext context, Identifier tex, int x, int y, int width, int height, int color) {
        int innerH = height - 2 * BORDER;
        if (innerH < 0) innerH = 0;

        // Top border (6px, full width)
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, width, BORDER, TEX_W, TEX_H, color);

        // Body: tile the 52px middle section (v=6..57) to fill innerH
        int drawn = 0;
        while (drawn < innerH) {
            int tileH = Math.min(BODY_H, innerH - drawn);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y + BORDER + drawn, 0, BORDER, width, tileH, TEX_W, TEX_H, color);
            drawn += BODY_H;
        }

        // Bottom border (6px, full width)
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y + height - BORDER, 0, TEX_H - BORDER, width, BORDER, TEX_W, TEX_H, color);
    }

    // ─── Tooltip ─────────────────────────────────────────────────────────

    private static void renderTooltip(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int bx = screenWidth - 42;
        int by = screenHeight - 80;
        int texW = 32;
        int texH = 64;

        if (mouseX >= bx && mouseX <= bx + texW && mouseY >= by && mouseY <= by + texH) {
            java.util.List<Text> tooltipText = new java.util.ArrayList<>();
            if (unnamedEffectActive) {
                tooltipText.add(Text.literal("Эффект: Безымянный").formatted(Formatting.DARK_RED));
            } else if (happinessTicksLeft > 0) {
                tooltipText.add(Text.literal("Эффект: Счастье").formatted(Formatting.GREEN));
                long minutesTotal = happinessTicksLeft / 1200;
                long hours = minutesTotal / 60;
                long minutes = minutesTotal % 60;
                tooltipText.add(Text.literal(String.format("Длительность: %dч %02dм", hours, minutes)).formatted(Formatting.GRAY));
                tooltipText.add(Text.literal(""));
                tooltipText.add(Text.literal("Активные бонусы:").formatted(Formatting.GOLD));
                tooltipText.add(Text.literal("• Скорость I").formatted(Formatting.YELLOW));
                tooltipText.add(Text.literal("• Притягивание опыта (радиус 6)").formatted(Formatting.YELLOW));
                tooltipText.add(Text.literal("• Животные следуют за вами").formatted(Formatting.YELLOW));
            } else {
                tooltipText.add(Text.literal("Эффект: Уныние").formatted(Formatting.RED));
                if (gloomPermanent) {
                    tooltipText.add(Text.literal("Длительность: Постоянно").formatted(Formatting.GRAY));
                }
                tooltipText.add(Text.literal(""));
                tooltipText.add(Text.literal("Активные дебаффы:").formatted(Formatting.GOLD));
                tooltipText.add(Text.literal("• Неудача").formatted(Formatting.YELLOW));
                tooltipText.add(Text.literal("• Руды могут превратиться в камень (2%)").formatted(Formatting.YELLOW));
            }
            context.drawTooltip(client.textRenderer, tooltipText, mouseX, mouseY);
        }
    }
}
