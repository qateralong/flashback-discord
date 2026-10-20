package com.qateralong.fbdiscord;

import com.mojang.blaze3d.platform.InputConstants;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.ActionRegistry;
import com.qateralong.fbdiscord.capture.AudioCapture;
import com.qateralong.fbdiscord.capture.Resampler;
import com.qateralong.fbdiscord.replay.ActionDiscordAudio;
import com.qateralong.fbdiscord.replay.DiscordAudioPayload;
import com.qateralong.fbdiscord.replay.ExportMixer;
import com.qateralong.fbdiscord.replay.LivePlayback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlashbackDiscord implements ClientModInitializer {
    public static final String MOD_ID = "flashback_discord";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final KeyMapping TOGGLE = new KeyMapping("key.flashback_discord.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.flashback_discord");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        DiscordConfig.load();
        ActionRegistry.register(ActionDiscordAudio.INSTANCE);
        PayloadTypeRegistry.playS2C().register(DiscordAudioPayload.TYPE, DiscordAudioPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(DiscordAudioPayload.TYPE, (payload, context) -> receive(payload));
        KeyBindingHelper.registerKeyBinding(TOGGLE);
        ClientTickEvents.END_CLIENT_TICK.register(FlashbackDiscord::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DiscordRecorder.stop());
        HudRenderCallback.EVENT.register(DiscordHud::render);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> DiscordCommand.register(dispatcher));
    }

    private static void receive(DiscordAudioPayload payload) {
        short[] pcm = payload.chunk().decode();
        short[] at48k = Resampler.resample(pcm, payload.chunk().sampleRate(), AudioCapture.RATE);
        if (Flashback.isExporting()) {
            ExportMixer.offer(at48k);
            return;
        }
        DiscordConfig config = DiscordConfig.get();
        if (!config.muted) {
            LivePlayback.offer(at48k, config.playbackVolume);
        }
    }

    private static void tick(Minecraft minecraft) {
        DiscordRecorder.tick();
        ExportMixer.tick();
        while (TOGGLE.consumeClick()) {
            DiscordConfig config = DiscordConfig.get();
            Component message;
            if (Flashback.isInReplay()) {
                config.muted = !config.muted;
                if (config.muted) {
                    LivePlayback.clear();
                }
                message = Component.translatable(config.muted ? "flashback_discord.muted" : "flashback_discord.unmuted");
            } else {
                config.enabled = !config.enabled;
                message = Component.translatable(config.enabled ? "flashback_discord.enabled" : "flashback_discord.disabled");
            }
            DiscordConfig.save();
            minecraft.gui.setOverlayMessage(message, false);
        }
    }
}
