package br.com.scanvag;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_BT = 1001;

    private final Elm327Client elm = new Elm327Client();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<BluetoothDevice> bondedDevices = new ArrayList<>();
    private final List<ScanResult> results = new ArrayList<>();

    private final VagModule[] modules = new VagModule[]{
            new VagModule("01", "Motor", "7E0", "7E8"),
            new VagModule("02", "Câmbio", "7E1", "7E9"),
            new VagModule("09", "BCM / Central Elétrica", "70E", "778"),
            new VagModule("17", "Painel / Instruments", "714", "77E"),
            new VagModule("19", "Gateway", "710", "77A"),
            new VagModule("5F", "Multimídia", "773", "7DD")
    };

    private BluetoothAdapter bluetoothAdapter;
    private Spinner deviceSpinner;
    private Button refreshButton, connectButton, scanButton, dtcButton, backupButton, shareButton, disconnectButton;
    private TextView statusText, vehicleText, outputText;
    private ProgressBar progress;
    private ScrollView scrollView;

    private String vin = "";
    private String elmInfo = "";
    private String protocol = "";
    private String lastReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        requestBluetoothPermissionIfNeeded();
    }

    private void buildUi() {
        int pad = dp(14);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("ScanVAG v1.0");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, fullWidth());

        TextView badge = new TextView(this);
        badge.setText("READ ONLY • ELM327 • VAG UDS");
        badge.setTextSize(13);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setPadding(0, dp(4), 0, dp(8));
        root.addView(badge, fullWidth());

        statusText = new TextView(this);
        statusText.setText("Status: aguardando Bluetooth");
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(statusText, fullWidth());

        vehicleText = new TextView(this);
        vehicleText.setText("VIN: -\nELM: -\nProtocolo: -");
        vehicleText.setTextSize(13);
        vehicleText.setPadding(0, dp(5), 0, dp(8));
        root.addView(vehicleText, fullWidth());

        deviceSpinner = new Spinner(this);
        root.addView(deviceSpinner, fullWidth());

        LinearLayout row1 = row();
        refreshButton = button("Atualizar");
        connectButton = button("Conectar");
        row1.addView(refreshButton, weighted());
        row1.addView(connectButton, weighted());
        root.addView(row1, fullWidth());

        LinearLayout row2 = row();
        scanButton = button("SCAN VAG");
        dtcButton = button("LER DTC");
        row2.addView(scanButton, weighted());
        row2.addView(dtcButton, weighted());
        root.addView(row2, fullWidth());

        LinearLayout row3 = row();
        backupButton = button("Salvar backup");
        shareButton = button("Compartilhar");
        row3.addView(backupButton, weighted());
        row3.addView(shareButton, weighted());
        root.addView(row3, fullWidth());

        disconnectButton = button("Desconectar");
        root.addView(disconnectButton, fullWidth());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(modules.length);
        progress.setProgress(0);
        root.addView(progress, fullWidth());

        scrollView = new ScrollView(this);
        outputText = new TextView(this);
        outputText.setTextSize(13);
        outputText.setTypeface(Typeface.MONOSPACE);
        outputText.setTextIsSelectable(true);
        outputText.setText("Pronto. Selecione o ELM327 e toque em Conectar.\n\nA v1.0 bloqueia qualquer comando de escrita no veículo.\n");
        scrollView.addView(outputText, fullWidth());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(8);
        root.addView(scrollView, scrollParams);

        refreshButton.setOnClickListener(v -> refreshBondedDevices());
        connectButton.setOnClickListener(v -> connectSelected());
        scanButton.setOnClickListener(v -> startScan());
        dtcButton.setOnClickListener(v -> startDtcScan());
        backupButton.setOnClickListener(v -> saveBackup());
        shareButton.setOnClickListener(v -> shareReport());
        disconnectButton.setOnClickListener(v -> disconnectElm());

        setConnectedUi(false);
        setContentView(root);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(3), dp(4), dp(3), dp(4));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
        } else {
            refreshBondedDevices();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshBondedDevices();
            } else {
                setStatus("Permissão Bluetooth negada");
                append("Conceda 'Dispositivos próximos' para acessar o ELM327.\n");
            }
        }
    }

    private void refreshBondedDevices() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissionIfNeeded();
            return;
        }
        if (bluetoothAdapter == null) {
            setStatus("Bluetooth não disponível");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setStatus("Ative o Bluetooth do celular");
            return;
        }

        bondedDevices.clear();
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        if (bonded != null) bondedDevices.addAll(bonded);
        bondedDevices.sort(Comparator.comparing(d -> safeName(d).toLowerCase()));

        List<String> labels = new ArrayList<>();
        for (BluetoothDevice d : bondedDevices) labels.add(safeName(d) + "  [" + d.getAddress() + "]");
        if (labels.isEmpty()) labels.add("Nenhum dispositivo pareado");

        deviceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        setStatus(bondedDevices.isEmpty() ? "Nenhum dispositivo pareado" : "Selecione o ELM327 / OBDII");
    }

    private String safeName(BluetoothDevice d) {
        try {
            String name = d.getName();
            return name == null || name.trim().isEmpty() ? "Bluetooth" : name;
        } catch (SecurityException e) {
            return "Bluetooth";
        }
    }

    private void connectSelected() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissionIfNeeded();
            return;
        }
        int pos = deviceSpinner.getSelectedItemPosition();
        if (bondedDevices.isEmpty() || pos < 0 || pos >= bondedDevices.size()) {
            toast("Pareie o ELM327 primeiro");
            return;
        }

        BluetoothDevice device = bondedDevices.get(pos);
        setBusy(true);
        setStatus("Conectando a " + safeName(device) + "...");
        append("\n=== CONEXÃO ===\nBluetooth: " + safeName(device) + " / " + device.getAddress() + "\n");

        worker.execute(() -> {
            try {
                elm.connect(device);
                postStatus("Inicializando ELM327...");
                elmInfo = clean(elm.command("ATI", 1800));
                elm.command("ATE0", 1000);
                elm.command("ATL0", 1000);
                elm.command("ATS0", 1000);
                elm.command("ATH1", 1000);
                elm.command("ATSP0", 2500);
                elm.command("ATCAF1", 1000);
                elm.command("ATCFC1", 1000);
                elm.command("ATFCSD300000", 1000);
                elm.command("ATFCSM1", 1000);
                protocol = clean(elm.command("ATDP", 1500));
                vin = readVin();

                main.post(() -> {
                    setBusy(false);
                    setConnectedUi(true);
                    updateVehicleHeader();
                    setStatus("Conectado — pronto para Scan VAG");
                    append("ELM: " + elmInfo + "\nProtocolo: " + protocol + "\nVIN: " + (vin.isEmpty() ? "-" : vin) + "\n");
                });
            } catch (Exception e) {
                elm.disconnect();
                main.post(() -> {
                    setBusy(false);
                    setConnectedUi(false);
                    setStatus("Falha ao conectar");
                    append("ERRO: " + e.getMessage() + "\n");
                });
            }
        });
    }

    private String readVin() {
        try {
            VagModule engine = modules[0];
            selectModule(engine);
            byte[] p = IsoTpParser.extractPayload(elm.command("22F190", 3000), engine.rxId);
            return IsoTpParser.decodeAsciiDid(p, 0xF190).replace("SEM RESPOSTA", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void startScan() {
        if (!elm.isConnected()) {
            setStatus("ELM327 não conectado");
            return;
        }
        setBusy(true);
        progress.setProgress(0);
        results.clear();
        outputText.setText("=== SCAN VAG v1.0 / READ ONLY ===\n");

        worker.execute(() -> {
            try {
                int done = 0;
                for (VagModule m : modules) {
                    postStatus("Lendo " + m.address + " - " + m.name + "...");
                    ScanResult result = scanModule(m);
                    results.add(result);
                    postModule(result);
                    done++;
                    final int p = done;
                    main.post(() -> progress.setProgress(p));
                }
                refreshReport();
                postStatus("Scan finalizado");
                postAppend("\n=== FIM DO SCAN ===\n");
            } catch (Exception e) {
                postStatus("Scan interrompido");
                postAppend("\nERRO NO SCAN: " + e.getMessage() + "\n");
            } finally {
                main.post(() -> setBusy(false));
            }
        });
    }

    private ScanResult scanModule(VagModule m) throws Exception {
        selectModule(m);
        ScanResult r = new ScanResult(m);
        byte[] p191 = IsoTpParser.extractPayload(elm.command("22F191", 2400), m.rxId);
        byte[] p187 = IsoTpParser.extractPayload(elm.command("22F187", 2400), m.rxId);
        byte[] p600 = IsoTpParser.extractPayload(elm.command("220600", 3200), m.rxId);
        r.present = p191 != null || p187 != null || p600 != null;
        if (r.present) {
            r.f191 = IsoTpParser.decodeAsciiDid(p191, 0xF191);
            r.f187 = IsoTpParser.decodeAsciiDid(p187, 0xF187);
            r.coding0600 = IsoTpParser.decodeHexDid(p600, 0x0600);
        }
        return r;
    }

    private void startDtcScan() {
        if (!elm.isConnected()) {
            setStatus("ELM327 não conectado");
            return;
        }
        if (results.isEmpty()) {
            toast("Faça o Scan VAG primeiro");
            return;
        }
        setBusy(true);
        progress.setProgress(0);
        append("\n=== LEITURA DTC UDS ===\n");

        worker.execute(() -> {
            int done = 0;
            try {
                for (ScanResult r : results) {
                    if (!r.present) {
                        done++;
                        continue;
                    }
                    postStatus("DTC " + r.module.address + " - " + r.module.name + "...");
                    selectModule(r.module);
                    byte[] payload = IsoTpParser.extractPayload(elm.command("1902FF", 3500), r.module.rxId);
                    r.dtcs.clear();
                    r.dtcs.addAll(DtcParser.parse1902(payload));
                    if (r.dtcs.size() == 1 && (r.dtcs.get(0).startsWith("NEGATIVA") || r.dtcs.get(0).equals("SEM RESPOSTA"))) {
                        r.dtcSummary = r.dtcs.get(0);
                    } else {
                        r.dtcSummary = r.dtcs.isEmpty() ? "0 DTC" : r.dtcs.size() + " registro(s)";
                    }
                    postAppend("[" + r.module.address + "] " + r.module.name + ": " + r.dtcSummary + "\n");
                    for (String d : r.dtcs) postAppend("   " + d + "\n");
                    done++;
                    final int p = done;
                    main.post(() -> progress.setProgress(p));
                }
                refreshReport();
                postStatus("Leitura DTC finalizada");
            } catch (Exception e) {
                postStatus("Falha na leitura DTC");
                postAppend("ERRO DTC: " + e.getMessage() + "\n");
            } finally {
                main.post(() -> setBusy(false));
            }
        });
    }

    private void selectModule(VagModule m) throws Exception {
        elm.command("ATSH" + m.txId, 1000);
        elm.command("ATFCSH" + m.txId, 1000);
        elm.command("ATCRA" + m.rxId, 1000);
    }

    private void postModule(ScanResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[").append(r.module.address).append("] ").append(r.module.name).append("\n");
        if (!r.present) {
            sb.append("SEM RESPOSTA\n");
        } else {
            sb.append("TX/RX: ").append(r.module.txId).append("/").append(r.module.rxId).append("\n");
            sb.append("F191: ").append(r.f191).append("\n");
            sb.append("F187: ").append(r.f187).append("\n");
            sb.append("Coding 0600: ").append(r.coding0600).append("\n");
        }
        postAppend(sb.toString());
    }

    private void saveBackup() {
        refreshReport();
        if (results.isEmpty()) {
            toast("Faça o Scan VAG primeiro");
            return;
        }
        try {
            File f = ReportManager.saveText(this, vin, lastReport);
            append("\nBackup salvo em:\n" + f.getAbsolutePath() + "\n");
            toast("Backup salvo");
        } catch (Exception e) {
            toast("Erro no backup: " + e.getMessage());
        }
    }

    private void shareReport() {
        refreshReport();
        if (results.isEmpty()) {
            toast("Faça o Scan VAG primeiro");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "ScanVAG " + (vin.isEmpty() ? "Relatório" : vin));
        send.putExtra(Intent.EXTRA_TEXT, lastReport);
        startActivity(Intent.createChooser(send, "Compartilhar relatório ScanVAG"));
    }

    private void refreshReport() {
        lastReport = ReportManager.buildText(vin, elmInfo, protocol, results);
    }

    private void updateVehicleHeader() {
        vehicleText.setText("VIN: " + (vin.isEmpty() ? "-" : vin)
                + "\nELM: " + (elmInfo.isEmpty() ? "-" : elmInfo)
                + "\nProtocolo: " + (protocol.isEmpty() ? "-" : protocol));
    }

    private String clean(String s) {
        if (s == null) return "";
        return s.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private void disconnectElm() {
        worker.execute(() -> {
            elm.disconnect();
            main.post(() -> {
                setBusy(false);
                setConnectedUi(false);
                setStatus("Desconectado");
                append("\nBluetooth desconectado.\n");
            });
        });
    }

    private void setConnectedUi(boolean connected) {
        scanButton.setEnabled(connected);
        dtcButton.setEnabled(connected);
        disconnectButton.setEnabled(connected);
        backupButton.setEnabled(!results.isEmpty());
        shareButton.setEnabled(!results.isEmpty());
    }

    private void setBusy(boolean busy) {
        refreshButton.setEnabled(!busy);
        connectButton.setEnabled(!busy);
        deviceSpinner.setEnabled(!busy);
        if (busy) {
            scanButton.setEnabled(false);
            dtcButton.setEnabled(false);
            disconnectButton.setEnabled(false);
            backupButton.setEnabled(false);
            shareButton.setEnabled(false);
        } else {
            setConnectedUi(elm.isConnected());
        }
    }

    private void setStatus(String text) { statusText.setText("Status: " + text); }
    private void postStatus(String text) { main.post(() -> setStatus(text)); }
    private void append(String text) {
        outputText.append(text);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
    private void postAppend(String text) { main.post(() -> append(text)); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onDestroy() {
        elm.disconnect();
        worker.shutdownNow();
        super.onDestroy();
    }
}
