package com.qateralong.fbdiscord.capture;

public final class Adpcm {
    private static final int[] INDEX_TABLE = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};
    private static final int[] STEP_TABLE = {
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
            130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060,
            1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484,
            7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};

    public int predictor;
    public int index;

    public byte[] encode(short[] samples) {
        byte[] out = new byte[(samples.length + 1) / 2];
        for (int i = 0; i < samples.length; i++) {
            int nibble = this.encodeSample(samples[i]);
            if ((i & 1) == 0) {
                out[i >> 1] = (byte) nibble;
            } else {
                out[i >> 1] |= (byte) (nibble << 4);
            }
        }
        return out;
    }

    public short[] decode(byte[] data, int count) {
        short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            int nibble = (i & 1) == 0 ? data[i >> 1] & 0x0F : (data[i >> 1] >> 4) & 0x0F;
            out[i] = (short) this.decodeSample(nibble);
        }
        return out;
    }

    private int encodeSample(int sample) {
        int step = STEP_TABLE[this.index];
        int diff = sample - this.predictor;
        int nibble = 0;
        if (diff < 0) {
            nibble = 8;
            diff = -diff;
        }
        int delta = step >> 3;
        if (diff >= step) {
            nibble |= 4;
            diff -= step;
            delta += step;
        }
        step >>= 1;
        if (diff >= step) {
            nibble |= 2;
            diff -= step;
            delta += step;
        }
        step >>= 1;
        if (diff >= step) {
            nibble |= 1;
            delta += step;
        }
        this.predictor += (nibble & 8) != 0 ? -delta : delta;
        this.clampState(nibble);
        return nibble;
    }

    private int decodeSample(int nibble) {
        int step = STEP_TABLE[this.index];
        int delta = step >> 3;
        if ((nibble & 4) != 0) {
            delta += step;
        }
        if ((nibble & 2) != 0) {
            delta += step >> 1;
        }
        if ((nibble & 1) != 0) {
            delta += step >> 2;
        }
        this.predictor += (nibble & 8) != 0 ? -delta : delta;
        this.clampState(nibble);
        return this.predictor;
    }

    private void clampState(int nibble) {
        this.predictor = Math.max(-32768, Math.min(32767, this.predictor));
        this.index = Math.max(0, Math.min(88, this.index + INDEX_TABLE[nibble]));
    }
}
