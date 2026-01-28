package io.homeassistant.devi.mqtt.service;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQTTService {
    private static final Logger logger = LoggerFactory.getLogger(MQTTService.class);

    private String broker;
    private String commandsTopic = "devi/command/#"; // Subscribing topic
    private String username;
    private String password;
    private Mediator inputCommandMediator;
    private MqttClient client;
    private String statePublishPrefix =  "devi/state/%s/";

    private MqttConnectOptions options;

    public MQTTService(String broker, String port, String username, String password) {
        this.broker = "tcp://" + broker + ":" + port;
        this.username = username;
        this.password = password;
    }

    private InputCommand getInputCommand(String topicFullPath, String payload) {
        String[] parts = topicFullPath.split("/");
        int length = parts.length;

        if (length != 4) { // devi/command/10159384/sensor_heating_state
            throw new IllegalArgumentException("Input string does not have enough parts");
        }

        InputCommand inputCommand = new InputCommand();

        inputCommand.deviceId = parts[length - 2];
        inputCommand.sensorName = parts[length - 1];
        inputCommand.topicFullPath = topicFullPath;
        inputCommand.payload = payload;

        return inputCommand;
    }

    public void registerMediator(Mediator inputCommandMediator) {
        this.inputCommandMediator = inputCommandMediator;
    }

    public void start() {
        try {
            client = new MqttClient(broker, MqttClient.generateClientId(), new MemoryPersistence());
            options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setUserName(username);
            options.setPassword(password.toCharArray());

            // Set a connection lost callback
            client.setCallback(new MqttCallbackWithCommand());

            // Initial connection
            connect();

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void SendSensorData(String sensorId, String sensorName, String sensorValue, boolean setRetain) {

        if (client.isConnected()) {
            try {
                String topic = String.format(statePublishPrefix, sensorId) + sensorName;
                MqttMessage message = new MqttMessage(sensorValue.getBytes());
                message.setQos(1);
				if (setRetain)
					message.setRetained(true);

                // Publish message
                client.publish(topic, message);
                logger.debug("Message published to topic {}: {}", topic, sensorValue);
            } catch (MqttException e) {
                logger.warn("Failed to publish sensor data to topic {}", String.format(statePublishPrefix, sensorId) + sensorName, e);
            }
        }
    }

    public void SendDiscoveryMessage(String topic, String payload) {

        if (client.isConnected()) {
            try {
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);
                message.setRetained(true);

                // Publish message
                client.publish(topic, message);
            } catch (MqttException e) {
                logger.warn("Failed to publish discovery message to topic {}", topic, e);
            }
        }
    }

    private void connect() {
        final int maxAttempts = 12;
        int attempts = 0;
        while (attempts < maxAttempts) {
            try {
                client.connect(options);
                logger.info("Connected to broker");
                return;
            } catch (MqttException e) {
                logger.warn("Failed to connect to broker; retrying", e);
                attempts++;
                // Retry connection after a delay
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        logger.error("Failed to connect to broker after {} attempts", maxAttempts);
    }

    private void subscribeCommands() {
        if (client == null || !client.isConnected()) {
            return;
        }
        try {
            client.subscribe(commandsTopic);
        } catch (MqttException e) {
            logger.warn("Failed to subscribe to commands topic {}", commandsTopic, e);
        }
    }

    public class MqttCallbackWithCommand implements MqttCallbackExtended {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            subscribeCommands();
        }

        @Override
        public void connectionLost(Throwable cause) {
            logger.warn("Connection lost. Reconnecting...", cause);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String payload = new String(message.getPayload());
            InputCommand inputCmd;
            try {
                inputCmd = getInputCommand(topic, payload);
            } catch (IllegalArgumentException e) {
                logger.debug("Ignoring unexpected topic: {}", topic);
                return;
            }

            logger.debug(inputCmd.toString());

            try {
                if (inputCommandMediator != null)
                    inputCommandMediator.notify(this, inputCmd);
            } catch (Exception e) {
                logger.warn("Failed to process MQTT command for topic {}", topic, e);
            }

            //System.out.println("Message received on topic " + topic + ": " + payload);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // No action needed for this example
        }
    }
}
