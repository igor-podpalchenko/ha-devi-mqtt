package io.homeassistant.devi.mqtt.service;

public class InputCommand {
    public String deviceId;

    public String sensorName;

    public String payload;

    public String topicFullPath;

    public String toString() {
        return  String.format("InputCommand deviceId: %s, sensorName: %s, payload %s", deviceId, sensorName, payload);
    }
}
