package com.example.dragonegg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DragonEggHeartsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("dragonegghearts.json");

    private static ConfigData data = new ConfigData();

    private DragonEggHeartsConfig() {
    }

    public static void load() {
        if (Files.notExists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
            data = loaded == null ? new ConfigData() : loaded;
        } catch (IOException | JsonParseException e) {
            data = new ConfigData();
            save();
        }

        sanitize();
    }

    public static ConfigData get() {
        return data;
    }

    private static void sanitize() {
        if (data.extraHearts < 0.0) {
            data.extraHearts = 0.0;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static final class ConfigData {
        public boolean doubleHearts = true;
        public boolean outline = true;
        public boolean redPlayerName = true;
        public boolean allowStorageInContainers = false;
        public double extraHearts = 10.0;

        public double extraHealthAmount() {
            if (doubleHearts) {
                return 20.0;
            }
            return extraHearts * 2.0;
        }
    }
}
