package br.com.scanvag;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;
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
            new VagModule("02", "Cambio", "7E1", "7E9"),
            new VagModule("03", "ABS / Freios", "713", "77D"),
            new VagModule("09", "BCM / Central Eletrica", "70E", "778"),
            new VagModule("17", "Painel / Instruments", "714", "77E"),
            new VagModule("19", "Gateway", "710", "77A"),
            new VagModule("5F", "Multimidia", "773", "7DD", true)
    };

    private BluetoothAdapter bluetoothAdapter;
    private Spinner deviceSpinner;
    private Button refreshButton, connectButton, scanButton, dtcButton, backupButton, shareButton, disconnectButton, logButton;
    private TextView statusText, vehicleText, outputText, moduleCountText;
    private LinearLayout cardsContainer;
    private ProgressBar progress;

    private String vin = "";
    private String elmInfo = "";
    private String protocol = "";
    private String lastReport = "";
    private boolean logVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        requestBluetoothPermissionIfNeeded();
    }

    private void buildUi() {
        int pad = dp(14);

        ScrollView page = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, dp(28));
        page.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("ScanVAG v1.2.2");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, fullWidth());

        TextView badge = new TextView(this);
        badge.setText("READ + CODING BETA • ELM327 • VAG UDS");
        badge.setTextSize(13);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setPadding(0, dp(4), 0, dp(8));
        root.addView(badge, fullWidth());

        statusText = new TextView(this);
        statusText.setText("Status: aguardando Bluetooth");
        statusText.setTextSize(15);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(statusText, fullWidth());

        vehicleText = new TextView(this);
        vehicleText.setText("VIN: -\nELM: -\nProtocolo: -");
        vehicleText.setTextSize(13);
        vehicleText.setPadding(0, dp(5), 0, dp(10));
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
        backupButton = button("Backup geral");
        shareButton = button("Compartilhar");
        row3.addView(backupButton, weighted());
        row3.addView(shareButton, weighted());
        root.addView(row3, fullWidth());

        disconnectButton = button("Desconectar");
        root.addView(disconnectButton, fullWidthWithMargin());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(modules.length);
        progress.setProgress(0);
        LinearLayout.LayoutParams progressParams = fullWidth();
        progressParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(progress, progressParams);

        LinearLayout moduleHeader = row();
        TextView moduleTitle = new TextView(this);
        moduleTitle.setText("Modulos");
        moduleTitle.setTextSize(19);
        moduleTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        moduleCountText = new TextView(this);
        moduleCountText.setText("0/" + modules.length + " encontrados");
        moduleCountText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        moduleHeader.addView(moduleTitle, weightedNoMargin());
        moduleHeader.addView(moduleCountText, weightedNoMargin());
        root.addView(moduleHeader, fullWidth());

        cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        cardsContainer.setPadding(0, dp(5), 0, dp(5));
        root.addView(cardsContainer, fullWidth());
        renderModuleCards();

        logButton = button("Mostrar log tecnico");
        root.addView(logButton, fullWidthWithMargin());

        outputText = new TextView(this);
        outputText.setTextSize(12);
        outputText.setTypeface(Typeface.MONOSPACE);
        outputText.setTextIsSelectable(true);
        outputText.setPadding(dp(4), dp(8), dp(4), dp(8));
        outputText.setText("Pronto. Selecione o ELM327 e toque em Conectar.\n\nCoding BETA: escrita liberada somente no 5F Multimidia, com backup e verificacao.\n");
        outputText.setVisibility(View.GONE);
        root.addView(outputText, fullWidth());

        refreshButton.setOnClickListener(v -> refreshBondedDevices());
        connectButton.setOnClickListener(v -> connectSelected());
        scanButton.setOnClickListener(v -> startScan());
        dtcButton.setOnClickListener(v -> startDtcScan());
        backupButton.setOnClickListener(v -> saveBackup());
        shareButton.setOnClickListener(v -> shareReport());
        disconnectButton.setOnClickListener(v -> disconnectElm());
        logButton.setOnClickListener(v -> toggleLog());

        setConnectedUi(false);
        setContentView(page);
    }

    private void toggleLog() {
        logVisible = !logVisible;
        outputText.setVisibility(logVisible ? View.VISIBLE : View.GONE);
        logButton.setText(logVisible ? "Ocultar log tecnico" : "Mostrar log tecnico");
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

    private LinearLayout.LayoutParams fullWidthWithMargin() {
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(dp(3), dp(4), dp(3), dp(4));
        return p;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(3), dp(4), dp(3), dp(4));
        return p;
    }

    private LinearLayout.LayoutParams weightedNoMargin() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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
                setStatus("Permissao Bluetooth negada");
                append("Conceda 'Dispositivos proximos' para acessar o ELM327.\n");
            }
        }
    }

    private void refreshBondedDevices() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissionIfNeeded();
            return;
        }
        if (bluetoothAdapter == null) {
            setStatus("Bluetooth nao disponivel");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setStatus("Ative o Bluetooth do celular");
            return;
        }

        bondedDevices.clear();
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        if (bonded != null) bondedDevices.addAll(bonded);
        bondedDevices.sort(new Comparator<BluetoothDevice>() {
            @Override
            public int compare(BluetoothDevice a, BluetoothDevice b) {
                int pa = obdPriority(safeName(a));
                int pb = obdPriority(safeName(b));
                if (pa != pb) return Integer.compare(pa, pb);
                return safeName(a).compareToIgnoreCase(safeName(b));
            }
        });

        List<String> labels = new ArrayList<>();
        for (BluetoothDevice d : bondedDevices) labels.add(safeName(d) + "  [" + d.getAddress() + "]");
        if (labels.isEmpty()) labels.add("Nenhum dispositivo pareado");

        deviceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        setStatus(bondedDevices.isEmpty() ? "Nenhum dispositivo pareado" : "Selecione o ELM327 / OBDII");
    }

    private int obdPriority(String name) {
        String n = name == null ? "" : name.toUpperCase();
        if (n.contains("OBD") || n.contains("ELM") || n.contains("VLINK") || n.contains("VEEPEAK")) return 0;
        return 1;
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
        append("\n=== CONEXAO ===\nBluetooth: " + safeName(device) + " / " + device.getAddress() + "\n");

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
                elm.command("ATAT1", 1000);
                elm.command("ATSTFF", 1000);
                elm.command("ATFCSD300000", 1000);
                elm.command("ATFCSM1", 1000);

                // Primeiro gera trafego real. Depois ATDP passa a informar o protocolo detectado,
                // em vez de exibir somente AUTO.
                vin = readVin();
                protocol = clean(elm.command("ATDP", 1500));

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
            byte[] p = readPayloadWithPending("22F190", engine, 5000);
            return IsoTpParser.decodeAsciiDid(p, 0xF190).replace("SEM RESPOSTA", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void startScan() {
        if (!elm.isConnected()) {
            setStatus("ELM327 nao conectado");
            return;
        }
        setBusy(true);
        progress.setMax(modules.length);
        progress.setProgress(0);
        results.clear();
        outputText.setText("=== SCAN VAG v1.2.2 / READ + CODING BETA ===\n");
        renderModuleCards();

        worker.execute(() -> {
            int done = 0;
            try {
                for (VagModule m : modules) {
                    postStatus("Lendo " + m.address + " - " + m.name + "...");
                    ScanResult result = scanModule(m);
                    results.add(result);
                    postModule(result);
                    done++;
                    final int p = done;
                    main.post(() -> {
                        progress.setProgress(p);
                        renderModuleCards();
                    });
                }
                refreshReport();
                postStatus("Scan finalizado");
                postAppend("\n=== FIM DO SCAN ===\n");
            } catch (Exception e) {
                postStatus("Scan interrompido");
                postAppend("\nERRO NO SCAN: " + e.getMessage() + "\n");
            } finally {
                main.post(() -> {
                    setBusy(false);
                    renderModuleCards();
                });
            }
        });
    }

    private ScanResult scanModule(VagModule m) {
        ScanResult r = new ScanResult(m);
        try {
            selectModule(m);
            byte[] p191 = readPayloadWithPending("22F191", m, 6000);
            byte[] p187 = readPayloadWithPending("22F187", m, 6000);
            byte[] p600 = readPayloadWithPending("220600", m, 7000);
            r.present = p191 != null || p187 != null || p600 != null;
            if (r.present) {
                r.f191 = IsoTpParser.decodeAsciiDid(p191, 0xF191);
                r.f187 = IsoTpParser.decodeAsciiDid(p187, 0xF187);
                r.coding0600 = IsoTpParser.decodeHexDid(p600, 0x0600);
            }
        } catch (Exception e) {
            r.present = false;
            appendFromWorker("[" + m.address + "] erro: " + e.getMessage() + "\n");
        }
        return r;
    }

    private byte[] readPayloadWithPending(String command, VagModule module, long totalTimeoutMs) {
        try {
            // Envia o request UMA única vez. Se a ECU responder 7F xx 78, o Elm327Client
            // permanece ouvindo a resposta definitiva sem reenviar o serviço UDS.
            String raw = elm.command(command, totalTimeoutMs);
            byte[] payload = IsoTpParser.extractPayload(raw, module.rxId);
            if (IsoTpParser.isResponsePending(payload)) {
                appendFromWorker("[" + module.address + "] UDS 0x78: timeout aguardando resposta definitiva.\n");
                postStatus("[" + module.address + "] resposta ainda pendente");
            }
            return payload;
        } catch (Exception e) {
            appendFromWorker("[" + module.address + "] " + command + ": " + e.getMessage() + "\n");
            return null;
        }
    }

    private void startDtcScan() {
        if (!elm.isConnected()) {
            setStatus("ELM327 nao conectado");
            return;
        }
        if (results.isEmpty()) {
            toast("Faca o Scan VAG primeiro");
            return;
        }
        setBusy(true);
        progress.setMax(results.size());
        progress.setProgress(0);
        append("\n=== LEITURA DTC UDS ===\n");

        worker.execute(() -> {
            int done = 0;
            for (ScanResult r : results) {
                if (r.present) {
                    try {
                        postStatus("DTC " + r.module.address + " - " + r.module.name + "...");
                        selectModule(r.module);
                        byte[] payload = readPayloadWithPending("1902FF", r.module, 7000);
                        r.dtcs.clear();
                        r.dtcs.addAll(DtcParser.parse1902(payload));
                        if (r.dtcs.size() == 1 && (r.dtcs.get(0).startsWith("NEGATIVA") || r.dtcs.get(0).equals("SEM RESPOSTA"))) {
                            r.dtcSummary = r.dtcs.get(0);
                        } else {
                            r.dtcSummary = r.dtcs.isEmpty() ? "0 DTC" : r.dtcs.size() + " registro(s)";
                        }
                        postAppend("[" + r.module.address + "] " + r.module.name + ": " + r.dtcSummary + "\n");
                    } catch (Exception e) {
                        r.dtcSummary = "ERRO: " + e.getMessage();
                    }
                }
                done++;
                final int p = done;
                main.post(() -> {
                    progress.setProgress(p);
                    renderModuleCards();
                });
            }
            refreshReport();
            postStatus("Leitura DTC finalizada");
            main.post(() -> {
                setBusy(false);
                renderModuleCards();
            });
        });
    }

    private void selectModule(VagModule m) throws Exception {
        elm.command("ATSH" + m.txId, 1000);
        elm.command("ATFCSH" + m.txId, 1000);
        elm.command("ATCRA" + m.rxId, 1000);
    }

    private void renderModuleCards() {
        cardsContainer.removeAllViews();
        int found = 0;

        for (VagModule module : modules) {
            ScanResult result = findResult(module.address);
            if (result != null && result.present) found++;
            cardsContainer.addView(createModuleCard(module, result), fullWidthWithCardMargin());
        }
        moduleCountText.setText(found + "/" + modules.length + " encontrados");
    }

    private View createModuleCard(VagModule module, ScanResult result) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(11), dp(13), dp(11));
        card.setBackground(cardBackground(result));
        card.setClickable(result != null);
        card.setFocusable(result != null);

        TextView title = new TextView(this);
        title.setText("[" + module.address + "] " + module.name);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, fullWidth());

        TextView status = new TextView(this);
        if (result == null) {
            status.setText("Aguardando scan");
        } else if (!result.present) {
            status.setText("Sem resposta • TX/RX " + module.txId + "/" + module.rxId);
        } else {
            status.setText("OK • " + shortPart(result) + " • DTC: " + result.dtcSummary);
        }
        status.setTextSize(13);
        status.setPadding(0, dp(4), 0, 0);
        card.addView(status, fullWidth());

        if (result != null && result.present && result.coding0600 != null && !result.coding0600.isEmpty()) {
            TextView coding = new TextView(this);
            coding.setTypeface(Typeface.MONOSPACE);
            coding.setTextSize(11);
            coding.setText("Coding: " + ellipsize(result.coding0600, 44));
            coding.setPadding(0, dp(4), 0, 0);
            card.addView(coding, fullWidth());
        }
        if (module.codingWriteAllowed) {
            TextView beta = new TextView(this);
            beta.setText("CODING BETA • backup + verificacao obrigatorios");
            beta.setTextSize(11);
            beta.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            beta.setPadding(0, dp(5), 0, 0);
            card.addView(beta, fullWidth());
        }

        if (result != null) card.setOnClickListener(v -> showModuleDetails(result));
        return card;
    }

    private GradientDrawable cardBackground(ScanResult result) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        if (result == null) {
            d.setColor(Color.rgb(245, 245, 245));
            d.setStroke(dp(1), Color.rgb(210, 210, 210));
        } else if (result.present) {
            d.setColor(Color.rgb(238, 247, 240));
            d.setStroke(dp(1), Color.rgb(140, 190, 150));
        } else {
            d.setColor(Color.rgb(250, 244, 244));
            d.setStroke(dp(1), Color.rgb(205, 170, 170));
        }
        return d;
    }

    private LinearLayout.LayoutParams fullWidthWithCardMargin() {
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(4), 0, dp(4));
        return p;
    }

    private ScanResult findResult(String address) {
        for (ScanResult r : results) {
            if (r.module.address.equals(address)) return r;
        }
        return null;
    }

    private String shortPart(ScanResult r) {
        String p = r.f191 == null ? "" : r.f191.trim();
        if (p.isEmpty() || p.startsWith("NEGATIVA") || p.startsWith("PENDENTE") || p.startsWith("SEM")) p = r.f187 == null ? "" : r.f187.trim();
        return p.isEmpty() ? "identificacao lida" : p;
    }

    private String ellipsize(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }

    private void showModuleDetails(ScanResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Endereco: ").append(r.module.address).append("\n");
        sb.append("TX/RX: ").append(r.module.txId).append("/").append(r.module.rxId).append("\n\n");
        if (!r.present) {
            sb.append("Modulo sem resposta no ultimo scan.");
        } else {
            sb.append("F191: ").append(valueOrDash(r.f191)).append("\n");
            sb.append("F187: ").append(valueOrDash(r.f187)).append("\n\n");
            sb.append("Coding 0600:\n").append(valueOrDash(r.coding0600)).append("\n\n");
            sb.append("DTC: ").append(valueOrDash(r.dtcSummary)).append("\n");
            for (String d : r.dtcs) sb.append("• ").append(d).append("\n");
            if (r.module.codingWriteAllowed) {
                sb.append("\nCODING BETA: escrita habilitada somente neste modulo.\n");
            }
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("[" + r.module.address + "] " + r.module.name)
                .setMessage(sb.toString())
                .setNegativeButton("Fechar", null)
                .setNeutralButton("Copiar coding", (d, which) -> copyCoding(r));

        if (r.present && r.module.codingWriteAllowed && isUsableCoding(r.coding0600)) {
            b.setPositiveButton("Coding BETA", (d, which) -> showCodingEditor(r));
        } else {
            b.setPositiveButton("Backup modulo", (d, which) -> saveModuleBackup(r));
        }
        b.show();
    }

    private boolean isUsableCoding(String coding) {
        if (coding == null) return false;
        String c = coding.trim().toUpperCase(Locale.US);
        return !c.isEmpty() && c.matches("[0-9A-F]+") && (c.length() % 2 == 0);
    }

    private void showCodingEditor(ScanResult r) {
        if (!r.module.codingWriteAllowed || !"5F".equals(r.module.address)) {
            toast("Escrita bloqueada neste modulo");
            return;
        }
        final String original = r.coding0600.trim().toUpperCase(Locale.US);
        final EditText input = new EditText(this);
        input.setText(original);
        input.setTextSize(13);
        input.setTypeface(Typeface.MONOSPACE);
        input.setSingleLine(false);
        input.setSelectAllOnFocus(false);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));

        new AlertDialog.Builder(this)
                .setTitle("Coding BETA — [5F] Multimidia")
                .setMessage("Edite somente o HEX do Coding 0600. O tamanho deve permanecer exatamente igual.\n\nOriginal:\n" + original)
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (d, which) -> {
                    String candidate = normalizeCodingInput(input.getText().toString());
                    if (candidate == null) {
                        showSimpleError("Coding invalido", "Use apenas caracteres HEX 0-9 / A-F.");
                        return;
                    }
                    if (candidate.length() != original.length()) {
                        showSimpleError("Tamanho diferente", "Original: " + (original.length() / 2)
                                + " bytes\nNovo: " + (candidate.length() / 2) + " bytes\n\nA v1.2 bloqueia alteracao de tamanho.");
                        return;
                    }
                    if (candidate.equals(original)) {
                        prepareCodingWrite(r, original, candidate, true);
                        return;
                    }
                    prepareCodingWrite(r, original, candidate, false);
                })
                .show();
    }

    private String normalizeCodingInput(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (!t.matches("[0-9A-Fa-f\\s]+")) return null;
        String hex = t.replaceAll("\\s+", "").toUpperCase(Locale.US);
        if (hex.isEmpty() || (hex.length() % 2) != 0) return null;
        return hex;
    }

    private void prepareCodingWrite(ScanResult r, String originalFromScreen, String candidate, boolean identicalValidation) {
        setBusy(true);
        setStatus(identicalValidation ? "Preparando validacao de escrita..." : "Preparando Coding BETA...");
        worker.execute(() -> {
            try {
                selectModule(r.module);
                byte[] freshPayload = readPayloadWithPending("220600", r.module, 7000);
                String fresh = IsoTpParser.decodeHexDid(freshPayload, 0x0600);
                if (!isUsableCoding(fresh)) throw new Exception("Nao foi possivel reler o coding original: " + fresh);
                fresh = fresh.trim().toUpperCase(Locale.US);
                if (!fresh.equals(originalFromScreen)) {
                    throw new Exception("O coding mudou desde o scan. Faca um novo Scan VAG antes de gravar.");
                }

                String voltageRaw = clean(elm.command("ATRV", 1200));
                double voltage = parseVoltage(voltageRaw);
                if (Double.isNaN(voltage)) throw new Exception("Nao consegui validar a tensao (ATRV: " + voltageRaw + ")");
                if (voltage < 11.8) throw new Exception(String.format(Locale.US,
                        "Tensao baixa para coding: %.2f V. Use carregador/fonte e tente novamente.", voltage));

                String backupText = ReportManager.buildModuleText(vin, elmInfo, protocol, r);
                File backup = ReportManager.saveModuleText(this, vin, r, backupText);
                final String freshFinal = fresh;
                final double voltageFinal = voltage;
                final String backupPath = backup.getAbsolutePath();
                main.post(() -> {
                    setBusy(false);
                    showFinalCodingConfirmation(r, freshFinal, candidate, voltageFinal, backupPath, identicalValidation);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false);
                    setStatus("Coding BETA cancelado");
                    showSimpleError("Nao foi possivel preparar a gravacao", e.getMessage());
                });
            }
        });
    }

    private double parseVoltage(String raw) {
        if (raw == null) return Double.NaN;
        String cleaned = raw.toUpperCase(Locale.US).replace("V", "").replace(",", ".").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(cleaned);
        if (!m.find()) return Double.NaN;
        try { return Double.parseDouble(m.group(1)); } catch (Exception e) { return Double.NaN; }
    }

    private void showFinalCodingConfirmation(ScanResult r, String original, String candidate,
                                             double voltage, String backupPath, boolean identicalValidation) {
        final EditText confirm = new EditText(this);
        final String confirmationWord = identicalValidation ? "TESTAR" : "GRAVAR";
        confirm.setHint("Digite " + confirmationWord);
        confirm.setSingleLine(true);

        String modeText = identicalValidation
                ? "VALIDACAO DE ESCRITA IDENTICA: o app enviara o MESMO Coding 0600 que ja esta no 5F. Nenhum byte de configuracao sera alterado."
                : "CODING BETA: o valor abaixo e diferente do original e pode alterar configuracoes do 5F.";

        String message = modeText
                + "\n\nBACKUP SALVO:\n" + backupPath
                + "\n\nTensao: " + String.format(Locale.US, "%.2f V", voltage)
                + "\n\nORIGINAL:\n" + original
                + "\n\nVALOR A ENVIAR:\n" + candidate
                + "\n\nA v1.2.2 grava somente o DID 0600 do modulo 5F e relera o DID apos a resposta da ECU."
                + "\nDigite " + confirmationWord + " para confirmar.";

        new AlertDialog.Builder(this)
                .setTitle(identicalValidation ? "Validar escrita 5F — SEM ALTERAR" : "Confirmacao final — CODING BETA")
                .setMessage(message)
                .setView(confirm)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(identicalValidation ? "Testar" : "Gravar", (d, which) -> {
                    if (!confirmationWord.equalsIgnoreCase(confirm.getText().toString().trim())) {
                        showSimpleError("Confirmacao incorreta", "Nada foi enviado ao modulo.");
                        return;
                    }
                    performCodingWrite(r, original, candidate, identicalValidation);
                })
                .show();
    }

    private void performCodingWrite(ScanResult r, String original, String candidate, boolean identicalValidation) {
        if (!r.module.codingWriteAllowed || !"5F".equals(r.module.address)) {
            toast("Escrita bloqueada neste modulo");
            return;
        }
        setBusy(true);
        setStatus(identicalValidation ? "Validando escrita identica no 5F..." : "Gravando Coding 0600 no 5F...");
        append("\n=== " + (identicalValidation ? "WRITE VALIDATION 5F" : "CODING BETA 5F") + " ===\nOriginal: " + original + "\nValor enviado: " + candidate + "\n");

        worker.execute(() -> {
            try {
                selectModule(r.module);
                elm.command("ATAL", 1000); // permite mensagens ISO-TP longas em adaptadores compativeis

                String sessionRaw = elm.codingCommand("1003", 3500);
                appendFromWorker("UDS 1003 RX bruto: " + clean(sessionRaw) + "\n");
                byte[] session = IsoTpParser.extractPayload(sessionRaw, r.module.rxId);
                if (IsoTpParser.isResponsePending(session)) {
                    android.os.SystemClock.sleep(650);
                } else if (!IsoTpParser.isPositiveSession(session, 0x03)) {
                    throw new Exception("Sessao diagnostica recusada: " + IsoTpParser.describe(session));
                }

                String writeRaw = elm.codingCommand("2E0600" + candidate, 8000);
                appendFromWorker("UDS 2E0600 RX bruto: " + clean(writeRaw) + "\n");
                byte[] writePayload = IsoTpParser.extractPayload(writeRaw, r.module.rxId);
                boolean pending = IsoTpParser.isResponsePending(writePayload);
                if (!pending && !IsoTpParser.isPositiveWriteDid(writePayload, 0x0600)) {
                    throw new Exception("ECU recusou a escrita: " + IsoTpParser.describe(writePayload));
                }

                if (pending) android.os.SystemClock.sleep(900);
                else android.os.SystemClock.sleep(350);

                byte[] verifyPayload = readPayloadWithPending("220600", r.module, 8000);
                String verified = IsoTpParser.decodeHexDid(verifyPayload, 0x0600);
                if (!candidate.equalsIgnoreCase(verified)) {
                    throw new Exception("Verificacao falhou. ECU retornou: " + verified
                            + "\nO backup original foi mantido; nao sera feita nova escrita automaticamente.");
                }

                r.coding0600 = verified.toUpperCase(Locale.US);
                refreshReport();
                main.post(() -> {
                    setBusy(false);
                    setStatus(identicalValidation ? "Validacao de escrita 5F concluida" : "Coding 5F gravado e verificado");
                    renderModuleCards();
                    append("Resultado: OK — ECU confirmou 2E0600 e a releitura ficou identica.\n=== FIM CODING ===\n");
                    new AlertDialog.Builder(this)
                            .setTitle(identicalValidation ? "Escrita validada" : "Coding concluido")
                            .setMessage((identicalValidation
                                    ? "O modulo 5F aceitou a reescrita do mesmo Coding 0600. Nenhum byte foi alterado e a releitura conferiu 100%."
                                    : "O modulo 5F confirmou o Coding 0600 e a releitura ficou identica ao valor gravado.")
                                    + "\n\nCoding verificado:\n" + r.coding0600)
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false);
                    setStatus("Coding nao concluido");
                    append("ERRO CODING: " + e.getMessage() + "\n=== FIM CODING ===\n");
                    showSimpleError("Coding nao concluido", e.getMessage()
                            + "\n\nNenhum bypass de Security Access e feito pela v1.2.2.");
                });
            }
        });
    }

    private void showSimpleError(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message == null ? "Erro desconhecido" : message)
                .setPositiveButton("OK", null)
                .show();
    }

    private String valueOrDash(String s) {
        return s == null || s.trim().isEmpty() ? "-" : s.trim();
    }

    private void copyCoding(ScanResult r) {
        if (r == null || r.coding0600 == null || r.coding0600.trim().isEmpty()) {
            toast("Coding nao disponivel");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("ScanVAG Coding " + r.module.address, r.coding0600));
            toast("Coding copiado");
        }
    }

    private void saveModuleBackup(ScanResult r) {
        try {
            String text = ReportManager.buildModuleText(vin, elmInfo, protocol, r);
            File f = ReportManager.saveModuleText(this, vin, r, text);
            append("\nBackup do modulo " + r.module.address + " salvo em:\n" + f.getAbsolutePath() + "\n");
            toast("Backup do modulo salvo");
        } catch (Exception e) {
            toast("Erro no backup: " + e.getMessage());
        }
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
            toast("Faca o Scan VAG primeiro");
            return;
        }
        try {
            File f = ReportManager.saveText(this, vin, lastReport);
            append("\nBackup geral salvo em:\n" + f.getAbsolutePath() + "\n");
            toast("Backup geral salvo");
        } catch (Exception e) {
            toast("Erro no backup: " + e.getMessage());
        }
    }

    private void shareReport() {
        refreshReport();
        if (results.isEmpty()) {
            toast("Faca o Scan VAG primeiro");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "ScanVAG " + (vin.isEmpty() ? "Relatorio" : vin));
        send.putExtra(Intent.EXTRA_TEXT, lastReport);
        startActivity(Intent.createChooser(send, "Compartilhar relatorio ScanVAG"));
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
        dtcButton.setEnabled(connected && !results.isEmpty());
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
    private void append(String text) { outputText.append(text); }
    private void postAppend(String text) { main.post(() -> append(text)); }
    private void appendFromWorker(String text) { main.post(() -> append(text)); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onDestroy() {
        elm.disconnect();
        worker.shutdownNow();
        super.onDestroy();
    }
}
