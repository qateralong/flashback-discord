package com.qateralong.fbdiscord.capture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class PipeWireCapture extends AudioCapture {
    private final List<String> patterns;
    private Thread thread;
    private volatile Process process;
    private volatile String target = "";

    public PipeWireCapture(List<String> patterns) {
        this.patterns = patterns;
    }

    public static boolean available() {
        try {
            Process process = new ProcessBuilder("pw-record", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String describe() {
        return "pipewire: " + (this.target.isEmpty() ? "waiting for " + String.join("/", this.patterns) : this.target);
    }

    @Override
    public void start() {
        this.running = true;
        this.thread = new Thread(this::loop, "Discord track capture");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void loop() {
        while (this.running) {
            String[] node = this.findNode();
            if (node == null) {
                this.target = "";
                sleep(2000);
                continue;
            }
            this.target = node[1];
            try {
                Process recorder = new ProcessBuilder("pw-record", "--target", node[0], "--rate", "48000", "--channels", "1",
                        "--format", "s16", "-P", "stream.capture.sink=true", "-P", "node.dont-reconnect=true",
                        "-P", "media.name=Flashback Discord track", "-").redirectError(ProcessBuilder.Redirect.DISCARD).start();
                this.process = recorder;
                this.error = null;
                InputStream in = recorder.getInputStream();
                byte[] buffer = new byte[1920];
                long lastCheck = System.currentTimeMillis();
                int read;
                while (this.running && (read = in.read(buffer)) > 0) {
                    this.push(toMono48k(buffer, read - (read & 1), 1, 48000, false));
                    if (System.currentTimeMillis() - lastCheck > 3000L) {
                        lastCheck = System.currentTimeMillis();
                        String[] current = this.findNode();
                        if (current == null || !current[0].equals(node[0])) {
                            break;
                        }
                    }
                }
                recorder.destroy();
            } catch (Exception e) {
                this.error = "pw-record failed: " + e.getMessage();
                sleep(2000);
            }
        }
    }

    private String[] findNode() {
        try {
            Process dump = new ProcessBuilder("pw-dump").redirectError(ProcessBuilder.Redirect.DISCARD).start();
            JsonArray objects;
            try (InputStreamReader reader = new InputStreamReader(dump.getInputStream(), StandardCharsets.UTF_8)) {
                objects = JsonParser.parseReader(reader).getAsJsonArray();
            }
            String[] best = null;
            int bestScore = 0;
            for (JsonElement element : objects) {
                JsonObject info = element.getAsJsonObject().getAsJsonObject("info");
                if (info == null || !info.has("props")) {
                    continue;
                }
                JsonObject props = info.getAsJsonObject("props");
                if (!"Stream/Output/Audio".equals(string(props, "media.class"))) {
                    continue;
                }
                String haystack = (string(props, "application.name") + " " + string(props, "application.process.binary") + " "
                        + string(props, "node.name") + " " + string(props, "media.name")).toLowerCase(Locale.ROOT);
                int score = 0;
                for (int i = 0; i < this.patterns.size(); i++) {
                    if (haystack.contains(this.patterns.get(i).toLowerCase(Locale.ROOT))) {
                        score += this.patterns.size() - i;
                    }
                }
                if (haystack.contains("webrtc")) {
                    score += 1;
                }
                if (score > bestScore) {
                    bestScore = score;
                    String serial = props.has("object.serial") ? props.get("object.serial").getAsString() : element.getAsJsonObject().get("id").getAsString();
                    best = new String[]{serial, string(props, "application.name") + " / " + string(props, "media.name")};
                }
            }
            return best;
        } catch (Exception e) {
            this.error = "pw-dump failed: " + e.getMessage();
            return null;
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        this.running = false;
        Process current = this.process;
        if (current != null) {
            current.destroy();
        }
    }
}
