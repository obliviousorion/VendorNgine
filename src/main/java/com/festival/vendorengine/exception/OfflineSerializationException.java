package com.festival.vendorengine.exception;

/**
 * Checked exception thrown when the application state cannot be serialized to
 * or deserialized from the offline backup file ({@code offline_stalls.ser}).
 *
 * <p>This is a <em>checked</em> exception because offline serialization failure
 * is a recoverable, expected-in-normal-operation condition: the caller must
 * explicitly decide whether to retry, log the incident, or surface a warning to
 * the UI so that the operator is aware that the failover backup was not written.
 * Callers must not silently swallow it.
 *
 * <p>Design choice rationale: see Section 5 of the architecture document.
 */
public class OfflineSerializationException extends Exception {

    public OfflineSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
