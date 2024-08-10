package io.homeassistant.devi.mqtt.service;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelGroupTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockThingCallback implements ThingHandlerCallback  {

    private KeyValueRead keyValueRead;

    public MockThingCallback(KeyValueRead callback) {
        keyValueRead = callback;
    }

    @Override
    public void stateUpdated(ChannelUID channelUID, State state) {
        //System.out.println(channelUID.getId()+ " " + state.toString());
        keyValueRead.onKeyFound(channelUID.getId(), state.toString());
    }

    @Override
    public void postCommand(ChannelUID channelUID, Command command) {

    }

    @Override
    public void statusUpdated(Thing thing, ThingStatusInfo thingStatusInfo) {

    }

    @Override
    public void thingUpdated(Thing thing) {
        for (String key : thing.getProperties().keySet()) {
            String value = thing.getProperties().get(key);

            keyValueRead.onKeyFound(key, value);
        }
    }

    @Override
    public void validateConfigurationParameters(Thing thing, Map<String, Object> map) {

    }

    @Override
    public void configurationUpdated(Thing thing) {

    }

    @Override
    public void migrateThingType(Thing thing, ThingTypeUID thingTypeUID, Configuration configuration) {

    }

    @Override
    public void channelTriggered(Thing thing, ChannelUID channelUID, String s) {

    }

    @Override
    public ChannelBuilder createChannelBuilder(ChannelUID channelUID, ChannelTypeUID channelTypeUID) {
        return ChannelBuilder.create(new ChannelUID("devi-events"));
    }

    @Override
    public ChannelBuilder editChannel(Thing thing, ChannelUID channelUID) {
        return ChannelBuilder.create(new ChannelUID("devi-events"));
    }

    @Override
    public List<ChannelBuilder> createChannelBuilders(ChannelGroupUID channelGroupUID, ChannelGroupTypeUID channelGroupTypeUID) {
        return new ArrayList<ChannelBuilder>();
    }

    @Override
    public boolean isChannelLinked(ChannelUID channelUID) {
        return false;
    }

    @Override
    public @Nullable Bridge getBridge(ThingUID thingUID) {
        return null;
    }

}
