package io.homeassistant.devi.mqtt.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.MalformedJsonException;
import io.homeassistant.binding.danfoss.internal.DanfossBindingConfig;
import io.homeassistant.binding.danfoss.internal.DeviRegHandler;
import org.apache.commons.text.StringSubstitutor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.RefreshType;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static io.homeassistant.binding.danfoss.internal.DanfossBindingConstants.*;


public class ConsoleRunner {
    static class Template {
        String topic;
        Map<String, Object> template;
    }

    static {
        // We are in the process of development, so we want to see everything
        System.setProperty("org.slf4j.simplelogger.defaultlog", "info");
    }

    private final static Logger logger = LoggerFactory.getLogger(ConsoleRunner.class);

    private static CommandMediator commandMediator;

    public static void main(String[] args) throws Exception {
        Run(args);
    }

    private static MQTTService mqttService;

    private static void Run(String[] args) throws Exception {
        // Parse the command-line arguments
        Map<String, String> params = parseParameters(args);

        // Check if the --help flag was present and print help if needed
        if (params.containsKey("help")) {
            printHelp();
            return;
        }

        logger.info("Starting exporter service");

        // Get file paths from the parsed parameters or use default values
        String autoDiscoveryTemplatesPath = params.getOrDefault("auto-discovery-templates", "auto-discovery-templates");
        String mqttConfigPath = params.getOrDefault("mqtt-config", "mqtt_config.json");
        String deviConfigPath = params.getOrDefault("devi-config", "devi_config.json");

        // Check if config files exist
        File mqttConfigFile = new File(mqttConfigPath);
        File deviConfigFile = new File(deviConfigPath);

        if (!mqttConfigFile.exists()) {
            logger.error("MQTT configuration file not found: " + mqttConfigPath);
            return;
        }

        if (!deviConfigFile.exists()) {
            logger.error("Devi configuration file not found: " + deviConfigPath);
            return;
        }

        Gson gson = new Gson();

        // Read MQTT configuration
        JsonObject mqttConfig = gson.fromJson(new JsonReader(new FileReader(mqttConfigFile)), JsonObject.class);
        String mqttHost = mqttConfig.get("host").getAsString();
        String mqttPort = mqttConfig.get("port").getAsString();
        String mqttUser = mqttConfig.get("user").getAsString();
        String mqttPassword = mqttConfig.get("password").getAsString();

        mqttService = new MQTTService(mqttHost, mqttPort, mqttUser, mqttPassword);
        mqttService.start();

        commandMediator = new CommandMediator();
        mqttService.registerMediator(commandMediator);

        // Read Devi configuration
        JsonObject deviConfig = gson.fromJson(new JsonReader(new FileReader(deviConfigFile)), JsonObject.class);
        String userName = deviConfig.get("userName").getAsString();
        String privateKey = deviConfig.get("privateKey").getAsString();

        int deviceNumber = 0;

        for (JsonElement room : deviConfig.getAsJsonArray("rooms")) {
            String devicePeerID = room.getAsJsonObject().get("devicePeerID").getAsString();
            String deviceSN = room.getAsJsonObject().get("serialNumber").getAsString();

            Map<String, String> valuesMap = new HashMap<>();
            valuesMap.put("deviceSN", deviceSN);
            valuesMap.put("deviceNumber", String.valueOf(deviceNumber++));

            readAndProcessTemplates(autoDiscoveryTemplatesPath, valuesMap);

            HandleThermostat(devicePeerID, userName, privateKey, deviceSN);
        }
    }

    private static Map<String, String> parseParameters(String[] args) {
        Map<String, String> params = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--auto-discovery-templates":
                    if (i + 1 < args.length) {
                        params.put("auto-discovery-templates", args[++i]);
                    } else {
                        logger.error("Missing value for --auto-discovery-templates");
                        params.put("help", "true");
                    }
                    break;
                case "--mqtt-config":
                    if (i + 1 < args.length) {
                        params.put("mqtt-config", args[++i]);
                    } else {
                        logger.error("Missing value for --mqtt-config");
                        params.put("help", "true");
                    }
                    break;
                case "--devi-config":
                    if (i + 1 < args.length) {
                        params.put("devi-config", args[++i]);
                    } else {
                        logger.error("Missing value for --devi-config");
                        params.put("help", "true");
                    }
                    break;
                case "--help":
                    params.put("help", "true");
                    break;
                default:
                    logger.error("Unknown parameter: " + args[i]);
                    params.put("help", "true");
                    break;
            }
        }

        return params;
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar YourApp.jar [options]");
        System.out.println("Options:");
        System.out.println("  --auto-discovery-templates <path>  Path to the auto-discovery templates. Default: auto-discovery-templates");
        System.out.println("  --mqtt-config <path>               Path to the MQTT configuration file. Default: mqtt_config.json");
        System.out.println("  --devi-config <path>               Path to the Devi configuration file. Default: devi_config.json");
        System.out.println("  --help                             Display this help message.");
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void readAndProcessTemplates(String folderPath, Map<String, String> valuesMap) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(folderPath))) {

            paths.filter(Files::isRegularFile)
                    .filter(filePath -> filePath.toString().endsWith(".json"))
                    .forEach(filePath -> {
                try {

                    logger.info(filePath.toString());

                    String content = new String(Files.readAllBytes(filePath));
                    Template template = gson.fromJson(content, Template.class);

                    String topic = StringSubstitutor.replace(template.topic, valuesMap);
                    String payload = StringSubstitutor.replace(gson.toJson(template.template), valuesMap);

                    mqttService.SendDiscoveryMessage(topic, payload);

                } catch (IOException  e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static void HandleThermostat(String devicePeerID, String userName, String privateKey, String deviceSN) {

        // Creating a configuration map
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("privateKey", privateKey);
        configMap.put("peerId", devicePeerID);
        configMap.put("userName", userName);

        // Assuming ConfigurationAdmin instance is available
        ConfigurationAdmin configAdmin = new MockConfigurationAdmin();

        // Calling the update method
        DanfossBindingConfig.update(configMap, configAdmin);

        DeviRegHandler deviRegHandler = new DeviRegHandler(new MockThing());

        commandMediator.addDeviRegHandler(deviceSN, deviRegHandler);

        MockThingCallback reportingCallback = new MockThingCallback((key, value) -> {
            System.out.println("Key: " + key + ", Value: " + value);
            mqttService.SendSensorData(deviceSN, key, value);
        });

        deviRegHandler.setCallback(reportingCallback);

        deviRegHandler.initialize();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        /*
        ChannelUID comfort_temp_uid = new ChannelUID(new ThingUID("cmd", "danfoss","devismart"),  CHANNEL_SETPOINT_COMFORT);
        deviRegHandler.handleCommand(comfort_temp_uid, new DecimalType(13.5));

        ChannelUID manual_temp_uid = new ChannelUID(new ThingUID("cmd", "danfoss","devismart"),  CHANNEL_SETPOINT_MANUAL);
        deviRegHandler.handleCommand(manual_temp_uid, new DecimalType(6.0));

        ChannelUID economy_temp_uid = new ChannelUID(new ThingUID("cmd", "danfoss","devismart"),  CHANNEL_SETPOINT_ECONOMY);
        deviRegHandler.handleCommand(economy_temp_uid, new DecimalType(8.0));

        ChannelUID away_temp_uid = new ChannelUID(new ThingUID("cmd", "danfoss","devismart"),  CHANNEL_SETPOINT_AWAY);
        deviRegHandler.handleCommand(away_temp_uid, new DecimalType(10.0));
         */

        System.out.println("=== Sending command ===");


        // Requesting refresh, thermostat will respond latest value
        ChannelUID refreshUptime = new ChannelUID(new ThingUID("cmd", "danfoss", "devismart"), CHANNEL_ON_TIME_TOTAL);
        deviRegHandler.handleCommand(refreshUptime, RefreshType.REFRESH);

        //ChannelUID brightness_uid = new ChannelUID(new ThingUID("cmd", "danfoss", "devismart"), CHANNEL_BRIGHTNESS);
        //deviRegHandler.handleCommand(brightness_uid, new PercentType(50));

    }
}




