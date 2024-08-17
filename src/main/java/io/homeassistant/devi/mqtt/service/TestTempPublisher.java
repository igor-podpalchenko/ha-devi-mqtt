package io.homeassistant.devi.mqtt.service;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Random;

public class TestTempPublisher {

    private static final String BROKER = "tcp://nas.local:1883"; // Change this to your MQTT broker address
    private static final String TOPIC_TEMP_FLOOR = "devi/state/0fdc761a357c62412592b0ffef0fa884a975c15d542787b6971e3789dd77b826/temperature_floor"; // Change this to your desired topic
    private static final String TOPIC_TEMP_ROOM = "devi/state/0fdc761a357c62412592b0ffef0fa884a975c15d542787b6971e3789dd77b826/temperature_room"; // Change this to your desired topic
    private static final String COMMANDS_TOPIC = "devi/command/#"; // Topic for subscribing
    private static final double MIN_TEMP = 10.0; // Minimum temperature
    private static final double MAX_TEMP = 40.0; // Maximum temperature
    private static final int INTERVAL_MS = 1000; // 1 second

    private static void MQTTTestOld() {
        try {
            MqttClient client = new MqttClient(BROKER, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            client.connect(options);

            // Publisher thread
            Thread publisherThread = new Thread(() -> {
                Random random = new Random();

                while (true) {
                    try {
                        // Generate a random temperature value with one decimal place
                        double roomTemp = MIN_TEMP + (MAX_TEMP - MIN_TEMP) * random.nextDouble();
                        String roomTempFormatted = String.format("%.1f", roomTemp);

                        double floorTemp = MIN_TEMP + (MAX_TEMP - MIN_TEMP) * random.nextDouble();
                        String floorTempFormatted = String.format("%.1f", floorTemp);

                        // Create payload
                        MqttMessage message = new MqttMessage(roomTempFormatted.getBytes());
                        message.setQos(1);

                        // Publish message
                        client.publish(TOPIC_TEMP_ROOM, message);
                        System.out.println("Message published: " + roomTempFormatted);

                        MqttMessage floorMessage = new MqttMessage(floorTempFormatted.getBytes());
                        message.setQos(1);

                        client.publish(TOPIC_TEMP_FLOOR, floorMessage);
                        System.out.println("Message published: " + floorTempFormatted);



                        // Wait for 1 second
                        Thread.sleep(INTERVAL_MS);
                    } catch (MqttException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });

            // Subscriber thread
            Thread subscriberThread = new Thread(() -> {
                try {
                    client.subscribe(COMMANDS_TOPIC, (topic, msg) -> {
                        String payload = new String(msg.getPayload());
                        System.out.println("Message received on topic " + topic + ": " + payload);
                    });
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            });

            // Start both threads
            publisherThread.start();
            subscriberThread.start();

        } catch (MqttException e) {
            e.printStackTrace();
        }

    }
}
