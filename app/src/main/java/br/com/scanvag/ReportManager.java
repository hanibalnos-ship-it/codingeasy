package br.com.scanvag;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReportManager {
    private ReportManager() {}

    public static String buildText(String vin, String elmInfo, String protocol, List<ScanResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("ScanVAG v1.0 - READ ONLY\n");
        sb.append("Data: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("\n");
        sb.append("VIN: ").append(empty(vin)).append("\n");
        sb.append("ELM: ").append(empty(elmInfo)).append("\n");
        sb.append("Protocolo: ").append(empty(protocol)).append("\n\n");
        for (ScanResult r : results) {
            sb.append("[").append(r.module.address).append("] ").append(r.module.name).append("\n");
            sb.append("TX/RX: ").append(r.module.txId).append("/").append(r.module.rxId).append("\n");
            sb.append("Presente: ").append(r.present ? "SIM" : "NÃO").append("\n");
            if (r.present) {
                sb.append("F191: ").append(empty(r.f191)).append("\n");
                sb.append("F187: ").append(empty(r.f187)).append("\n");
                sb.append("Coding 0600: ").append(empty(r.coding0600)).append("\n");
                sb.append("DTC: ").append(empty(r.dtcSummary)).append("\n");
                for (String d : r.dtcs) sb.append("  - ").append(d).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public static File saveText(Context context, String vin, String text) throws Exception {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "ScanVAG/backups");
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new Exception("Não foi possível criar pasta de backup");
        }
        String safeVin = (vin == null || vin.trim().isEmpty()) ? "SEM_VIN" : vin.replaceAll("[^A-Za-z0-9_-]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File out = new File(dir, "ScanVAG_" + safeVin + "_" + stamp + ".txt");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String empty(String s) {
        return s == null || s.trim().isEmpty() ? "-" : s.trim();
    }
}
