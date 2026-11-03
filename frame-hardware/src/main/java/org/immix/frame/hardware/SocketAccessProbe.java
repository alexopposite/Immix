package org.immix.frame.hardware;

/** Diagnostic entry point for testing kernel peer-UID isolation; never changes screen power. */
public final class SocketAccessProbe {
    public static void main(String[] args) {
        System.out.println("caller_uid=" + android.os.Process.myUid());
        try {
            new BackendClient().command("POLL");
            System.out.println("backend_access=accepted");
        } catch (java.io.IOException error) {
            System.out.println("backend_access=rejected");
        }
    }
}
