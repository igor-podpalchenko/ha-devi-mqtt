package io.homeassistant.binding.danfoss.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.EOFException;
import java.rmi.RemoteException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;

import io.homeassistant.binding.danfoss.internal.SDGPeerConnector.Outcome;
import io.homeassistant.binding.danfoss.internal.SDGPeerConnector.Phase;
import io.homeassistant.binding.danfoss.internal.protocol.Dominion;

class SDGPeerConnectorTest {

    private static final String PEER = "1111111111111111111111111111111111111111111111111111111111111111";
    private static final long S = 1000;

    /**
     * opensdg_java reports a relay refusal as a plain RemoteException; code 4 is
     * FORWARD_PEER_TIMEOUT, i.e. the thermostat already serves two clients.
     */
    private static RemoteException peerRefused(int code) {
        return new RemoteException("Connection refused by peer: " + code);
    }

    /** The grid has no such peer online. */
    private static RemoteException gridRefused(int code) {
        return new RemoteException("Connection refused by grid: " + code);
    }

    // ------------------------------------------------------------------ fakes

    static final class FakeLink implements PeerLink {
        final long createdAt;
        Exception connectError;
        CountDownLatch block;
        Listener listener;
        volatile boolean closed;
        boolean failSends;
        final List<byte[]> sent = new ArrayList<>();

        FakeLink(long createdAt) {
            this.createdAt = createdAt;
        }

        @Override
        public void connect() throws Exception {
            if (block != null) {
                // Like a connect wedged in the network stack: close() does NOT release it
                if (!block.await(10, TimeUnit.SECONDS)) {
                    throw new TimeoutException("test connect never released");
                }
                if (closed) {
                    throw new AsynchronousCloseException();
                }
            }
            if (connectError != null) {
                throw connectError;
            }
        }

        @Override
        public void startReceiving(Listener l) {
            listener = l;
        }

        @Override
        public void send(byte[] data) throws ClosedChannelException {
            if (failSends) {
                throw new ClosedChannelException();
            }
            sent.add(data);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    static final class FakeHandler implements ISDGPeerHandler {
        SDGPeerConnector connector;
        final List<String> statuses = new ArrayList<>();
        final List<String> applied = new ArrayList<>();

        @Override
        public void reportStatus(ThingStatus status, ThingStatusDetail detail, String description) {
            statuses.add(status + ":" + description);
        }

        @Override
        public void handlePacket(Dominion.Packet pkt) {
            if (pkt.getMsgCode() == 30470) {
                connector.onConnectionCount(Byte.toUnsignedInt(pkt.getByte()));
            }
        }

        @Override
        public void ping() {
            connector.SendPacket(new Dominion.Packet(6, 29298));
        }

        @Override
        public void applyCommand(String channel, String payload) {
            applied.add(channel + "=" + payload);
            connector.SendPacket(new Dominion.Packet(7, 29330, Double.parseDouble(payload)));
        }

        String last() {
            return statuses.isEmpty() ? "" : statuses.get(statuses.size() - 1);
        }
    }

    static final class Rig {
        final FakeScheduler clock = new FakeScheduler();
        final FakeHandler handler = new FakeHandler();
        final List<FakeLink> links = new ArrayList<>();
        final LinkedList<Exception> outcomes = new LinkedList<>();
        final List<Runnable> captured = new ArrayList<>();
        boolean captureConnects;
        CountDownLatch blockConnect;
        final SDGPeerConnector connector;

        Rig(Map<String, String> env) {
            connector = new SDGPeerConnector(handler, null);
            handler.connector = connector;
            Executor pool = task -> {
                if (captureConnects) {
                    captured.add(task);
                } else {
                    task.run();
                }
            };
            connector.configure(BridgeSettings.from(env::get), clock, pool, id -> {
                FakeLink l = new FakeLink(clock.now);
                l.connectError = outcomes.poll();
                l.block = blockConnect;
                links.add(l);
                return l;
            });
            connector.initialize(PEER);
            clock.runDue();
        }

        Rig() {
            this(Map.of());
        }

        FakeLink link() {
            return links.get(links.size() - 1);
        }

        void deliver(byte[] data) {
            link().listener.onData(data);
            clock.runDue();
        }
    }

    static byte[] packet(int cls, int code, byte... payload) {
        byte[] b = new byte[4 + payload.length];
        b[0] = (byte) cls;
        b[1] = (byte) (code & 0xff);
        b[2] = (byte) (code >> 8);
        b[3] = (byte) payload.length;
        System.arraycopy(payload, 0, b, 4, payload.length);
        return b;
    }

    /** A state dump as sent on connection: room temperature, client count, heating state. */
    static byte[] dump(int clients) {
        byte[] temp = packet(6, 29299, (byte) 0x3C, (byte) 0x09);
        byte[] count = packet(3, 30470, (byte) clients);
        byte[] heating = packet(5, 29240, (byte) 1);
        byte[] all = new byte[temp.length + count.length + heating.length];
        System.arraycopy(temp, 0, all, 0, temp.length);
        System.arraycopy(count, 0, all, temp.length, count.length);
        System.arraycopy(heating, 0, all, temp.length + count.length, heating.length);
        return all;
    }

    // ------------------------------------------------------------------ poll mode

    @Test
    void pollSessionEndsOnceTheDumpHasSettled() {
        Rig r = new Rig();
        assertEquals(1, r.links.size());
        assertEquals(Phase.CONNECTED, r.connector.phase());

        r.deliver(dump(1));
        assertTrue(r.handler.last().startsWith("ONLINE:connected"), r.handler.last());

        r.clock.advance(1 * S);
        assertFalse(r.link().closed, "must wait for the dump to settle");
        r.clock.advance(1500);
        assertTrue(r.link().closed, "session must end ~2 s after the last packet");
        assertEquals(Phase.IDLE, r.connector.phase());

        r.clock.advance(265 * S);
        assertEquals(1, r.links.size(), "next poll not before 300 s - 10 %");
        r.clock.advance(70 * S);
        assertEquals(2, r.links.size(), "next poll not after 300 s + 10 %");
    }

    /** SYSTEM_TIME, SYSTEM_TIME_ISVALID and NVM_RUNTIME_STATS, as pushed every second by a real thermostat. */
    static byte[] clockTick() {
        byte[] time = packet(5, 29237, (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6);
        byte[] valid = packet(5, 29236, (byte) 1);
        byte[] stats = packet(5, 29264, new byte[17]);
        byte[] all = new byte[time.length + valid.length + stats.length];
        System.arraycopy(time, 0, all, 0, time.length);
        System.arraycopy(valid, 0, all, time.length, valid.length);
        System.arraycopy(stats, 0, all, time.length + valid.length, stats.length);
        return all;
    }

    @Test
    void periodicClockTicksDoNotKeepAPollSessionOpen() {
        // Regression (first deployment, 2026-09-14): every session ran to the 30 s cap
        Rig r = new Rig();
        r.deliver(dump(1));
        for (int i = 0; i < 6 && !r.link().closed; i++) {
            r.clock.advance(1 * S);
            if (!r.link().closed) {
                r.deliver(clockTick());
            }
        }
        assertTrue(r.link().closed, "the session must end ~2 s after the dump despite the ticks");
        assertEquals(Phase.IDLE, r.connector.phase());
    }

    @Test
    void clockTicksStillProveThatAPersistentSessionIsAlive() {
        Rig r = new Rig(Map.of("DEVI_MODE", "persistent", "DEVI_TIMEOUT_S", "600"));
        r.deliver(dump(1));
        for (int i = 0; i < 700; i++) {
            r.clock.advance(1 * S);
            r.deliver(clockTick());
        }
        assertFalse(r.link().closed, "ticks are traffic: no silence timeout");
    }

    @Test
    void thermostatClosingTheSessionAfterItsDumpIsACompletePoll() {
        Rig r = new Rig();
        r.deliver(dump(1));
        r.link().listener.onClosed(new EOFException("Connection closed by peer"));
        r.clock.runDue();

        assertEquals(Phase.IDLE, r.connector.phase());
        assertTrue(r.handler.last().startsWith("ONLINE"), r.handler.last());
        r.clock.advance(265 * S);
        assertEquals(1, r.links.size(), "a drop after the dump must not trigger a quick retry");
    }

    @Test
    void sessionLostBeforeAnyDataRetriesSoonThenReportsUnreachable() {
        Rig r = new Rig();
        r.link().listener.onClosed(new EOFException("Connection closed by peer"));
        r.clock.runDue();
        long first = r.clock.now;
        assertFalse(r.handler.last().startsWith("OFFLINE"));

        for (int i = 0; i < 100 && r.links.size() < 2; i++) {
            r.clock.advance(1 * S);
        }
        assertEquals(2, r.links.size());
        long retry = r.links.get(1).createdAt - first;
        assertTrue(retry >= 54 * S && retry <= 67 * S, "retry after 60 s ± 10 %: " + retry);

        r.link().listener.onClosed(new EOFException("Connection closed by peer"));
        r.clock.runDue();
        assertTrue(r.handler.last().startsWith("OFFLINE:unreachable"), r.handler.last());
    }

    // ------------------------------------------------------------------ stepping aside for the app

    @Test
    void leavesAtOnceWhenTheThermostatReportsAnotherClient() {
        Rig r = new Rig();
        r.deliver(dump(2));
        r.clock.advance(600);

        assertTrue(r.link().closed, "must leave within half a second of the dump that shows the app");
        assertTrue(r.handler.last().startsWith("ONLINE:busy"), r.handler.last());

        long left = r.clock.now;
        for (int i = 0; i < 2000 && r.links.size() < 2; i++) {
            r.clock.advance(1 * S);
        }
        assertEquals(2, r.links.size());
        long away = r.links.get(1).createdAt - left;
        assertTrue(away >= 900 * S && away <= 991 * S, "quiet period of 900 s + up to 10 %: " + away);
    }

    @Test
    void persistentModeLeavesWhenTheAppConnectsMidSession() {
        Rig r = new Rig(Map.of("DEVI_MODE", "persistent"));
        r.deliver(dump(1));
        r.clock.advance(60 * S);
        assertFalse(r.link().closed, "persistent mode keeps the session");

        r.deliver(packet(3, 30470, (byte) 2));
        r.clock.advance(600);
        assertTrue(r.link().closed);
        assertTrue(r.handler.last().startsWith("ONLINE:busy"));
    }

    @Test
    void queuedCommandIsAppliedBeforeLeavingForTheApp() {
        Rig r = new Rig();
        r.connector.submitCommand("setpoint_comfort", "21.5");
        r.clock.runDue();
        r.deliver(dump(2));
        assertFalse(r.link().closed, "the pending command must be applied first");

        r.clock.advance(2100);
        assertEquals(List.of("setpoint_comfort=21.5"), r.handler.applied);
        assertFalse(r.link().sent.isEmpty());

        r.clock.advance(1100);
        assertTrue(r.link().closed);
        assertEquals(0, r.connector.pendingCount());
    }

    // ------------------------------------------------------------------ back-off

    @Test
    void thermostatFullBacksOffAndDoubles() {
        Rig r = new Rig();
        // the first attempt succeeds; the next three find the thermostat full
        r.deliver(dump(1));
        r.clock.advance(3 * S);
        r.links.clear();
        for (int i = 0; i < 3; i++) {
            r.outcomes.add(peerRefused(4));
        }

        // stop as soon as the 4th attempt (the successful one) starts: later sessions
        // in this test never receive data and would legitimately time out
        for (int i = 0; i < 6000 && r.links.size() < 4; i++) {
            r.clock.advance(1 * S);
        }
        assertEquals(4, r.links.size(), "attempts: " + r.links.size());
        long d1 = r.links.get(1).createdAt - r.links.get(0).createdAt;
        long d2 = r.links.get(2).createdAt - r.links.get(1).createdAt;
        long d3 = r.links.get(3).createdAt - r.links.get(2).createdAt;
        assertTrue(d1 >= 540 * S && d1 <= 660 * S, "first busy back-off " + d1);
        assertTrue(d2 >= 1080 * S && d2 <= 1320 * S, "doubled back-off " + d2);
        assertTrue(d3 >= 1620 * S && d3 <= 1980 * S, "capped back-off " + d3);
        assertTrue(r.handler.statuses.stream().anyMatch(s -> s.startsWith("ONLINE:busy")));
        assertFalse(r.handler.statuses.stream().anyMatch(s -> s.startsWith("OFFLINE")),
                "a full thermostat is reachable, not offline");
    }

    @Test
    void notOnTheGridIsReportedOfflineOnlyAfterTwoFailures() {
        Rig r = new Rig();
        r.deliver(dump(1));
        r.clock.advance(3 * S);
        r.outcomes.add(gridRefused(1));
        r.outcomes.add(gridRefused(1));

        r.clock.advance(335 * S);
        assertEquals(2, r.links.size());
        assertFalse(r.handler.last().startsWith("OFFLINE"), "one failure is not enough");

        r.clock.advance(700 * S);
        assertEquals(3, r.links.size());
        assertTrue(r.handler.statuses.stream().anyMatch(s -> s.startsWith("OFFLINE:unreachable")));
    }

    @Test
    void gridOutageDoesNotChangeReachabilityUntilStale() {
        Rig r = new Rig();
        r.deliver(dump(1));
        r.clock.advance(3 * S);
        int before = r.handler.statuses.size();
        for (int i = 0; i < 200; i++) {
            r.outcomes.add(new GridUnavailableException("no internet"));
        }

        r.clock.advance(1800 * S);
        assertEquals(before, r.handler.statuses.size(), "grid problems say nothing about the thermostat");
        assertTrue(r.links.size() > 20, "grid retries are frequent: " + r.links.size());

        r.clock.advance(2000 * S);
        assertTrue(r.handler.last().startsWith("OFFLINE:stale"), r.handler.last());
    }

    // ------------------------------------------------------------------ commands

    @Test
    void commandBringsTheNextSessionForwardAndIsAppliedAfterTheDump() {
        Rig r = new Rig();
        r.deliver(dump(1));
        r.clock.advance(3 * S);
        assertEquals(Phase.IDLE, r.connector.phase());

        r.clock.advance(10 * S);
        r.connector.submitCommand("setpoint_comfort", "20.5");
        r.clock.advance(600);
        assertEquals(2, r.links.size(), "the command must trigger a session");
        assertTrue(r.handler.applied.isEmpty(), "not before the thermostat state is known");

        r.deliver(dump(1));
        // ticks run every 250 ms from the connection, not from the delivery
        r.clock.advance(2400);
        assertEquals(List.of("setpoint_comfort=20.5"), r.handler.applied);
        assertFalse(r.link().closed, "waits for the echo");
        r.clock.advance(2300);
        assertTrue(r.link().closed);
    }

    @Test
    void latestValuePerChannelWins() {
        Rig r = new Rig();
        r.connector.submitCommand("setpoint_comfort", "19");
        r.connector.submitCommand("setpoint_economy", "17");
        r.connector.submitCommand("setpoint_comfort", "22");
        r.clock.runDue();
        r.deliver(dump(1));
        r.clock.advance(2100);
        assertEquals(List.of("setpoint_economy=17", "setpoint_comfort=22"), r.handler.applied);
    }

    @Test
    void commandWaitsWhileTheThermostatIsFull() {
        Rig r = new Rig();
        r.deliver(dump(1));
        r.clock.advance(3 * S);
        r.outcomes.add(peerRefused(4));
        r.clock.advance(335 * S);
        assertEquals(2, r.links.size());

        r.connector.submitCommand("setpoint_comfort", "21");
        r.clock.advance(450 * S);
        assertEquals(2, r.links.size(), "no attempt during the busy back-off");
        for (int i = 0; i < 300 && r.links.size() < 3; i++) {
            r.clock.advance(1 * S);
        }
        assertEquals(3, r.links.size(), "attempt once the back-off is over");
        r.deliver(dump(1));
        r.clock.advance(2400);
        assertEquals(List.of("setpoint_comfort=21"), r.handler.applied);
    }

    @Test
    void failedSendKeepsTheCommandForTheNextSession() {
        Rig r = new Rig();
        r.link().failSends = true;
        r.connector.submitCommand("setpoint_comfort", "21");
        r.clock.runDue();
        r.deliver(dump(1));
        r.clock.advance(2100);

        assertEquals(List.of("setpoint_comfort=21"), r.handler.applied, "the command was attempted");
        assertEquals(1, r.connector.pendingCount(), "but kept, since it did not go out");
    }

    // ------------------------------------------------------------------ robustness

    @Test
    void lateCallbacksFromAnOldSessionAreIgnored() {
        Rig r = new Rig(Map.of("DEVI_MODE", "persistent"));
        FakeLink first = r.link();
        r.deliver(dump(1));
        first.listener.onClosed(new EOFException("gone"));
        r.clock.runDue();
        r.clock.advance(70 * S);
        assertEquals(2, r.links.size());
        FakeLink second = r.link();
        r.deliver(dump(1));

        first.listener.onData(dump(2));
        first.listener.onClosed(new EOFException("late"));
        r.clock.runDue();

        assertEquals(Phase.CONNECTED, r.connector.phase(), "the new session must be unaffected");
        assertFalse(second.closed);
    }

    @Test
    void queuedAttemptIsNotMistakenForAStuckOne() {
        Rig s = new Rig(Map.of("DEVI_POLL_S", "5"));
        s.deliver(dump(1));
        s.clock.advance(3 * S);
        s.captureConnects = true; // attempts wait in the pool, as behind other thermostats
        s.clock.advance(6 * S);
        assertEquals(Phase.CONNECTING, s.connector.phase());

        s.clock.advance(100 * S);
        assertNull(s.connector.watchdogCheck(s.clock.now), "queued is not stuck");
        assertFalse(s.link().closed, "a queued attempt must not be aborted");
    }

    @Test
    void watchdogAbortsAnOverrunningConnectionAttemptThenDeclaresItFatal() throws Exception {
        Rig s = new Rig(Map.of("DEVI_POLL_S", "5"));
        s.deliver(dump(1));
        s.clock.advance(3 * S);
        CountDownLatch release = new CountDownLatch(1);
        s.blockConnect = release;
        s.captureConnects = true;
        s.clock.advance(6 * S);
        assertEquals(Phase.CONNECTING, s.connector.phase());

        // the attempt starts on a pool thread and hangs inside connect()
        Thread connectThread = new Thread(s.captured.get(s.captured.size() - 1));
        connectThread.start();
        for (int i = 0; i < 200 && s.connector.connectRunningSince() == 0; i++) {
            Thread.sleep(5);
        }
        assertNotEquals(0, s.connector.connectRunningSince());

        s.clock.advance(60 * S);
        assertNull(s.connector.watchdogCheck(s.clock.now));
        assertFalse(s.link().closed);

        s.clock.advance(31 * S);
        assertNull(s.connector.watchdogCheck(s.clock.now), "an overrun is aborted, not fatal");
        assertTrue(s.link().closed, "the overrunning attempt must be closed");

        s.clock.advance(180 * S);
        String fatal = s.connector.watchdogCheck(s.clock.now);
        assertNotNull(fatal, "a connect that ignores close() is fatal");
        assertTrue(fatal.contains("stuck"), fatal);

        // when it finally returns, it counts as a failed attempt
        release.countDown();
        connectThread.join(2000);
        s.clock.runDue();
        assertEquals(Phase.IDLE, s.connector.phase());
    }

    @Test
    void disposeClosesTheSessionAndStopsAttempts() {
        Rig r = new Rig(Map.of("DEVI_MODE", "persistent"));
        r.deliver(dump(1));
        r.connector.dispose();
        r.clock.runDue();
        assertTrue(r.link().closed);
        r.clock.advance(5000 * S);
        assertEquals(1, r.links.size());
    }

    @Test
    void persistentModePingsAfterSilenceAndDropsAfterTimeout() {
        Rig r = new Rig(Map.of("DEVI_MODE", "persistent", "DEVI_PING_S", "120", "DEVI_TIMEOUT_S", "600"));
        r.deliver(dump(1));
        r.clock.advance(125 * S);
        assertFalse(r.link().sent.isEmpty(), "keepalive request after 120 s of silence");
        r.clock.advance(480 * S);
        assertTrue(r.link().closed, "dropped after 600 s without data");
    }

    @Test
    void failuresAreClassified() {
        assertEquals(Outcome.BUSY, SDGPeerConnector.classify(peerRefused(4)));
        assertEquals(Outcome.UNREACHABLE, SDGPeerConnector.classify(gridRefused(1)));
        assertEquals(Outcome.UNREACHABLE, SDGPeerConnector.classify(peerRefused(1)));
        assertEquals(Outcome.GRID, SDGPeerConnector.classify(new ExecutionException("Grid communication timeout",
                new TimeoutException())));
        assertEquals(Outcome.GRID,
                SDGPeerConnector.classify(new ExecutionException("Grid is not connected",
                        new ClosedChannelException())));
        assertEquals(Outcome.GRID, SDGPeerConnector.classify(new GridUnavailableException("x")));
        assertEquals(Outcome.UNREACHABLE, SDGPeerConnector.classify(new TimeoutException()));
        assertEquals(Outcome.UNREACHABLE, SDGPeerConnector.classify(new ExecutionException(new EOFException())));
        assertEquals(Outcome.ERROR, SDGPeerConnector.classify(new IllegalStateException()));
    }
}
