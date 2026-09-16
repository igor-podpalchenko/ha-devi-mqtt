package io.homeassistant.binding.danfoss.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sonic_amiga.opensdg.java.PeerConnection;
import io.homeassistant.binding.danfoss.internal.protocol.Dominion;

/**
 * One encrypted session with one thermostat, over the Danfoss grid.
 *
 * It is the real {@link PeerLink}: a fresh instance is created for every session,
 * so a callback of an old connection can never be taken for one of the current
 * session. Splitting the received bytes into Dominion packets is done by
 * {@link SDGPeerConnector}, which needs to see the whole buffer to know when a
 * state dump is complete.
 */
public class DeviSmartConnection extends PeerConnection implements PeerLink {
    private final Logger logger = LoggerFactory.getLogger(DeviSmartConnection.class);

    private final byte[] peerId;
    private volatile Listener listener;

    public DeviSmartConnection(byte[] peerId) {
        this.peerId = peerId;
    }

    @Override
    public void connect() throws Exception {
        connectToRemote(GridConnectionKeeper.getConnection(), peerId, Dominion.ProtocolName);
    }

    @Override
    public void startReceiving(Listener l) {
        listener = l;
        asyncReceive();
    }

    @Override
    public void send(byte[] data) throws Exception {
        sendData(data);
    }

    @Override
    public void close() {
        listener = null;
        super.close();
    }

    @Override
    protected void onError(Throwable t) {
        Listener l = listener;
        if (l != null) {
            l.onClosed(t);
        }
    }

    @Override
    protected void onDataReceived(InputStream stream) {
        byte[] data;

        try {
            // Read the full payload; InputStream.available() is not a reliable size
            // for network data.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (stream.available() == 0) {
                    break;
                }
            }
            data = out.toByteArray();
        } catch (IOException e) {
            logger.warn("Failed to read input data: {}", e.toString());
            return;
        }

        Listener l = listener;
        if (l != null && data.length > 0) {
            l.onData(data);
        }
    }
}
