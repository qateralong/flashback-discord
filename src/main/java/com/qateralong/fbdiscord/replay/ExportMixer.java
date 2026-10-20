package com.qateralong.fbdiscord.replay;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.exporting.ExportJob;
import com.qateralong.fbdiscord.DiscordConfig;
import com.qateralong.fbdiscord.FlashbackDiscord;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;

public final class ExportMixer {
    private static final int RATE = 48000;
    private static ExportJob job;
    private static final ArrayDeque<short[]> QUEUE = new ArrayDeque<>();
    private static int queued;
    private static short[] current;
    private static int currentOffset;
    private static RandomAccessFile wav;
    private static Path wavPath;
    private static long wavSamples;

    private ExportMixer() {
    }

    public static synchronized void offer(short[] samples48k) {
        ensureJob();
        if (job == null) {
            return;
        }
        QUEUE.add(samples48k);
        queued += samples48k.length;
        while (queued > RATE / 2 && QUEUE.size() > 1) {
            queued -= QUEUE.removeFirst().length;
        }
    }

    public static synchronized void onRender(FloatBuffer buffer, int samples) {
        ensureJob();
        if (job == null) {
            return;
        }
        int channels = job.getSettings().stereoAudio() ? 2 : 1;
        DiscordConfig config = DiscordConfig.get();
        float volume = config.muted ? 0.0F : config.playbackVolume;
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = next();
        }
        if (config.exportMix && volume > 0.0F) {
            for (int i = 0; i < samples; i++) {
                float add = out[i] / 32768.0F * volume;
                for (int c = 0; c < channels; c++) {
                    int index = i * channels + c;
                    if (index < buffer.capacity()) {
                        buffer.put(index, Math.max(-1.0F, Math.min(1.0F, buffer.get(index) + add)));
                    }
                }
            }
        }
        if (wav != null) {
            byte[] bytes = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                int value = Math.max(-32768, Math.min(32767, Math.round(out[i] * volume)));
                bytes[i * 2] = (byte) value;
                bytes[i * 2 + 1] = (byte) (value >> 8);
            }
            try {
                wav.write(bytes);
                wavSamples += samples;
            } catch (IOException e) {
                FlashbackDiscord.LOGGER.error("Failed to write Discord track", e);
                closeWav();
            }
        }
    }

    private static short next() {
        while (current == null || currentOffset >= current.length) {
            current = QUEUE.pollFirst();
            currentOffset = 0;
            if (current == null) {
                return 0;
            }
            queued -= current.length;
        }
        return current[currentOffset++];
    }

    private static void ensureJob() {
        ExportJob active = Flashback.EXPORT_JOB;
        if (active == job) {
            return;
        }
        finish();
        job = active;
        if (job != null && DiscordConfig.get().exportFile) {
            try {
                Path output = job.getSettings().output();
                String name = output.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String base = dot > 0 ? name.substring(0, dot) : name;
                wavPath = output.resolveSibling(base + ".discord.wav");
                wav = new RandomAccessFile(wavPath.toFile(), "rw");
                wav.setLength(0);
                wav.write(new byte[44]);
                wavSamples = 0;
            } catch (Exception e) {
                FlashbackDiscord.LOGGER.error("Failed to create Discord track file", e);
                wav = null;
            }
        }
    }

    public static synchronized void tick() {
        if (job != null && Flashback.EXPORT_JOB != job) {
            finish();
            job = null;
        }
    }

    private static void finish() {
        QUEUE.clear();
        queued = 0;
        current = null;
        closeWav();
    }

    private static void closeWav() {
        if (wav == null) {
            return;
        }
        try {
            long dataBytes = wavSamples * 2;
            wav.seek(0);
            wav.writeBytes("RIFF");
            writeIntLE(wav, (int) (36 + dataBytes));
            wav.writeBytes("WAVEfmt ");
            writeIntLE(wav, 16);
            writeShortLE(wav, 1);
            writeShortLE(wav, 1);
            writeIntLE(wav, RATE);
            writeIntLE(wav, RATE * 2);
            writeShortLE(wav, 2);
            writeShortLE(wav, 16);
            wav.writeBytes("data");
            writeIntLE(wav, (int) dataBytes);
            wav.close();
            FlashbackDiscord.LOGGER.info("Saved Discord track to {}", wavPath);
        } catch (IOException e) {
            FlashbackDiscord.LOGGER.error("Failed to finish Discord track file", e);
        }
        wav = null;
    }

    private static void writeIntLE(RandomAccessFile file, int value) throws IOException {
        file.write(value & 0xFF);
        file.write((value >> 8) & 0xFF);
        file.write((value >> 16) & 0xFF);
        file.write((value >> 24) & 0xFF);
    }

    private static void writeShortLE(RandomAccessFile file, int value) throws IOException {
        file.write(value & 0xFF);
        file.write((value >> 8) & 0xFF);
    }
}
