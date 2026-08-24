package br.com.scanvag;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class Elm327Client {
    public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket socket;
    private InputStream input;
    private OutputStream output;

    public synchronized void connect(BluetoothDevice device) throws IOException {
        disconnect();
        IOException first = null;
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
        } catch (IOException e) {
            first = e;
            closeSocketOnly();
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
        }

        if (socket == null || !socket.isConnected()) {
            throw first != null ? first : new IOException("Bluetooth não conectou");
        }
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && input != null && output != null;
    }

    public synchronized void disconnect() {
        try { if (input != null) input.close(); } catch (Exception ignored) {}
        try { if (output != null) output.close(); } catch (Exception ignored) {}
        input = null;
        output = null;
        closeSocketOnly();
    }

    private void closeSocketOnly() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null;
    }

    public synchronized String command(String command, long timeoutMs) throws IOException {
        if (!isConnected()) throw new IOException("ELM327 não conectado");
        String cmd = command == null ? "" : command.trim().toUpperCase(Locale.US).replace(" ", "");
        enforceReadOnly(cmd);

        drainInput();
        output.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
        output.flush();

        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        StringBuilder response = new StringBuilder();
        while (SystemClock.elapsedRealtime() < deadline) {
            int available = input.available();
            if (available > 0) {
                byte[] buffer = new byte[Math.min(available, 1024)];
                int n = input.read(buffer);
                if (n > 0) {
                    String chunk = new String(buffer, 0, n, StandardCharsets.US_ASCII);
                    response.append(chunk);
                    if (response.indexOf(">") >= 0) break;
                }
            } else {
                SystemClock.sleep(18);
            }
        }

        if (response.length() == 0) throw new IOException("Timeout no comando " + cmd);
        return response.toString().replace(">", "").trim();
    }

    private void enforceReadOnly(String cmd) throws IOException {
        if (cmd.startsWith("AT")) return;
        // Whitelist: UDS ReadDataByIdentifier (22) and ReadDTCInformation (19).
        if (cmd.startsWith("22") || cmd.startsWith("19")) return;
        throw new IOException("Bloqueado pelo modo READ-ONLY: " + cmd);
    }

    private void drainInput() throws IOException {
        long until = SystemClock.elapsedRealtime() + 100;
        while (SystemClock.elapsedRealtime() < until) {
            int available = input.available();
            if (available <= 0) {
                SystemClock.sleep(8);
                continue;
            }
            byte[] buffer = new byte[Math.min(available, 512)];
            input.read(buffer);
        }
    }
}
