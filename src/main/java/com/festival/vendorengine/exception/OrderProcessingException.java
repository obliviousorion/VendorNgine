package com.festival.vendorengine.exception;

/**
 * Unchecked exception thrown when an order payload cannot be parsed or routed.
 *
 * <p>This is an <em>unchecked</em> ({@link RuntimeException}) exception because it
 * wraps malformed JSON from a corrupt or unexpected stream — a programmer/data error
 * rather than a recoverable runtime condition. Forcing every call site to catch it
 * would add noise without value; however, it must never be silently swallowed: the
 * consumer's catch block must always log it with the original cause chained via
 * {@link #getCause()}.
 *
 * <p>Design choice rationale: see Section 5 of the architecture document.
 */
public class OrderProcessingException extends RuntimeException {

    public OrderProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
