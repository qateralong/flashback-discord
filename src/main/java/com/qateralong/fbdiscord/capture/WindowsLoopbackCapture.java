package com.qateralong.fbdiscord.capture;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WindowsLoopbackCapture extends AudioCapture {
    private static final Guid.GUID IID_IUNKNOWN = Guid.GUID.fromString("{00000000-0000-0000-C000-000000000046}");
    private static final Guid.GUID IID_AGILE = Guid.GUID.fromString("{94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90}");
    private static final Guid.GUID IID_COMPLETION = Guid.GUID.fromString("{41D949AB-9862-444A-80F6-C261334DA5EB}");
    private static final Guid.GUID IID_AUDIO_CLIENT = Guid.GUID.fromString("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}");
    private static final Guid.GUID IID_CAPTURE_CLIENT = Guid.GUID.fromString("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}");
    private static final int E_NOINTERFACE = 0x80004002;

    static {
        IID_CAPTURE_CLIENT.write();
        IID_AUDIO_CLIENT.write();
    }

    private final List<String> patterns;
    private Thread thread;
    private volatile String target = "";

    public WindowsLoopbackCapture(List<String> patterns) {
        this.patterns = patterns;
    }

    public interface Mmdevapi extends Library {
        int ActivateAudioInterfaceAsync(WString path, Guid.GUID riid, Pointer params, Pointer handler, PointerByReference operation);
    }

    public interface QueryInterface extends Callback {
        int invoke(Pointer self, Pointer riid, Pointer ppv);
    }

    public interface RefCount extends Callback {
        int invoke(Pointer self);
    }

    public interface ActivateCompleted extends Callback {
        int invoke(Pointer self, Pointer operation);
    }

    public static boolean available() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public String describe() {
        return "windows process loopback: " + (this.target.isEmpty() ? "waiting for " + String.join("/", this.patterns) : this.target);
    }

    @Override
    public void start() {
        this.running = true;
        this.thread = new Thread(this::loop, "Discord track capture");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    @Override
    public void stop() {
        this.running = false;
    }

    private Optional<ProcessHandle> findProcess() {
        return ProcessHandle.allProcesses().filter(this::matches)
                .filter(process -> process.parent().map(parent -> !this.matches(parent)).orElse(true))
                .findFirst();
    }

    private boolean matches(ProcessHandle process) {
        String command = process.info().command().orElse("").toLowerCase(Locale.ROOT);
        int slash = Math.max(command.lastIndexOf('\\'), command.lastIndexOf('/'));
        String exe = command.substring(slash + 1);
        for (String pattern : this.patterns) {
            if (exe.contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void loop() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        while (this.running) {
            Optional<ProcessHandle> process = this.findProcess();
            if (process.isEmpty()) {
                this.target = "";
                sleep(2000);
                continue;
            }
            this.target = process.get().info().command().orElse("?") + " (pid " + process.get().pid() + ")";
            try {
                this.capture((int) process.get().pid(), process.get());
            } catch (Throwable e) {
                this.error = "WASAPI: " + e.getMessage();
                sleep(3000);
            }
        }
    }

    private void capture(int pid, ProcessHandle handle) throws Exception {
        Memory params = new Memory(12);
        params.setInt(0, 1);
        params.setInt(4, pid);
        params.setInt(8, 0);
        Memory variant = new Memory(24);
        variant.clear();
        variant.setShort(0, (short) 65);
        variant.setInt(8, 12);
        variant.setPointer(16, params);

        CountDownLatch latch = new CountDownLatch(1);
        int[] activateResult = {-1};
        Pointer[] activated = {null};
        Memory handler = new Memory(Native.POINTER_SIZE);
        Memory vtable = new Memory(Native.POINTER_SIZE * 4L);
        QueryInterface qi = (self, riid, ppv) -> {
            Guid.GUID iid = new Guid.GUID(riid);
            if (iid.equals(IID_IUNKNOWN) || iid.equals(IID_AGILE) || iid.equals(IID_COMPLETION)) {
                ppv.setPointer(0, self);
                return 0;
            }
            ppv.setPointer(0, Pointer.NULL);
            return E_NOINTERFACE;
        };
        RefCount addRef = self -> 1;
        RefCount release = self -> 1;
        ActivateCompleted completed = (self, operation) -> {
            IntByReference hr = new IntByReference();
            PointerByReference unknown = new PointerByReference();
            call(operation, 3, operation, hr.getPointer(), unknown.getPointer());
            activateResult[0] = hr.getValue();
            activated[0] = unknown.getValue();
            latch.countDown();
            return 0;
        };
        vtable.setPointer(0, CallbackReference.getFunctionPointer(qi));
        vtable.setPointer(Native.POINTER_SIZE, CallbackReference.getFunctionPointer(addRef));
        vtable.setPointer(Native.POINTER_SIZE * 2L, CallbackReference.getFunctionPointer(release));
        vtable.setPointer(Native.POINTER_SIZE * 3L, CallbackReference.getFunctionPointer(completed));
        handler.setPointer(0, vtable);

        Mmdevapi mmdevapi = Native.load("Mmdevapi", Mmdevapi.class);
        PointerByReference operation = new PointerByReference();
        check(mmdevapi.ActivateAudioInterfaceAsync(new WString("VAD\\Process_Loopback"), IID_AUDIO_CLIENT, variant, handler, operation), "ActivateAudioInterfaceAsync");
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("activation timed out");
        }
        release(operation.getValue());
        check(activateResult[0], "activation");
        Pointer client = activated[0];
        if (client == null) {
            throw new IllegalStateException("no audio client");
        }
        Memory format = new Memory(18);
        format.setShort(0, (short) 1);
        format.setShort(2, (short) 2);
        format.setInt(4, 48000);
        format.setInt(8, 48000 * 4);
        format.setShort(12, (short) 4);
        format.setShort(14, (short) 16);
        format.setShort(16, (short) 0);
        int flags = 0x00020000 | 0x80000000 | 0x08000000;
        check(call(client, 3, client, 0, flags, 1_000_000L, 0L, format, Pointer.NULL), "IAudioClient.Initialize");
        PointerByReference captureRef = new PointerByReference();
        check(call(client, 14, client, IID_CAPTURE_CLIENT.getPointer(), captureRef.getPointer()), "GetService");
        Pointer capture = captureRef.getValue();
        check(call(client, 10, client), "Start");
        this.error = null;
        IntByReference packet = new IntByReference();
        PointerByReference data = new PointerByReference();
        IntByReference frames = new IntByReference();
        IntByReference bufferFlags = new IntByReference();
        long lastAliveCheck = System.currentTimeMillis();
        try {
            while (this.running) {
                check(call(capture, 5, capture, packet.getPointer()), "GetNextPacketSize");
                while (packet.getValue() > 0) {
                    check(call(capture, 3, capture, data.getPointer(), frames.getPointer(), bufferFlags.getPointer(), Pointer.NULL, Pointer.NULL), "GetBuffer");
                    int count = frames.getValue();
                    if (count > 0) {
                        if ((bufferFlags.getValue() & 0x2) != 0) {
                            this.push(new short[count]);
                        } else {
                            byte[] bytes = data.getValue().getByteArray(0, count * 4);
                            this.push(toMono48k(bytes, bytes.length, 2, 48000, false));
                        }
                    }
                    check(call(capture, 4, capture, count), "ReleaseBuffer");
                    check(call(capture, 5, capture, packet.getPointer()), "GetNextPacketSize");
                }
                sleep(10);
                if (System.currentTimeMillis() - lastAliveCheck > 3000L) {
                    lastAliveCheck = System.currentTimeMillis();
                    if (!handle.isAlive()) {
                        break;
                    }
                }
            }
        } finally {
            call(client, 11, client);
            release(capture);
            release(client);
        }
        if (qi == null || addRef == null || release == null || completed == null) {
            throw new IllegalStateException();
        }
    }

    private static int call(Pointer object, int index, Object... args) {
        Pointer vtable = object.getPointer(0);
        Function function = Function.getFunction(vtable.getPointer((long) index * Native.POINTER_SIZE));
        return function.invokeInt(args);
    }

    private static void release(Pointer object) {
        if (object != null) {
            call(object, 2, object);
        }
    }

    private static void check(int hr, String what) {
        if (hr < 0) {
            throw new IllegalStateException(what + " failed: 0x" + Integer.toHexString(hr));
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
