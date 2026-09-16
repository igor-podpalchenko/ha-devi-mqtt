package io.homeassistant.devi.mqtt.service;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.homeassistant.binding.danfoss.internal.GridConnectionKeeper;
import io.homeassistant.binding.danfoss.internal.SDGPeerConnector;

/**
 * Last line of defence. The old bridge could freeze for days with the container
 * still "Up". This thread checks that every thermostat session keeps making
 * progress, aborts connection attempts that overrun, and — if something is truly
 * stuck — dumps all threads and halts the JVM so Docker restarts the container.
 */
final class BridgeWatchdog implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(BridgeWatchdog.class);
    private static final long PERIOD_MS = 15_000;
    private static final long STATUS_EVERY_MS = 30 * 60_000;

    private final List<SDGPeerConnector> sessions;
    private long lastStatus = System.currentTimeMillis();

    BridgeWatchdog(List<SDGPeerConnector> sessions) {
        this.sessions = sessions;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(PERIOD_MS);
                check(System.currentTimeMillis());
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                logger.error("Watchdog error", t);
            }
        }
    }

    private void check(long now) {
        for (SDGPeerConnector s : sessions) {
            String problem = s.watchdogCheck(now);
            if (problem != null) {
                fatal(problem);
            }
        }

        if (now - lastStatus >= STATUS_EVERY_MS) {
            lastStatus = now;
            logger.info("Status: grid {} | {}", GridConnectionKeeper.isConnected() ? "connected" : "DISCONNECTED",
                    sessions.stream().map(s -> s.describeState(now)).collect(Collectors.joining(", ")));
        }
    }

    /**
     * Writes straight to the stderr file descriptor (the Java streams may be the
     * very thing that is stuck), then halts: a clean shutdown could block on the
     * same lock. Docker's restart policy brings the bridge back.
     */
    static void fatal(String reason) {
        try {
            PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true);
            err.println("FATAL: " + reason + " — dumping threads and restarting the bridge");
            for (ThreadInfo ti : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
                err.print(ti);
            }
            err.flush();
        } finally {
            Runtime.getRuntime().halt(3);
        }
    }
}
