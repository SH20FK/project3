# Измерение Уныния: Gloom Void

Измерение Войда — это кастомная плоскость, спроектированная так, чтобы внушать игроку чувство паранойи и одиночества. Оно опирается на несколько независимых систем, которые вместе создают густую лиминальную атмосферу.

---

### 1. Генерация и Атмосфера (JSON Data)

Измерение генерируется абсолютно плоским. С высоты `Y=0` до `Y=62` находится воздух, а на `Y=63` располагается пол из нашего неразрушаемого, тонированного стекла `p3:void_glass`. Эффекты измерения переключены на Энд, чтобы получить глубокое тёмно-пурпурное небо без звёзд.

```json
// data/p3/dimension/gloom_void.json
{
  "type": "p3:gloom_void",
  "generator": {
    "type": "minecraft:flat",
    "settings": {
      "layers": [
        { "block": "minecraft:air", "height": 63 },
        { "block": "p3:void_glass", "height": 1 }
      ],
      "biome": "minecraft:the_void"
    }
  }
}
```

---

### 2. Динамическое Освещение и "Поломка фонарика"

Когда игрок держит источник света (например, фонарь), скрипт `tickGloomVoidPlayer` каждый тик генерирует вокруг него невидимые блоки `Blocks.LIGHT`. Однако фонарик может "сломаться": сервер рандомно гасит свет и накладывает на игрока эффект `Darkness` со звуком переключения рубильника.

```java
// Project3Mod.java -> tickGloomVoidPlayer()

boolean holdingLight = isHoldingLightSource(player);
if (holdingLight) {
    int flTicks = FLASHLIGHT_COOLDOWNS.computeIfAbsent(player.getUuid(), 
                  uuid -> player.getRandom().nextInt(600) + 600);
                  
    if (flTicks > 0) {
        FLASHLIGHT_COOLDOWNS.put(player.getUuid(), flTicks - 1);
    } else {
        // "Поломка" фонарика
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 50, 0, false, false, true));
        player.playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f, 0.5f);
        
        schedule(50, () -> {
            if (player.isAlive()) {
                player.playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f, 0.8f); // Звук включения
            }
        });
        FLASHLIGHT_COOLDOWNS.put(player.getUuid(), player.getRandom().nextInt(600) + 600);
    }
}
```

---

### 3. Давящий Эмбиент и Галлюцинации

Чтобы создать чувство постоянной слежки, мод рандомно проигрывает звуки шагов за спиной игрока и отправляет звуковые пакеты с экстремально заниженным тоном (`pitch = 0.25f`). Из-за такого низкого питча обычные мелодии звучат как утробный гул.

```java
// Project3Mod.java -> tickGloomVoidPlayer()

// Звуки шагов за спиной
net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0f).normalize();
double bx = player.getX() - look.x * 1.5; // На 1.5 блока позади игрока
double bz = player.getZ() - look.z * 1.5;
playerWorld.playSound(null, bx, player.getY(), bz, SoundEvents.BLOCK_STONE_STEP, SoundCategory.AMBIENT, 1.0f, 0.9f);

// Искаженная музыка через прямую отправку пакета
SoundEvent chosen = SoundEvents.MUSIC_DISC_13.value();
PlaySoundS2CPacket musicPacket = new PlaySoundS2CPacket(
    net.minecraft.registry.Registries.SOUND_EVENT.getEntry(chosen),
    SoundCategory.AMBIENT,
    player.getX(), player.getY(), player.getZ(),
    0.18f, // Очень тихо
    0.25f, // Замедлено в 4 раза (жуткий гул)
    player.getRandom().nextLong()
);
player.networkHandler.sendPacket(musicPacket);
```

---

### 4. Визуальные Шейдерные Сбои (Client-side)

Каждые ~25 секунд на клиенте может случиться приступ паники. Экран резко размывается (`minecraft:blur`) или цвета выворачиваются наизнанку (`minecraft:invert`) на пару секунд.

```java
// Project3Client.java -> triggerShaderGlitch()

public static void triggerShaderGlitch(net.minecraft.client.MinecraftClient client) {
    if (client.world == null) return;
    
    // Длительность глитча: от 1 до 2.5 секунд
    glitchTicksLeft = 20 + client.world.random.nextInt(30); 
    
    int type = client.world.random.nextInt(2);
    net.minecraft.util.Identifier shaderId = (type == 0) 
        ? net.minecraft.util.Identifier.of("minecraft", "invert")
        : net.minecraft.util.Identifier.of("minecraft", "blur");
        
    try {
        // Принудительно загружаем пост-процессинг шейдер
        ((GameRendererAccessor) client.gameRenderer).invokeLoadPostProcessor(shaderId);
    } catch (Exception e) {}
}

// ClientTickEvents.END_CLIENT_TICK
if (glitchTicksLeft > 0) {
    glitchTicksLeft--;
    if (glitchTicksLeft == 0) {
        client.gameRenderer.clearPostProcessor(); // Снимаем шейдер
    }
}
```

---

### 5. Ловушка "Sector Closure"

Случайным образом измерение может решить вас запереть. В чат выводится сообщение, и вокруг вас (исключительно в пустых блоках воздуха) появляются стены из кастомного стекла Войда.

```java
// Project3Mod.java -> tickGloomVoidPlayer() (Sector Closure logic)

player.sendMessage(Text.literal("§8[Эхо]: §7Сектор замыкается..."), false);

// ... (через 60 тиков / 3 секунды)
for (BlockPos p : allBarriers) {
    if (playerWorld.getBlockState(p).isAir()) { // ПРОВЕРКА: заменяем ТОЛЬКО воздух
        playerWorld.setBlockState(p, VOID_GLASS.getDefaultState());
    }
}

// ... (еще через 200 тиков / 10 секунд)
for (BlockPos p : allBarriers) {
    // Удаляем стекло, которое сами же и поставили
    if (playerWorld.getBlockState(p).isOf(VOID_GLASS)) {
        playerWorld.setBlockState(p, Blocks.AIR.getDefaultState());
    }
}
```

---

### 6. Спавн Фантомов-убийц (Screamer)

Скример спавнится на расстоянии 8 блоков от игрока, издает леденящий душу крик Гаста и бежит на вас с огромной скоростью, топая по стеклу. 

```java
// PhantomReplicator.java -> spawnScreamerSprintChase()

ActiveNpc activeNpc = new ActiveNpc(npc, targetPlayer, ActiveNpc.NpcType.SCREAMER_SPRINT, 200);
ACTIVE_NPCS.add(activeNpc);

// Звук крика Гаста при спавне
world.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
        net.minecraft.sound.SoundEvents.ENTITY_GHAST_SCREAM, 
        net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 0.8f);

// PhantomReplicator.java -> tick loop (во время спринта)
if (activeNpc.ticksLeft % 6 == 0) {
    // Быстрые звуки шуршания/шагов каждый 6-й тик
    world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
            net.minecraft.sound.SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 
            net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 0.8f);
}
```
