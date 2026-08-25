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

    /**
     * Canal normal do aplicativo: somente leitura UDS + comandos AT.
     */
    public synchronized String command(String command, long timeoutMs) throws IOException {
        String cmd = normalize(command);
        enforceReadOnly(cmd);
        return execute(cmd, timeoutMs);
    }

    /**
     * Canal separado e deliberado para o fluxo de Coding BETA.
     * Não aceita SecurityAccess (27), RoutineControl (31), ClearDTC (14), reset (11), etc.
     */
    public synchronized String codingCommand(String command, long timeoutMs) throws IOException {
        String cmd = normalize(command);
        if (!(cmd.startsWith("10") || cmd.startsWith("2E") || cmd.startsWith("3E"))) {
            throw new IOException("Comando fora da whitelist de Coding BETA: " + cmd);
        }
        return execute(cmd, timeoutMs);
    }

    private String execute(String cmd, long timeoutMs) throws IOException {
        if (!isConnected()) throw new IOException("ELM327 não conectado");

        drainInput();
        output.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
        output.flush();

        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        StringBuilder response = new StringBuilder();
        boolean pendingSeen = false;

        while (SystemClock.elapsedRealtime() < deadline) {
            int available = input.available();
            if (available > 0) {
                byte[] buffer = new byte[Math.min(available, 1024)];
                int n = input.read(buffer);
                if (n > 0) {
                    String chunk = new String(buffer, 0, n, StandardCharsets.US_ASCII);
                    response.append(chunk);

                    // Alguns módulos (ex.: ABS MQB) devolvem UDS NRC 0x78 e só depois
                    // enviam a resposta definitiva. Não reenviamos o request: permanecemos
                    // ouvindo o mesmo socket até a resposta final ou até o timeout total.
                    if (chunk.indexOf('>') >= 0) {
                        String all = response.toString();
                        if (containsResponsePending(all, cmd) && !containsFinalResponse(all, cmd)) {
                            pendingSeen = true;
                            continue;
                        }
                        break;
                    }

                    // Depois de um 0x78 o ELM pode entregar a resposta final sem novo prompt
                    // imediatamente. Se já identificamos uma resposta definitiva, encerramos.
                    if (pendingSeen && containsFinalResponse(response.toString(), cmd)) break;
                }
            } else {
                SystemClock.sleep(pendingSeen ? 25 : 18);
            }
        }

        if (response.length() == 0) throw new IOException("Timeout no comando " + cmd);
        return response.toString().replace(">", "").trim();
    }

    private boolean containsResponsePending(String raw, String cmd) {
        if (cmd == null || cmd.length() < 2) return false;
        String hex = raw == null ? "" : raw.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
        String service = cmd.substring(0, 2);
        return hex.contains("7F" + service + "78");
    }

    private boolean containsFinalResponse(String raw, String cmd) {
        if (cmd == null || cmd.length() < 2) return false;
        String hex = raw == null ? "" : raw.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
        String service = cmd.substring(0, 2);
        String positive;
        switch (service) {
            case "10": positive = "50"; break;
            case "19": positive = "59"; break;
            case "22": positive = "62"; break;
            case "2E": positive = "6E"; break;
            case "3E": positive = "7E"; break;
            default: positive = ""; break;
        }

        if (!positive.isEmpty()) {
            if ("22".equals(service) && cmd.length() >= 6) {
                if (hex.contains(positive + cmd.substring(2, 6))) return true;
            } else if (hex.contains(positive)) {
                return true;
            }
        }

        // Qualquer negativa definitiva (NRC diferente de 0x78) encerra a espera.
        String marker = "7F" + service;
        int pos = hex.lastIndexOf(marker);
        if (pos >= 0 && pos + marker.length() + 2 <= hex.length()) {
            String nrc = hex.substring(pos + marker.length(), pos + marker.length() + 2);
            return !"78".equals(nrc);
        }
        return false;
    }

    private String normalize(String command) {
        return (command == null ? "" : command.trim().toUpperCase(Locale.US).replace(" ", ""));
    }

    private void enforceReadOnly(String cmd) throws IOException {
        if (cmd.startsWith("AT")) return;
        // Whitelist de leitura: UDS ReadDataByIdentifier (22) e ReadDTCInformation (19).
        if (cmd.startsWith("22") || cmd.startsWith("19")) return;
        throw new IOException("Bloqueado pelo modo de leitura: " + cmd);
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
