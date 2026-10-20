package com.qateralong.fbdiscord;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import com.qateralong.fbdiscord.capture.Adpcm;
import com.qateralong.fbdiscord.capture.AudioCapture;
import com.qateralong.fbdiscord.capture.Captures;
import com.qateralong.fbdiscord.capture.Resampler;
import com.qateralong.fbdiscord.replay.ActionDiscordAudio;
import com.qateralong.fbdiscord.replay.AudioChunk;

public final class DiscordRecorder {
    private static AudioCapture capture;
    private static Adpcm encoder = new Adpcm();
    private static long testUntil;
    private static long chunks;

    private DiscordRecorder() {
    }

    public static AudioCapture capture() {
        return capture;
    }

    public static boolean recording() {
        return Flashback.RECORDER != null && capture != null;
    }

    public static long chunks() {
        return chunks;
    }

    public static void startTest(int seconds) {
        testUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    public static boolean testing() {
        return System.currentTimeMillis() < testUntil;
    }

    public static void restart() {
        stop();
    }

    public static void tick() {
        DiscordConfig config = DiscordConfig.get();
        Recorder recorder = Flashback.RECORDER;
        boolean wanted = (recorder != null && config.enabled) || testing();
        if (wanted && capture == null) {
            capture = Captures.create(config);
            capture.start();
            encoder = new Adpcm();
            chunks = 0;
            FlashbackDiscord.LOGGER.info("Discord track capture started ({})", capture.describe());
        } else if (!wanted && capture != null) {
            stop();
            return;
        }
        if (capture == null) {
            return;
        }
        short[] pcm = capture.drain();
        capture.decayLevel();
        if (recorder == null || !config.enabled || pcm.length == 0 || !recorder.readyToWrite() || recorder.isPaused()) {
            return;
        }
        if (config.recordGain != 1.0F) {
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = Resampler.clamp(pcm[i] * config.recordGain);
            }
        }
        int rate = config.sampleRate;
        short[] resampled = Resampler.resample(pcm, AudioCapture.RATE, rate);
        AudioChunk chunk = AudioChunk.encode(resampled, rate, encoder);
        chunks++;
        recorder.submitCustomTask(writer -> {
            writer.startAction(ActionDiscordAudio.INSTANCE);
            chunk.write(writer.friendlyByteBuf());
            writer.finishAction(ActionDiscordAudio.INSTANCE);
        });
    }

    public static void stop() {
        if (capture != null) {
            capture.stop();
            FlashbackDiscord.LOGGER.info("Discord track capture stopped after {} chunks", chunks);
            capture = null;
        }
    }
}
