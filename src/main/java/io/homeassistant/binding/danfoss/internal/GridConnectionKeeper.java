package io.homeassistant.binding.danfoss.internal;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sonic_amiga.opensdg.java.Connection;
import io.github.sonic_amiga.opensdg.java.GridConnection;

/**
 * The single control connection to the Danfoss grid, shared by every thermostat.
 * The grid connection does not count against a thermostat's two client slots: it
 * only serves to request relay tunnels.
 */
public class GridConnectionKeeper {
    private static final Logger logger = LoggerFactory.getLogger(GridConnectionKeeper.class);

    /** After a failed attempt, callers fail fast for this long instead of queueing 40 s attempts. */
    private static final long RETRY_AFTER_FAILURE_MS = 15_000;

    private static GridConnection g_Conn;
    private static int numUsers = 0;
    private static String privateKey = null;
    private static long lastFailure = 0;
    private static long connectedSince = 0;

    public synchronized static GridConnection getConnection()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        if (privateKey == null) {
            privateKey = DanfossBindingConfig.get().privateKey;
        }
        if (g_Conn == null) {
            // Own ping scheduler (the library's). It used to borrow openHAB's shared
            // "thingHandler" pool, which the watchdog and reconnect tasks of all
            // sixteen thermostats could starve.
            g_Conn = new GridConnection(SDGUtils.ParseKey(privateKey));
        }

        if (g_Conn.getState() == Connection.State.CONNECTED) {
            return g_Conn;
        }

        long now = System.currentTimeMillis();
        if (now - lastFailure < RETRY_AFTER_FAILURE_MS) {
            throw new GridUnavailableException(
                    "Danfoss grid unreachable (last attempt " + (now - lastFailure) / 1000 + " s ago)");
        }

        if (connectedSince != 0) {
            logger.info("Grid connection lost after {} min, reconnecting", (now - connectedSince) / 60000);
            connectedSince = 0;
        }

        try {
            g_Conn.close(); // no-op unless a previous connection was left half-open
            g_Conn.connect(GridConnection.Danfoss);
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException | RuntimeException e) {
            lastFailure = System.currentTimeMillis();
            logger.warn("Cannot connect to the Danfoss grid: {}", e.toString());
            throw new GridUnavailableException("Cannot connect to the Danfoss grid: " + e, e);
        }

        connectedSince = System.currentTimeMillis();
        logger.info("Connected to the Danfoss grid");
        return g_Conn;
    }

    /**
     * A request sent over this grid connection was never answered: the connection is
     * presumably dead even if the socket looks fine. Drop it so the next caller
     * reconnects.
     */
    public static synchronized void reportGridTimeout(GridConnection which) {
        if (which != null && which == g_Conn && g_Conn.getState() != Connection.State.CLOSED) {
            logger.warn("The Danfoss grid stopped answering, dropping the control connection");
            g_Conn.close();
        }
    }

    /**
     * Drops the control connection whatever its apparent state.
     *
     * Used when a connection attempt overran: a grid that accepted the request and
     * never answered leaves the caller waiting inside the library, and closing the
     * peer connection does not release it. Closing the grid connection does — every
     * request still pending on it then fails — so the next attempt starts clean.
     */
    public static synchronized void dropConnection(String reason) {
        if (g_Conn != null && g_Conn.getState() != Connection.State.CLOSED) {
            logger.warn("Dropping the Danfoss grid connection: {}", reason);
            g_Conn.close();
            connectedSince = 0;
            lastFailure = System.currentTimeMillis();
        }
    }

    public static synchronized boolean isConnected() {
        return g_Conn != null && g_Conn.getState() == Connection.State.CONNECTED;
    }

    public static synchronized void UpdatePrivateKey(String newKey) {
        if (g_Conn != null && !newKey.equals(privateKey)) {
            // Will reconnect on demand
            closeConnection();
        }
        privateKey = newKey;
    }

    public static synchronized void AddUser() {
        numUsers++;
    }

    public static synchronized void RemoveUser() {
        if (--numUsers > 0) {
            return;
        }

        if (g_Conn == null) {
            return;
        }

        logger.info("Last user is gone, disconnecting from Danfoss grid");
        closeConnection();
    }

    private static void closeConnection() {
        g_Conn.close();
        g_Conn = null;
        connectedSince = 0;
        logger.info("Grid connection closed");
    }
}
