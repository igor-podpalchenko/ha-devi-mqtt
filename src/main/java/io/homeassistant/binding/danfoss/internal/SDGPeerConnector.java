package io.homeassistant.binding.danfoss.internal;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import io.homeassistant.binding.danfoss.internal.protocol.Dominion;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sonic_amiga.opensdg.java.Connection;
import io.github.sonic_amiga.opensdg.java.GridConnection;
import io.github.sonic_amiga.opensdg.java.SDG;

public class SDGPeerConnector {

    private final ExecutorService singleThread = Executors.newSingleThreadExecutor();
    private ISDGPeerHandler thingHandler;
    private ScheduledExecutorService scheduler;
    private Logger logger = LoggerFactory.getLogger(SDGPeerConnector.class);
    private byte[] peerId;
    private DeviSmartConnection connection;
    private @Nullable Future<?> reconnectReq;
    private @Nullable Future<?> watchdog;
    private long lastPacket = 0;

    SDGPeerConnector(ISDGPeerHandler handler, ScheduledExecutorService scheduler) {
        this.thingHandler = handler;
        this.scheduler = scheduler;
    }

    public void initialize(String peerIdStr) {
        logger.trace("initialize()");

        GridConnectionKeeper.AddUser();

        peerId = SDGUtils.ParseKey(peerIdStr);
        if (peerId == null) {
            thingHandler.reportStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "Peer ID is not set");
            return;
        }

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        reportStatus(ThingStatus.UNKNOWN);

        connection = new DeviSmartConnection(this);

        watchdog = scheduler.scheduleAtFixedRate(() -> {
            if (connection == null || connection.getState() != Connection.State.CONNECTED) {
                return;
            }
            if (System.currentTimeMillis() - lastPacket > 30000) {
                logger.warn("Device is inactive during 30 seconds, reconnecting");
                thingHandler.reportStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Communication timeout");
                singleThread.execute(() -> {
                    if (connection == null) {
                        return; // We are being disposed
                    }
                    connection.close();
                    scheduleReconnect();
                });
            } else if (System.currentTimeMillis() - lastPacket > 15000) {
                logger.warn("Device is inactive during 15 seconds, sending PING");
                thingHandler.ping();
            }
        }, 10, 10, TimeUnit.SECONDS);

        connect();
    }

    public void dispose() {
        logger.trace("dispose()");

        singleThread.execute(() -> {
            DeviSmartConnection conn = connection;
            connection = null; // This signals we are being disposed

            Future<?> reconnect = reconnectReq;
            if (reconnect != null) {
                reconnect.cancel(false);
                reconnectReq = null;
            }

            Future<?> wd = watchdog;
            if (wd != null) {
                wd.cancel(false);
                watchdog = null;
            }

            if (conn != null) {
                conn.close();
            }
        });

        GridConnectionKeeper.RemoveUser();
    }

    private void connect() {
        // In order not to mess up our connection state we need to make sure
        // that any two calls are never running concurrently. We use
        // singleThreadExecutorService for this purpose
        singleThread.execute(() -> {
            if (connection == null) {
                return; // Stale Reconnect request from deleted/disabled Thing
            }

            try {
                GridConnection grid = GridConnectionKeeper.getConnection();

                logger.info("Connecting to peer {}", SDG.bin2hex(peerId));
                connection.connectToRemote(grid, peerId, Dominion.ProtocolName);
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
                setOfflineStatus(e);
                return;
            }

            connection.asyncReceive();
            setOnlineStatus();
        });
    }

    public void setOnlineStatus() {
        logger.info("Connection established");

        if (connection != null) {
            reportStatus(ThingStatus.ONLINE);
        }
    }

    public void setOfflineStatus(Throwable t) {
        String reason = t.getMessage();

        if (reason == null) {
            // Some Throwables might not have a reason
            if (t instanceof ClosedChannelException) {
                reason = "Peer not connected";
            } else if (t instanceof TimeoutException) {
                reason = "Communication timeout";
            } else {
                reason = t.toString();
            }
        }

        logger.warn("Device went offline: {}", reason);

        if (connection != null) {
            thingHandler.reportStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, reason);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        reconnectReq = scheduler.schedule(() -> {
            connect();
        }, 10, TimeUnit.SECONDS);
    }

    public void Send(byte[] data) {
        // Cache "connection" in order to avoid possible race condition
        // with dispose() zeroing it between test and usage
        DeviSmartConnection conn = connection;

        if (conn == null || conn.getState() != Connection.State.CONNECTED) {
            // Avoid "Failed to send data" warning if the connection hasn't been
            // connected yet. This may happen as OpenHAB sends REFRESH request for
            // every item right after the Thing has been initialized; it doesn't wait
            // for the Thing to go online.
            return;
        }

        try {
            conn.sendData(data);
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("Failed to send data: {}", e.toString());
        }
    }

    public void SendPacket(Dominion.Packet pkt) {
        Send(pkt.getBuffer());
    }

    public void handlePacket(Dominion.Packet pkt) {
        lastPacket = System.currentTimeMillis();
        thingHandler.handlePacket(pkt);
    }

    public void setTemperature(int msgClass, int msgCode, Command command) {
        double newTemperature;

        if (command instanceof DecimalType) {
            newTemperature = ((DecimalType) command).doubleValue();
        } else if (command instanceof QuantityType) {
            @SuppressWarnings("unchecked")
            QuantityType<Temperature> celsius = ((QuantityType<Temperature>) command).toUnit(SIUnits.CELSIUS);
            if (celsius == null) {
                return;
            }
            newTemperature = celsius.doubleValue();
        } else {
            sendRefresh(msgClass, msgCode, command);
            return;
        }

        SendPacket(new Dominion.Packet(msgClass, msgCode, newTemperature));
    }

    public void sendRefresh(int msgClass, int msgCode, Command command) {
        if (command instanceof RefreshType) {
            SendPacket(new Dominion.Packet(msgClass, msgCode));
        }
    }

    private void reportStatus(@NonNull ThingStatus status) {
        thingHandler.reportStatus(status, ThingStatusDetail.NONE, null);
    }
}
