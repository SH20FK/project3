1. Обнови окружение до 1.21.11

Что гуглить: Porting to 1.21.11 Fabric

Что менять:

· В gradle.properties обнови:
  · minecraft_version = 1.21.11
  · fabric_loader_version → последняя (0.16.9+)
  · fabric_api_version → последняя под 1.21.11
· Обнови fabric-loom до последней версии (1.9+)
· Запусти ./gradlew clean и ./gradlew genSources

---

2. Найди актуальный метод в FogRenderer

Что гуглить: BackgroundRenderer 1.21.11 applyFog или открой maven.fabricmc.net → класс BackgroundRenderer

Что менять:
В 1.21.11 класс называется BackgroundRenderer (не FogRenderer).
Метод для fog — applyFog(Camera, FogType, float, boolean, float) — он статический и принимает 5 параметров.

---

3. Исправь миксин

Что гуглить: @Inject method not found mixin

Что менять:

· Вместо @Mixin(FogRenderer.class) → @Mixin(BackgroundRenderer.class)
· Вместо getFogStart найди, куда именно тебе нужно вставить код. Скорее всего:
  · Ловить вызов RenderSystem.setShaderFogStart() через @Redirect
  · Или работать с полем fogStart в BackgroundRenderer.FogData

---

4. Проверь refmap

Что гуглить: fabric mixin refmap not loaded

Что менять:

· В p3.client.mixins.json должно быть поле "refmap": "project3-refmap.json" (или client-project3-refmap.json)
· Убедись, что он генерируется loom-ом и попадает в JAR
· В build.gradle добавь remapMixin = true (если нет)
