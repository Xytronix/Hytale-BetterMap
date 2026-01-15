package dev.ninesliced;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class BetterMapConfig {
    private static final Logger LOGGER = Logger.getLogger(BetterMapConfig.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BetterMapConfig INSTANCE;

    private int explorationRadius = 16;
    private int updateRateMs = 500;

    private transient Path configPath;

    public BetterMapConfig() {
    }

    public static synchronized BetterMapConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BetterMapConfig();
        }
        return INSTANCE;
    }

    public void initialize(Path rootPath) {
        Path configDir = rootPath.resolve("mods").resolve("BetterMap");
        this.configPath = configDir.resolve("config.json");
        
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            if (Files.exists(configPath)) {
                load();
            } else {
                save();
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize configuration: " + e.getMessage());
        }
    }

    public void load() {
        try (Reader reader = Files.newBufferedReader(configPath)) {
            BetterMapConfig loaded = GSON.fromJson(reader, BetterMapConfig.class);
            if (loaded != null) {
                this.explorationRadius = loaded.explorationRadius;
                this.updateRateMs = loaded.updateRateMs;
                LOGGER.info("Configuration loaded from " + configPath);
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to load configuration: " + e.getMessage());
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(this, writer);
            LOGGER.info("Configuration saved to " + configPath);
        } catch (IOException e) {
            LOGGER.severe("Failed to save configuration: " + e.getMessage());
        }
    }
    
    public void reload() {
        if (configPath != null && Files.exists(configPath)) {
            load();
        }
    }

    public int getExplorationRadius() {
        return explorationRadius;
    }
    
    public int getUpdateRateMs() {
        return updateRateMs;
    }
}
