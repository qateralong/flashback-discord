package com.qateralong.fbdiscord.capture;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class JavaSoundCapture extends AudioCapture {
    private final String deviceName;
    private Thread thread;
    private TargetDataLine line;

    public JavaSoundCapture(String deviceName) {
        this.deviceName = deviceName;
    }

    public static List<String> devices() {
        List<String> names = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.getTargetLineInfo().length > 0 && mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, null))) {
                names.add(info.getName());
            }
        }
        return names;
    }

    @Override
    public String describe() {
        return "device: " + (this.deviceName.isEmpty() ? "default" : this.deviceName);
    }

    @Override
    public void start() {
        AudioFormat[] formats = {
                new AudioFormat(48000, 16, 1, true, false), new AudioFormat(48000, 16, 2, true, false),
                new AudioFormat(44100, 16, 2, true, false), new AudioFormat(44100, 16, 1, true, false)};
        Mixer.Info chosen = null;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (!this.deviceName.isEmpty() && info.getName().equalsIgnoreCase(this.deviceName)) {
                chosen = info;
            }
        }
        if (!this.deviceName.isEmpty() && chosen == null) {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (info.getName().toLowerCase().contains(this.deviceName.toLowerCase())) {
                    chosen = info;
                    break;
                }
            }
        }
        for (AudioFormat format : formats) {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine target = chosen != null ? (TargetDataLine) AudioSystem.getMixer(chosen).getLine(info) : (TargetDataLine) AudioSystem.getLine(info);
                target.open(format, (int) (format.getFrameRate() * format.getFrameSize() / 10));
                target.start();
                this.line = target;
                this.running = true;
                this.error = null;
                AudioFormat used = format;
                this.thread = new Thread(() -> this.loop(target, used), "Discord track capture");
                this.thread.setDaemon(true);
                this.thread.start();
                return;
            } catch (Exception ignored) {
            }
        }
        this.error = "cannot open input device " + this.describe();
    }

    private void loop(TargetDataLine target, AudioFormat format) {
        byte[] buffer = new byte[(int) (format.getFrameRate() / 50) * format.getFrameSize()];
        while (this.running) {
            int read = target.read(buffer, 0, buffer.length);
            if (read > 0) {
                this.push(toMono48k(buffer, read, format.getChannels(), (int) format.getSampleRate(), format.isBigEndian()));
            }
        }
    }

    @Override
    public void stop() {
        this.running = false;
        if (this.line != null) {
            this.line.stop();
            this.line.close();
            this.line = null;
        }
    }
}
