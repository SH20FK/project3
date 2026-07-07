[20.06.2026 15:21] Кирдымандайム: Раз ты разработчик, давай разберем ошибку до уровня байт-кода и маппингов. Краш не в рандомном месте, а в жесткой связке с внутренностями ServerPlayerEntity.

Вот полный технический разбор:

1. Точный перевод маппингов (Intermediary → Yarn/MojMap)

Стек трейд:

java.lang.NullPointerException: Cannot invoke "net.minecraft.class_3244.method_52405()" because "$$0.field_13987" is null
at knot//net.minecraft.class_2703$class_2705.<init>(class_2703.java:156)
Расшифровываю для версии 1.21.1:

· class_3244 = net.minecraft.server.network.ServerPlayNetworkHandler (обработчик сетевого соединения конкретного игрока на сервере).
· field_13987 = это поле networkHandler внутри класса ServerPlayerEntity (class_3222). Именно на него ссылается $$0.
· method_52405() = это метод getPlayer() внутри ServerPlayNetworkHandler (возвращает самого игрока).

Итог перевода: Ты пытаешься вызвать player.networkHandler.getPlayer(), но player.networkHandler (поле field_13987) в этот момент равен null.

---

2. Почему это произошло в class_2703 (Text)?

Следующая часть стека:
at knot//net.minecraft.class_2703.<init>(class_2703.java:39)
at knot//com.project3.entity.PhantomReplicator.renderNpc(PhantomReplicator.java:502)

· class_2703 в 1.21.1 — это базовый интерфейс/класс для Text (чаты, надписи).
· Вложенный класс class_2705 (строка 156) — это, скорее всего, Text$WithText (конструктор для составных текстовых объектов).

Что делает твой код?
В строке PhantomReplicator.java:502 ты создаешь новый объект Text для отображения имени NPC или сообщения в чате. Вероятно, это выглядит так:

// Примерная реализация твоего кода
Text text = Text.literal( ... player.getName() ... );
// или
Text text = new Text(player.networkHandler.getPlayer().getDisplayName());
Minecraft при создании некоторых типов Text пытается подтянуть данные игрока. Из-за того, что networkHandler == null, конструктор Text падает с NPE раньше, чем твой код успевает обработать результат.

---

3. Сценарий, сломавший состояние игрока (почему networkHandler стал null)

В логе есть 2 ключевых события, которые привели к этому:

1. [13:09:11] [Render thread/WARN]: Duplicate entity UUID ...
       Это значит, что в мире появилась дублирующая сущность с тем же UUID, что и у игрока. Это происходит, когда ты спавнишь "Фантома со скином игрока" (/p3 phantom), и мод создает поддельную копию игрока (через PlayerEntity или FakePlayer), но не до конца чистит ссылки.
2. Телепорт на огромные координаты (x=89001.73, z=876004.34) в измерение gloom_void.
       После такого рывка серверный тик ServerTickEvents (строка 564 твоего мода) вызывает tickGloomVoidPlayer (строка 1308). В этот момент сервер пытается обработать всех игроков в измерении.

Механика бага:
Когда ты создаешь фантома через PhantomReplicator.spawnScreamerSprint, ты передаешь в него ссылку на оригинального игрока. Но из-за дубликата UUID или из-за форсированного телепорта/выгрузки чанков, оригинальный объект игрока был маркирован на удаление (removed = true) и его networkHandler был обнулен (это стандартное поведение Mojang при дисконнекте или смерти).
Однако твоя ссылка на игрока в PhantomReplicator осталась старой. В следующем же тике (tickGloomVoidPlayer) мод берет эту "мертвую" ссылку и пытается создать текст для NPC.

---

4. Конкретные строки твоего кода, которые нужно править

Судя по stacktrace:

· Project3Mod.java:1308 — вызов tickGloomVoidPlayer.
· Внутри него — вызов PhantomReplicator.spawnScreamerSprint (строка 326) → затем spawnScreamerSprintChase (строка 361) → затем renderNpc (строка 502).

В методе renderNpc (строка 502) ты пишешь что-то вроде:

// Условный код, который вызывает краш
PlayerEntity player = getTargetPlayer(); 
Text name = Text.literal(player.getName().getString()); // <-- Безопасно, если player null
// НО! если ты делаешь так:
Text name = new Text(player.networkHandler.getPlayer().getName());
Исправление 1 (самое важное):
Перед любым обращением к networkHandler вставь жесткую проверку:
[20.06.2026 15:21] Кирдымандайム: if (player == null || player.networkHandler == null) {
    // Если игрок отключен или умер, не спавним NPC и выходим
    return; 
}
Исправление 2 (для надежности):
Добавь проверку на player.isRemoved() и player.isAlive(), чтобы не брать игроков, которые уже покинули мир:

if (player.isRemoved() || !player.isAlive()) {
    return;
}
Исправление 3 (глобально в tickGloomVoidPlayer):
Перед передачей игрока в PhantomReplicator, убедись, что это валидный ServerPlayerEntity, у которого networkHandler не протух. Лучше всего брать игрока по UUID через server.getPlayerManager().getPlayer(uuid) прямо перед использованием, а не хранить старую ссылку в полях класса.

---

5. Почему я не советую просто ловить Exception?

Ты можешь обернуть вызов renderNpc в try-catch, но это маскирует проблему. Если networkHandler == null, значит игрок уже не в игре (или умер). Спавнить фантома для мертвого/ушедшего игрока бессмысленно — ты просто засоряешь лог и нагружаешь сервер. Лучше сделать return до вызова методов, работающих с Text.

---

Итоговый диагноз для разработчика

Корень зла: В методе PhantomReplicator.renderNpc (строка 502) ты используешь прямую ссылку на ServerPlayerEntity, которая была получена в прошлых тиках, и пытаешься создать Text с использованием его networkHandler.
Из-за дублирования UUID или телепортации оригинальный объект игрока был инвалидирован (networkHandler обнулен), а твой PhantomReplicator этого не заметил.

Решение: Добавить if (player.networkHandler == null) return; в самом начале renderNpc и заменить хранение долгоживущих ссылок на игроков на поиск по UUID в каждом тике.