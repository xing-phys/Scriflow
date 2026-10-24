package org.eink.inkprobe.notebook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Single-writer page store. Call only from a serialized background worker, never pen callbacks.
 * A synced, checksummed record commits each completed stroke without rewriting previous points.
 * Size/template changes publish an atomic snapshot and retain the previous file as a backup.
 */
public final class NotebookStore {
    public static final int MAX_POINTS_PER_STROKE = 200000;
    public static final int MAX_TOTAL_POINTS = 1000000;
    public static final int MAX_STROKES = 50000;
    public static final int MAX_DIMENSION = 32768;

    private static final int MAGIC = 0x4e424b31; // NBK1
    private static final int VERSION = 1;
    private static final int SNAPSHOT = 1, APPEND_STROKE = 2;
    private static final int MAX_RECORD_BYTES = 32 * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final int POINT_BYTES = 20;

    public static final class CorruptDocumentException extends IOException {
        public CorruptDocumentException(String message) { super(message); }
        public CorruptDocumentException(String message, Throwable cause) { super(message, cause); }
    }

    private final File directory, document, backup, temporary;
    private NotebookPage page;
    private long validLength;
    private boolean needsSnapshot, recoveredTail, failed;

    public NotebookStore(File directory) {
        if (directory == null) throw new IllegalArgumentException("Missing notebook directory");
        this.directory = directory;
        document = new File(directory, "page.nbk");
        backup = new File(directory, "page.nbk.bak");
        temporary = new File(directory, "page.nbk.tmp");
    }

    /** Fully corrupt files are left untouched. Only an incomplete final journal record is ignored. */
    public NotebookPage load() throws IOException {
        page = null;
        failed = false;
        recoveredTail = false;
        needsSnapshot = false;
        File source = document.exists() ? document : backup.exists() ? backup : temporary.exists() ? temporary : null;
        if (source == null) {
            page = NotebookPage.empty();
            validLength = 0;
            needsSnapshot = true;
            return page;
        }
        try {
            ReadResult result = read(source);
            page = result.page;
            validLength = result.validLength;
            recoveredTail = result.incompleteTail;
            needsSnapshot = !source.equals(document);
            return page;
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    public NotebookPage current() {
        if (page == null) throw new IllegalStateException("Notebook has not loaded successfully");
        return page;
    }

    /** True when load found an interrupted final append; all earlier completed records were retained. */
    public boolean isRecoveredTail() { return recoveredTail; }

    /** Re-confirms durability after a retry loaded a complete record whose earlier sync was uncertain. */
    public void sync() throws IOException {
        requireReady();
        if (!document.exists()) return;
        try (RandomAccessFile file = new RandomAccessFile(document, "rw")) {
            file.getFD().sync();
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    /** Establishes page dimensions once. Subsequent view sizes must be mapped to this coordinate space. */
    public NotebookPage initializeSize(int width, int height) throws IOException {
        requireReady();
        validateDimensions(width, height, false);
        if (page.isInitialized()) return page;
        return publish(page.withSize(width, height));
    }

    public NotebookPage setTemplate(NotebookPage.Template template) throws IOException {
        requireReady();
        if (template == null) throw new IllegalArgumentException("Missing paper template");
        if (page.template == template) return page;
        return publish(page.withTemplate(template));
    }

    public NotebookPage appendStroke(NotebookPage.Stroke stroke) throws IOException {
        requireReady();
        if (!page.isInitialized()) throw new IOException("Notebook size has not been initialized");
        validateStroke(page, stroke);
        if (page.strokes.size() >= MAX_STROKES || page.pointCount + stroke.points.size() > MAX_TOTAL_POINTS) {
            throw new IOException("Notebook page has reached its stroke/point limit");
        }
        NotebookPage updated = page.withStroke(stroke);
        if (needsSnapshot) return publish(updated);
        byte[] payload = encodeStrokeRecord(stroke);
        if (validLength + payload.length + 12 > MAX_FILE_BYTES) throw new IOException("Notebook file is too large");
        try (RandomAccessFile file = new RandomAccessFile(document, "rw")) {
            // A torn final record is never allowed to hide a later valid append.
            file.setLength(validLength);
            file.seek(validLength);
            writeRecord(file, payload);
            file.getFD().sync();
            validLength = file.getFilePointer();
            page = updated;
            return page;
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    private void requireReady() throws IOException {
        if (failed) throw new IOException("Notebook writes stopped after an earlier storage error; reload before retrying");
        if (page == null) throw new IllegalStateException("Load the notebook before writing");
    }

    private NotebookPage publish(NotebookPage updated) throws IOException {
        byte[] snapshot = encodeSnapshot(updated);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create notebook directory");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                DataOutputStream data = new DataOutputStream(output);
                data.writeInt(MAGIC);
                data.writeInt(VERSION);
                writeRecord(data, snapshot);
                data.flush();
                output.getFD().sync();
            }
            // Same-directory rename is atomic. If interrupted between renames, load uses the backup.
            if (document.exists()) {
                if (backup.exists() && !backup.delete()) throw new IOException("Cannot rotate notebook backup");
                if (!document.renameTo(backup)) throw new IOException("Cannot preserve notebook backup");
            }
            if (!temporary.renameTo(document)) {
                if (!document.exists() && backup.exists()) backup.renameTo(document);
                throw new IOException("Cannot publish notebook snapshot");
            }
            validLength = document.length();
            needsSnapshot = false;
            page = updated;
            return page;
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    private static ReadResult read(File source) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            long length = input.length();
            if (length < 8 || length > MAX_FILE_BYTES) throw corrupt("Invalid notebook file size");
            if (input.readInt() != MAGIC || input.readInt() != VERSION) throw corrupt("Unsupported notebook file header");
            ReadState state = new ReadState();
            long valid = input.getFilePointer();
            boolean incomplete = false;
            while (input.getFilePointer() < length) {
                long remaining = length - input.getFilePointer();
                if (remaining < 8) { incomplete = true; break; }
                int size = input.readInt();
                if (input.readInt() != ~size) throw corrupt("Notebook record length check failed");
                if (size <= 0 || size > MAX_RECORD_BYTES) throw corrupt("Invalid notebook record size");
                if (length - input.getFilePointer() < (long) size + 4) { incomplete = true; break; }
                byte[] payload = new byte[size];
                input.readFully(payload);
                int storedCrc = input.readInt();
                if (checksum(payload) != storedCrc) throw corrupt("Notebook record checksum failed");
                decode(payload, state);
                valid = input.getFilePointer();
            }
            if (!state.hasSnapshot) throw corrupt("Notebook has no complete initial snapshot");
            NotebookPage loaded = NotebookPage.restored(state.width, state.height, state.template,
                                                       state.strokes, state.pointCount);
            return new ReadResult(loaded, valid, incomplete);
        } catch (IllegalArgumentException error) {
            throw new CorruptDocumentException("Invalid notebook data", error);
        }
    }

    private static void decode(byte[] payload, ReadState state) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        int type = input.readUnsignedByte();
        if (type == SNAPSHOT) {
            if (state.hasSnapshot) throw corrupt("Unexpected second notebook snapshot");
            int width = input.readInt(), height = input.readInt();
            validateDimensions(width, height, true);
            int template = input.readUnsignedByte();
            if (template >= NotebookPage.Template.values().length) throw corrupt("Unknown paper template");
            int count = input.readInt();
            if (count < 0 || count > MAX_STROKES) throw corrupt("Invalid stroke count");
            // Every stroke needs at least its style/count and one point before allocating a list.
            if ((long) count * 36 > input.available()) throw corrupt("Truncated snapshot stroke list");
            state.width = width;
            state.height = height;
            state.template = NotebookPage.Template.values()[template];
            for (int i = 0; i < count; i++) {
                NotebookPage.Stroke stroke = readStroke(input);
                validateStroke(width, height, stroke);
                state.pointCount += stroke.points.size();
                if (state.pointCount > MAX_TOTAL_POINTS) throw corrupt("Notebook point limit exceeded");
                state.strokes.add(stroke);
            }
            state.hasSnapshot = true;
        } else if (type == APPEND_STROKE) {
            if (!state.hasSnapshot || state.width == 0) throw corrupt("Stroke appears before initialized snapshot");
            NotebookPage.Stroke stroke = readStroke(input);
            validateStroke(state.width, state.height, stroke);
            if (state.strokes.size() >= MAX_STROKES || state.pointCount + stroke.points.size() > MAX_TOTAL_POINTS) {
                throw corrupt("Notebook point/stroke limit exceeded");
            }
            state.strokes.add(stroke);
            state.pointCount += stroke.points.size();
        } else {
            throw corrupt("Unknown notebook record type");
        }
        if (input.available() != 0) throw corrupt("Unexpected bytes in notebook record");
    }

    private static NotebookPage.Stroke readStroke(DataInputStream input) throws IOException {
        int penType = input.readInt();
        float width = input.readFloat();
        int color = input.readInt();
        int count = input.readInt();
        if (count < 1 || count > MAX_POINTS_PER_STROKE || (long) count * POINT_BYTES > input.available()) {
            throw corrupt("Invalid stroke point count");
        }
        ArrayList<NotebookPage.Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new NotebookPage.Point(input.readFloat(), input.readFloat(), input.readLong(), input.readFloat()));
        }
        return new NotebookPage.Stroke(penType, width, color, points);
    }

    private static void validateDimensions(int width, int height, boolean allowEmpty) throws IOException {
        if (allowEmpty && width == 0 && height == 0) return;
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw corrupt("Invalid notebook dimensions");
        }
    }

    private static void validateStroke(NotebookPage bounds, NotebookPage.Stroke stroke) throws IOException {
        validateStroke(bounds.width, bounds.height, stroke);
    }

    private static void validateStroke(int width, int height, NotebookPage.Stroke stroke) throws IOException {
        if (stroke == null) throw new IllegalArgumentException("Missing stroke");
        if (width <= 0 || height <= 0) throw corrupt("Stroke in unsized notebook");
        for (NotebookPage.Point point : stroke.points) {
            if (point.x < 0 || point.y < 0 || point.x > width || point.y > height) {
                throw corrupt("Pen point is outside logical page bounds");
            }
        }
    }

    private static byte[] encodeSnapshot(NotebookPage page) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(SNAPSHOT);
        output.writeInt(page.width);
        output.writeInt(page.height);
        output.writeByte(page.template.ordinal());
        output.writeInt(page.strokes.size());
        for (NotebookPage.Stroke stroke : page.strokes) writeStroke(output, stroke);
        output.flush();
        return bytes.toByteArray();
    }

    private static byte[] encodeStrokeRecord(NotebookPage.Stroke stroke) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(APPEND_STROKE);
        writeStroke(output, stroke);
        output.flush();
        return bytes.toByteArray();
    }

    private static void writeStroke(DataOutputStream output, NotebookPage.Stroke stroke) throws IOException {
        output.writeInt(stroke.penType);
        output.writeFloat(stroke.width);
        output.writeInt(stroke.color);
        output.writeInt(stroke.points.size());
        for (NotebookPage.Point point : stroke.points) {
            output.writeFloat(point.x);
            output.writeFloat(point.y);
            output.writeLong(point.time);
            output.writeFloat(point.pressure);
        }
    }

    private static void writeRecord(DataOutputStream output, byte[] payload) throws IOException {
        output.writeInt(payload.length);
        output.writeInt(~payload.length);
        output.write(payload);
        output.writeInt(checksum(payload));
    }

    private static void writeRecord(RandomAccessFile output, byte[] payload) throws IOException {
        output.writeInt(payload.length);
        output.writeInt(~payload.length);
        output.write(payload);
        output.writeInt(checksum(payload));
    }

    private static int checksum(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        return (int) crc.getValue();
    }

    private static CorruptDocumentException corrupt(String message) { return new CorruptDocumentException(message); }

    private static final class ReadState {
        int width, height, pointCount;
        boolean hasSnapshot;
        NotebookPage.Template template;
        final List<NotebookPage.Stroke> strokes = new ArrayList<>();
    }

    private static final class ReadResult {
        final NotebookPage page;
        final long validLength;
        final boolean incompleteTail;
        ReadResult(NotebookPage page, long validLength, boolean incompleteTail) {
            this.page = page;
            this.validLength = validLength;
            this.incompleteTail = incompleteTail;
        }
    }
}
