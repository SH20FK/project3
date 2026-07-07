package com.project3.client.screen;

import com.project3.block.entity.ProducerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Client-side GUI for Producer Block.
 * Shows input/output slots, progress bar, zone info.
 */
public class ProducerScreen extends HandledScreen<ProducerScreenHandler> {

    private static final Identifier TEXTURE = Identifier.of("p3", "textures/gui/producer_gui.png");
    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;

    // Progress bar position and size
    private static final int PROGRESS_X = 62;
    private static final int PROGRESS_Y = 36;
    private static final int PROGRESS_WIDTH = 52;
    private static final int PROGRESS_HEIGHT = 10;

    // Zone color names
    private static final String[] ZONE_NAMES = {
        "§aБезопасная",
        "§eРискованная",
        "§6Опасная",
        "§cЭкстремальная"
    };

    private static final int[] ZONE_COLORS = {
        0x55FF55, 0xFFFF55, 0xFFAA00, 0xFF5555
    };

    public ProducerScreen(ProducerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = HEIGHT;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = 72;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        // Draw background — dark theme
        ctx.fill(this.x, this.y, this.x + WIDTH, this.y + HEIGHT, 0xFF1A1A2E);
        ctx.fill(this.x + 1, this.y + 1, this.x + WIDTH - 1, this.y + HEIGHT - 1, 0xFF16213E);

        // Title
        ctx.drawTextWithShadow(this.textRenderer, "ПРОДЮСЕР-СТАНЦИЯ",
                this.x + WIDTH / 2 - this.textRenderer.getWidth("ПРОДЮСЕР-СТАНЦИЯ") / 2,
                this.y + 4, 0xFFCCCCCC);

        // Input slot background
        ctx.fill(this.x + 24, this.y + 33, this.x + 44, this.y + 53, 0xFF0F3460);
        ctx.fill(this.x + 25, this.y + 34, this.x + 43, this.y + 52, 0xFF1A1A2E);

        // Output slot background
        ctx.fill(this.x + 132, this.y + 33, this.x + 152, this.y + 53, 0xFF0F3460);
        ctx.fill(this.x + 133, this.y + 34, this.x + 151, this.y + 52, 0xFF1A1A2E);

        // Arrow between slots
        ctx.fill(this.x + 72, this.y + 38, this.x + 102, this.y + 42, 0xFF333333);

        // Progress bar background
        ctx.fill(this.x + PROGRESS_X - 1, this.y + PROGRESS_Y - 1,
                this.x + PROGRESS_X + PROGRESS_WIDTH + 1, this.y + PROGRESS_Y + PROGRESS_HEIGHT + 1, 0xFF333333);

        // Progress bar fill
        float progressRatio = handler.maxProgress > 0 ? (float) handler.progress / handler.maxProgress : 0;
        int fillWidth = (int) (PROGRESS_WIDTH * progressRatio);
        int progressColor = progressRatio > 0.8f ? 0xFF00CC44 :
                           progressRatio > 0.5f ? 0xFFCCCC00 : 0xFF4488FF;
        if (fillWidth > 0) {
            ctx.fill(this.x + PROGRESS_X, this.y + PROGRESS_Y,
                    this.x + PROGRESS_X + fillWidth, this.y + PROGRESS_Y + PROGRESS_HEIGHT, progressColor);
        }

        // Progress text
        String progressText = String.format("%d%%", (int)(progressRatio * 100));
        ctx.drawTextWithShadow(this.textRenderer, progressText,
                this.x + PROGRESS_X + PROGRESS_WIDTH / 2 - this.textRenderer.getWidth(progressText) / 2,
                this.y + PROGRESS_Y + 2, 0xFFFFFFFF);

        // Zone info
        int zone = handler.zone;
        String zoneName = zone >= 0 && zone < ZONE_NAMES.length ? ZONE_NAMES[zone] : "?";
        ctx.drawTextWithShadow(this.textRenderer, "Зона: " + zoneName,
                this.x + 8, this.y + 58, 0xFFAAAAAA);

        // Zone bonus info
        String[] bonusDescs = {
            "Двойной: 0%  Tier+: 0%",
            "Двойной: 3%  Tier+: 0%",
            "Двойной: 8%  Tier+: 1%",
            "Двойной: 15% Tier+: 3%"
        };
        String bonus = zone >= 0 && zone < bonusDescs.length ? bonusDescs[zone] : "";
        ctx.drawTextWithShadow(this.textRenderer, bonus,
                this.x + 8, this.y + 68, 0xFF888888);

        // Labels
        ctx.drawTextWithShadow(this.textRenderer, "ВХОД",
                this.x + 30, this.y + 25, 0xFF888888);
        ctx.drawTextWithShadow(this.textRenderer, "ВЫХОД",
                this.x + 136, this.y + 25, 0xFF888888);
    }
}
