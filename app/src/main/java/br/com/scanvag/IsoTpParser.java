package br.com.scanvag;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IsoTpParser {
    private IsoTpParser() {}

    public static byte[] extractPayload(String elmResponse, String expectedRxId) {
        if (elmResponse == null) return null;
        String rx = expectedRxId.toUpperCase(Locale.US);
        String[] rawLines = elmResponse.replace('\r', '\n').split("\\n+");
        List<byte[]> frames = new ArrayList<>();

        for (String raw : rawLines) {
            String line = raw.trim().toUpperCase(Locale.US);
            if (line.isEmpty() || line.equals("OK") || line.equals("?")) continue;
            if (line.contains("NO DATA") || line.contains("STOPPED") || line.startsWith("SEARCHING")) continue;

            String hex = line.replaceAll("[^0-9A-F]", "");
            if (!hex.startsWith(rx)) continue;
            String dataHex = hex.substring(rx.length());
            if (dataHex.length() < 2 || (dataHex.length() % 2) != 0) continue;
            try {
                frames.add(hexToBytes(dataHex));
            } catch (RuntimeException ignored) {}
        }

        if (frames.isEmpty()) return null;

        // Primeiro procure uma resposta ISO-TP multi-frame completa. Isso é importante
        // quando o mesmo request recebeu antes um NRC 0x78 (Response Pending) e depois
        // a ECU entregou a resposta positiva em vários frames.
        byte[] lastMulti = null;
        for (int start = 0; start < frames.size(); start++) {
            byte[] frame = frames.get(start);
            if (frame.length < 2) continue;
            int pci = frame[0] & 0xFF;
            if (((pci >> 4) & 0x0F) != 0x1) continue;

            int totalLen = ((pci & 0x0F) << 8) | (frame[1] & 0xFF);
            ByteArrayOutputStream out = new ByteArrayOutputStream(totalLen);
            out.write(frame, 2, frame.length - 2);

            int expectedSeq = 1;
            for (int i = start + 1; i < frames.size() && out.size() < totalLen; i++) {
                byte[] next = frames.get(i);
                if (next.length < 2) continue;
                int npci = next[0] & 0xFF;
                if (((npci >> 4) & 0x0F) != 0x2) continue;
                int seq = npci & 0x0F;
                if (seq != (expectedSeq & 0x0F)) continue;
                out.write(next, 1, next.length - 1);
                expectedSeq++;
            }

            byte[] assembled = out.toByteArray();
            if (assembled.length >= totalLen) {
                byte[] exact = new byte[totalLen];
                System.arraycopy(assembled, 0, exact, 0, totalLen);
                // Prefira uma resposta definitiva; um 0x78 nunca deve esconder a final.
                if (!isResponsePending(exact)) lastMulti = exact;
            }
        }
        if (lastMulti != null) return lastMulti;

        // Para single-frame, varra de trás para frente e prefira a resposta definitiva
        // mais recente. Só devolva o NRC 0x78 se realmente não existir resposta final.
        byte[] pending = null;
        for (int i = frames.size() - 1; i >= 0; i--) {
            byte[] frame = frames.get(i);
            if (frame.length < 1) continue;
            int pci = frame[0] & 0xFF;
            int type = (pci >> 4) & 0x0F;
            if (type != 0x0) continue;
            int len = pci & 0x0F;
            if (len <= 0 || frame.length < 1 + len) continue;
            byte[] out = new byte[len];
            System.arraycopy(frame, 1, out, 0, len);
            if (isResponsePending(out)) {
                if (pending == null) pending = out;
                continue;
            }
            return out;
        }
        return pending;
    }

    public static boolean isResponsePending(byte[] payload) {
        return payload != null && payload.length >= 3
                && (payload[0] & 0xFF) == 0x7F
                && (payload[2] & 0xFF) == 0x78;
    }

    public static boolean isPositiveWriteDid(byte[] payload, int did) {
        return payload != null && payload.length >= 3
                && (payload[0] & 0xFF) == 0x6E
                && (payload[1] & 0xFF) == ((did >> 8) & 0xFF)
                && (payload[2] & 0xFF) == (did & 0xFF);
    }

    public static boolean isPositiveSession(byte[] payload, int subFunction) {
        return payload != null && payload.length >= 2
                && (payload[0] & 0xFF) == 0x50
                && (payload[1] & 0x7F) == (subFunction & 0x7F);
    }

    public static boolean isPositiveDid(byte[] payload, int did) {
        return payload != null && payload.length >= 3
                && (payload[0] & 0xFF) == 0x62
                && (payload[1] & 0xFF) == ((did >> 8) & 0xFF)
                && (payload[2] & 0xFF) == (did & 0xFF);
    }

    public static String decodeAsciiDid(byte[] payload, int did) {
        if (!isPositiveDid(payload, did)) return describe(payload);
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < payload.length; i++) {
            int b = payload[i] & 0xFF;
            if (b == 0x00 || b == 0xAA || b == 0xFF) continue;
            if (b >= 32 && b <= 126) sb.append((char) b);
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? "(sem texto)" : s;
    }

    public static String decodeHexDid(byte[] payload, int did) {
        if (!isPositiveDid(payload, did)) return describe(payload);
        if (payload.length <= 3) return "(vazio)";
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < payload.length; i++) {
            sb.append(String.format(Locale.US, "%02X", payload[i] & 0xFF));
        }
        return sb.toString();
    }

    public static String describe(byte[] payload) {
        if (payload == null || payload.length == 0) return "SEM RESPOSTA";
        if (payload.length >= 3 && (payload[0] & 0xFF) == 0x7F) {
            if ((payload[2] & 0xFF) == 0x78) {
                return String.format(Locale.US, "PENDENTE 7F %02X 78", payload[1] & 0xFF);
            }
            return String.format(Locale.US, "NEGATIVA 7F %02X %02X",
                    payload[1] & 0xFF, payload[2] & 0xFF);
        }
        return "RESPOSTA: " + bytesToHex(payload);
    }

    public static String bytesToHex(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        return sb.toString();
    }

    private static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(s.substring(i, i + 2), 16);
        }
        return out;
    }
}
