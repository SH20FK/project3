package com.project3.entity;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class PlayerSessionData {

    public static final Map<UUID, List<PhantomReplicator.PlayerFrame>> CURRENT_RECORDING = new ConcurrentHashMap<>();
    public static final Map<UUID, List<PhantomReplicator.PlayerFrame>> LAST_SAVED_RECORDING = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> RECORDING_TIMER = new ConcurrentHashMap<>();

    public static final Map<UUID, Deque<Vec3d>> POSITION_HISTORY = new ConcurrentHashMap<>();
    public static final Map<UUID, Deque<Float>> YAW_HISTORY = new ConcurrentHashMap<>();

    private PlayerSessionData() {}

    public static void tick(ServerPlayerEntity player) {
        if (player.isSpectator()) return;

        UUID uuid = player.getUuid();

        int ticks = RECORDING_TIMER.computeIfAbsent(uuid, k -> 0);
        ticks++;
        if (ticks >= 1200) {
            ticks = 0;
        }
        RECORDING_TIMER.put(uuid, ticks);

        if (ticks < 300) {
            List<PhantomReplicator.PlayerFrame> list = CURRENT_RECORDING.computeIfAbsent(uuid, k -> new ArrayList<>());
            list.add(new PhantomReplicator.PlayerFrame(player));
        } else if (ticks == 300) {
            List<PhantomReplicator.PlayerFrame> list = CURRENT_RECORDING.remove(uuid);
            if (list != null && !list.isEmpty()) {
                LAST_SAVED_RECORDING.put(uuid, list);
            }
        }

        Deque<Vec3d> posList = POSITION_HISTORY.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());
        Deque<Float> yawList = YAW_HISTORY.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());

        posList.addLast(player.getEntityPos());
        yawList.addLast(player.getYaw());

        if (posList.size() > 300) {
            posList.pollFirst();
        }
        if (yawList.size() > 300) {
            yawList.pollFirst();
        }
    }

    public static List<PhantomReplicator.PlayerFrame> getLastSavedRecording(UUID uuid) {
        return LAST_SAVED_RECORDING.get(uuid);
    }

    public static Deque<Vec3d> getPositionHistory(UUID uuid) {
        return POSITION_HISTORY.get(uuid);
    }

    public static Deque<Float> getYawHistory(UUID uuid) {
        return YAW_HISTORY.get(uuid);
    }
}
