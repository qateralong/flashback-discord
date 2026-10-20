package com.qateralong.fbdiscord.capture;

public final class Resampler {
    private Resampler() {
    }

    public static short[] resample(float[] input, int from, int to) {
        if (from == to) {
            short[] out = new short[input.length];
            for (int i = 0; i < input.length; i++) {
                out[i] = clamp(input[i]);
            }
            return out;
        }
        int length = (int) ((long) input.length * to / from);
        short[] out = new short[length];
        double step = (double) from / to;
        for (int i = 0; i < length; i++) {
            double pos = i * step;
            int index = (int) pos;
            double frac = pos - index;
            float a = input[Math.min(index, input.length - 1)];
            float b = input[Math.min(index + 1, input.length - 1)];
            out[i] = clamp((float) (a + (b - a) * frac));
        }
        return out;
    }

    public static short[] resample(short[] input, int from, int to) {
        float[] floats = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            floats[i] = input[i];
        }
        return resample(floats, from, to);
    }

    public static short clamp(float value) {
        return (short) Math.max(-32768, Math.min(32767, Math.round(value)));
    }
}
