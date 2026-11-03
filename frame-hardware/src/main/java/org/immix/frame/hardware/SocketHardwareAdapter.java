package org.immix.frame.hardware;

/** Transport adapter, independent of Nixplay JNI. One POLL supplies both values for a controller tick. */
final class SocketHardwareAdapter implements PresenceSensor, DisplayPower {
    private final BackendClient backend = new BackendClient();
    private boolean interactive;
    @Override public boolean isActivityDetected() throws Exception {
        String[] values = backend.command("POLL").split(" ");
        if (values.length != 3 || !(values[1].equals("0") || values[1].equals("1")) ||
                !(values[2].equals("0") || values[2].equals("1"))) throw new java.io.IOException("Invalid hardware sample");
        interactive = values[2].equals("1");
        return values[1].equals("1");
    }
    @Override public boolean isInteractive() { return interactive; }
    @Override public void setManaging(boolean enabled) throws Exception { backend.command(enabled ? "MANAGE" : "OBSERVE"); }
    @Override public void setAwakeHold(boolean enabled) throws Exception { backend.command(enabled ? "HOLD" : "RELEASE"); }
    @Override public void wakeUp() throws Exception { backend.command("WAKE"); }
    @Override public void goToSleep() throws Exception { backend.command("SLEEP"); }
}
