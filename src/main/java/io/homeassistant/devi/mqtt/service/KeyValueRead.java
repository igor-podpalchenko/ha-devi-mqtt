package io.homeassistant.devi.mqtt.service;

@FunctionalInterface
public interface KeyValueRead {
    void onKeyFound(String key, String value);
}
