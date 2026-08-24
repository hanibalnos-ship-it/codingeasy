package br.com.scanvag;

public final class VagModule {
    public final String address;
    public final String name;
    public final String txId;
    public final String rxId;
    public final boolean codingWriteAllowed;

    public VagModule(String address, String name, String txId, String rxId) {
        this(address, name, txId, rxId, false);
    }

    public VagModule(String address, String name, String txId, String rxId, boolean codingWriteAllowed) {
        this.address = address;
        this.name = name;
        this.txId = txId;
        this.rxId = rxId;
        this.codingWriteAllowed = codingWriteAllowed;
    }

    @Override
    public String toString() {
        return address + " - " + name;
    }
}
