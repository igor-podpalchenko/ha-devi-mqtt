package io.homeassistant.binding.danfoss.internal;

import java.util.Locale;
import java.util.function.Function;

/**
 * Session policy for the DEVIreg Smart thermostats, read once from the process
 * environment (see the README for the list of variables).
 *
 * Why the defaults are what they are (measured on a 16-thermostat installation):
 * <ul>
 * <li>A DEVIreg Smart accepts only TWO simultaneous clients. A third attempt is
 * refused by the relay with FORWARD_PEER_TIMEOUT (code 4).</li>
 * <li>While the bridge held a permanent session, the Danfoss app looped
 * "connect, dropped after ~3 s, reconnect" on all 16 thermostats: the bridge's
 * mere presence made the app unusable (house sharing impossible).</li>
 * <li>Connecting takes ~1 s and the thermostat sends its complete state (all
 * temperatures, setpoints, schedule, energy counters) within ~0.3 s.</li>
 * <li>The thermostat pushes its client count live, so the arrival of the app is
 * visible immediately while a session is open.</li>
 * </ul>
 * Hence the default: short polling sessions that leave the moment another client
 * shows up, and long back-offs when a thermostat is full or unreachable.
 */
public final class BridgeSettings {

    public enum Mode {
        /** Connect, take the state dump, apply queued commands, disconnect. */
        POLL,
        /** Keep the session open (still steps aside when the app connects). */
        PERSISTENT
    }

    public final Mode mode;
    /** Interval between two polls of one thermostat. */
    public final long pollMs;
    /** Random spread applied to every delay, in percent, so thermostats never synchronise. */
    public final int jitterPct;
    /** A dump is complete once the thermostat has been silent this long. */
    public final long settleMs;
    /** A session that produced no data at all after this long is abandoned. */
    public final long firstDataTimeoutMs;
    /** Hard cap on a poll session (normally ~3 s). */
    public final long maxSessionMs;
    /** A connection attempt still running after this long is aborted by the watchdog. */
    public final long connectTimeoutMs;
    /** Leave as soon as the thermostat reports another client (the Danfoss app). */
    public final boolean yieldToApp;
    /** How long to stay away after stepping aside for the app. */
    public final long yieldQuietMs;
    /** First back-off after "thermostat full" (code 4); doubles on repeat. */
    public final long busyBackoffMs;
    /** First back-off after the thermostat could not be reached; doubles on repeat. */
    public final long offlineBackoffMs;
    /** Retry delay when the Danfoss grid itself is unavailable. */
    public final long gridRetryMs;
    /** First retry delay after a session was lost before it delivered anything. */
    public final long dropRetryMs;
    /** Upper bound for every back-off. */
    public final long maxBackoffMs;
    /** Report a thermostat as disconnected when nothing was heard from it for this long. */
    public final long staleMs;
    /** PERSISTENT mode: request data after this much silence. */
    public final long pingMs;
    /** PERSISTENT mode: drop the session after this much silence. */
    public final long silenceTimeoutMs;
    /** How many connection attempts may run at the same time (all thermostats together). */
    public final int maxParallelConnects;
    /** Delay between the first attempts of successive thermostats at start-up. */
    public final long startupStaggerMs;
    /** Minimal delay before an on-demand session, so a burst of commands shares it. */
    public final long commandDelayMs;
    /** Session state machine resolution. */
    public final long tickMs;

    private BridgeSettings(Function<String, String> env) {
        mode = parseMode(env.apply("DEVI_MODE"));
        pollMs = seconds(env, "DEVI_POLL_S", 300);
        jitterPct = (int) Math.min(50, number(env, "DEVI_JITTER_PCT", 10));
        settleMs = number(env, "DEVI_SETTLE_MS", 2000);
        firstDataTimeoutMs = seconds(env, "DEVI_FIRST_DATA_S", 15);
        maxSessionMs = seconds(env, "DEVI_MAX_SESSION_S", 30);
        connectTimeoutMs = seconds(env, "DEVI_CONNECT_TIMEOUT_S", 90);
        yieldToApp = !"0".equals(env.apply("DEVI_YIELD")) && !"false".equalsIgnoreCase(env.apply("DEVI_YIELD"));
        yieldQuietMs = seconds(env, "DEVI_YIELD_QUIET_S", 900);
        busyBackoffMs = seconds(env, "DEVI_BUSY_BACKOFF_S", 600);
        offlineBackoffMs = seconds(env, "DEVI_OFFLINE_BACKOFF_S", 300);
        gridRetryMs = seconds(env, "DEVI_GRID_RETRY_S", 60);
        dropRetryMs = seconds(env, "DEVI_DROP_RETRY_S", 60);
        maxBackoffMs = seconds(env, "DEVI_BACKOFF_MAX_S", 1800);
        staleMs = seconds(env, "DEVI_STALE_S", 3600);
        pingMs = seconds(env, "DEVI_PING_S", 120);
        silenceTimeoutMs = seconds(env, "DEVI_TIMEOUT_S", 600);
        maxParallelConnects = (int) Math.max(1, number(env, "DEVI_PARALLEL_CONNECTS", 4));
        startupStaggerMs = number(env, "DEVI_STAGGER_MS", 3000);
        commandDelayMs = number(env, "DEVI_COMMAND_DELAY_MS", 500);
        tickMs = 250;
    }

    public static BridgeSettings fromEnv() {
        return new BridgeSettings(System::getenv);
    }

    /** For tests: settings from an explicit map-like lookup. */
    public static BridgeSettings from(Function<String, String> env) {
        return new BridgeSettings(env);
    }

    private static Mode parseMode(String v) {
        if (v != null && v.trim().equalsIgnoreCase("persistent")) {
            return Mode.PERSISTENT;
        }
        return Mode.POLL;
    }

    private static long seconds(Function<String, String> env, String name, long def) {
        return number(env, name, def) * 1000L;
    }

    private static long number(Function<String, String> env, String name, long def) {
        String v = env.apply(name);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            long parsed = Long.parseLong(v.trim());
            return parsed > 0 ? parsed : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "mode=%s poll=%ds yield=%s(quiet %ds) busyBackoff=%ds offlineBackoff=%ds maxBackoff=%ds "
                        + "settle=%dms maxSession=%ds connectTimeout=%ds parallel=%d stale=%ds",
                mode, pollMs / 1000, yieldToApp, yieldQuietMs / 1000, busyBackoffMs / 1000,
                offlineBackoffMs / 1000, maxBackoffMs / 1000, settleMs, maxSessionMs / 1000,
                connectTimeoutMs / 1000, maxParallelConnects, staleMs / 1000);
    }
}
