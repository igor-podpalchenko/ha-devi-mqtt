package io.homeassistant.binding.danfoss.internal;

import java.time.Instant;

import org.eclipse.jdt.annotation.NonNull;
import io.homeassistant.binding.danfoss.internal.protocol.Dominion;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;

public interface ISDGPeerHandler {

    public void reportStatus(@NonNull ThingStatus status, @NonNull ThingStatusDetail statusDetail, String description);

    public void handlePacket(Dominion.@NonNull Packet pkt);

    public void ping();

    /** Applies a command that was queued until a session with the thermostat was ready. */
    default void applyCommand(String channel, String payload) {
    }

    /** The thermostat answered at this time (start of the latest successful session). */
    default void reportLastContact(Instant when) {
    }
}
