package io.homeassistant.devi.mqtt.service;

import java.util.HashMap;
import java.util.Map;
import io.homeassistant.binding.danfoss.internal.DeviRegHandler;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.openhab.core.thing.ChannelUID;

public class CommandMediator implements Mediator {

    private Map<String, DeviRegHandler> deviRegHandlers = new HashMap<>();

    public void addDeviRegHandler(String handlerId, DeviRegHandler deviRegHandler) {
        deviRegHandlers.put(handlerId, deviRegHandler);
    }

    @Override
    public void notify(Object sender, InputCommand event) {
        if (sender instanceof MqttCallback) {
            handleMQTTMessage(event);
        } else if (sender instanceof DeviRegHandler) {
            // Handle events from DeviRegHandler if needed
        }
    }

    private void handleMQTTMessage(InputCommand inputCommand) {
        DeviRegHandler handler = deviRegHandlers.get(inputCommand.deviceId);
        if (handler != null) {
            handler.handleCommand(inputCommand.sensorName, inputCommand.payload);
        }
    }
}

