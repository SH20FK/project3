package com.project3.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project3.Project3Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(Project3Mod.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", Project3Mod.MODID + ".json");

    private static ConfigData data = new ConfigData();

    private ModConfig() {}

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    data = GSON.fromJson(reader, ConfigData.class);
                    if (data == null) data = new ConfigData();
                }
            }
            save();
        } catch (IOException e) {
            LOGGER.error("Failed to load config, using defaults", e);
            data = new ConfigData();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public static ConfigData get() { return data; }

    public static class ConfigData {
        // Screamer Sprint
        public int screamerTicks = 200;
        public double screamerSpeed = 0.4;
        public int screamerGracePeriod = 20;

        // Stalker
        public int stalkerTicks = 900;
        public double stalkerSpeed = 0.35;
        public int stalkerGracePeriod = 80;

        // Dead Scenario
        public int deadScenarioTicks = 200;

        // Chat Echo
        public int chatEchoTicks = 600;

        // Static
        public int staticTicks = 400;

        // Deja Vu
        public int dejaVuTicks = 300;
        public double dejaVuSpeed = 0.45;

        // Recording
        public int recordingDuration = 300;
        public int recordingCycle = 1200;
        public int positionHistorySize = 300;
    }
}
