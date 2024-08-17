package io.homeassistant.binding.danfoss.internal;

import org.eclipse.jdt.annotation.NonNull;
import io.homeassistant.binding.danfoss.internal.protocol.Dominion;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;

public interface ISDGPeerHandler {

    public void reportStatus(@NonNull ThingStatus status, @NonNull ThingStatusDetail statusDetail, String description);

    public void handlePacket(Dominion.@NonNull Packet pkt);

    public void ping();
}
