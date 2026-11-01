package org.immix.frame.hardware;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import com.kitesystems.nix.frame.MotionSensor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manually started root process. No boot hook, shell execution, network socket, or arbitrary command API.
 * Socket access is restricted by kernel-provided peer UID to the helper app UID or root.
 */
public final class RootBackend {
    public static final String SOCKET = "org.immix.frame.hardware.v1";
    private static Context systemContext() throws Exception {
        Class<?> type = Class.forName("android.app.ActivityThread");
        Object thread = type.getMethod("systemMain").invoke(null);
        return (Context) type.getMethod("getSystemContext").invoke(thread);
    }
    public static void main(String[] args) {
        try { run(args); }
        catch (Throwable error) {
            System.err.println("hardware_backend_failed=" + error.getClass().getSimpleName());
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }
    private static void run(String[] args) throws Exception {
        if (android.os.Looper.getMainLooper() == null) android.os.Looper.prepareMainLooper();
        if (Process.myUid() != 0) throw new SecurityException("Root backend requires UID 0");
        if (args.length == 2 && args[0].equals("--command")) {
            String command = args[1];
            if (!java.util.Arrays.asList("POLL", "STOP", "OBSERVE", "MANAGE", "WAKE", "SLEEP").contains(command))
                throw new IllegalArgumentException("Unsupported command");
            System.out.println(new BackendClient().command(command));
            return;
        }
        Context context = systemContext();
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        MotionSensor motion = new MotionSensor();
        if (args.length == 1 && args[0].equals("--probe")) {
            System.out.println("sensor_library_loaded=true");
            System.out.println("sensor_power=" + motion.readMotionSensorPower());
            System.out.println("display_interactive=" + power.isInteractive());
            System.out.println("probe_only=true");
            return;
        }
        if (args.length != 2 || !args[0].equals("--serve")) throw new IllegalArgumentException("Use --probe or --serve HELPER_UID");
        int uid = Integer.parseInt(args[1]);
        if (uid < 10000) throw new IllegalArgumentException("Expected ordinary helper app UID");
        Method wake = PowerManager.class.getMethod("wakeUp", long.class);
        Method sleep = PowerManager.class.getMethod("goToSleep", long.class);
        boolean wasPowered = motion.readMotionSensorPower();
        PowerManager.WakeLock cpu = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FrameHardware:motion-monitor");
        cpu.setReferenceCounted(false);
        AtomicBoolean sleptByBackend = new AtomicBoolean(false);
        AtomicLong heartbeat = new AtomicLong(0);
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
        try (LocalServerSocket server = new LocalServerSocket(SOCKET)) {
            watchdog.schedule(() -> { try { server.close(); } catch (Exception ignored) {} }, 10, TimeUnit.MINUTES);
            watchdog.scheduleAtFixedRate(() -> {
                if (heartbeat.get() > 0 && SystemClock.elapsedRealtime() - heartbeat.get() > 10_000L) {
                    try {
                        if (sleptByBackend.getAndSet(false)) wake.invoke(power, SystemClock.uptimeMillis());
                        if (cpu.isHeld()) cpu.release();
                    } catch (Exception ignored) {}
                }
            }, 1, 1, TimeUnit.SECONDS);
            // With no verified hardware wake interrupt, only active management holds a bounded CPU lock.
            long until = SystemClock.elapsedRealtime() + 10 * 60_000L;
            while (SystemClock.elapsedRealtime() < until) {
                try (LocalSocket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    int peer = socket.getPeerCredentials().getUid();
                    if (peer != uid && peer != 0) continue;
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    // Fixed commands only; never pass arbitrary input to a shell.
                    StringBuilder input = new StringBuilder();
                    int c;
                    while (input.length() <= 32 && (c = reader.read()) != -1 && c != '\n') input.append((char) c);
                    String command = input.toString();
                    if (command == null || command.length() > 32) continue;
                    try {
                        switch (command) {
                            case "POLL":
                                if (!motion.readMotionSensorPower()) motion.setMotionSensorPower(true);
                                writer.println("OK " + (motion.readMotionSensor() > 0 ? "1" : "0") + " " + (power.isInteractive() ? "1" : "0"));
                                break;
                            case "MANAGE":
                                heartbeat.set(SystemClock.elapsedRealtime());
                                cpu.acquire(30_000L);
                                writer.println("OK");
                                break;
                            case "OBSERVE":
                                heartbeat.set(0);
                                if (sleptByBackend.getAndSet(false)) wake.invoke(power, SystemClock.uptimeMillis());
                                if (cpu.isHeld()) cpu.release();
                                writer.println("OK");
                                break;
                            case "WAKE":
                                wake.invoke(power, SystemClock.uptimeMillis());
                                sleptByBackend.set(false);
                                writer.println("OK");
                                break;
                            case "SLEEP":
                                if (heartbeat.get() == 0) throw new SecurityException("Management heartbeat required");
                                sleep.invoke(power, SystemClock.uptimeMillis());
                                sleptByBackend.set(true);
                                writer.println("OK");
                                break;
                            case "STOP":
                                writer.println("OK");
                                return;
                            default:
                                writer.println("ERROR unsupported_command");
                        }
                    } catch (Exception e) { writer.println("ERROR hardware_operation"); }
                }
            }
        } finally {
            watchdog.shutdownNow();
            if (sleptByBackend.get()) try { wake.invoke(power, SystemClock.uptimeMillis()); } catch (Exception ignored) {}
            if (cpu.isHeld()) cpu.release();
            motion.setMotionSensorPower(wasPowered);
        }
    }
}
