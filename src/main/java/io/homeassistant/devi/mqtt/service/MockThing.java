package io.homeassistant.devi.mqtt.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.homeassistant.binding.danfoss.internal.DeviRegConfiguration;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.common.registry.Identifiable;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.ThingHandler;

import static io.homeassistant.binding.danfoss.internal.DanfossBindingConstants.BINDING_ID;

@NonNullByDefault
public class MockThing implements Thing {

    private String peerId;

    public MockThing(String peerId) {
        this.peerId = peerId;
    }

    private Map<String, String> prorerties = new HashMap<String, String>();
    @Override
    @Nullable
    public String getLabel() {
        return null;
    }

    @Override
    public void setLabel(@Nullable String label) {
        // Do nothing
    }

    @Override
    public List<Channel> getChannels() {
        return Collections.emptyList();
    }

    @Override
    public List<Channel> getChannelsOfGroup(String groupId) {
        return Collections.emptyList();
    }

    @Override
    @Nullable
    public Channel getChannel(String channelId) {
        return null;
    }


    @Override
    @Nullable
    public Channel getChannel(ChannelUID channelUID) {
        return null;
    }

    @Override
    public ThingStatus getStatus() {
        return ThingStatus.UNKNOWN;
    }

    @Override
    public ThingStatusInfo getStatusInfo() {
        return new ThingStatusInfo(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "");
    }


    @Override
    public void setStatusInfo(ThingStatusInfo statusInfo) {
        // Do nothing
    }

    @Override
    public void setHandler(@Nullable ThingHandler handler) {
        // Do nothing
    }

    @Override
    @Nullable
    public ThingHandler getHandler() {
        return null;
    }

    @Override
    @Nullable
    public ThingUID getBridgeUID() {
        return null;
    }

    @Override
    public void setBridgeUID(@Nullable ThingUID thingUID) {

    }


    @Override
    public Configuration getConfiguration() {
        Configuration config = new Configuration();

        Map<String, Object> configMap = new HashMap<>();
        configMap.put(DeviRegConfiguration.PEER_ID, this.peerId);

        config.setProperties(configMap);
        return config;
    }

    @Override
    public ThingUID getUID() {
        return new ThingUID("mock:mock:1");
    }

    @Override
    public ThingTypeUID getThingTypeUID() {
        return new ThingTypeUID("danfoss", "devismart");
    }

    @Override
    public Map<String, String> getProperties() {
        return this. prorerties;
    }

    @Override
    @Nullable
    public String setProperty(String key, @Nullable String value) {
        if(value == null)
            return null;

        if(this.prorerties.containsKey(key)) {
            this.prorerties.replace(key, value);
        }
        else {
            this.prorerties.put(key, value);
        }
        return null;
    }

    @Override
    public void setProperties(Map<String, String> properties) {
        this.prorerties = properties;
    }

    @Override
    @Nullable
    public String getLocation() {
        return null;
    }

    @Override
    public void setLocation(@Nullable String location) {
        // Do nothing
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
