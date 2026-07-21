Задача: slide-анимация и текстурный фон в ActiveAchievementHud.java

━━━ ПЕРЕНОС ТЕКСТУР ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Переместить эти файлы:
  project3/assets/texture_window_gray.png
  project3/assets/texture_window_green.png
  project3/assets/texture_window_red.png
  project3/assets/texture_window_purple.png

Переместить в:
  src/main/resources/assets/p3/textures/hud/texture_window_gray.png
  src/main/resources/assets/p3/textures/hud/texture_window_green.png
  src/main/resources/assets/p3/textures/hud/texture_window_red.png
  src/main/resources/assets/p3/textures/hud/texture_window_purple.png

━━━ КОНТЕКСТ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Файл: src/client/java/com/project3/client/hud/ActiveAchievementHud.java

Сейчас панель задания рисуется статично через context.fill() (чёрный фон).
Позиция: x = screenWidth - 220 - 10, y = 15, width = 220.
Высота динамическая: 32 + titleLines * 10 + descLines * 10.

━━━ ТОЧНАЯ ПОСЛЕДОВАТЕЛЬНОСТЬ АНИМАЦИИ ━━━━━━━━━━━━━━━━━━━━

Шаг 1 — IDLE:
  Панель стоит на месте. Рисуется texture_window_gray.png как фон.
  Задание активно, ничего не происходит.

Шаг 2 — COMPLETE_FADE (20 тиков = 1 сек):
  Задание выполнено.
  Поверх серой текстуры начинает появляться цветная (green/red/purple).
  Цветная текстура рисуется с alpha от 0 до 255 плавно за 20 тиков.
  В конце цветная текстура полностью непрозрачна (полностью перекрывает серую).
  Панель при этом стоит на месте — никуда не едет.

Шаг 3 — SLIDE_OUT (17 тиков):
  Панель (с уже непрозрачной цветной текстурой) уезжает вправо за экран.
  panelAnimOffset нарастает от 0 до 300f со скоростью 18f за тик.

Шаг 4 — SLIDE_IN (17 тиков):
  Сразу после того как панель ушла — въезжает новая серая панель.
  panelAnimOffset падает от 300f до 0f со скоростью 18f за тик.
  Фон — texture_window_gray.png (нет цветного оверлея).

━━━ ВЫБОР ЦВЕТА ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Определять в момент завершения квеста по client.world.getRegistryKey():
  World.OVERWORLD → texture_window_green.png
  World.NETHER    → texture_window_red.png
  World.END       → texture_window_purple.png
  иначе           → texture_window_green.png

━━━ ИЗМЕНЕНИЯ В КОДЕ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ДОБАВИТЬ ПОЛЯ в класс (рядом с BAR_TEXTURES):

private static final Identifier WIN_GRAY   = Identifier.of("p3", "textures/hud/texture_window_gray.png");
private static final Identifier WIN_GREEN  = Identifier.of("p3", "textures/hud/texture_window_green.png");
private static final Identifier WIN_RED    = Identifier.of("p3", "textures/hud/texture_window_red.png");
private static final Identifier WIN_PURPLE = Identifier.of("p3", "textures/hud/texture_window_purple.png");

// Slide animation
private static float panelAnimOffset = 300f;
// panelState:
//   0 = HIDDEN
//   1 = SLIDE_IN      (серая въезжает)
//   2 = IDLE          (серая стоит)
//   3 = COMPLETE_FADE (цветная проявляется поверх серой)
//   4 = SLIDE_OUT     (цветная уезжает)
private static int panelState = 0;

private static int   completeFadeTicks = 0;  // от 20 до 0
private static float colorAlpha = 0f;        // 0.0 → 1.0 во время COMPLETE_FADE
private static Identifier activeColorTex = WIN_GREEN; // текущая цветная текстура

private static String   prevId = "";
private static int      prevCompletedCount = 0;
private static com.project3.network.AchievementSyncPayload pendingPayload = null;

2. ДОБАВИТЬ вспомогательный метод applyPayload:

private static void applyPayload(com.project3.network.AchievementSyncPayload payload) {
    currentId          = payload.id();
    currentTitle       = payload.title();
    currentDescription = payload.description();
    iconItemId         = payload.iconItemId();
    currentValue       = payload.currentValue();
    targetValue        = payload.targetValue();
    completedCount     = payload.completedCount();
    totalCount         = payload.totalCount();
}

3. ЗАМЕНИТЬ тело метода update(AchievementSyncPayload payload):

public static void update(AchievementSyncPayload payload) {
    boolean isCompleted = payload.completedCount() > prevCompletedCount
                          && prevCompletedCount >= 0
                          && !currentId.isEmpty();
    boolean isNewTask   = !payload.id().equals(prevId) && !payload.id().isEmpty();

    prevCompletedCount = payload.completedCount();

    if (isCompleted && (panelState == 2 || panelState == 1)) {
        // Определить цветную текстуру по измерению
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
        panelState = 3; // COMPLETE_FADE
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
                panelState = 4; // SLIDE_OUT
            }
            // если 3 (COMPLETE_FADE) — пусть доиграет, pending подхватится после SLIDE_OUT
        }
    } else {
        applyPayload(payload);
    }

    hasData = true;
}

4. В метод tick() ДОБАВИТЬ в конец (после happinessTicksLeft и gloomTicksLeft):

float slideSpeed = 18f;

switch (panelState) {
    case 1 -> { // SLIDE_IN
        panelAnimOffset -= slideSpeed;
        if (panelAnimOffset <= 0f) {
            panelAnimOffset = 0f;
            panelState = 2;
        }
    }
    case 3 -> { // COMPLETE_FADE
        completeFadeTicks--;
        colorAlpha = 1f - (completeFadeTicks / 20f); // 0→1
        if (completeFadeTicks <= 0) {
            colorAlpha = 1f;
            panelState = 4; // сразу в SLIDE_OUT
        }
    }
    case 4 -> { // SLIDE_OUT
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

5. В методе render() ИЗМЕНИТЬ блок панели задания:

а) Условие:
   Было:   if (panelVisible && hasData && !currentId.isEmpty()) {
   Стало:  if (panelVisible && hasData && !currentId.isEmpty() && panelState != 0) {

б) Вычисление x — добавить offset:
   Было:   int x = screenWidth - width - 10;
   Стало:  int x = (int)(screenWidth - width - 10 + panelAnimOffset);

в) Найти все строки context.fill() которые рисуют чёрный фон и рамку панели
   (это context.fill с цветами 0xD0101010, 0xFF000000, 0xFF8B8B8B, 0xFF373737)
   и ЗАМЕНИТЬ их на:

   // Серая текстура — всегда основа
   context.drawTexture(RenderPipelines.GUI_TEXTURED,
       WIN_GRAY, x, y, 0, 0, width, height, 220, 64);

   // Цветная текстура поверх — только в состояниях 3 и 4
   if ((panelState == 3 || panelState == 4) && colorAlpha > 0f) {
       int alpha = (int)(colorAlpha * 255);
       // Рисуем цветную текстуру с нужной прозрачностью через матрицу цвета
       // Используем RenderSystem для установки alpha
       com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, colorAlpha);
       context.drawTexture(RenderPipelines.GUI_TEXTURED,
           activeColorTex, x, y, 0, 0, width, height, 220, 64);
       com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
   }

г) Весь остальной контент внутри блока (иконка, текст задания, прогресс-бар,
   маска, невидимость) — оставить БЕЗ ИЗМЕНЕНИЙ.

━━━ ЧТО НЕ ТРОГАТЬ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- renderBar() — не трогать
- renderInScreen() — не трогать  
- renderTooltip() — не трогать
- updatePlayerState() — не трогать
- блок с emitter glitch (producer block рядом) — не трогать
- поля BAR_TEXTURES и всё связанное с барами — не трогать
