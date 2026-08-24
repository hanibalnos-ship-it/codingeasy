package br.com.scanvag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DtcParser {
    private DtcParser() {}

    // UDS ReadDTCInformation: 19 02 FF -> 59 02 availabilityMask + records.
    // Cada registro UDS possui 3 bytes de DTC + 1 byte de status.
    public static List<String> parse1902(byte[] payload) {
        List<String> out = new ArrayList<>();
        if (payload == null || payload.length == 0) {
            out.add("SEM RESPOSTA");
            return out;
        }
        if (payload.length >= 3 && (payload[0] & 0xFF) == 0x7F) {
            out.add(String.format(Locale.US, "NEGATIVA 7F %02X %02X",
                    payload[1] & 0xFF, payload[2] & 0xFF));
            return out;
        }
        if (payload.length < 3 || (payload[0] & 0xFF) != 0x59 || (payload[1] & 0xFF) != 0x02) {
            out.add("RESPOSTA INESPERADA: " + IsoTpParser.bytesToHex(payload));
            return out;
        }

        int index = 3; // payload[2] = DTCStatusAvailabilityMask
        while (index + 3 < payload.length) {
            String code = String.format(Locale.US, "%02X%02X%02X",
                    payload[index] & 0xFF,
                    payload[index + 1] & 0xFF,
                    payload[index + 2] & 0xFF);
            int status = payload[index + 3] & 0xFF;
            out.add(code + "  status=0x" + String.format(Locale.US, "%02X", status)
                    + "  [" + describeStatus(status) + "]");
            index += 4;
        }
        return out;
    }

    public static String describeStatus(int status) {
        List<String> flags = new ArrayList<>();
        if ((status & 0x01) != 0) flags.add("falha atual");
        if ((status & 0x02) != 0) flags.add("falhou neste ciclo");
        if ((status & 0x04) != 0) flags.add("pendente");
        if ((status & 0x08) != 0) flags.add("confirmado");
        if ((status & 0x10) != 0) flags.add("teste incompleto desde apagar");
        if ((status & 0x20) != 0) flags.add("falhou desde apagar");
        if ((status & 0x40) != 0) flags.add("teste incompleto neste ciclo");
        if ((status & 0x80) != 0) flags.add("MIL/aviso solicitado");
        if (flags.isEmpty()) return "sem flags ativas";
        return join(flags, ", ");
    }

    private static String join(List<String> values, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(values.get(i));
        }
        return sb.toString();
    }
}
