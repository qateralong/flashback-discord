package com.qateralong.fbdiscord.replay;

import com.qateralong.fbdiscord.FlashbackDiscord;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DiscordAudioPayload(AudioChunk chunk) implements CustomPacketPayload {
    public static final Type<DiscordAudioPayload> TYPE = new Type<>(FlashbackDiscord.id("audio"));
    public static final StreamCodec<FriendlyByteBuf, DiscordAudioPayload> CODEC = StreamCodec.of(
            (buf, payload) -> payload.chunk.write(buf), buf -> new DiscordAudioPayload(AudioChunk.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
