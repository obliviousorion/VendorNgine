package com.festival.vendorengine.exception;

/**
 * Checked exception thrown when a stall ID is looked up but has not been
 * registered in the application's stall map.
 *
 * <p>This is a <em>checked</em> exception because a missing stall ID is a
 * recoverable, expected-in-normal-operation condition: the caller (typically
 * {@code OrderController.transitionStatus}) must explicitly decide whether to
 * retry, log the incident, or surface the error to the UI. Callers must not
 * silently swallow it.
 *
 * <p>Design choice rationale: see Section 5 of the architecture document.
 */
public class StallNotFoundException extends Exception {

    public StallNotFoundException(String stallId) {
        super("No stall registered with id: " + stallId);
    }
}
