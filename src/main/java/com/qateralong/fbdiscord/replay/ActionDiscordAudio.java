package com.qateralong.fbdiscord.replay;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import com.qateralong.fbdiscord.FlashbackDiscord;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionDiscordAudio implements Action {
    public static final ActionDiscordAudio INSTANCE = new ActionDiscordAudio();
    private static final ResourceLocation NAME = FlashbackDiscord.id("action/discord_audio_optional");

    private ActionDiscordAudio() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        AudioChunk chunk = AudioChunk.read(buf);
        if (replayServer.isProcessingSnapshot) {
            return;
        }
        boolean send;
        if (Flashback.isExporting()) {
            send = Flashback.EXPORT_JOB.getSettings().recordAudio() && Flashback.EXPORT_JOB.getCurrentTickDouble() > 0.0;
        } else {
            send = !replayServer.fastForwarding && !replayServer.replayPaused;
        }
        if (!send) {
            return;
        }
        DiscordAudioPayload payload = new DiscordAudioPayload(chunk);
        for (ReplayPlayer viewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }
}
