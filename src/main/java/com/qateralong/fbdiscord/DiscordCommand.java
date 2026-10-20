package com.qateralong.fbdiscord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.qateralong.fbdiscord.capture.AudioCapture;
import com.qateralong.fbdiscord.capture.JavaSoundCapture;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class DiscordCommand {
    private DiscordCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("discordtrack")
                .executes(context -> status(context.getSource()))
                .then(ClientCommandManager.literal("status").executes(context -> status(context.getSource())))
                .then(ClientCommandManager.literal("record").then(ClientCommandManager.argument("on", BoolArgumentType.bool())
                        .executes(context -> {
                            DiscordConfig.get().enabled = BoolArgumentType.getBool(context, "on");
                            return saved(context.getSource());
                        })))
                .then(ClientCommandManager.literal("source").then(ClientCommandManager.argument("source", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("auto", "pipewire", "windows", "device"), builder))
                        .executes(context -> {
                            DiscordConfig.get().source = StringArgumentType.getString(context, "source");
                            DiscordRecorder.restart();
                            return saved(context.getSource());
                        })))
                .then(ClientCommandManager.literal("device").then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(JavaSoundCapture.devices(), builder))
                        .executes(context -> {
                            DiscordConfig.get().device = StringArgumentType.getString(context, "name");
                            DiscordConfig.get().source = "device";
                            DiscordRecorder.restart();
                            return saved(context.getSource());
                        })))
                .then(ClientCommandManager.literal("match").then(ClientCommandManager.argument("patterns", StringArgumentType.greedyString())
                        .executes(context -> {
                            DiscordConfig.get().appMatch = List.of(StringArgumentType.getString(context, "patterns").trim().split("\\s*,\\s*"));
                            DiscordRecorder.restart();
                            return saved(context.getSource());
                        })))
                .then(ClientCommandManager.literal("quality").then(ClientCommandManager.argument("rate", IntegerArgumentType.integer())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("16000", "24000", "48000"), builder))
                        .executes(context -> {
                            int rate = IntegerArgumentType.getInteger(context, "rate");
                            DiscordConfig.get().sampleRate = rate == 16000 || rate == 48000 ? rate : 24000;
                            return saved(context.getSource());
                        })))
                .then(ClientCommandManager.literal("gain").then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(0, 400))
                        .executes(context -> {
                            DiscordConfig.get().recordGain = IntegerArgumentType.getInteger(context, "percent") / 100.0F;
                            return saved(context.getSource());
                        })))
                .then(ClientCommandManager.literal("volume").then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(0, 400))
                        .executes(context -> {
                            DiscordConfig.get().playbackVolume = IntegerArgumentType.getInteger(context, "percent") / 100.0F;
                            return saved(context.getSource());
                        })))
                .then(ClientCommandManager.literal("mute").then(ClientCommandManager.argument("on", BoolArgumentType.bool())
                        .executes(context -> {
                            DiscordConfig.get().muted = BoolArgumentType.getBool(context, "on");
                            return saved(context.getSource());
                        })))
                .then(ClientCommandManager.literal("export").then(ClientCommandManager.literal("mix").then(ClientCommandManager.argument("on", BoolArgumentType.bool())
                                .executes(context -> {
                                    DiscordConfig.get().exportMix = BoolArgumentType.getBool(context, "on");
                                    return saved(context.getSource());
                                })))
                        .then(ClientCommandManager.literal("file").then(ClientCommandManager.argument("on", BoolArgumentType.bool())
                                .executes(context -> {
                                    DiscordConfig.get().exportFile = BoolArgumentType.getBool(context, "on");
                                    return saved(context.getSource());
                                }))))
                .then(ClientCommandManager.literal("hud").then(ClientCommandManager.argument("on", BoolArgumentType.bool())
                        .executes(context -> {
                            DiscordConfig.get().showHud = BoolArgumentType.getBool(context, "on");
                            return saved(context.getSource());
                        })))
                .then(ClientCommandManager.literal("test")
                        .executes(context -> test(context.getSource(), 10))
                        .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1, 120))
                                .executes(context -> test(context.getSource(), IntegerArgumentType.getInteger(context, "seconds"))))));
    }

    private static int test(FabricClientCommandSource source, int seconds) {
        DiscordRecorder.startTest(seconds);
        source.sendFeedback(Component.translatable("flashback_discord.command.test", seconds));
        return 1;
    }

    private static int saved(FabricClientCommandSource source) {
        DiscordConfig.save();
        return status(source);
    }

    private static int status(FabricClientCommandSource source) {
        DiscordConfig config = DiscordConfig.get();
        AudioCapture capture = DiscordRecorder.capture();
        source.sendFeedback(Component.translatable("flashback_discord.command.status",
                yes(config.enabled), config.source, capture == null ? "-" : capture.describe(),
                config.sampleRate, Math.round(config.recordGain * 100), Math.round(config.playbackVolume * 100), yes(!config.muted),
                yes(config.exportMix), yes(config.exportFile)));
        if (capture != null && capture.error() != null) {
            source.sendError(Component.literal(capture.error()));
        }
        return 1;
    }

    private static Component yes(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }
}
