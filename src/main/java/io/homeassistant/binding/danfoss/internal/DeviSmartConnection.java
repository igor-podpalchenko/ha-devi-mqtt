package io.homeassistant.binding.danfoss.internal;

import java.io.IOException;
import java.io.InputStream;

import  jakarta.xml.bind.DatatypeConverter;

import io.homeassistant.binding.danfoss.internal.protocol.Dominion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sonic_amiga.opensdg.java.PeerConnection;

public class DeviSmartConnection extends PeerConnection {
    private final Logger logger = LoggerFactory.getLogger(DeviSmartConnection.class);

    private SDGPeerConnector m_Handler;

    DeviSmartConnection(SDGPeerConnector handler) {
        m_Handler = handler;
    }

    @Override
    protected void onError(Throwable t) {
        m_Handler.setOfflineStatus(t);
    }

    @Override
    protected void onDataReceived(InputStream stream) {
        int offset = 0;
        int length;
        byte[] data;

        try {
            length = stream.available();
            data = new byte[length];
            stream.read(data);
        } catch (IOException e) {
            logger.warn("Failed to read input data: {}", e.toString());
            return;
        }

        /*
         * For some reason the first data packet from the thermostat actually
         * consists of many merged messages. It looks like nothing forbids this
         * to be done at any moment. Also this suggests that garbage zero byte
         * in the beginning of this bunch could be a buffering bug.
         */
        while (length >= Dominion.Packet.HeaderSize) {
            Dominion.Packet pkt = new Dominion.Packet(data, offset);
            int packetLen = pkt.getLength();

            if (packetLen > length) {
                // Packet header specifies more bytes than we have. The packet is clearly malformed.
                logger.warn("Malformed data at position {}; size exceeds buffer", offset);
                logger.warn(DatatypeConverter.printHexBinary(data));
                break; // Drop the rest of data and continue
            }

            m_Handler.handlePacket(pkt);

            offset += packetLen;
            length -= packetLen;
        }
    }
}
