package io.homeassistant.binding.danfoss.internal;

/**
 * One encrypted session with one thermostat over the Danfoss grid. The real
 * implementation is {@link DeviSmartConnection}; tests use a fake.
 */
public interface PeerLink {

    interface Listener {
        /** Raw Dominion payload, possibly several merged packets. Called on an I/O thread. */
        void onData(byte[] data);

        /** The session broke (remote close, I/O error). Not called after {@link PeerLink#close()}. */
        void onClosed(Throwable cause);
    }

    /** Blocking: grid lookup, relay tunnel and encryption handshake. Bounded by library timeouts. */
    void connect() throws Exception;

    /** Starts delivering data; separate from connect() so no packet can arrive before the caller is ready. */
    void startReceiving(Listener listener);

    void send(byte[] data) throws Exception;

    /** Idempotent and thread-safe; also aborts a connect() in progress. */
    void close();
}
