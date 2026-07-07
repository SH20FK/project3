package com.project3.client.hud;

import com.project3.network.AchievementSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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

    // Smooth needle animation
    private static float currentNeedleAngle = 0.0f;

    public static void tick() {
        if (happinessTicksLeft > 0) {
            happinessTicksLeft--;
        }
        if (gloomTicksLeft > 0) {
            gloomTicksLeft--;
        }
    }

    public static void update(AchievementSyncPayload payload) {
        currentId = payload.id();
        currentTitle = payload.title();
        currentDescription = payload.description();
        iconItemId = payload.iconItemId();
        currentValue = payload.currentValue();
        targetValue = payload.targetValue();
        completedCount = payload.completedCount();
        totalCount = payload.totalCount();
        hasData = true;
    }

    public static void updatePlayerState(com.project3.network.PlayerStateSyncPayload payload) {
        happinessTicksLeft = payload.happinessTicksLeft();
        gloomPermanent = payload.gloomPermanent();
        gloomTicksLeft = payload.gloomTicksLeft();
        unnamedEffectActive = payload.unnamedEffectActive();
        progressLevel = payload.progressLevel();
    }

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
                                if (world.getBlockState(playerPos.add(dx, dy, dz)).isOf(com.project3.Project3Mod.PRODUCER_BLOCK)) {
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
                        int bandHeight = 2 + rand.nextInt(8); // Height between 2 and 9 pixels
                        int bandY = rand.nextInt(screenHeight - bandHeight);
                        context.fill(0, bandY, screenWidth, bandY + bandHeight, 0x507A7A7A);
                    }
                }
            }
        }

        // 1. Render active achievements panel (top-right) if data exists and panel is visible
        if (panelVisible && hasData && !currentId.isEmpty()) {
            int width = 220;
            int x = screenWidth - width - 10;
            int y = 15;

            // Wrap lines dynamically
            java.util.List<OrderedText> titleLines = client.textRenderer.wrapLines(Text.literal(currentTitle).formatted(Formatting.YELLOW), 180);
            java.util.List<OrderedText> descLines = client.textRenderer.wrapLines(Text.literal(currentDescription).formatted(Formatting.GRAY), 180);

            int height = 32 + titleLines.size() * 10 + descLines.size() * 10;

            // Shift tab to the left (aligned with x + 8) and sit it on top of the box
            String tabText = "ЗАДАЧИ";
            int tw = client.textRenderer.getWidth(tabText);
            int tabWidth = tw + 12; // 6 pixels padding on left and right
            int tabX = x + 8;
            int tabHeight = 10;
            int tabY = y - tabHeight;
            int tx = tabX + 6;

            // Render tab background
            context.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, 0xD0101010);
            // Render tab borders
            context.fill(tabX, tabY, tabX + 1, tabY + tabHeight, 0xFF000000); // left outline
            context.fill(tabX, tabY, tabX + tabWidth, tabY + 1, 0xFF000000); // top outline
            context.fill(tabX + tabWidth - 1, tabY, tabX + tabWidth, tabY + tabHeight, 0xFF000000); // right outline
            // Inner highlights for tab
            context.fill(tabX + 1, tabY + 1, tabX + 2, tabY + tabHeight, 0xFF8B8B8B); // inner left
            context.fill(tabX + 1, tabY + 1, tabX + tabWidth - 1, tabY + 2, 0xFF8B8B8B); // inner top
            context.fill(tabX + tabWidth - 2, tabY + 1, tabX + tabWidth - 1, tabY + tabHeight, 0xFF373737); // inner right

            // Draw tab text
            context.drawText(client.textRenderer, Text.literal(tabText).formatted(Formatting.GOLD), tx, tabY + 1, 0xFFFFFFFF, true);

            // Draw main box background
            context.fill(x, y, x + width, y + height, 0xD0101010);

            // Draw main box borders
            // Outer black border
            context.fill(x, y, tabX, y + 1, 0xFF000000); // top left outline
            context.fill(tabX + tabWidth, y, x + width, y + 1, 0xFF000000); // top right outline
            context.fill(x, y + 1, x + 1, y + height, 0xFF000000); // left outline
            context.fill(x + width - 1, y + 1, x + width, y + height, 0xFF000000); // right outline
            context.fill(x, y + height - 1, x + width, y + height, 0xFF000000); // bottom outline

            // Inner highlight (light gray)
            context.fill(x + 1, y + 1, tabX + 1, y + 2, 0xFF8B8B8B); // top left highlight
            context.fill(tabX + tabWidth - 1, y + 1, x + width - 1, y + 2, 0xFF8B8B8B); // top right highlight
            context.fill(x + 1, y + 2, x + 2, y + height - 1, 0xFF8B8B8B); // left highlight

            // Inner shadow (dark gray)
            context.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF373737); // bottom shadow
            context.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, 0xFF373737); // right shadow

            // Resolve icon item stack
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

            // Draw active achievement item icon
            context.drawItem(iconStack, x + 8, y + 8);
            
            // Draw progressive count under the item icon
            if (targetValue > 1) {
                String countText = currentValue + "/" + targetValue;
                int countColor = currentValue >= targetValue ? 0xFF55FF55 : 0xFFFF5555;
                int textWidth = client.textRenderer.getWidth(countText);
                int txCount = x + 16 - textWidth / 2;
                context.drawText(client.textRenderer, Text.literal(countText), txCount, y + 26, countColor, true);
            }

            // Draw title lines
            int currentY = y + 8;
            for (OrderedText line : titleLines) {
                context.drawText(client.textRenderer, line, x + 32, currentY, 0xFFFFFFFF, true);
                currentY += 10;
            }

            // Draw description lines
            for (OrderedText line : descLines) {
                context.drawText(client.textRenderer, line, x + 32, currentY, 0xFFFFFFFF, true);
                currentY += 10;
            }

            // Overall achievements progress tracker
            String progressStr = "Прогресс: " + completedCount + " / " + totalCount;
            context.drawText(client.textRenderer, Text.literal(progressStr).formatted(Formatting.AQUA), x + 32, currentY, 0xFFFFFFFF, true);
            currentY += 11;

            // Draw visual overall progress bar
            if (totalCount > 0) {
                int barX = x + 32;
                int barY = currentY;
                int barW = width - 40; // 140
                int barH = 5;

                // Background
                context.fill(barX, barY, barX + barW, barY + barH, 0xFF000000);
                context.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF2A2A2A);

                // Fill
                float progressRatio = (float) completedCount / (float) totalCount;
                int fillW = (int) (progressRatio * (barW - 2));
                if (fillW > 0) {
                    context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, 0xFF55FF55);
                }
            }

            // Render active statuses
            int statusY = y + height + 5;
            
            // 1. Mask Active (Wearing Pumpkin)
            boolean isMasked = client.player != null && client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isOf(net.minecraft.item.Items.CARVED_PUMPKIN);
            if (isMasked) {
                context.fill(x, statusY, x + width, statusY + 12, 0xD0101010);
                
                context.fill(x, statusY, x + width, statusY + 1, 0xFF000000); // top outline
                context.fill(x, statusY + 11, x + width, statusY + 12, 0xFF000000); // bottom outline
                context.fill(x, statusY + 1, x + 1, statusY + 11, 0xFF000000); // left outline
                context.fill(x + width - 1, statusY + 1, x + width, statusY + 11, 0xFF000000); // right outline

                context.fill(x + 1, statusY + 1, x + width - 1, statusY + 2, 0xFF8B8B8B); // top highlight
                context.fill(x + 1, statusY + 2, x + 2, statusY + 11, 0xFF8B8B8B); // left highlight
                context.fill(x + 1, statusY + 10, x + width - 1, statusY + 11, 0xFF373737); // bottom shadow
                context.fill(x + width - 2, statusY + 2, x + width - 1, statusY + 10, 0xFF373737); // right shadow

                context.drawText(client.textRenderer, Text.literal("Маскировка").formatted(Formatting.GOLD), x + 6, statusY + 2, 0xFFFFFFFF, true);
                statusY += 14;
            }

            // 2. Invisibility Active
            boolean isInvisible = client.player != null && client.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY);
            if (isInvisible) {
                context.fill(x, statusY, x + width, statusY + 12, 0xD0101010);

                context.fill(x, statusY, x + width, statusY + 1, 0xFF000000); // top outline
                context.fill(x, statusY + 11, x + width, statusY + 12, 0xFF000000); // bottom outline
                context.fill(x, statusY + 1, x + 1, statusY + 11, 0xFF000000); // left outline
                context.fill(x + width - 1, statusY + 1, x + width, statusY + 11, 0xFF000000); // right outline

                context.fill(x + 1, statusY + 1, x + width - 1, statusY + 2, 0xFF8B8B8B); // top highlight
                context.fill(x + 1, statusY + 2, x + 2, statusY + 11, 0xFF8B8B8B); // left highlight
                context.fill(x + 1, statusY + 10, x + width - 1, statusY + 11, 0xFF373737); // bottom shadow
                context.fill(x + width - 2, statusY + 2, x + width - 1, statusY + 10, 0xFF373737); // right shadow

                context.drawText(client.textRenderer, Text.literal("Скрытие").formatted(Formatting.DARK_AQUA), x + 6, statusY + 2, 0xFFFFFFFF, true);
            }
        }

        // 2. Render Dial/Meter (bottom-right)
        renderDial(context, client, screenWidth, screenHeight);
    }

    public static void renderInScreen(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Render the Dial/Meter (bottom-right)
        renderDial(context, client, screenWidth, screenHeight);

        // Render hover tooltip on screens
        renderTooltip(context, client, screenWidth, screenHeight, mouseX, mouseY);
    }

    private static void renderDial(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight) {
        // Position watch in bottom-right corner, slightly offset from the screen boundaries
        int cx = screenWidth - 30;
        int cy = screenHeight - 30;

        // Dial face background colors
        // Left side is Gloom (dark purple #2A0833), right side is Happiness (gold/green #526D1E)
        context.fill(cx - 11, cy - 11, cx, cy + 11, 0xFF2A0833);
        context.fill(cx, cy - 11, cx + 11, cy + 11, 0xFF526D1E);

        // Draw outer watch frame (circular look)
        // Top/bottom outer border
        context.fill(cx - 10, cy - 14, cx + 10, cy - 13, 0xFF373737);
        context.fill(cx - 10, cy + 13, cx + 10, cy + 14, 0xFF373737);
        // Left/right outer border
        context.fill(cx - 14, cy - 10, cx - 13, cy + 10, 0xFF373737);
        context.fill(cx + 13, cy - 10, cx + 14, cy + 10, 0xFF373737);
        // Diagonals outer border
        context.fill(cx - 13, cy - 13, cx - 10, cy - 10, 0xFF373737);
        context.fill(cx + 10, cy - 13, cx + 13, cy - 10, 0xFF373737);
        context.fill(cx - 13, cy + 10, cx - 10, cy + 13, 0xFF373737);
        context.fill(cx + 10, cy + 10, cx + 13, cy + 13, 0xFF373737);

        // Inner border highlight (light gray)
        context.fill(cx - 9, cy - 13, cx + 9, cy - 12, 0xFF8B8B8B);
        context.fill(cx - 9, cy + 12, cx + 9, cy + 13, 0xFF8B8B8B);
        context.fill(cx - 13, cy - 9, cx - 12, cy + 9, 0xFF8B8B8B);
        context.fill(cx + 12, cy - 9, cx + 13, cy + 9, 0xFF8B8B8B);

        // Determine target angle
        float targetAngle = 0.0f;
        String statusText = "Нейтрально";
        int textColor = 0xFFAAAAAA;

        if (happinessTicksLeft > 0) {
            // Points to the right side (0 to PI/2). Clamp to max ratio 1.0.
            float ratio = (float) Math.pow(Math.min(72000.0, (double) happinessTicksLeft) / 72000.0, 0.3);
            targetAngle = ratio * (float) (Math.PI / 2.0);

            // Calculate hours/minutes for text display
            long minutesTotal = happinessTicksLeft / 1200;
            long hours = minutesTotal / 60;
            long minutes = minutesTotal % 60;
            statusText = String.format("Счастье %dч %02dм", hours, minutes);
            textColor = 0xFF55FF55;
        } else if (gloomTicksLeft > 0) {
            // Points to the left side (-PI/2 to 0). Clamp to max ratio 1.0.
            float ratio = (float) Math.pow(Math.min(72000.0, (double) gloomTicksLeft) / 72000.0, 0.3);
            targetAngle = -ratio * (float) (Math.PI / 2.0);

            // Calculate hours/minutes for text display
            long minutesTotal = gloomTicksLeft / 1200;
            long hours = minutesTotal / 60;
            long minutes = minutesTotal % 60;
            statusText = String.format("Уныние %dч %02dм", hours, minutes);
            textColor = 0xFFBB55FF;
        } else if (gloomPermanent) {
            // Points all the way left (-PI/2)
            targetAngle = -(float) (Math.PI / 2.0);
            statusText = "Уныние";
            textColor = 0xFFBB55FF;
        }

        // Interpolate needle angle (F3+A-like smooth rotation)
        currentNeedleAngle += (targetAngle - currentNeedleAngle) * 0.1f;

        // Draw the needle
        float needleLength = 9.0f;
        int endX = cx + (int) Math.round(Math.sin(currentNeedleAngle) * needleLength);
        int endY = cy - (int) Math.round(Math.cos(currentNeedleAngle) * needleLength);
        drawLine(context, cx, cy, endX, endY, 0xFFFF2222);

        // Draw the center pin
        context.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);

        // Render text label to the left of the dial
        int textWidth = client.textRenderer.getWidth(statusText);
        context.drawText(client.textRenderer, Text.literal(statusText), cx - 18 - textWidth, cy - 4, textColor, true);
    }

    private static void renderTooltip(DrawContext context, MinecraftClient client, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int cx = screenWidth - 30;
        int cy = screenHeight - 30;

        if (mouseX >= cx - 14 && mouseX <= cx + 14 && mouseY >= cy - 14 && mouseY <= cy + 14) {
            java.util.List<Text> tooltipText = new java.util.ArrayList<>();
            if (happinessTicksLeft > 0) {
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
            } else if (gloomTicksLeft > 0 || gloomPermanent) {
                tooltipText.add(Text.literal("Эффект: Уныние").formatted(Formatting.RED));
                if (gloomTicksLeft > 0) {
                    long minutesTotal = gloomTicksLeft / 1200;
                    long hours = minutesTotal / 60;
                    long minutes = minutesTotal % 60;
                    tooltipText.add(Text.literal(String.format("Длительность: %dч %02dм", hours, minutes)).formatted(Formatting.GRAY));
                } else {
                    tooltipText.add(Text.literal("Длительность: Постоянно").formatted(Formatting.GRAY));
                }
                tooltipText.add(Text.literal(""));
                tooltipText.add(Text.literal("Активные дебаффы:").formatted(Formatting.GOLD));
                tooltipText.add(Text.literal("• Неудача").formatted(Formatting.YELLOW));
                tooltipText.add(Text.literal("• Руды могут превратиться в камень (2%)").formatted(Formatting.YELLOW));
            } else {
                tooltipText.add(Text.literal("Эффект: Нейтрально").formatted(Formatting.GRAY));
            }
            context.drawTooltip(client.textRenderer, tooltipText, mouseX, mouseY);
        }
    }

    private static void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            context.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }
}
