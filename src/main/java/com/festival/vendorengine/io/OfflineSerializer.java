package com.festival.vendorengine.io;

import com.festival.vendorengine.exception.OfflineSerializationException;
import com.festival.vendorengine.model.AppState;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;

/**
 * Saves and restores the full {@link AppState} to/from a binary file using
 * Java's built-in object serialization ({@link ObjectOutputStream} /
 * {@link ObjectInputStream}).
 *
 * <h2>Why Java serialization (Section 8.2)</h2>
 * <p>The entire object graph ({@code AppState → ConcurrentHashMap<String,Stall>
 * → PriorityBlockingQueue<Order> → Order → LineItem}) already implements
 * {@code Serializable}. Writing and reading the whole thing in two method calls
 * gives us atomic, consistent snapshots with no hand-rolled marshalling code.
 * The only correctness trap is {@code PriorityBlockingQueue}'s comparator field —
 * solved by {@code OrderComparator} being a named {@code Serializable} class
 * (Section 6.4).
 *
 * <h2>File path</h2>
 * <p>The no-arg constructor uses the default filename {@value #DEFAULT_FILE_NAME}
 * in the process working directory. A path-aware constructor is provided for
 * testing (avoids polluting the working directory with test artefacts —
 * used with JUnit 5 {@code @TempDir}).
 *
 * <h2>Exception wrapping</h2>
 * <p>{@link OfflineSerializationException} (checked) is always thrown instead of
 * raw {@link IOException} or {@link ClassNotFoundException}. This forces callers
 * to make an explicit decision (retry, log, warn the UI) rather than letting
 * infrastructure errors propagate silently.
 *
 * <p>No javax.swing or java.awt imports — hard MVC constraint (Section 10).
 */
public class OfflineSerializer {

    /** Default file name used when no path is specified. */
    public static final String DEFAULT_FILE_NAME = "offline_stalls.ser";

    private final String filePath;

    /**
     * Constructs a serializer that reads/writes {@value #DEFAULT_FILE_NAME}
     * in the current working directory.
     */
    public OfflineSerializer() {
        this.filePath = DEFAULT_FILE_NAME;
    }

    /**
     * Constructs a serializer that reads/writes the given path.
     * Intended for tests that supply a {@code @TempDir} path.
     *
     * @param path the target file path (may not exist yet for {@link #save})
     */
    public OfflineSerializer(Path path) {
        this.filePath = path.toString();
    }

    /**
     * Constructs a serializer using an explicit path string.
     *
     * @param filePath the target file path string
     */
    public OfflineSerializer(String filePath) {
        this.filePath = filePath;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Serializes {@code state} to the configured file path.
     *
     * <p>Uses try-with-resources so the stream is always closed, even if
     * {@code writeObject} throws.
     *
     * @param state the application snapshot to persist; must not be null
     * @throws OfflineSerializationException if writing fails for any reason
     */
    public void save(AppState state) throws OfflineSerializationException {
        try (ObjectOutputStream oos =
                     new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(state);
        } catch (IOException e) {
            throw new OfflineSerializationException(
                    "Failed to persist offline state to: " + filePath, e);
        }
    }

    /**
     * Deserializes an {@link AppState} from the configured file path.
     *
     * <p>Uses try-with-resources so the stream is always closed, even if
     * {@code readObject} throws.
     *
     * @return the deserialized {@link AppState}
     * @throws OfflineSerializationException if reading or class resolution fails
     */
    public AppState load() throws OfflineSerializationException {
        try {
            // Read all bytes first so the OS file handle is released before
            // ObjectInputStream deserializes — critical on Windows where an open
            // ObjectInputStream holds the file handle and prevents TempDir cleanup.
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath));
            try (ObjectInputStream ois =
                         new ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
                return (AppState) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new OfflineSerializationException(
                    "Failed to restore offline state from: " + filePath, e);
        }
    }

    /**
     * Returns the file path this serializer is configured to use.
     *
     * @return the file path string
     */
    public String getFilePath() {
        return filePath;
    }
}
