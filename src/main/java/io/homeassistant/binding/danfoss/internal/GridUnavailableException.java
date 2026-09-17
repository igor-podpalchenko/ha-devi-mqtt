package io.homeassistant.binding.danfoss.internal;

import java.io.IOException;

/** The control connection to the Danfoss grid could not be established. */
public class GridUnavailableException extends IOException {
    private static final long serialVersionUID = 1L;

    public GridUnavailableException(String message) {
        super(message);
    }

    public GridUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
