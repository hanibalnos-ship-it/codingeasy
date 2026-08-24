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
        sb.append("ScanVAG v1.1 - READ ONLY\n");
        sb.append("Data: ").append(nowHuman()).append("\n");
        sb.append("VIN: ").append(empty(vin)).append("\n");
        sb.append("ELM: ").append(empty(elmInfo)).append("\n");
        sb.append("Protocolo: ").append(empty(protocol)).append("\n\n");
        for (ScanResult r : results) {
            appendModule(sb, r);
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String buildModuleText(String vin, String elmInfo, String protocol, ScanResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("ScanVAG v1.1 - BACKUP DE MODULO / READ ONLY\n");
        sb.append("Data: ").append(nowHuman()).append("\n");
        sb.append("VIN: ").append(empty(vin)).append("\n");
        sb.append("ELM: ").append(empty(elmInfo)).append("\n");
        sb.append("Protocolo: ").append(empty(protocol)).append("\n\n");
        appendModule(sb, result);
        return sb.toString();
    }

    private static void appendModule(StringBuilder sb, ScanResult r) {
        sb.append("[").append(r.module.address).append("] ").append(r.module.name).append("\n");
        sb.append("TX/RX: ").append(r.module.txId).append("/").append(r.module.rxId).append("\n");
        sb.append("Presente: ").append(r.present ? "SIM" : "NAO").append("\n");
        if (r.present) {
            sb.append("F191: ").append(empty(r.f191)).append("\n");
            sb.append("F187: ").append(empty(r.f187)).append("\n");
            sb.append("Coding 0600: ").append(empty(r.coding0600)).append("\n");
            sb.append("DTC: ").append(empty(r.dtcSummary)).append("\n");
            for (String d : r.dtcs) sb.append("  - ").append(d).append("\n");
        }
    }

    public static File saveText(Context context, String vin, String text) throws Exception {
        return saveNamed(context, "ScanVAG_" + safeVin(vin) + "_" + nowFile() + ".txt", text);
    }

    public static File saveModuleText(Context context, String vin, ScanResult result, String text) throws Exception {
        String module = result == null ? "MOD" : result.module.address;
        return saveNamed(context, "ScanVAG_" + safeVin(vin) + "_MOD" + module + "_" + nowFile() + ".txt", text);
    }

    private static File saveNamed(Context context, String filename, String text) throws Exception {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "ScanVAG/backups");
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new Exception("Nao foi possivel criar pasta de backup");
        }
        File out = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String safeVin(String vin) {
        return (vin == null || vin.trim().isEmpty()) ? "SEM_VIN" : vin.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String nowFile() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private static String nowHuman() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String empty(String s) {
        return s == null || s.trim().isEmpty() ? "-" : s.trim();
    }
}
