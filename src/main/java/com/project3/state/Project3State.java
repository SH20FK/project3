package com.project3.state;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Stores all persistent data for the Project3 season system:
 * - Season start time (millis)
 * - Per-player producer effect cooldown (server tick when last applied)
 * - Per-player completed quest IDs
 */
public class Project3State extends PersistentState {

    /** Maximum happiness duration: 1 hour (120000 ticks) */
    public static final long MAX_HAPPINESS_TICKS = 120000L;

    private static boolean producerCapWarningLogged = false;

    private static final String STATE_KEY = "project3_state";
    private static final PersistentStateType<Project3State> TYPE = new PersistentStateType<>(
            STATE_KEY,
            Project3State::new,
            NbtCompound.CODEC.xmap(
                    nbt -> fromNbt(nbt, null),
                    state -> state.writeNbt(new NbtCompound(), null)
            ),
            null
    );

    /** System.currentTimeMillis() when /p3 start was run; 0 if not started. */
    private long seasonStartTime = 0L;

    /** Maps player UUID → server tick when the producer effect was last applied. */
    private final Map<UUID, Long> playerProducerCooldowns = new HashMap<>();



    /** Set of chunk keys that have been generated for producer blocks. */
    private final Set<Long> generatedProducerChunks = new HashSet<>();


    /** Maps player UUID → current achievement index (linear progress). */
    private final Map<UUID, Integer> playerAchievementIndex = new HashMap<>();

    /** Maps player UUID → set of completed achievement IDs. */
    private final Map<UUID, Set<String>> completedAchievements = new HashMap<>();

    /** Maps player UUID → Map of achievement ID → baseline statistic value */
    private final Map<UUID, Map<String, Integer>> playerAchievementBaselines = new HashMap<>();

    private int progressLevel = 0;
    private final Map<UUID, Long> playerHappinessTicksLeft = new HashMap<>();
    private final Map<UUID, Boolean> playerGloomPermanent = new HashMap<>();
    private final Map<UUID, Long> playerGloomTicksLeft = new HashMap<>();
    private final Map<UUID, Boolean> playerUnnamedEffectActive = new HashMap<>();
    /** Cumulative ticks player has spent in gloom (happiness=0). Used for HUD bar states 3-5. */
    private final Map<UUID, Long> playerGloomDepthTicks = new HashMap<>();
    private final Map<UUID, Set<String>> playerTradedProfessions = new HashMap<>();
    private final Map<UUID, String> playerLastNetherPortalPos = new HashMap<>();
    private boolean netherForceUnlocked = false;
    private boolean endForceUnlocked = false;

    /** Tracks the player's experience level at the moment they enchanted an item (0 = no enchant detected). */
    private final Map<UUID, Integer> playerEnchantLevelAtTake = new HashMap<>();

    public Project3State() {}

    // ─── Accessors ───────────────────────────────────────────────────────────

    public long getSeasonStartTime() { return seasonStartTime; }
    public void setSeasonStartTime(long time) { seasonStartTime = time; markDirty(); }

    public boolean isSeasonStarted() { return seasonStartTime > 0; }

    /** Returns elapsed real-world milliseconds since the season started (0 if not started). */
    public long getElapsedMs() {
        if (seasonStartTime == 0) return 0;
        return System.currentTimeMillis() - seasonStartTime;
    }

    public Map<UUID, Long> getPlayerProducerCooldowns() { return playerProducerCooldowns; }

    public long getProducerCooldown(UUID uuid) {
        return playerProducerCooldowns.getOrDefault(uuid, 0L);
    }

    public void setProducerCooldown(UUID uuid, long tick) {
        playerProducerCooldowns.put(uuid, tick);
        markDirty();
    }



    public boolean hasGeneratedProducer(int cx, int cz) {
        long key = (((long)cx) << 32) | (cz & 0xFFFFFFFFL);
        return generatedProducerChunks.contains(key);
    }

    public void markGeneratedProducer(int cx, int cz) {
        long key = (((long)cx) << 32) | (cz & 0xFFFFFFFFL);
        // Fix #44: cap set size to prevent unbounded memory growth
        if (generatedProducerChunks.size() < 10_000) {
            generatedProducerChunks.add(key);
            markDirty();
        } else if (!producerCapWarningLogged) {
            producerCapWarningLogged = true;
            com.project3.Project3Mod.LOGGER.warn("generatedProducerChunks reached cap of 10_000 — new chunks will NOT be tracked, possible duplicate Producer blocks");
        }
    }

    // ─── Achievement Progress ───────────────────────────────────────────────

    public int getCurrentAchievementIndex(UUID uuid) {
        return playerAchievementIndex.getOrDefault(uuid, 0);
    }

    public void advanceAchievementIndex(UUID uuid) {
        playerAchievementIndex.put(uuid, getCurrentAchievementIndex(uuid) + 1);
        markDirty();
    }

    public void setAchievementIndex(UUID uuid, int index) {
        playerAchievementIndex.put(uuid, index);
        markDirty();
    }

    public Set<String> getCompletedAchievements(UUID uuid) {
        return completedAchievements.computeIfAbsent(uuid, k -> new HashSet<>());
    }

    public void completeAchievement(UUID uuid, String achievementId) {
        getCompletedAchievements(uuid).add(achievementId);
        markDirty();
    }

    public Map<String, Integer> getAchievementBaselines(UUID uuid) {
        return playerAchievementBaselines.computeIfAbsent(uuid, k -> new HashMap<>());
    }

    public void setAchievementBaseline(UUID uuid, String achievementId, int value) {
        getAchievementBaselines(uuid).put(achievementId, value);
        markDirty();
    }

    public Integer getAchievementBaseline(UUID uuid, String achievementId) {
        return getAchievementBaselines(uuid).get(achievementId);
    }

    public void resetAchievements(UUID uuid) {
        playerAchievementIndex.put(uuid, 0);
        getCompletedAchievements(uuid).clear();
        playerAchievementBaselines.computeIfAbsent(uuid, k -> new HashMap<>()).clear();
        markDirty();
    }

    public int getProgressLevel() { return progressLevel; }
    public void setProgressLevel(int val) { progressLevel = val; markDirty(); }

    public long getHappinessTicksLeft(UUID uuid) { return playerHappinessTicksLeft.getOrDefault(uuid, 0L); }
    public void setHappinessTicksLeft(UUID uuid, long val) { playerHappinessTicksLeft.put(uuid, val); markDirty(); }
    public void addHappinessTicks(UUID uuid, long ticks) { setHappinessTicksLeft(uuid, Math.min(getHappinessTicksLeft(uuid) + ticks, MAX_HAPPINESS_TICKS)); }

    public boolean isGloomPermanent(UUID uuid) { return playerGloomPermanent.getOrDefault(uuid, false); }
    public void setGloomPermanent(UUID uuid, boolean val) { playerGloomPermanent.put(uuid, val); markDirty(); }

    public long getGloomTicksLeft(UUID uuid) { return playerGloomTicksLeft.getOrDefault(uuid, 0L); }
    public void setGloomTicksLeft(UUID uuid, long val) { playerGloomTicksLeft.put(uuid, val); markDirty(); }
    public void addGloomTicks(UUID uuid, long ticks) { setGloomTicksLeft(uuid, getGloomTicksLeft(uuid) + ticks); }

    public boolean isUnnamedEffectActive(UUID uuid) { return playerUnnamedEffectActive.getOrDefault(uuid, false); }
    public void setUnnamedEffectActive(UUID uuid, boolean val) { playerUnnamedEffectActive.put(uuid, val); markDirty(); }

    public long getGloomDepthTicks(UUID uuid) { return playerGloomDepthTicks.getOrDefault(uuid, 0L); }
    public void setGloomDepthTicks(UUID uuid, long val) { playerGloomDepthTicks.put(uuid, val); markDirty(); }
    public void addGloomDepthTicks(UUID uuid, long ticks) { setGloomDepthTicks(uuid, getGloomDepthTicks(uuid) + ticks); }

    public Set<String> getTradedProfessions(UUID uuid) {
        return playerTradedProfessions.computeIfAbsent(uuid, k -> new HashSet<>());
    }

    public void addTradedProfession(UUID uuid, String profession) {
        getTradedProfessions(uuid).add(profession);
        markDirty();
    }

    public void reset() {
        seasonStartTime = 0L;
        playerProducerCooldowns.clear();
        generatedProducerChunks.clear();
        playerAchievementIndex.clear();
        completedAchievements.clear();
        playerAchievementBaselines.clear();

        progressLevel = 0;
        playerHappinessTicksLeft.clear();
        playerGloomPermanent.clear();
        playerGloomTicksLeft.clear();
        playerUnnamedEffectActive.clear();
        playerGloomDepthTicks.clear();
        playerTradedProfessions.clear();
        playerLastNetherPortalPos.clear();
        playerEnchantLevelAtTake.clear();
        netherForceUnlocked = false;
        endForceUnlocked = false;
        markDirty();
    }

    public boolean isNetherForceUnlocked() { return netherForceUnlocked; }
    public void setNetherForceUnlocked(boolean val) { netherForceUnlocked = val; markDirty(); }

    public boolean isEndForceUnlocked() { return endForceUnlocked; }
    public void setEndForceUnlocked(boolean val) { endForceUnlocked = val; markDirty(); }

    public void setLastNetherPortalPos(UUID uuid, double x, double y, double z) {
        playerLastNetherPortalPos.put(uuid, x + "," + y + "," + z);
        markDirty();
    }

    public String getLastNetherPortalPos(UUID uuid) {
        return playerLastNetherPortalPos.get(uuid);
    }

    /** Called by MixinEnchantmentScreenHandler when a player takes an enchanted item. */
    public void setPlayerEnchantedAtHighLevel(UUID uuid, int levelAtEnchant) {
        playerEnchantLevelAtTake.put(uuid, levelAtEnchant);
        markDirty();
    }

    /** Returns the player's experience level at the moment they last enchanted (0 if no enchant detected). */
    public int getPlayerEnchantLevelAtTake(UUID uuid) {
        return playerEnchantLevelAtTake.getOrDefault(uuid, 0);
    }

    /** Clears the enchant flag (called after processing in achievement check). */
    public void clearPlayerEnchantLevelAtTake(UUID uuid) {
        playerEnchantLevelAtTake.put(uuid, 0);
        markDirty();
    }



    // ─── Serialisation ───────────────────────────────────────────────────────

    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.putLong("season_start_time", seasonStartTime);
        nbt.putBoolean("nether_force_unlocked", netherForceUnlocked);
        nbt.putBoolean("end_force_unlocked", endForceUnlocked);

        NbtCompound cooldownsNbt = new NbtCompound();
        playerProducerCooldowns.forEach((uuid, tick) ->
                cooldownsNbt.putLong(uuid.toString(), tick));
        nbt.put("producer_cooldowns", cooldownsNbt);



        // Serialize generated producer chunks
        long[] chunksArray = new long[generatedProducerChunks.size()];
        int idx = 0;
        for (long key : generatedProducerChunks) {
            chunksArray[idx++] = key;
        }
        nbt.putLongArray("generated_producer_chunks", chunksArray);

        // Serialize achievement progress
        NbtCompound achievementsNbt = new NbtCompound();
        Set<UUID> allAchievementUuids = new HashSet<>();
        allAchievementUuids.addAll(playerAchievementIndex.keySet());
        allAchievementUuids.addAll(completedAchievements.keySet());
        for (UUID uuid : allAchievementUuids) {
            NbtCompound playerData = new NbtCompound();
            playerData.putInt("index", playerAchievementIndex.getOrDefault(uuid, 0));
            NbtList completedList = new NbtList();
            Set<String> completed = completedAchievements.getOrDefault(uuid, Collections.emptySet());
            completed.forEach(id -> completedList.add(NbtString.of(id)));
            playerData.put("completed", completedList);
            achievementsNbt.put(uuid.toString(), playerData);
        }
        nbt.put("achievement_progress", achievementsNbt);

        // Serialize achievement baselines
        NbtCompound baselinesNbt = new NbtCompound();
        playerAchievementBaselines.forEach((uuid, map) -> {
            NbtCompound playerMapCompound = new NbtCompound();
            map.forEach((achId, val) -> playerMapCompound.putInt(achId, val));
            baselinesNbt.put(uuid.toString(), playerMapCompound);
        });
        nbt.put("achievement_baselines", baselinesNbt);



        nbt.putInt("progress_level", progressLevel);

        NbtCompound happinessNbt = new NbtCompound();
        playerHappinessTicksLeft.forEach((uuid, val) -> happinessNbt.putLong(uuid.toString(), val));
        nbt.put("happiness_ticks", happinessNbt);

        NbtCompound gloomNbt = new NbtCompound();
        playerGloomPermanent.forEach((uuid, val) -> gloomNbt.putBoolean(uuid.toString(), val));
        nbt.put("gloom_permanent", gloomNbt);

        NbtCompound gloomTicksNbt = new NbtCompound();
        playerGloomTicksLeft.forEach((uuid, val) -> gloomTicksNbt.putLong(uuid.toString(), val));
        nbt.put("gloom_ticks", gloomTicksNbt);

        NbtCompound unnamedNbt = new NbtCompound();
        playerUnnamedEffectActive.forEach((uuid, val) -> unnamedNbt.putBoolean(uuid.toString(), val));
        nbt.put("unnamed_effect_active", unnamedNbt);

        NbtCompound gloomDepthNbt = new NbtCompound();
        playerGloomDepthTicks.forEach((uuid, val) -> gloomDepthNbt.putLong(uuid.toString(), val));
        nbt.put("gloom_depth_ticks", gloomDepthNbt);

        NbtCompound tradedNbt = new NbtCompound();
        playerTradedProfessions.forEach((uuid, professions) -> {
            NbtList list = new NbtList();
            professions.forEach(prof -> list.add(NbtString.of(prof)));
            tradedNbt.put(uuid.toString(), list);
        });
        nbt.put("traded_professions", tradedNbt);

        NbtCompound portalsNbt = new NbtCompound();
        playerLastNetherPortalPos.forEach((uuid, val) -> portalsNbt.putString(uuid.toString(), val));
        nbt.put("last_nether_portal_pos", portalsNbt);

        NbtCompound enchantLevelNbt = new NbtCompound();
        playerEnchantLevelAtTake.forEach((uuid, val) -> enchantLevelNbt.putInt(uuid.toString(), val));
        nbt.put("enchant_level_at_take", enchantLevelNbt);

        com.project3.event.NetherCorruptionEvent.writeNbt(nbt, registries);

        return nbt;
    }

    private static Project3State fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        Project3State state = new Project3State();
        state.seasonStartTime = nbt.getLong("season_start_time").orElse(0L);
        state.netherForceUnlocked = nbt.getBoolean("nether_force_unlocked").orElse(false);
        state.endForceUnlocked = nbt.getBoolean("end_force_unlocked").orElse(false);

        NbtCompound cooldownsNbt = nbt.getCompound("producer_cooldowns").orElseGet(NbtCompound::new);
        cooldownsNbt.getKeys().forEach(key -> {
            try {
                state.playerProducerCooldowns.put(UUID.fromString(key), cooldownsNbt.getLong(key).orElse(0L));
            } catch (IllegalArgumentException ignored) {
                // Fix #27: skip corrupted UUID keys gracefully
            }
        });



        // Deserialize generated producer chunks
        long[] chunksArray = nbt.getLongArray("generated_producer_chunks").orElse(new long[0]);
        for (long key : chunksArray) {
            state.generatedProducerChunks.add(key);
        }

        // Deserialize achievement progress
        NbtCompound achievementsNbt = nbt.getCompound("achievement_progress").orElseGet(NbtCompound::new);
        achievementsNbt.getKeys().forEach(key -> {
            try {
                NbtCompound playerData = achievementsNbt.getCompound(key).orElseGet(NbtCompound::new);
                UUID uuid = UUID.fromString(key);
                state.playerAchievementIndex.put(uuid, playerData.getInt("index").orElse(0));
                NbtList completedList = playerData.getList("completed").orElseGet(NbtList::new);
                Set<String> completed = new HashSet<>();
                for (int i = 0; i < completedList.size(); i++) {
                    completed.add(completedList.getString(i).orElse(""));
                }
                state.completedAchievements.put(uuid, completed);
            } catch (IllegalArgumentException ignored) {
                // Fix #27: skip corrupted UUID keys
            }
        });

        // Deserialize achievement baselines
        NbtCompound baselinesNbt = nbt.getCompound("achievement_baselines").orElseGet(NbtCompound::new);
        baselinesNbt.getKeys().forEach(key -> {
            try {
                NbtCompound playerMapCompound = baselinesNbt.getCompound(key).orElseGet(NbtCompound::new);
                UUID uuid = UUID.fromString(key);
                Map<String, Integer> map = new HashMap<>();
                playerMapCompound.getKeys().forEach(achId -> {
                    map.put(achId, playerMapCompound.getInt(achId).orElse(0));
                });
                state.playerAchievementBaselines.put(uuid, map);
            } catch (IllegalArgumentException ignored) {
                // Fix #27: skip corrupted UUID keys
            }
        });



        state.progressLevel = nbt.getInt("progress_level").orElse(0);

        NbtCompound happinessNbt = nbt.getCompound("happiness_ticks").orElseGet(NbtCompound::new);
        happinessNbt.getKeys().forEach(key -> {
            try {
                state.playerHappinessTicksLeft.put(UUID.fromString(key), happinessNbt.getLong(key).orElse(0L));
            } catch (IllegalArgumentException ignored) {}
        });

        NbtCompound gloomNbt = nbt.getCompound("gloom_permanent").orElseGet(NbtCompound::new);
        gloomNbt.getKeys().forEach(key -> {
            try {
                state.playerGloomPermanent.put(UUID.fromString(key), gloomNbt.getBoolean(key).orElse(false));
            } catch (IllegalArgumentException ignored) {}
        });

        NbtCompound gloomTicksNbt = nbt.getCompound("gloom_ticks").orElseGet(NbtCompound::new);
        gloomTicksNbt.getKeys().forEach(key -> {
            try {
                state.playerGloomTicksLeft.put(UUID.fromString(key), gloomTicksNbt.getLong(key).orElse(0L));
            } catch (IllegalArgumentException ignored) {}
        });

        NbtCompound unnamedNbt = nbt.getCompound("unnamed_effect_active").orElseGet(NbtCompound::new);
        unnamedNbt.getKeys().forEach(key -> {
            try {
                state.playerUnnamedEffectActive.put(UUID.fromString(key), unnamedNbt.getBoolean(key).orElse(false));
            } catch (IllegalArgumentException ignored) {}
        });

        NbtCompound gloomDepthNbt = nbt.getCompound("gloom_depth_ticks").orElseGet(NbtCompound::new);
        gloomDepthNbt.getKeys().forEach(key -> {
            try {
                state.playerGloomDepthTicks.put(UUID.fromString(key), gloomDepthNbt.getLong(key).orElse(0L));
            } catch (IllegalArgumentException ignored) {}
        });

        NbtCompound tradedNbt = nbt.getCompound("traded_professions").orElseGet(NbtCompound::new);
        tradedNbt.getKeys().forEach(key -> {
            try {
                NbtList list = tradedNbt.getList(key).orElseGet(NbtList::new);
                Set<String> professions = new HashSet<>();
                for (int i = 0; i < list.size(); i++) {
                    professions.add(list.getString(i).orElse(""));
                }
                state.playerTradedProfessions.put(UUID.fromString(key), professions);
            } catch (IllegalArgumentException ignored) {
                // Fix #27: skip corrupted UUID keys
            }
        });

        NbtCompound portalsNbt = nbt.getCompound("last_nether_portal_pos").orElseGet(NbtCompound::new);
        portalsNbt.getKeys().forEach(key -> {
            try {
                state.playerLastNetherPortalPos.put(UUID.fromString(key), portalsNbt.getString(key).orElse(""));
            } catch (IllegalArgumentException ignored) {}
        });

        NbtCompound enchantLevelNbt = nbt.getCompound("enchant_level_at_take").orElseGet(NbtCompound::new);
        enchantLevelNbt.getKeys().forEach(key -> {
            try {
                state.playerEnchantLevelAtTake.put(UUID.fromString(key), enchantLevelNbt.getInt(key).orElse(0));
            } catch (IllegalArgumentException ignored) {}
        });

        com.project3.event.NetherCorruptionEvent.readNbt(nbt, registries);

        return state;
    }

    // ─── Static accessor ─────────────────────────────────────────────────────

    public static Project3State getOrCreate(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return (Project3State) manager.getOrCreate(TYPE);
    }
}
