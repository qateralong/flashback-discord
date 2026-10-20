package com.qateralong.fbdiscord.capture;

import java.util.ArrayDeque;

public abstract class AudioCapture {
    public static final int RATE = 48000;
    private static final int MAX_BUFFERED = RATE * 2;

    private final ArrayDeque<short[]> chunks = new ArrayDeque<>();
    private int buffered;
    private volatile float level;
    protected volatile String error;
    protected volatile boolean running;

    public abstract String describe();

    public abstract void start();

    public abstract void stop();

    public boolean running() {
        return this.running;
    }

    public String error() {
        return this.error;
    }

    public float level() {
        return this.level;
    }

    protected void push(short[] mono48k) {
        if (mono48k.length == 0) {
            return;
        }
        double sum = 0.0;
        for (short sample : mono48k) {
            sum += (double) sample * sample;
        }
        float rms = (float) Math.sqrt(sum / mono48k.length) / 32768.0F;
        this.level = Math.max(rms, this.level * 0.9F);
        synchronized (this.chunks) {
            this.chunks.add(mono48k);
            this.buffered += mono48k.length;
            while (this.buffered > MAX_BUFFERED && !this.chunks.isEmpty()) {
                this.buffered -= this.chunks.removeFirst().length;
            }
        }
    }

    public short[] drain() {
        synchronized (this.chunks) {
            short[] out = new short[this.buffered];
            int offset = 0;
            for (short[] chunk : this.chunks) {
                System.arraycopy(chunk, 0, out, offset, chunk.length);
                offset += chunk.length;
            }
            this.chunks.clear();
            this.buffered = 0;
            return out;
        }
    }

    public void decayLevel() {
        this.level *= 0.85F;
    }

    protected static short[] toMono48k(byte[] data, int length, int channels, int rate, boolean bigEndian) {
        int frames = length / (2 * channels);
        float[] mono = new float[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                int index = (i * channels + c) * 2;
                int sample = bigEndian
                        ? (short) ((data[index] << 8) | (data[index + 1] & 0xFF))
                        : (short) ((data[index + 1] << 8) | (data[index] & 0xFF));
                sum += sample;
            }
            mono[i] = (float) sum / channels;
        }
        return Resampler.resample(mono, rate, RATE);
    }
}
