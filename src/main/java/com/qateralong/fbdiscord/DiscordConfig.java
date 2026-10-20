package com.qateralong.fbdiscord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public class DiscordConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("flashback_discord.json");
    private static DiscordConfig instance = new DiscordConfig();

    public boolean enabled = true;
    public String source = "auto";
    public String device = "";
    public List<String> appMatch = new ArrayList<>(List.of("discord", "webrtc", "vesktop", "legcord", "armcord", "webcord"));
    public int sampleRate = 24000;
    public float recordGain = 1.0F;
    public float playbackVolume = 1.0F;
    public boolean muted = false;
    public boolean exportMix = true;
    public boolean exportFile = true;
    public boolean showHud = true;

    public static DiscordConfig get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    DiscordConfig loaded = GSON.fromJson(reader, DiscordConfig.class);
                    if (loaded != null) {
                        instance = loaded;
                    }
                }
            }
        } catch (Exception e) {
            FlashbackDiscord.LOGGER.error("Failed to read flashback_discord.json", e);
        }
        if (instance.sampleRate != 16000 && instance.sampleRate != 24000 && instance.sampleRate != 48000) {
            instance.sampleRate = 24000;
        }
        save();
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            FlashbackDiscord.LOGGER.error("Failed to write flashback_discord.json", e);
        }
    }
}
