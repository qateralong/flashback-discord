package com.qateralong.fbdiscord.replay;

import com.qateralong.fbdiscord.capture.Adpcm;
import net.minecraft.network.FriendlyByteBuf;

public record AudioChunk(int sampleRate, int samples, short predictor, byte index, byte[] data) {
    public static final int VERSION = 1;

    public static AudioChunk encode(short[] pcm, int sampleRate, Adpcm encoder) {
        short predictor = (short) encoder.predictor;
        byte index = (byte) encoder.index;
        byte[] data = encoder.encode(pcm);
        return new AudioChunk(sampleRate, pcm.length, predictor, index, data);
    }

    public short[] decode() {
        Adpcm decoder = new Adpcm();
        decoder.predictor = this.predictor;
        decoder.index = this.index;
        return decoder.decode(this.data, this.samples);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(VERSION);
        buf.writeVarInt(this.sampleRate);
        buf.writeVarInt(this.samples);
        buf.writeShort(this.predictor);
        buf.writeByte(this.index);
        buf.writeByteArray(this.data);
    }

    public static AudioChunk read(FriendlyByteBuf buf) {
        int version = buf.readByte();
        if (version != VERSION) {
            throw new IllegalStateException("Unknown Discord audio chunk version " + version);
        }
        int rate = buf.readVarInt();
        int samples = buf.readVarInt();
        short predictor = buf.readShort();
        byte index = buf.readByte();
        byte[] data = buf.readByteArray(1 << 20);
        return new AudioChunk(rate, samples, predictor, index, data);
    }
}
