package com.project3.event;

import com.project3.Project3Mod;
import com.project3.state.Project3State;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.*;

/**
 * Nether Corruption Event
 *
 * Фаза 1 — Постепенная замена блоков (поверхность + 10 вниз, радиус RADIUS) на магму/базальт/незерак
 * Фаза 2 — Fake disconnect (выглядит как краш сервера)
 * Фаза 3 — При реконнекте: восстановление блоков + телепорт на сохранённые позиции
 *
 * Использование: NetherCorruptionEvent.trigger(server);
 */
public class NetherCorruptionEvent {

    // ─── Настройки ──────────────────────────────────────────────────────────

    /** Радиус в блоках вокруг центра всех игроков */
    private static final int RADIUS = 60;

    /** Глубина вниз от поверхности */
    private static final int DEPTH = 10;

    /** Сколько тиков занимает полная замена блоков (20 тиков = 1 сек) */
    private static final int CORRUPTION_TICKS = 600; // 30 секунд

    /** Чанки которые менять нельзя (tile entities — сундуки, печки) */
    private static final boolean SKIP_BLOCK_ENTITIES = true;

    // ─── Блоки для замены ───────────────────────────────────────────────────

    private static final BlockState[] NETHER_BLOCKS = {
        Blocks.MAGMA_BLOCK.getDefaultState(),
        Blocks.BASALT.getDefaultState(),
        Blocks.NETHERRACK.getDefaultState(),
        Blocks.NETHERRACK.getDefaultState(), // чаще незерак
        Blocks.NETHERRACK.getDefaultState(),
        Blocks.BLACKSTONE.getDefaultState(),
        Blocks.SOUL_SAND.getDefaultState(),
    };

    // ─── Fake crash текст ───────────────────────────────────────────────────

    private static final String FAKE_CRASH_TEXT =
        "§4[SYSTEM CRITICAL]\n\n" +
        "§cjava.lang.OutOfMemoryError: GC overhead limit exceeded\n" +
        "§7  at com.project3.core.SystemCore.allocateMemory(SystemCore.java:██)\n" +
        "§7  at com.project3.core.SystemCore.tick(SystemCore.java:██)\n" +
        "§7  at net.minecraft.server.MinecraftServer.runServer\n\n" +
        "§8>> ИНИЦИАЛИЗАЦИЯ АВАРИЙНОГО ВОССТАНОВЛЕНИЯ <<\n" +
        "§8>> RECONNECT TO RESTORE SESSION...";

    // ─── Состояние события ──────────────────────────────────────────────────

    /** Сохранённые блоки: позиция → оригинальное состояние */
    private static final Map<BlockPos, BlockState> savedBlocks = new LinkedHashMap<>();

    /** Позиции игроков на момент дисконнекта */
    private static final Map<UUID, Vec3d> savedPositions = new HashMap<>();

    /** Мир в котором произошло событие */
    private static ServerWorld eventWorld = null;

    /** Регистрационный ключ мира для восстановления после рестарта */
    private static String eventWorldKey = null;

    /** Ожидает ли восстановление */
    public static boolean restorePending = false;

    /**
     * Принудительное восстановление всех блоков по команде.
     * Не зависит от restorePending — просто берёт savedBlocks и восстанавливает.
     */
    public static void restoreAll(MinecraftServer server) {
        if (savedBlocks.isEmpty()) {
            Project3Mod.LOGGER.warn("Nether corruption restoreAll called but savedBlocks is empty");
            return;
        }

        ServerWorld world = eventWorld;
        if (world == null && eventWorldKey != null) {
            try {
                net.minecraft.util.Identifier worldId = net.minecraft.util.Identifier.of(eventWorldKey);
                RegistryKey<net.minecraft.world.World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
                world = server.getWorld(worldKey);
            } catch (Exception e) {
                Project3Mod.LOGGER.error("Failed to resolve nether corruption world for restoreAll", e);
            }
        }

        if (world == null) {
            Project3Mod.LOGGER.warn("Nether corruption restoreAll: eventWorld is null, cannot restore");
            return;
        }

        restoreBlocks(world);
    }

    // ─── Триггер события ────────────────────────────────────────────────────

    /**
     * Запустить событие.
     * Вызывать из Project3Mod или команды.
     */
    public static void trigger(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        // Определяем мир — берём из первого игрока
        ServerWorld world = (ServerWorld) players.get(0).getEntityWorld();
        eventWorld = world;
        eventWorldKey = world.getRegistryKey().getValue().toString();
        savedBlocks.clear();
        savedPositions.clear();

        // Центр — среднее всех игроков
        double cx = 0, cy = 0, cz = 0;
        for (ServerPlayerEntity p : players) {
            cx += p.getX(); cy += p.getY(); cz += p.getZ();
        }
        cx /= players.size();
        cz /= players.size();

        // Собираем все позиции для замены
        List<BlockPos> toReplace = collectSurface(world, cx, cz);

        // Перемешиваем для органического вида замены
        Collections.shuffle(toReplace);

        // Порция замены за тик
        int totalBlocks = toReplace.size();
        int blocksPerTick = Math.max(1, totalBlocks / CORRUPTION_TICKS);

        // Растягиваем замену на CORRUPTION_TICKS тиков через schedule
        for (int tick = 0; tick < CORRUPTION_TICKS; tick++) {
            final int from = tick * blocksPerTick;
            final int to = Math.min(from + blocksPerTick, totalBlocks);
            if (from >= totalBlocks) break;

            final List<BlockPos> chunk = toReplace.subList(from, to);
            final int currentTick = tick;

            Project3Mod.schedule(tick, () -> {
                Random rng = new Random();
                for (BlockPos pos : chunk) {
                    BlockState original = world.getBlockState(pos);

                    // Пропускаем воздух и блоки с tile entity
                    if (original.isAir()) continue;
                    if (SKIP_BLOCK_ENTITIES && original.hasBlockEntity()) continue;

                    // Сохраняем оригинал (только один раз)
                    savedBlocks.putIfAbsent(pos.toImmutable(), original);

                    // Ставим адский блок
                    BlockState netherBlock = NETHER_BLOCKS[rng.nextInt(NETHER_BLOCKS.length)];
                    world.setBlockState(pos, netherBlock,
                        net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
                }

                // На последнем тике — дисконнект
                if (currentTick >= CORRUPTION_TICKS - 2) {
                    Project3Mod.schedule(40, () -> fakeDisconnectAll(server));
                }
            });
        }
    }

    // ─── Сбор блоков поверхности ────────────────────────────────────────────

    /**
     * Собирает все блоки поверхности + DEPTH вниз в радиусе RADIUS от (cx, cz).
     * Использует WORLD_SURFACE heightmap — только загруженные чанки.
     */
    private static List<BlockPos> collectSurface(ServerWorld world, double cx, double cz) {
        List<BlockPos> result = new ArrayList<>();
        int icx = (int) cx;
        int icz = (int) cz;

        for (int x = icx - RADIUS; x <= icx + RADIUS; x++) {
            for (int z = icz - RADIUS; z <= icz + RADIUS; z++) {
                // Только в радиусе (круг, а не квадрат)
                double dist = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (dist > RADIUS) continue;

                // Проверяем что чанк загружен
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                // Высота поверхности
                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);

                // Добавляем от поверхности вниз на DEPTH
                for (int dy = 0; dy < DEPTH; dy++) {
                    int y = surfaceY - dy;
                    if (y < world.getBottomY()) break;
                    result.add(new BlockPos(x, y, z));
                }
            }
        }

        return result;
    }

    // ─── Fake disconnect ────────────────────────────────────────────────────

    private static void fakeDisconnectAll(MinecraftServer server) {
        restorePending = true;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        // Сохраняем позиции всех игроков ДО дисконнекта
        for (ServerPlayerEntity player : players) {
            savedPositions.put(player.getUuid(), player.getEntityPos());
        }

        // Отключаем всех с fake crash screen
        Text crashText = Text.literal(FAKE_CRASH_TEXT);
        for (ServerPlayerEntity player : players) {
            Project3Mod.schedule(player.getRandom().nextInt(20), () -> {
                // Небольшой разброс по времени — не все разом, выглядит органичнее
                player.networkHandler.disconnect(crashText);
            });
        }
    }

    // ─── Восстановление при реконнекте ──────────────────────────────────────

    /**
     * Вызывать из ServerPlayConnectionEvents.JOIN.
     * Восстанавливает блоки и телепортирует игрока на сохранённую позицию.
     */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        if (!restorePending) return;

        // Resolve eventWorld from key if null (after server restart)
        if (eventWorld == null && eventWorldKey != null) {
            try {
                net.minecraft.util.Identifier worldId = net.minecraft.util.Identifier.of(eventWorldKey);
                RegistryKey<net.minecraft.world.World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
                MinecraftServer server = ((ServerWorld) player.getEntityWorld()).getServer();
                if (server != null) {
                    eventWorld = server.getWorld(worldKey);
                }
            } catch (Exception e) {
                Project3Mod.LOGGER.error("Failed to resolve nether corruption event world: {}", eventWorldKey, e);
            }
        }

        if (eventWorld == null) {
            Project3Mod.LOGGER.warn("Nether corruption restore pending but eventWorld is null");
            restorePending = false;
            savedBlocks.clear();
            savedPositions.clear();
            eventWorldKey = null;
            return;
        }

        UUID uuid = player.getUuid();
        Vec3d savedPos = savedPositions.get(uuid);

        // Если это первый игрок кто зашёл — восстанавливаем блоки
        // (делаем это один раз, не для каждого игрока)
        if (!savedBlocks.isEmpty()) {
            restoreBlocks(eventWorld);
        }

        // Телепортируем на сохранённую позицию через 1 тик
        // (сразу на JOIN не работает — чанки ещё не загружены)
        if (savedPos != null) {
            Project3Mod.schedule(5, () -> {
                if (player.isAlive() && player.networkHandler != null) {
                    player.teleport(
                        eventWorld,
                        savedPos.x, savedPos.y, savedPos.z,
                        Set.of(),
                        player.getYaw(),
                        player.getPitch(),
                        false
                    );

                    // Сообщение от Системы
                    player.sendMessage(Text.literal(
                        "§8[Система]: §7...восстановление сессии завершено. " +
                        "Приносим извинения за временные неполадки."
                    ), false);
                }
            });
        }

        savedPositions.remove(uuid);

        // Если все игроки вернулись — чистим состояние
        if (savedPositions.isEmpty()) {
            restorePending = false;
            savedBlocks.clear();
            eventWorld = null;
            eventWorldKey = null;
        }
    }

    // ─── Восстановление блоков ──────────────────────────────────────────────

    private static void restoreBlocks(ServerWorld world) {
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(savedBlocks.entrySet());
        List<Map.Entry<BlockPos, BlockState>> remaining = Collections.synchronizedList(new ArrayList<>());
        int batchSize = 500;
        int batches = (int) Math.ceil(entries.size() / (double) batchSize);

        for (int i = 0; i < batches; i++) {
            final int from = i * batchSize;
            final int to = Math.min(from + batchSize, entries.size());
            final List<Map.Entry<BlockPos, BlockState>> batch = entries.subList(from, to);
            final int delayTick = i * 2;

            Project3Mod.schedule(delayTick, () -> {
                int skipped = 0;
                for (Map.Entry<BlockPos, BlockState> entry : batch) {
                    BlockPos pos = entry.getKey();
                    if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                        world.setBlockState(pos, entry.getValue(),
                            net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
                    } else {
                        remaining.add(entry);
                        skipped++;
                    }
                }
                Project3Mod.LOGGER.info("Nether corruption restore batch at tick {}: {}/{} restored",
                    delayTick, batch.size() - skipped, batch.size());
            });
        }

        // Retry blocks in unloaded chunks after main batches complete
        int retryDelay = (batches + 1) * 2;
        Project3Mod.schedule(retryDelay, () -> retryRemainingBlocks(world, remaining));
    }

    private static void retryRemainingBlocks(ServerWorld world, List<Map.Entry<BlockPos, BlockState>> remaining) {
        if (remaining.isEmpty()) return;

        List<Map.Entry<BlockPos, BlockState>> stillPending = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : remaining) {
            BlockPos pos = entry.getKey();
            if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                world.setBlockState(pos, entry.getValue(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
            } else {
                stillPending.add(entry);
            }
        }

        Project3Mod.LOGGER.info("Nether corruption retry: {}/{} blocks restored, {} still pending",
            remaining.size() - stillPending.size(), remaining.size(), stillPending.size());

        remaining.clear();
        remaining.addAll(stillPending);

        if (!stillPending.isEmpty()) {
            Project3Mod.schedule(40, () -> retryRemainingBlocks(world, remaining));
        }
    }

    // ─── NBT сохранение (для персистентности между рестартами) ──────────────

    /**
     * Сохранить состояние события в NBT (вызывать из Project3State.writeNbt).
     */
    public static void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        if (!restorePending) return;

        NbtCompound eventNbt = new NbtCompound();
        eventNbt.putBoolean("restorePending", true);
        if (eventWorldKey != null) {
            eventNbt.putString("eventWorldKey", eventWorldKey);
        }

        // Сохраняем блоки
        NbtCompound blocksNbt = new NbtCompound();
        int i = 0;
        for (Map.Entry<BlockPos, BlockState> entry : savedBlocks.entrySet()) {
            NbtCompound blockEntry = new NbtCompound();
            BlockPos pos = entry.getKey();
            blockEntry.putInt("x", pos.getX());
            blockEntry.putInt("y", pos.getY());
            blockEntry.putInt("z", pos.getZ());
            blockEntry.put("state", NbtHelper.fromBlockState(entry.getValue()));
            blocksNbt.put(String.valueOf(i++), blockEntry);
        }
        blocksNbt.putInt("count", i);
        eventNbt.put("blocks", blocksNbt);

        // Сохраняем позиции игроков
        NbtCompound posNbt = new NbtCompound();
        for (Map.Entry<UUID, Vec3d> entry : savedPositions.entrySet()) {
            NbtCompound playerPos = new NbtCompound();
            playerPos.putDouble("x", entry.getValue().x);
            playerPos.putDouble("y", entry.getValue().y);
            playerPos.putDouble("z", entry.getValue().z);
            posNbt.put(entry.getKey().toString(), playerPos);
        }
        eventNbt.put("positions", posNbt);

        nbt.put("netherCorruption", eventNbt);
    }

    /**
     * Загрузить состояние события из NBT (вызывать из Project3State.readNbt).
     */
    public static void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        if (!nbt.contains("netherCorruption")) return;

        NbtCompound eventNbt = nbt.getCompound("netherCorruption").orElseGet(NbtCompound::new);
        if (!eventNbt.getBoolean("restorePending").orElse(false)) return;

        restorePending = true;
        eventWorldKey = eventNbt.getString("eventWorldKey").orElse(null);

        // Читаем блоки
        NbtCompound blocksNbt = eventNbt.getCompound("blocks").orElseGet(NbtCompound::new);
        int count = blocksNbt.getInt("count").orElse(0);
        for (int i = 0; i < count; i++) {
            NbtCompound blockEntry = blocksNbt.getCompound(String.valueOf(i)).orElseGet(NbtCompound::new);
            BlockPos pos = new BlockPos(
                blockEntry.getInt("x").orElse(0),
                blockEntry.getInt("y").orElse(0),
                blockEntry.getInt("z").orElse(0)
            );
            BlockState state = NbtHelper.toBlockState(
                registries.getOrThrow(net.minecraft.registry.RegistryKeys.BLOCK),
                blockEntry.getCompound("state").orElseGet(NbtCompound::new)
            );
            savedBlocks.put(pos, state);
        }

        // Читаем позиции
        NbtCompound posNbt = eventNbt.getCompound("positions").orElseGet(NbtCompound::new);
        for (String key : posNbt.getKeys()) {
            NbtCompound playerPos = posNbt.getCompound(key).orElseGet(NbtCompound::new);
            savedPositions.put(
                UUID.fromString(key),
                new Vec3d(
                    playerPos.getDouble("x").orElse(0.0),
                    playerPos.getDouble("y").orElse(0.0),
                    playerPos.getDouble("z").orElse(0.0)
                )
            );
        }
    }
}
