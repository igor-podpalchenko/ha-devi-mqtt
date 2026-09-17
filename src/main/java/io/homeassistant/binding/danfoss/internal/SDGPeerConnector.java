package io.homeassistant.binding.danfoss.internal;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.homeassistant.binding.danfoss.internal.BridgeSettings.Mode;
import io.homeassistant.binding.danfoss.internal.protocol.Dominion;

/**
 * Manages the sessions with ONE thermostat.
 *
 * The previous implementation kept a permanent session, reconnected every few
 * seconds, reused one connection object across attempts and blocked on a grid
 * request that never timed out. Measured consequences on a 16-thermostat
 * installation: the Danfoss app could not hold a session on any thermostat
 * (sharing the house became impossible), and the bridge itself froze regularly
 * while its container stayed "Up".
 *
 * Policy now:
 * <ul>
 * <li>POLL (default): connect, receive the state dump (~0.3 s), apply queued
 * commands, disconnect. One thermostat client slot is used ~3 s every few
 * minutes instead of permanently.</li>
 * <li>The thermostat pushes its client count while a session is open. As soon as
 * it reports another client (the app), the session ends and the thermostat is
 * left alone for a quiet period.</li>
 * <li>"Thermostat full" (relay code 4), "not on the grid" and timeouts back off
 * exponentially instead of retrying every few seconds.</li>
 * <li>Commands from Home Assistant are queued and applied at the next session,
 * which they bring forward when allowed.</li>
 * <li>Reachability reported to Home Assistant means "heard from recently", not
 * "a TCP session is open right now", so it does not flap between polls.</li>
 * </ul>
 *
 * Threading: every state change runs on one serial {@link SessionScheduler}.
 * Blocking connection attempts run on a shared pool and post their result back.
 * I/O callbacks only copy data and post it. Nothing here ever blocks the socket
 * threads, and nothing writes to System.out/err.
 */
public class SDGPeerConnector {

    enum Phase {
        IDLE,
        CONNECTING,
        CONNECTED
    }

    /** What a failed connection attempt says about the thermostat. */
    enum Outcome {
        /** Answered, but already has two clients (relay code 4). Reachable. */
        BUSY,
        /** Not on the grid, relay or handshake failure. */
        UNREACHABLE,
        /** The Danfoss grid itself failed; says nothing about the thermostat. */
        GRID,
        /** Anything unexpected (a bug); treated like UNREACHABLE. */
        ERROR
    }

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final int MDG_CLASS = 3; // DeviSmart.MsgClass.MDG
    /** Relay refusal text of opensdg_java (see {@link #classify}). */
    private static final String REFUSED_BY_PEER = "Connection refused by peer: ";
    /** Relay code for "the peer already serves the maximum number of clients". */
    private static final int FORWARD_PEER_TIMEOUT = 4;
    private static final long HEARTBEAT_MS = 10_000;
    private static final long EXECUTOR_STUCK_MS = 120_000;
    private static final long YIELD_LINGER_MS = 1_000;
    /** The whole state dump arrives within ~0.3 s: leave for the app once it has been quiet this long. */
    private static final long YIELD_SETTLE_MS = 300;

    /**
     * Messages the thermostat pushes on its own while a session is open. They prove
     * the session is alive but say nothing about the state dump being complete, so
     * they are ignored when waiting for it to settle. Measured 2026-09-14: after the
     * dump (all registers, within 0.4 s) the thermostat sends SYSTEM_TIME,
     * SYSTEM_TIME_ISVALID and NVM_RUNTIME_STATS every second; thermostats with a
     * flaky cloud link also repeat their MDG connection status. Without this, no
     * poll session ever reached its "2 s of silence" and all ran to the 30 s cap.
     */
    private static final java.util.Set<Integer> PERIODIC_CODES = java.util.Set.of(
            29236, // SYSTEM_TIME_ISVALID
            29237, // SYSTEM_TIME
            29264, // NVM_RUNTIME_STATS
            29271, // SYSTEM_MDG_CONNECT_PROGRESS
            29825, // MDG_CONNECTED_TO_SERVER
            30473); // MDG_SERVER_DISCONNECT_COUNT

    private static volatile @Nullable ExecutorService defaultConnectPool;

    private final Logger logger = LoggerFactory.getLogger(SDGPeerConnector.class);
    private final ISDGPeerHandler thingHandler;
    private final Random random = new Random();

    // ---- collaborators: replaced by configure() before initialize() ----
    private BridgeSettings cfg = BridgeSettings.fromEnv();
    private @Nullable SessionScheduler exec;
    private @Nullable Executor connectPool;
    private Function<byte[], PeerLink> linkFactory = DeviSmartConnection::new;
    private String label = "thermostat";
    private long initialDelayMs = 0;

    // ---- state: owned by `exec`, except the volatile fields read by the watchdog ----
    private byte @Nullable [] peerId;
    private boolean started;
    private boolean disposed;
    private boolean gridUser;
    private volatile Phase phase = Phase.IDLE;
    private volatile long phaseSince;
    private volatile @Nullable PeerLink link;
    private volatile long heartbeat;
    private volatile int abortedGeneration = -1;
    private volatile int generation;
    /** When the blocking connect actually started on the pool (0 while still queued). */
    private volatile long connectRunningSince;

    private long established;
    /** Any packet: proof that the session is alive. */
    private long lastPacket;
    /** A packet that is not a periodic push: the dump (or a command echo) is still arriving. */
    private long lastMeaningful;
    private long lastSend;
    private long attemptStartedAt;
    private int packets;
    private int commandsApplied;
    private int lastCount = -1;
    private boolean reachableReportedThisSession;
    private boolean yieldRequested;
    private long yieldRequestedAt;
    private long yieldCloseAt;
    private boolean sendFailed;

    private final LinkedHashMap<String, String> pending = new LinkedHashMap<>();
    private long notBefore;
    private int busyStreak;
    private int failStreak;
    private long lastContact;
    private @Nullable Boolean reportedOnline;
    private @Nullable String reportedKind;

    private SessionScheduler.@Nullable Cancellable nextAttempt;
    private long nextAttemptAt;
    private SessionScheduler.@Nullable Cancellable tickTimer;
    private SessionScheduler.@Nullable Cancellable beatTimer;

    SDGPeerConnector(ISDGPeerHandler handler, @Nullable ScheduledExecutorService unused) {
        this.thingHandler = handler;
    }

    // ------------------------------------------------------------------ configuration

    /** Must be called before {@link #initialize(String)}. Uses the real grid link. */
    public void configure(BridgeSettings settings, SessionScheduler scheduler, Executor pool) {
        configure(settings, scheduler, pool, DeviSmartConnection::new);
    }

    /** Must be called before {@link #initialize(String)}. */
    public void configure(BridgeSettings settings, SessionScheduler scheduler, Executor pool,
            Function<byte[], PeerLink> factory) {
        this.cfg = settings;
        this.exec = scheduler;
        this.connectPool = pool;
        this.linkFactory = factory;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setInitialDelayMs(long delayMs) {
        this.initialDelayMs = Math.max(0, delayMs);
    }

    public static synchronized ExecutorService sharedConnectPool(int threads) {
        ExecutorService pool = defaultConnectPool;
        if (pool == null) {
            AtomicInteger n = new AtomicInteger();
            pool = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "devi-connect-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
            defaultConnectPool = pool;
        }
        return pool;
    }

    // ------------------------------------------------------------------ lifecycle

    public void initialize(String peerIdStr) {
        peerId = SDGUtils.ParseKey(peerIdStr);
        if (peerId == null) {
            thingHandler.reportStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Peer ID is not set");
            return;
        }

        if (exec == null) {
            // Standalone use (e.g. the pairing tool): private executor, shared connect pool
            exec = new ExecutorSessionScheduler("devi-" + label);
        }
        if (connectPool == null) {
            connectPool = sharedConnectPool(cfg.maxParallelConnects);
        }

        GridConnectionKeeper.AddUser();
        gridUser = true;

        SessionScheduler e = exec;
        e.execute(() -> {
            started = true;
            beat();
            scheduleAttemptIn(initialDelayMs);
        });
    }

    public void dispose() {
        SessionScheduler e = exec;
        if (e == null) {
            return;
        }
        try {
            e.execute(this::doDispose);
        } catch (RejectedExecutionException ex) {
            doDispose();
        }
    }

    /**
     * Closes the session (freeing the thermostat slot at once) without waiting.
     *
     * @return released when done, so several sessions can be closed in parallel
     */
    public CountDownLatch disposeAsync() {
        CountDownLatch done = new CountDownLatch(1);
        SessionScheduler e = exec;
        if (e == null) {
            done.countDown();
            return done;
        }
        try {
            e.execute(() -> {
                doDispose();
                done.countDown();
            });
        } catch (RejectedExecutionException ex) {
            doDispose();
            done.countDown();
        }
        return done;
    }

    private void doDispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        cancel(nextAttempt);
        nextAttempt = null;
        cancel(beatTimer);
        beatTimer = null;
        if (phase != Phase.IDLE) {
            endSession("bridge stopping");
        }
        if (gridUser) {
            gridUser = false;
            GridConnectionKeeper.RemoveUser();
        }
    }

    private void beat() {
        heartbeat = now();
        if (!disposed) {
            beatTimer = sched().schedule(this::beat, HEARTBEAT_MS);
        }
    }

    // ------------------------------------------------------------------ attempts

    private void scheduleAttemptIn(long delayMs) {
        scheduleAttemptAt(now() + Math.max(0, delayMs));
    }

    private void scheduleAttemptAt(long at) {
        if (disposed) {
            return;
        }
        SessionScheduler.Cancellable planned = nextAttempt;
        if (planned != null) {
            if (nextAttemptAt <= at) {
                return; // an earlier attempt is already planned
            }
            planned.cancel();
        }
        nextAttemptAt = at;
        nextAttempt = sched().schedule(this::attempt, at - now());
    }

    private void attempt() {
        nextAttempt = null;
        if (disposed || phase != Phase.IDLE) {
            return;
        }
        long now = now();
        if (now < notBefore) {
            scheduleAttemptAt(notBefore);
            return;
        }
        checkStale(now);

        final int gen = ++generation;
        final PeerLink l = linkFactory.apply(Objects.requireNonNull(peerId));
        link = l;
        attemptStartedAt = now;
        connectRunningSince = 0;
        setPhase(Phase.CONNECTING);

        try {
            Objects.requireNonNull(connectPool).execute(() -> {
                Throwable error = null;
                try {
                    if (gen == generation) {
                        // The watchdog times the attempt from here, not from when it was
                        // queued behind other thermostats' attempts.
                        connectRunningSince = now();
                    }
                    l.connect();
                } catch (Throwable t) {
                    error = t;
                }
                final Throwable result = error;
                try {
                    sched().execute(() -> onConnectResult(gen, l, result));
                } catch (RejectedExecutionException ex) {
                    l.close(); // bridge shutting down
                }
            });
        } catch (RejectedExecutionException ex) {
            link = null;
            setPhase(Phase.IDLE);
            scheduleAttemptIn(cfg.gridRetryMs);
        }
    }

    private void onConnectResult(int gen, PeerLink l, @Nullable Throwable error) {
        if (gen != generation || disposed || phase != Phase.CONNECTING) {
            l.close();
            return;
        }
        long now = now();
        if (error == null && gen == abortedGeneration) {
            error = new TimeoutException("connection attempt aborted after " + (now - attemptStartedAt) / 1000 + " s");
        }
        if (error != null) {
            link = null;
            l.close();
            setPhase(Phase.IDLE);
            onAttemptFailed(classify(error), error, now);
            return;
        }

        established = now;
        lastPacket = now;
        lastMeaningful = now;
        lastSend = now;
        packets = 0;
        commandsApplied = 0;
        lastCount = -1;
        reachableReportedThisSession = false;
        yieldRequested = false;
        yieldCloseAt = 0;
        setPhase(Phase.CONNECTED);

        try {
            l.startReceiving(new LinkListener(gen));
        } catch (RuntimeException e) {
            endSession(null);
            onSessionLostEarly("could not start receiving: " + e, now);
            return;
        }
        tickTimer = sched().schedule(this::tick, cfg.tickMs);
    }

    private void onAttemptFailed(Outcome outcome, Throwable error, long now) {
        String why = describe(error);
        long delay;

        switch (outcome) {
            case BUSY:
                busyStreak++;
                failStreak = 0;
                lastContact = now; // it answered: reachable, only full
                delay = jitter(backoff(cfg.busyBackoffMs, busyStreak));
                report(true, "busy", "thermostat full (two clients already connected)");
                logger.info("[{}] thermostat full (two clients already connected, app in use?), next try at {}",
                        label, hhmm(now + delay));
                break;
            case GRID:
                delay = jitter(cfg.gridRetryMs);
                logger.info("[{}] Danfoss grid unavailable ({}), next try at {}", label, why, hhmm(now + delay));
                break;
            case ERROR:
                logger.warn("[{}] unexpected connection error", label, error);
                // fall through
            case UNREACHABLE:
            default:
                failStreak++;
                busyStreak = 0;
                delay = jitter(backoff(cfg.offlineBackoffMs, failStreak));
                if (failStreak >= 2) {
                    report(false, "unreachable", why);
                }
                logger.info("[{}] unreachable ({}), attempt {} failed, next try at {}", label, why, failStreak,
                        hhmm(now + delay));
                break;
        }

        notBefore = now + delay;
        scheduleAttemptAt(notBefore);
        checkStale(now);
    }

    /**
     * What a failure says about the thermostat.
     *
     * opensdg_java 1.0.0 reports both refusals as a plain {@link java.rmi.RemoteException}
     * with a fixed text, and {@code ForwardRequest} re-wraps it in an
     * {@link ExecutionException} carrying the same message, so the message is the
     * only reliable signal:
     * <ul>
     * <li>"Connection refused by peer: 4" — FORWARD_PEER_TIMEOUT, i.e. the
     * thermostat already serves its two clients (the Danfoss app is connected).
     * It answered, so it is reachable.</li>
     * <li>"Connection refused by peer: n" — any other relay refusal.</li>
     * <li>"Connection refused by grid: n" — the grid has no such peer online.</li>
     * </ul>
     */
    static Outcome classify(Throwable t) {
        String message = messageOf(t);
        if (message.contains(REFUSED_BY_PEER)) {
            return message.endsWith(REFUSED_BY_PEER + FORWARD_PEER_TIMEOUT) ? Outcome.BUSY : Outcome.UNREACHABLE;
        }
        if (message.contains("Connection refused by grid:")) {
            return Outcome.UNREACHABLE;
        }
        if (t instanceof GridUnavailableException || message.contains("Grid communication timeout")
                || message.contains("Grid is not connected") || message.contains("Grid connection error")) {
            return Outcome.GRID;
        }
        if (t instanceof IOException || t instanceof TimeoutException || t instanceof ExecutionException
                || t instanceof InterruptedException) {
            return Outcome.UNREACHABLE;
        }
        return Outcome.ERROR;
    }

    /** The message of the exception or, when it has none, of the cause it wraps. */
    private static String messageOf(@Nullable Throwable t) {
        for (Throwable e = t; e != null; e = e.getCause()) {
            String m = e.getMessage();
            if (m != null) {
                return m;
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ session

    private final class LinkListener implements PeerLink.Listener {
        private final int gen;

        LinkListener(int gen) {
            this.gen = gen;
        }

        @Override
        public void onData(byte[] data) {
            post(() -> handleData(gen, data));
        }

        @Override
        public void onClosed(Throwable cause) {
            post(() -> onLinkClosed(gen, cause));
        }
    }

    private void handleData(int gen, byte[] data) {
        if (gen != generation || phase != Phase.CONNECTED) {
            return;
        }
        long now = now();
        lastPacket = now;

        if (logger.isDebugEnabled()) {
            logger.debug("[{}] +{} ms: {} bytes: {}", label, now - established, data.length, summarize(data));
        }

        int offset = 0;
        int length = data.length;

        // The first buffer of a session holds many merged packets
        while (length >= Dominion.Packet.HeaderSize) {
            Dominion.Packet pkt = new Dominion.Packet(data, offset);
            int packetLen = pkt.getLength();

            if (packetLen > length) {
                logger.warn("[{}] malformed data at offset {} (packet exceeds buffer), rest dropped", label, offset);
                break;
            }

            packets++;
            if (!PERIODIC_CODES.contains(pkt.getMsgCode())) {
                lastMeaningful = now;
            }
            try {
                thingHandler.handlePacket(pkt);
            } catch (RuntimeException e) {
                logger.warn("[{}] could not decode message class {} code {}: {}", label, safeClass(pkt),
                        safeCode(pkt), e.toString());
            }
            if (gen != generation) {
                return; // the handler ended the session
            }

            offset += packetLen;
            length -= packetLen;
        }

        if (packets > 0 && !reachableReportedThisSession) {
            reachableReportedThisSession = true;
            lastContact = now;
            busyStreak = 0;
            failStreak = 0;
            // No timestamp in the status text: it is only republished when the state
            // changes, so a time there would freeze. The time has its own sensor.
            report(true, "connected", cfg.mode == Mode.POLL ? "polled" : "session open");
            thingHandler.reportLastContact(Instant.ofEpochMilli(now));
        }
    }

    /** Called by the thing handler when the thermostat reports how many clients it serves (including us). */
    public void onConnectionCount(int count) {
        lastCount = count;
        if (phase != Phase.CONNECTED || !cfg.yieldToApp || count < 2 || yieldRequested) {
            return;
        }
        yieldRequested = true;
        yieldRequestedAt = now();
        logger.info("[{}] {} clients connected: the Danfoss app is using this thermostat, stepping aside for {} min",
                label, count, cfg.yieldQuietMs / 60000);
    }

    private void tick() {
        tickTimer = null;
        if (phase != Phase.CONNECTED || disposed) {
            return;
        }
        long now = now();
        long age = now - established;
        long silence = now - lastPacket;
        long quiet = now - lastMeaningful;
        boolean settled = packets > 0 && quiet >= cfg.settleMs;

        if (yieldRequested) {
            if (!pending.isEmpty() && (settled || now - yieldRequestedAt >= cfg.firstDataTimeoutMs)) {
                // The app is there, but Home Assistant is waiting for these: apply them
                // while we have the session, then leave right after the echo.
                flushCommands();
                yieldCloseAt = now() + YIELD_LINGER_MS;
            }
            // Leave once the dump is complete (it can span several buffers) and any
            // command echo had its second; never linger with the app for long.
            boolean done = pending.isEmpty() && quiet >= YIELD_SETTLE_MS
                    && (yieldCloseAt == 0 || now >= yieldCloseAt);
            if (done || age >= cfg.maxSessionMs) {
                endSessionForApp();
                return;
            }
        } else if (!pending.isEmpty() && (settled || (packets == 0 && age >= cfg.firstDataTimeoutMs))) {
            flushCommands();
        } else if (cfg.mode == Mode.POLL) {
            if (packets == 0 && age >= cfg.firstDataTimeoutMs) {
                endSession(null);
                onSessionLostEarly("no data " + age / 1000 + " s after connecting", now);
                return;
            }
            if (settled && pending.isEmpty()) {
                endSession(null);
                onPollDone(now, null);
                return;
            }
            if (age >= cfg.maxSessionMs) {
                endSession(null);
                onPollDone(now, "session limit reached");
                return;
            }
        } else {
            if (silence >= cfg.silenceTimeoutMs) {
                endSession(null);
                onSessionLostEarly("no data for " + silence / 1000 + " s", now);
                return;
            }
            if (silence >= cfg.pingMs && now - lastSend >= cfg.pingMs) {
                thingHandler.ping();
                lastSend = now;
            }
            if (packets > 0) {
                lastContact = now;
            }
        }

        tickTimer = sched().schedule(this::tick, cfg.tickMs);
    }

    private void onPollDone(long now, @Nullable String note) {
        failStreak = 0;
        busyStreak = 0;
        long delay = jitter(cfg.pollMs);
        notBefore = now + Math.min(cfg.commandDelayMs, delay);
        logger.info("[{}] poll ok: {} values in {} ms{}{}{}, next at {}", label, packets, now - established,
                commandsApplied > 0 ? ", " + commandsApplied + " command(s) applied" : "",
                lastCount > 0 ? ", clients=" + lastCount : "", note != null ? " (" + note + ")" : "",
                hhmm(now + delay));
        scheduleAttemptIn(delay);
    }

    private void endSessionForApp() {
        long now = now();
        int values = packets;
        endSession(null);
        lastContact = now;
        busyStreak = 0;
        failStreak = 0;
        notBefore = now + jitterUp(cfg.yieldQuietMs);
        report(true, "busy", "Danfoss app connected");
        logger.info("[{}] left for the app after {} values{}, back at {}", label, values,
                commandsApplied > 0 ? " and " + commandsApplied + " command(s)" : "", hhmm(notBefore));
        scheduleAttemptAt(notBefore);
    }

    private void onLinkClosed(int gen, Throwable cause) {
        if (gen != generation || phase != Phase.CONNECTED) {
            return;
        }
        long now = now();
        boolean hadData = packets > 0;
        endSession(null);

        if (yieldRequested) {
            lastContact = now;
            notBefore = now + jitterUp(cfg.yieldQuietMs);
            report(true, "busy", "Danfoss app connected");
            scheduleAttemptAt(notBefore);
            return;
        }
        if (hadData && pending.isEmpty() && cfg.mode == Mode.POLL) {
            // The dump was already received: this poll is complete anyway
            onPollDone(now, "closed by the thermostat");
            return;
        }
        onSessionLostEarly(describe(cause), now);
    }

    private void onSessionLostEarly(String why, long now) {
        failStreak++;
        long delay = jitter(backoff(cfg.dropRetryMs, failStreak));
        notBefore = now + delay;
        if (failStreak >= 2 && packets == 0) {
            report(false, "unreachable", why);
        }
        logger.info("[{}] session lost ({}), next try at {}", label, why, hhmm(notBefore));
        scheduleAttemptAt(notBefore);
    }

    private void endSession(@Nullable String reason) {
        PeerLink l = link;
        link = null;
        generation++; // late callbacks from this link are now ignored
        cancel(tickTimer);
        tickTimer = null;
        setPhase(Phase.IDLE);
        if (l != null) {
            l.close();
        }
        if (reason != null) {
            logger.info("[{}] session closed: {}", label, reason);
        }
    }

    // ------------------------------------------------------------------ commands

    /** Queues a Home Assistant command; the latest value per channel wins. Thread-safe. */
    public void submitCommand(String channel, String payload) {
        post(() -> {
            if (disposed) {
                return;
            }
            pending.remove(channel);
            pending.put(channel, payload);

            long now = now();
            if (phase == Phase.CONNECTED) {
                if (!yieldRequested && packets > 0 && now - lastPacket >= cfg.settleMs) {
                    flushCommands();
                }
                return; // otherwise tick() applies it once the dump has settled
            }
            if (phase == Phase.IDLE) {
                if (now < notBefore - cfg.commandDelayMs) {
                    logger.info("[{}] {} queued until {} (thermostat busy or unreachable)", label, channel,
                            hhmm(notBefore));
                }
                scheduleAttemptAt(Math.max(now + cfg.commandDelayMs, notBefore));
            }
        });
    }

    private void flushCommands() {
        List<Map.Entry<String, String>> batch = new ArrayList<>(pending.entrySet());
        pending.clear();

        for (Map.Entry<String, String> c : batch) {
            sendFailed = false;
            try {
                thingHandler.applyCommand(c.getKey(), c.getValue());
            } catch (RuntimeException e) {
                logger.warn("[{}] command {} = {} rejected: {}", label, c.getKey(), shorten(c.getValue()),
                        e.toString());
                continue;
            }
            if (sendFailed) {
                pending.putIfAbsent(c.getKey(), c.getValue());
                logger.info("[{}] command {} not sent, kept for the next session", label, c.getKey());
            } else {
                commandsApplied++;
                logger.info("[{}] applied {} = {}", label, c.getKey(), shorten(c.getValue()));
            }
        }
        long now = now();
        lastPacket = now; // wait for the thermostat's echo before closing
        lastMeaningful = now;
        lastSend = now;
    }

    // ------------------------------------------------------------------ sending (thing handler API)

    public void Send(byte[] data) {
        PeerLink l = link;

        if (phase != Phase.CONNECTED || l == null) {
            sendFailed = true;
            logger.debug("[{}] not connected, {} bytes not sent", label, data.length);
            return;
        }

        try {
            l.send(data);
        } catch (Exception e) {
            sendFailed = true;
            logger.info("[{}] send failed: {}", label, e.toString());
        }
    }

    public void SendPacket(Dominion.Packet pkt) {
        Send(pkt.getBuffer());
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

    /** Request for the client count, used as keepalive in PERSISTENT mode. */
    public static Dominion.Packet connectionCountRequest(int msgCode) {
        return new Dominion.Packet(MDG_CLASS, msgCode);
    }

    // ------------------------------------------------------------------ watchdog support

    /**
     * Called periodically from the bridge watchdog thread.
     *
     * @return null when healthy, otherwise why the process should be restarted
     */
    public @Nullable String watchdogCheck(long now) {
        if (!started || disposed) {
            return null;
        }
        if (now - heartbeat > EXECUTOR_STUCK_MS) {
            return label + ": session executor unresponsive for " + (now - heartbeat) / 1000 + " s";
        }
        if (phase == Phase.CONNECTING) {
            long since = connectRunningSince;
            if (since == 0) {
                return null; // still queued behind other attempts
            }
            long running = now - since;
            if (running > 3 * cfg.connectTimeoutMs) {
                return label + ": connection attempt stuck for " + running / 1000 + " s";
            }
            PeerLink l = link;
            if (running > cfg.connectTimeoutMs && l != null && abortedGeneration != generation) {
                abortedGeneration = generation;
                logger.warn("[{}] connection attempt running for {} s, aborting it", label, running / 1000);
                l.close();
                // A grid that stopped answering leaves the request waiting inside the
                // library, and closing the peer connection does not release it.
                // Dropping the grid connection does: every pending request then fails.
                GridConnectionKeeper.dropConnection("a connection attempt overran");
            }
        }
        return null;
    }

    /** One-line state for the periodic status log. */
    public String describeState(long now) {
        StringBuilder sb = new StringBuilder(label).append('=').append(phase.name().toLowerCase());
        if (Boolean.FALSE.equals(reportedOnline)) {
            sb.append("(offline)");
        } else if ("busy".equals(reportedKind)) {
            sb.append("(busy)");
        }
        if (lastContact > 0) {
            sb.append(" seen ").append((now - lastContact) / 60000).append("m ago");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ reachability

    private void report(boolean online, String kind, String detail) {
        if (Objects.equals(reportedOnline, online) && Objects.equals(reportedKind, kind)) {
            return;
        }
        reportedOnline = online;
        reportedKind = kind;
        thingHandler.reportStatus(online ? ThingStatus.ONLINE : ThingStatus.OFFLINE,
                online ? ThingStatusDetail.NONE : ThingStatusDetail.COMMUNICATION_ERROR, kind + ": " + detail);
    }

    private void checkStale(long now) {
        if (Boolean.TRUE.equals(reportedOnline) && lastContact > 0 && now - lastContact > cfg.staleMs) {
            report(false, "stale", "nothing heard for " + (now - lastContact) / 60000 + " min");
        }
    }

    // ------------------------------------------------------------------ helpers

    private void setPhase(Phase p) {
        phase = p;
        phaseSince = now();
    }

    private SessionScheduler sched() {
        return Objects.requireNonNull(exec);
    }

    private long now() {
        return sched().now();
    }

    private void post(Runnable r) {
        try {
            sched().execute(r);
        } catch (RejectedExecutionException ignored) {
            // bridge shutting down
        }
    }

    private static void cancel(SessionScheduler.@Nullable Cancellable c) {
        if (c != null) {
            c.cancel();
        }
    }

    private long backoff(long base, int streak) {
        long d = base;
        for (int i = 1; i < streak && d < cfg.maxBackoffMs; i++) {
            d *= 2;
        }
        return Math.min(d, Math.max(base, cfg.maxBackoffMs));
    }

    private long jitter(long ms) {
        double spread = cfg.jitterPct / 100.0;
        return Math.max(0, Math.round(ms * (1 + (random.nextDouble() * 2 - 1) * spread)));
    }

    private long jitterUp(long ms) {
        return Math.round(ms * (1 + random.nextDouble() * cfg.jitterPct / 100.0));
    }

    private static String hhmm(long epochMs) {
        return HHMM.format(Instant.ofEpochMilli(epochMs));
    }

    private static String describe(@Nullable Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String m = t.getMessage();
        return m != null ? m : t.getClass().getSimpleName();
    }

    private static String shorten(String s) {
        return s.length() > 80 ? s.substring(0, 77) + "..." : s;
    }

    /** "class:code" of each packet in a buffer, for debug logs. */
    private static String summarize(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        int count = 0;
        while (data.length - offset >= Dominion.Packet.HeaderSize) {
            Dominion.Packet pkt = new Dominion.Packet(data, offset);
            int len = pkt.getLength();
            if (len > data.length - offset) {
                break;
            }
            if (count < 40) {
                sb.append(count == 0 ? "" : " ").append(safeClass(pkt)).append(':').append(safeCode(pkt));
            }
            count++;
            offset += len;
        }
        return count + " packets [" + sb + (count > 40 ? " ..." : "") + "]";
    }

    private static String safeClass(Dominion.Packet pkt) {
        try {
            return String.valueOf(pkt.getMsgClass());
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private static String safeCode(Dominion.Packet pkt) {
        try {
            return String.valueOf(pkt.getMsgCode());
        } catch (RuntimeException e) {
            return "?";
        }
    }

    // ---- for tests ----
    Phase phase() {
        return phase;
    }

    int pendingCount() {
        return pending.size();
    }

    long connectRunningSince() {
        return connectRunningSince;
    }
}
