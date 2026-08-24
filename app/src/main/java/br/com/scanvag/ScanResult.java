package br.com.scanvag;

import java.util.ArrayList;
import java.util.List;

public final class ScanResult {
    public final VagModule module;
    public boolean present;
    public String f191 = "";
    public String f187 = "";
    public String coding0600 = "";
    public String dtcSummary = "Não lido";
    public final List<String> dtcs = new ArrayList<>();

    public ScanResult(VagModule module) {
        this.module = module;
    }
}
