package com.qateralong.fbdiscord.replay;

import com.qateralong.fbdiscord.FlashbackDiscord;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public final class LivePlayback {
    private static final int RATE = 48000;
    private static final LinkedBlockingDeque<short[]> QUEUE = new LinkedBlockingDeque<>();
    private static volatile int queued;
    private static Thread thread;
    private static volatile float level;

    private LivePlayback() {
    }

    public static float level() {
        return level;
    }

    public static synchronized void offer(short[] samples48k, float volume) {
        if (thread == null) {
            thread = new Thread(LivePlayback::loop, "Discord track playback");
            thread.setDaemon(true);
            thread.start();
        }
        short[] scaled = new short[samples48k.length];
        double sum = 0.0;
        for (int i = 0; i < samples48k.length; i++) {
            int value = Math.round(samples48k[i] * volume);
            scaled[i] = (short) Math.max(-32768, Math.min(32767, value));
            sum += (double) scaled[i] * scaled[i];
        }
        level = samples48k.length == 0 ? 0.0F : (float) Math.sqrt(sum / samples48k.length) / 32768.0F;
        QUEUE.add(scaled);
        queued += scaled.length;
        while (queued > RATE * 3 / 10 && QUEUE.size() > 1) {
            short[] dropped = QUEUE.pollFirst();
            if (dropped != null) {
                queued -= dropped.length;
            }
        }
    }

    public static void clear() {
        QUEUE.clear();
        queued = 0;
        level = 0.0F;
    }

    private static void loop() {
        SourceDataLine line = null;
        try {
            AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, RATE / 5 * 2);
            line.start();
            while (true) {
                short[] chunk = QUEUE.pollFirst(1, TimeUnit.SECONDS);
                if (chunk == null) {
                    level = 0.0F;
                    continue;
                }
                queued -= chunk.length;
                byte[] bytes = new byte[chunk.length * 2];
                for (int i = 0; i < chunk.length; i++) {
                    bytes[i * 2] = (byte) chunk[i];
                    bytes[i * 2 + 1] = (byte) (chunk[i] >> 8);
                }
                line.write(bytes, 0, bytes.length);
            }
        } catch (Exception e) {
            FlashbackDiscord.LOGGER.error("Discord track playback stopped", e);
        } finally {
            if (line != null) {
                line.close();
            }
            synchronized (LivePlayback.class) {
                thread = null;
            }
        }
    }
}
