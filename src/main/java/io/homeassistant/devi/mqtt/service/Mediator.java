package io.homeassistant.devi.mqtt.service;

public interface Mediator {
    void notify(Object sender, InputCommand event);
}
