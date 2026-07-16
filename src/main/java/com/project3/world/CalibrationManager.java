package com.project3.world;

import com.project3.player.PlayerStateManager;
import com.project3.state.Project3State;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Handles the calibration countdown phase at the start of a season.
 */
public final class CalibrationManager {

    private CalibrationManager() {}

    public static int calibrationTicksLeft = -1;
    public static final Object CALIBRATION_LOCK = new Object();

    public static String getProgressBar(double percent) {
        int totalBars = 20;
        int filledBars = (int) (percent / 100.0 * totalBars);
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < filledBars; i++) {
            sb.append("█");
        }
        sb.append("§7");
        for (int i = filledBars; i < totalBars; i++) {
            sb.append("░");
        }
        return sb.toString();
    }

    public static void tick(MinecraftServer server) {
        if (calibrationTicksLeft > 0) {
            calibrationTicksLeft--;
            double percent = (1200 - calibrationTicksLeft) / 12.0;
            String progressBar = getProgressBar(percent);
            Text actionbarText = Text.literal("§e[ КАЛИБРОВКА: " + String.format("%.1f", percent) + "% ] " + progressBar);
            
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(actionbarText, true);
                if (calibrationTicksLeft % 20 == 0) {
                    player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.3f, 1.2f);
                }
            }

            if (calibrationTicksLeft == 0) {
                calibrationTicksLeft = -1;
                Project3State state = Project3State.getOrCreate(server);
                state.setSeasonStartTime(System.currentTimeMillis());
                
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    PlayerStateManager.grantHappiness(player, state, 144000L); // 2 hours of Happiness on season start
                }
                
                ServerWorld overworld = server.getOverworld();
                if (overworld != null) {
                    net.minecraft.world.border.WorldBorder border = overworld.getWorldBorder();
                    border.interpolateSize(border.getSize(), 16000.0, 30000L, net.minecraft.util.Util.getMeasuringTimeMs());
                    
                    // Disable vanilla advancement announcements
                    overworld.getGameRules().setValue(net.minecraft.world.rule.GameRules.ANNOUNCE_ADVANCEMENTS, false, server);
                }
            }
        }
    }
}
