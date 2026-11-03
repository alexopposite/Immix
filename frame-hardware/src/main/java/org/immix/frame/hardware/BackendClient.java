package org.immix.frame.hardware;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;

final class BackendClient {
    String command(String command) throws IOException {
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(RootBackend.SOCKET, LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(1500);
            new PrintWriter(socket.getOutputStream(), true).println(command);
            String result = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
            if (result == null || !result.startsWith("OK")) throw new IOException("Backend operation unavailable");
            return result;
        }
    }
}
