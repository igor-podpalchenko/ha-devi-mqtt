package io.homeassistant.devi.mqtt.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import io.github.sonic_amiga.opensdg.java.GridConnection;
import io.github.sonic_amiga.opensdg.java.PairingConnection;
import io.github.sonic_amiga.opensdg.java.PeerConnection;
import io.github.sonic_amiga.opensdg.java.SDG;
import io.homeassistant.binding.danfoss.internal.DanfossBindingConfig;
import io.homeassistant.binding.danfoss.internal.DeviRegHandler;
import io.homeassistant.binding.danfoss.internal.GridConnectionKeeper;
import io.homeassistant.binding.danfoss.internal.protocol.DominionConfiguration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class DiscoveryService {

    static {
        // We are in the process of development, so we want to see everything
        System.setProperty("org.slf4j.simplelogger.defaultlog", "info");
    }

    private final static Logger logger = LoggerFactory.getLogger(DiscoveryService.class);


    public static void main(String[] args) throws Exception {
        Discover();
    }

    private static String requestOtp() {

        // Replace with actual logic to request OTP from the user
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter OTP: ");
        return scanner.nextLine();
    }

    public static String getUsernameFromInput(String defaultUsername) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter username (press Enter to use default [" + defaultUsername + "]): ");
        String input = scanner.nextLine();

        if (input.trim().isEmpty()) {
            return defaultUsername;
        } else {
            return input.trim();
        }
    }

    private static void Discover() throws Exception {

        System.out.println("To configure thermostat's access. Download / open Devi application.");
        System.out.println("Go to settings -> Share Home -> OTP code will be generated. Enter this code, when requested.");
        System.out.println("For some reasons, first OTP code usually fails. Click back in mobile app and enter new code.");
        System.out.println("Try entering OTP few times.");

        String defaultUsername = "ha-user";
        String username = getUsernameFromInput(defaultUsername);

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("userName", username);

        // Assuming ConfigurationAdmin instance is available
        ConfigurationAdmin configAdmin = new MockConfigurationAdmin(); // Replace this with actual ConfigurationAdmin instance

        // Calling the update method
        DanfossBindingConfig.update(configMap, configAdmin);

        GridConnectionKeeper.AddUser();
        String userName = DanfossBindingConfig.get().userName;
        String houseName = null;
        GridConnection grid = null;

        // Save the JSON data
        Map<String, Object> jsonMap = new HashMap<>();
        List<Map<String, String>> roomsList = new ArrayList<>();

        try {
            grid = GridConnectionKeeper.getConnection();
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            //GridConnectionKeeper.RemoveUser();
            return;
        }

        byte[] myPeerId = grid.getMyPeerId();
        if (myPeerId == null) {
            //GridConnectionKeeper.RemoveUser();
            return;
        }

        String phonePeerID = SDG.bin2hex(myPeerId);
        logger.debug("phonePeerID: {}", phonePeerID);


        jsonMap.put("privateKey", DanfossBindingConfig.get().privateKey);
        jsonMap.put("peerId", phonePeerID);
        jsonMap.put("userName", userName);


        boolean finished = false;
        do {

            logger.error("Cycle");

            Thread.sleep(500);

            PairingConnection pairing = new PairingConnection();
            String otp;

            boolean pairingSuccessful = false;
            while (!pairingSuccessful) {
                otp = requestOtp();
                try {
                    pairing.pairWithRemote(grid, otp);
                    pairingSuccessful = true;
                } catch (RemoteException e) {
                    logger.error("Connection refused by peer; likely wrong OTP");
                } catch (IOException | InterruptedException | ExecutionException | GeneralSecurityException | TimeoutException e) {
                    logger.error("Pairing failed: {}", e.getMessage());
                }
            }

            byte[] phoneId = pairing.getPeerId();
            pairing.close();

            logger.debug("Pairing successful");

            PeerConnection cfg = new PeerConnection();

            try {
                cfg.connectToRemote(grid, phoneId, "dominion-configuration-1.0");
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Failed to connect to the sender: {}", e.getMessage());
                continue;
            }

            DominionConfiguration.Request request = new DominionConfiguration.Request(userName, phonePeerID);

            String errorStr = null;
            int dataSize = 0;
            int offset = 0;
            byte[] data = null;

            try {
                cfg.sendData((new Gson()).toJson(request).getBytes());

                do {
                    DataInputStream chunk = new DataInputStream(cfg.receiveData());
                    int chunkSize = chunk.available();

                    if (chunkSize > 8) {
                        if (chunk.readInt() == 0) {
                            dataSize = Integer.reverseBytes(chunk.readInt());
                            logger.trace("Chunked mode; full size = {}", dataSize);
                            data = new byte[dataSize];
                            chunkSize -= 8;
                        } else {
                            chunk.reset();
                        }
                    }

                    if (dataSize == 0) {
                        dataSize = chunkSize;
                        logger.trace("Raw mode; full size = {}", dataSize);
                        data = new byte[dataSize];
                    }

                    chunk.read(data, offset, chunkSize);
                    offset += chunkSize;
                } while (offset < dataSize);
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Failed to receive config: {}", e.getMessage());
                continue;
            }

            cfg.close();
            GridConnectionKeeper.RemoveUser();

            if (errorStr != null) {
                continue;
            } else if (data == null) {
                continue;
            }

            JsonReader jsonReader = new JsonReader(new StringReader(new String(data)));
            DominionConfiguration.Response parsedConfig = (new Gson()).fromJson(jsonReader, DominionConfiguration.Response.class);
            houseName = parsedConfig.houseName;
            jsonMap.put("homePeerId", parsedConfig.housePeerId);
            jsonMap.put("houseName", parsedConfig.houseName);

            logger.debug("Received house: {}", houseName);
            finished = true;


            if (parsedConfig.rooms != null) {

                for (DominionConfiguration.Room room : parsedConfig.rooms) {

                    String roomName = room.roomName;
                    String peerId = room.peerId;
                    logger.info("Received DeviSmart thing: {} {}", peerId, roomName);

                    Map<String, String> roomMap = new HashMap<>();
                    roomMap.put("name", room.roomName);
                    roomMap.put("devicePeerID", room.peerId);
                    roomsList.add(roomMap);

                }

                jsonMap.put("rooms", roomsList);
            }

        } while (!finished);


        // CountDownLatch to wait for all async tasks to complete
        CountDownLatch latch = new CountDownLatch(roomsList.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        configMap.put("privateKey", DanfossBindingConfig.get().privateKey);

        System.out.println(
                String.format("Home data, phonePeerID: %s  privateKey: ", jsonMap.get("peerId"), jsonMap.get("privateKey")));

        // Iterate over the list of devices, connect and extract SN
        for (Map<String, String> room : roomsList) {
            for (Map.Entry<String, String> entry : room.entrySet()) {
                if(entry.getKey() != "devicePeerID")
                    continue;

                if(configMap.containsKey("peerId"))
                    configMap.replace("peerId", entry.getValue());
                else
                    configMap.put("peerId", entry.getValue());

                // Create a CompletableFuture for the callback
                CompletableFuture<Void> future = new CompletableFuture<>();
                futures.add(future);


                DanfossBindingConfig.update(configMap, configAdmin);
                DeviRegHandler deviRegHandler = new DeviRegHandler(new MockThing());

                MockThingCallback reportingCallback = new MockThingCallback((key, value) -> {

                    System.out.println("Parsing thermostat data - key: " + key + ", value: " + value);

                    if(!room.containsKey("sys_serial_number") && key == "sys_serial_number") {
                        room.put("serialNumber", value);
                        deviRegHandler.dispose();

                        future.complete(null);  // Complete the CompletableFuture
                        latch.countDown();  // Decrease the latch count
                    }
                });

                deviRegHandler.setCallback(reportingCallback);

                System.out.println(String.format("Connecting to peer: %s", DanfossBindingConfig.get().publicKey));

                deviRegHandler.initialize();

                Thread.sleep(3000);
            }
        }

        System.out.println("Awaiting query completion");

        // Wait for all async tasks to complete
        try {
            latch.await();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }


        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonString = gson.toJson(jsonMap);

        try (FileWriter fileWriter = new FileWriter("devi_config.json")) {
            fileWriter.write(jsonString);
        } catch (IOException e) {
            logger.error("Failed to save JSON data: {}", e.getMessage());
        }

        // Continue with the rest of your code
        System.out.println("Configuration complete. devi_config.json is generated");

        System.exit(0);
    }




}
