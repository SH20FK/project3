
🔍 1. Проверь существование метода в 1.21.11

Открой класс FogRenderer (через IDE с маппингами или декомпилируй). Убедись, что метод modifyEnvironmentalStart(float) действительно есть.
В новых версиях его могли переименовать или удалить. Если его нет – выбери другой метод для модификации.

---

🧩 2. Исправь @At в миксине

Ты используешь @ModifyVariable, но ошибка Scanned 0 target(s) означает, что @At не находит точку вставки.
Если хочешь изменить возвращаемое значение – используй @ModifyReturnValue с @At("RETURN").
Пример:

```java
@ModifyReturnValue(method = "modifyEnvironmentalStart", at = @At("RETURN"))
private float p3$modifyEnvironmentalStart(float original) { ... }
```

Если метод принимает float и возвращает float – это именно то, что нужно.
Убедись, что сигнатура в @ModifyVariable совпадает с оригиналом.

---

📦 3. Настрой генерацию refmap

В build.gradle добавь:

```gradle
mixin {
    add sourceSets.main, "project3-refmap.json"
}
```

И в p3.client.mixins.json укажи:

```json
"refmap": "project3-refmap.json"
```

Обязательно пересобери мод – без refmap миксин не сможет сопоставить обфусцированные имена.

---

🔧 4. Проверь используемые маппинги

В build.gradle укажи актуальные маппинги для 1.21.11:

```gradle
mappings channel: 'yarn', version: '1.21.11+build.1'
```

или Mojang:

```gradle
mappings channel: 'official', version: '1.21.11'
```

Это даст правильные имена методов.
   · Определи новое имя метода или поле, которое хранит значение тумана. Возможно, теперь это поле fogStart с модификатором или метод getFogStart().
3. Переписать Mixin-класс(ы)
   · В классе, который содержит @ModifyVariable (или @Inject), измени цель на правильный метод/поле.
   · Если нужного метода больше нет, рассмотри альтернативные подходы:
     · Использовать @Redirect для подмены вызова метода, который устанавливает туман.
     · Использовать @WrapOperation для перехвата вычисления.
     · Использовать @Inject в начало метода renderFog и модифицировать локальную переменную через @ModifyArg.
   · Пример адаптации под новую структуру (требуется знание конкретного метода).
        Если метод environmentalStart превратился в поле, используй @Accessor или @Shadow.
