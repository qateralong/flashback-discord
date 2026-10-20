package com.qateralong.fbdiscord.capture;

import com.qateralong.fbdiscord.DiscordConfig;
import java.util.Locale;

public final class Captures {
    private Captures() {
    }

    public static AudioCapture create(DiscordConfig config) {
        String source = config.source.toLowerCase(Locale.ROOT);
        return switch (source) {
            case "pipewire" -> new PipeWireCapture(config.appMatch);
            case "windows" -> new WindowsLoopbackCapture(config.appMatch);
            case "device" -> new JavaSoundCapture(config.device);
            default -> {
                if (WindowsLoopbackCapture.available()) {
                    yield new WindowsLoopbackCapture(config.appMatch);
                }
                if (PipeWireCapture.available()) {
                    yield new PipeWireCapture(config.appMatch);
                }
                yield new JavaSoundCapture(config.device);
            }
        };
    }
}
