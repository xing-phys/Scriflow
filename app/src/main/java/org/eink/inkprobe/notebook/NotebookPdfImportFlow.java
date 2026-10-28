package org.eink.inkprobe.notebook;

/** Main-thread picker/import handoff state retained independently of any Activity or orientation. */
final class NotebookPdfImportFlow {
    enum Phase { CHOOSE, PICKING, IMPORTING, ERROR, READY, OPENED, CANCELLED }
    private Phase phase = Phase.CHOOSE;
    private int notebookId;

    Phase phase() { return phase; }
    int notebookId() { return notebookId; }
    boolean busy() { return phase == Phase.IMPORTING; }
    boolean complete() { return phase == Phase.READY || phase == Phase.OPENED; }
    boolean beginPicker() {
        if (phase != Phase.CHOOSE && phase != Phase.ERROR) return false;
        phase = Phase.PICKING; return true;
    }
    boolean beginImport() {
        if (phase != Phase.PICKING) return false;
        phase = Phase.IMPORTING; return true;
    }
    void cancelPicker() { if (phase == Phase.PICKING) phase = Phase.CANCELLED; }
    void failed() { phase = Phase.ERROR; }
    void imported(int id) {
        if (phase != Phase.IMPORTING || id <= 0) throw new IllegalStateException("Invalid PDF import completion");
        notebookId = id; phase = Phase.READY;
    }
    boolean beginOpen() {
        if (phase != Phase.READY || notebookId <= 0) return false;
        phase = Phase.OPENED; return true;
    }
    /** An in-process retained job keeps IMPORTING; saved state alone cannot restore a dead worker. */
    void restore(String saved, int id) {
        try { phase = Phase.valueOf(saved == null ? "CHOOSE" : saved); }
        catch (IllegalArgumentException error) { phase = Phase.ERROR; }
        notebookId = id;
        if (phase == Phase.IMPORTING || (phase == Phase.READY || phase == Phase.OPENED) && id <= 0) phase = Phase.ERROR;
    }
}
