package br.com.scanvag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DtcParser {
    private DtcParser() {}

    // UDS ReadDTCInformation: 19 02 FF -> 59 02 availabilityMask + records.
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

        int index = 3; // payload[2] is DTCStatusAvailabilityMask
        while (index + 3 < payload.length) {
            String code = String.format(Locale.US, "%02X%02X%02X",
                    payload[index] & 0xFF,
                    payload[index + 1] & 0xFF,
                    payload[index + 2] & 0xFF);
            int status = payload[index + 3] & 0xFF;
            out.add(String.format(Locale.US, "%s  status=%02X", code, status));
            index += 4;
        }
        return out;
    }
}
