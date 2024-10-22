/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package io.homeassistant.binding.danfoss.internal;

import static io.homeassistant.binding.danfoss.internal.DanfossBindingConstants.*;
import static io.homeassistant.binding.danfoss.internal.protocol.DeviSmart.MsgClass.*;
import static io.homeassistant.binding.danfoss.internal.protocol.DeviSmart.MsgCode.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Map;

import io.homeassistant.devi.mqtt.service.ScheduleManager;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import io.homeassistant.binding.danfoss.internal.protocol.DeviSmart;
import io.homeassistant.binding.danfoss.internal.protocol.DeviSmart.ControlMode;
import io.homeassistant.binding.danfoss.internal.protocol.DeviSmart.ControlState;
import io.homeassistant.binding.danfoss.internal.protocol.DeviSmart.WizardInfo;
import io.homeassistant.binding.danfoss.internal.protocol.Dominion;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DeviRegHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Pavel Fedin - Initial contribution
 */
@NonNullByDefault
public class DeviRegHandler extends BaseThingHandler implements ISDGPeerHandler {

    private final Logger logger = LoggerFactory.getLogger(DeviRegHandler.class);
    private SDGPeerConnector connHandler = new SDGPeerConnector(this, scheduler);
    private byte currentMode = -1;
    private Dominion.@Nullable Version firmwareVer;
    private int firmwareBuild = -1;

    private int readOutputPower = 0;

    private ScheduleManager scheduleManager = new ScheduleManager();

    public DeviRegHandler(Thing thing) {
        super(thing);
    }

    public void handleCommand(String sensorId, String payload) {

        ChannelUID ch = new ChannelUID(new ThingUID("cmd", "danfoss", "devismart"), sensorId);

        switch (sensorId) {
            case CHANNEL_CONTROL_MODE:
                handleCommand(ch, new StringType(payload));
                break;
            case CHANNEL_THERMOSTAT_PRESET:
                if(payload.equals("SET OVERRIDE")) {
                    ChannelUID selectChannelUID = new ChannelUID(new ThingUID("cmd", "danfoss", "devismart"), CHANNEL_CONTROL_MODE);
                    handleCommand(selectChannelUID, new StringType("OVERRIDE"));
                }
                break;
            case CHANNEL_WEEK_SCHEDULE:

                ScheduleManager writeScheduleManager = new ScheduleManager();
                boolean fromJsonSuccess = writeScheduleManager.fromJson(payload);

                //writeScheduleManager.printScheduleHumanReadable();

                Map<String, byte[]> parts = writeScheduleManager.splitScheduleToParts();

                // Clean up the schedule
                sendScheduleUpdateCommand(
                        ScheduleManager.getZeroScheduleWeek1(),
                        ScheduleManager.getZeroScheduleWeek2()
                );
                sendScheduleUpdateCommand(parts.get("firstPart"), parts.get("secondPart"));
                break;
            case CHANNEL_SETPOINT_WARNING:
            case CHANNEL_SETPOINT_COMFORT:
            case CHANNEL_SETPOINT_ECONOMY:
            case CHANNEL_SETPOINT_MANUAL:
            case CHANNEL_SETPOINT_TEMPORARY:
            case CHANNEL_SETPOINT_AWAY:
            case CHANNEL_SETPOINT_ANTIFREEZE:
            case CHANNEL_SETPOINT_MIN_FLOOR:
            case CHANNEL_SETPOINT_MAX_FLOOR:
            case CHANNEL_BRIGHTNESS:
                handleCommand(ch, new DecimalType(payload));
                break;
            case CHANNEL_SETPOINT_MIN_FLOOR_ENABLE:
            case CHANNEL_WINDOW_DETECTION:
            case CHANNEL_FORECAST:
            case CHANNEL_SCREEN_LOCK:
                handleCommand(ch, OnOffType.from(payload));
                break;
            // Read-only channels may send refreshType command
            case CHANNEL_TEMPERATURE_FLOOR:
            case CHANNEL_TEMPERATURE_ROOM:
            case CHANNEL_CONTROL_STATE:
            case CHANNEL_WINDOW_OPEN:
            case CHANNEL_HEATING_STATE:
            case CHANNEL_ON_TIME_7_DAYS:
            case CHANNEL_ON_TIME_30_DAYS:
            case CHANNEL_ON_TIME_TOTAL:
            case CHANNEL_DISCONNECTED:
            case CHANNEL_SHORTED:
            case CHANNEL_OVERHEAT:
            case CHANNEL_UNRECOVERABLE:
                handleCommand(ch, RefreshType.REFRESH);
                break;
            default:
                logger.info(String.format("Command for unknown sensorName: %s", sensorId));
                break;
        }
    }

    private void sendScheduleUpdateCommand(byte[] week, byte[] week2) {

        ByteArrayOutputStream buffer1 = new ByteArrayOutputStream();
        ByteArrayOutputStream buffer2 = new ByteArrayOutputStream();

        try {
            buffer1.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_WEEK, week).getBuffer());
            buffer2.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_WEEK_2, week2).getBuffer());
            connHandler.Send(buffer1.toByteArray());
            connHandler.Send(buffer2.toByteArray());

        } catch (IOException e) {
            //throw new RuntimeException(e);
            logger.error("Error sending schedule update command: {}", e);
        }

    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String ch = channelUID.getId();

        logger.trace("Sending {} = {}", ch, command);

        switch (ch) {
            case CHANNEL_SETPOINT_WARNING:
                connHandler.setTemperature(DOMINION_HEATING, HEATING_LOW_TEMPERATURE_WARNING, command);
                break;
            case CHANNEL_SETPOINT_COMFORT:
                connHandler.setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_COMFORT, command);
                break;
            case CHANNEL_SETPOINT_ECONOMY:
                connHandler.setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_ECONOMY, command);
                break;
            case CHANNEL_SETPOINT_MANUAL:
                connHandler.setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_MANUAL, command);
                break;
            case CHANNEL_SETPOINT_TEMPORARY:
                connHandler.setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_TEMPORARY, command);
                break;
            case CHANNEL_SETPOINT_AWAY:
                connHandler.setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_AWAY, command);
                break;
            case CHANNEL_SETPOINT_ANTIFREEZE:
                connHandler.setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_FROST_PROTECTION, command);
                break;
            case CHANNEL_SETPOINT_MIN_FLOOR:
                connHandler.setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_FLOOR_COMFORT, command);
                break;
            case CHANNEL_SETPOINT_MAX_FLOOR:
                connHandler.setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_MAX_FLOOR, command);
                break;
            case CHANNEL_CONTROL_MODE:
                setMode(command);
                break;
            case CHANNEL_SETPOINT_MIN_FLOOR_ENABLE:
                setSwitch(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_FLOOR_COMFORT_ENABLED, command);
                break;
            case CHANNEL_WINDOW_DETECTION:
                setSwitch(DOMINION_SYSTEM, SYSTEM_WINDOW_OPEN, command);
                break;
            case CHANNEL_FORECAST:
                setSwitch(DOMINION_SYSTEM, SYSTEM_INFO_FORECAST_ENABLED, command);
                break;
            case CHANNEL_SCREEN_LOCK:
                setSwitch(DOMINION_SYSTEM, SYSTEM_UI_SAFETY_LOCK, command);
                break;
            case CHANNEL_BRIGHTNESS:
                setByte(DOMINION_SYSTEM, SYSTEM_UI_BRIGTHNESS, command);
                break;
            // Read-only channels may send refreshType command
            case CHANNEL_TEMPERATURE_FLOOR:
                connHandler.sendRefresh(DOMINION_HEATING, HEATING_TEMPERATURE_FLOOR, command);
                break;
            case CHANNEL_TEMPERATURE_ROOM:
                connHandler.sendRefresh(DOMINION_HEATING, HEATING_TEMPERATURE_ROOM, command);
                break;
            case CHANNEL_CONTROL_STATE:
                connHandler.sendRefresh(DOMINION_SCHEDULER, SCHEDULER_CONTROL_INFO, command);
                break;
            case CHANNEL_WINDOW_OPEN:
                connHandler.sendRefresh(DOMINION_SYSTEM, SYSTEM_INFO_WINDOW_OPEN_DETECTION, command);
                break;
            case CHANNEL_HEATING_STATE:
                connHandler.sendRefresh(DOMINION_SYSTEM, SYSTEM_HEATING_INFO, command);
                break;
            case CHANNEL_ON_TIME_7_DAYS:
                connHandler.sendRefresh(DOMINION_LOGS, LOG_ENERGY_CONSUMPTION_7DAYS, command);
                break;
            case CHANNEL_ON_TIME_30_DAYS:
                connHandler.sendRefresh(DOMINION_LOGS, LOG_ENERGY_CONSUMPTION_30DAYS, command);
                break;
            case CHANNEL_ON_TIME_TOTAL:
                connHandler.sendRefresh(DOMINION_SYSTEM, SYSTEM_RUNTIME_INFO_RELAY_ON_TIME, command);
                break;
            case CHANNEL_DISCONNECTED:
            case CHANNEL_SHORTED:
            case CHANNEL_OVERHEAT:
            case CHANNEL_UNRECOVERABLE:
                connHandler.sendRefresh(DOMINION_SYSTEM, SYSTEM_ALARM_INFO, command);
                break;
        }
    }

    private void setSwitch(int msgClass, int msgCode, Command command) {
        if (command instanceof OnOffType) {
            connHandler.SendPacket(new Dominion.Packet(msgClass, msgCode, command.equals(OnOffType.ON)));
        } else {
            connHandler.sendRefresh(msgClass, msgCode, command);
        }
    }

    private void setByte(int msgClass, int msgCode, Command command) {
        if (command instanceof DecimalType) {
            connHandler.SendPacket(new Dominion.Packet(msgClass, msgCode, ((DecimalType) command).byteValue()));
        } else {
            connHandler.sendRefresh(msgClass, msgCode, command);
        }
    }

    private void setMode(Command command) {
        if (command instanceof StringType) {
            try {
                String cmdString = command.toString();
                // We are going to send more than one packet, let's collect them and send
                // together.
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                // In "special" modes (Off, Pause, Vacation) the thermostat ignores
                // other mode commands; we first need to cancel this mode. We also
                // check if the same mode is requested and just bail out in such a case.
                switch (currentMode) {
                    case ControlState.Configuring:
                    case ControlState.Fatal:
                        return; // I think we cannot do much here

                    case ControlState.Vacation:
                        if (cmdString.equals(CONTROL_MODE_VACATION)) {
                            return;
                        }

                        // Original app resets both scheduled period and PLANNED flag, we do the same.
                        buffer.write(new DeviSmart.AwayPacket(null, null).getBuffer());
                        buffer.write(
                                new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_AWAY_ISPLANNED, false).getBuffer());
                        break;

                    case ControlState.Pause:
                        if (cmdString.equals(CONTROL_MODE_PAUSE)) {
                            return;
                        }

                        buffer.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.FROST_PROTECTION_OFF).getBuffer());
                        break;

                    case ControlState.Off:
                        if (cmdString.equals(CONTROL_MODE_OFF)) {
                            return;
                        }

                        buffer.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.OFF_STATE_OFF).getBuffer());
                        break;

                    case ControlState.AtHomeOverride:
                        if (cmdString.equals(CONTROL_MODE_OVERRIDE)) {
                            return;
                        }

                        buffer.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.TEMPORARY_HOME_OFF).getBuffer());
                        break;
                }

                switch (cmdString) {
                    case CONTROL_MODE_MANUAL:
                        buffer.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.WEEKLY_SCHEDULE_OFF).getBuffer());
                        break;
                    case CONTROL_MODE_OVERRIDE:
                        buffer.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.TEMPORARY_HOME_ON).getBuffer());
                        break;
                    case CONTROL_MODE_SCHEDULE:
                        buffer.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.WEEKLY_SCHEDULE_ON).getBuffer());
                        break;
                    case CONTROL_MODE_VACATION:
                        // In order to enter vacation mode immediately we need to reset
                        // scheduled time period and set PLANNED to true.
                        buffer.write(new DeviSmart.AwayPacket(null, null).getBuffer());
                        buffer.write(
                                new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_AWAY_ISPLANNED, true).getBuffer());
                        break;
                    case CONTROL_MODE_PAUSE:
                        buffer.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.FROST_PROTECTION_ON).getBuffer());
                        break;
                    case CONTROL_MODE_OFF:
                        buffer.write(new Dominion.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.OFF_STATE_ON).getBuffer());
                        break;
                }

                connHandler.Send(buffer.toByteArray());

            } catch (IOException e) {
                // We should never get here
                logger.warn("Error building control mode packet(s): {}", e);
            }
        } else {
            connHandler.sendRefresh(DOMINION_SCHEDULER, SCHEDULER_CONTROL_INFO, command);
        }
    }

    @Override
    public void initialize() {
        DeviRegConfiguration config = getConfigAs(DeviRegConfiguration.class);

        connHandler.initialize(config.peerId);
    }

    @Override
    public void dispose() {
        connHandler.dispose();
    }

    private void reportTemperature(String ch, double temp) {
        logger.trace("Received {} = {}", ch, temp);

        //updateState(ch, new QuantityType<Temperature>(new DecimalType(temp), SIUnits.CELSIUS));
        double roundedTemp = temp; //Math.round(temp * 10.0) / 10.0;
        updateState(ch, new DecimalType(roundedTemp));

    }

    private void reportSwitch(String ch, boolean on) {
        logger.trace("Received {} = {}", ch, on);
        updateState(ch, OnOffType.from(on));
    }

    private void reportDecimal(String ch, long value) {
        logger.trace("Received {} = {}", ch, value);
        updateState(ch, new DecimalType(value));
    }

    private void reportDuration(String ch, int time) {
        logger.trace("Received {} = {}", ch, time);
        //updateState(ch, new QuantityType<Time>(Integer.toUnsignedLong(time), Units.SECOND));
        updateState(ch, new StringType(String.valueOf(time)));
    }


    private String selectSetpointChannelByModeAndState(String mode, String state) {

        switch (mode) {
            case CONTROL_MODE_MANUAL:
                return CHANNEL_SETPOINT_MANUAL;
            case CONTROL_MODE_SCHEDULE:
                switch (state){
                    case "OVERRIDE":
                        return CHANNEL_SETPOINT_TEMPORARY;
                    case "AWAY":
                        return CHANNEL_SETPOINT_ECONOMY;
                    case "HOME":
                        return CHANNEL_SETPOINT_COMFORT;
                    default:
                        return CHANNEL_SETPOINT_ECONOMY;
                }
            case CONTROL_MODE_VACATION:
                    return CHANNEL_SETPOINT_AWAY;
            case CONTROL_MODE_PAUSE:
                return CHANNEL_SETPOINT_ANTIFREEZE;
            case CONTROL_MODE_OVERRIDE:
                return CHANNEL_SETPOINT_TEMPORARY;
            case CONTROL_MODE_OFF:
                    return "OFF";
            default:
                return "UNKNOWN";
        }

    }


    private void reportControlInfo(byte info) {
        // Modes corresponding to states below
        final String[] CONTROL_MODES = { "", CONTROL_MODE_MANUAL, CONTROL_MODE_SCHEDULE, CONTROL_MODE_SCHEDULE,
                CONTROL_MODE_VACATION, "", CONTROL_MODE_PAUSE, CONTROL_MODE_OFF, CONTROL_MODE_OVERRIDE };
        final String[] CONTROL_STATES = { "CONFIGURING", "MANUAL", "HOME", "AWAY", "VACATION", "FATAL", "PAUSE", "OFF",
                "OVERRIDE" };
        String mode, state;

        currentMode = info;

        if (info >= ControlState.Configuring && info <= ControlState.AtHomeOverride) {
            mode = CONTROL_MODES[info];
            state = CONTROL_STATES[info];

            String autoSetpointChannel = selectSetpointChannelByModeAndState(mode, state);
            updateProperty(CHANNEL_ACTIVE_SETPOINT, autoSetpointChannel);
        } else {
            mode = "";
            state = "";
        }

        logger.trace("Received {} = {}", CHANNEL_CONTROL_STATE, state);

        updateState(CHANNEL_CONTROL_MODE, StringType.valueOf(mode));
        updateState(CHANNEL_CONTROL_STATE, StringType.valueOf(state));
    }

    private void reportFirmware() {
        Dominion.Version ver = firmwareVer;

        if (ver != null && firmwareBuild != -1) {
            //Thing.PROPERTY_FIRMWARE_VERSION
            updateProperty("sys_firmware_version", ver.toString() + "." + String.valueOf(firmwareBuild));
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Override
    public void handlePacket(Dominion.Packet pkt) {
        switch (pkt.getMsgCode()) {
            case HEATING_TEMPERATURE_FLOOR:
                reportTemperature(CHANNEL_TEMPERATURE_FLOOR, pkt.getDecimal());
                break;
            case HEATING_TEMPERATURE_ROOM:
                reportTemperature(CHANNEL_TEMPERATURE_ROOM, pkt.getDecimal());
                break;
            case HEATING_LOW_TEMPERATURE_WARNING:
                reportTemperature(CHANNEL_SETPOINT_WARNING, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_COMFORT:
                reportTemperature(CHANNEL_SETPOINT_COMFORT, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_ECONOMY:
                reportTemperature(CHANNEL_SETPOINT_ECONOMY, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_MANUAL:
                reportTemperature(CHANNEL_SETPOINT_MANUAL, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_TEMPORARY:
                reportTemperature(CHANNEL_SETPOINT_TEMPORARY, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_AWAY:
                reportTemperature(CHANNEL_SETPOINT_AWAY, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_FROST_PROTECTION:
                reportTemperature(CHANNEL_SETPOINT_ANTIFREEZE, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_FLOOR_COMFORT:
                reportTemperature(CHANNEL_SETPOINT_MIN_FLOOR, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_MAX_FLOOR:
                reportTemperature(CHANNEL_SETPOINT_MAX_FLOOR, pkt.getDecimal());
                break;
            case MDG_CONNECTED_TO_SERVER:
                reportSwitch(CHANNEL_MDG_CONNECTED_TO_CLOUD, pkt.getBoolean());
                break;
            case SCHEDULER_WEEK:
                byte[] shBuffer1 = pkt.getArray();
                if(scheduleManager.putScheduleBytes(shBuffer1)) {
                    byte[] weekSchedule1 = scheduleManager.getWeeklySchedule();
                    //String weekSchedule = bytesToHex(weekSchedule1);
                    updateProperty(CHANNEL_WEEK_SCHEDULE,  scheduleManager.toJson());
                }
                break;
            case SCHEDULER_WEEK_2:
                byte[] shBuffer2 = pkt.getArray();
                if(scheduleManager.putScheduleBytes(shBuffer2)) {
                    byte[] weekSchedule2 = scheduleManager.getWeeklySchedule();
                    //String weekSchedule = bytesToHex(weekSchedule2);
                    //updateProperty(CHANNEL_WEEK_SCHEDULE, weekSchedule);
                    updateProperty(CHANNEL_WEEK_SCHEDULE, scheduleManager.toJson());
                }
                break;
            case SYSTEM_HEATING_INFO:
                reportSwitch(CHANNEL_HEATING_STATE, pkt.getBoolean());
                break;
            case SCHEDULER_SETPOINT_FLOOR_COMFORT_ENABLED:
                reportSwitch(CHANNEL_SETPOINT_MIN_FLOOR_ENABLE, pkt.getBoolean());
                break;
            case SYSTEM_WINDOW_OPEN:
                reportSwitch(CHANNEL_WINDOW_DETECTION, pkt.getBoolean());
                break;
            case SYSTEM_INFO_WINDOW_OPEN_DETECTION:
                reportSwitch(CHANNEL_WINDOW_OPEN, pkt.getBoolean());
                break;
            case SYSTEM_INFO_FORECAST_ENABLED:
                reportSwitch(CHANNEL_FORECAST, pkt.getBoolean());
                break;
            case SYSTEM_UI_SAFETY_LOCK:
                reportSwitch(CHANNEL_SCREEN_LOCK, pkt.getBoolean());
                break;
            case SYSTEM_UI_BRIGTHNESS:
                reportDecimal(CHANNEL_BRIGHTNESS, pkt.getByte());
                break;
            case SCHEDULER_CONTROL_INFO:
                reportControlInfo(pkt.getByte());
                break;
            case LOG_ENERGY_CONSUMPTION_7DAYS:
                reportDuration(CHANNEL_ON_TIME_7_DAYS, pkt.getInt());
                if(this.readOutputPower > 0) {
                    String kwh = ConvertSecondsToKWH(pkt.getInt(), this.readOutputPower);
                    updateProperty(CHANNEL_ON_TIME_7_DAYS_KWH, kwh);
                }
                break;
            case LOG_ENERGY_CONSUMPTION_30DAYS:
                reportDuration(CHANNEL_ON_TIME_30_DAYS, pkt.getInt());
                if(this.readOutputPower > 0) {
                    String kwh = ConvertSecondsToKWH(pkt.getInt(), this.readOutputPower);
                    updateProperty(CHANNEL_ON_TIME_30_DAYS_KWH, kwh);
                }
                break;
            case SYSTEM_RUNTIME_INFO_RELAY_ON_TIME:
                reportDuration(CHANNEL_ON_TIME_TOTAL, pkt.getInt());
                if(this.readOutputPower > 0) {
                    String kwh = ConvertSecondsToKWH(pkt.getInt(), this.readOutputPower);
                    updateProperty(CHANNEL_ON_TIME_TOTAL_KWH, kwh);
                }
                break;
            case SYSTEM_ALARM_INFO:
                byte alarms = pkt.getByte();
                for (int i = 0; i < ALARM_CHANNELS.length; i++) {
                    reportSwitch(ALARM_CHANNELS[i], (alarms & (1 << i)) != 0);
                }
                break;
            case GLOBAL_HARDWAREREVISION:
                updateProperty("sys_hardware_version", pkt.getVersion().toString());
                break;
            case GLOBAL_SOFTWAREREVISION:
                firmwareVer = pkt.getVersion();
                reportFirmware();
                break;
            case GLOBAL_SOFTWAREBUILDREVISION:
                firmwareBuild = Short.toUnsignedInt(pkt.getShort());
                reportFirmware();
                break;
            case GLOBAL_SERIALNUMBER:
                updateProperty("sys_serial_number", String.valueOf(pkt.getInt()));
                break;
            case GLOBAL_PRODUCTIONDATE:
                updateProperty("sys_production_date", DateFormat.getDateTimeInstance().format(pkt.getDate(0)));
                break;
            case MDG_CONNECTION_COUNT:
                updateProperty("sys_connection_count", String.valueOf(pkt.getByte()));
                break;
            case WIFI_CONNECTED_STRENGTH:
                updateProperty("sys_wifi_strength", String.valueOf(pkt.getShort()));
                break;
            case WIFI_CONNECT_SSID:
                updateProperty("sys_wifi_connect_ssid", pkt.getString());
                break;
            case SYSTEM_HOUSE_NAME:
                updateProperty("sys_house_name", pkt.getString());
                break;
            case SYSTEM_ROOM_NAME:
                updateProperty("sys_room_name", pkt.getString());
                break;
            case SYSTEM_RUNTIME_INFO_RELAY_COUNT:
                updateProperty("sys_relay_on_count", String.valueOf(pkt.getInt()));
                break;
            case SYSTEM_RUNTIME_INFO_SYSTEM_RUNTIME:
                updateProperty("sys_run_time", formatDurationS(pkt.getInt()));
                break;
            case SYSTEM_INFO_BREAKOUT:
                updateProperty(CHANNEL_BREAKOUT, pkt.getBoolean() ? "ON" : "OFF");
                break;
            case SYSTEM_WIZARD_INFO:
                WizardInfo config = pkt.getWizardInfo();
                updateProperty("sys_info_sensor_type", numToString(SensorTypes, config.sensorType));
                updateProperty("sys_info_regulation_type", numToString(RegulationTypes, config.regulationType));
                updateProperty("sys_info_floor_type", numToString(FloorTypes, config.flooringType));
                updateProperty("sys_info_room_type", numToString(RoomTypes, config.roomType));
                updateProperty("sys_info_output_power", config.outputPower == WizardInfo.EXTERNAL_RELAY ? "External relay"
                        : String.valueOf(config.outputPower));

                if(config.outputPower != WizardInfo.EXTERNAL_RELAY)
                    this.readOutputPower = config.outputPower;

                break;
        }
    }

    // Convert duration in seconds to (years, days, hours, minutes),
    // just like the original application.


    private String formatDurationS(int seconds) {
        long d = Integer.toUnsignedLong(seconds);
        return String.valueOf(d);
    }
    
    private String formatDuration(int seconds) {
        long d = Integer.toUnsignedLong(seconds);
        long years = d / 31536000;
        long d2 = d % 31536000;
        long days = d2 / 86400;
        long d3 = d2 % 86400;
        long hours = d3 / 3600;
        long d4 = d3 % 3600;
        long minutes = d4 / 60;


        return String.valueOf(years) + " y " + days + " d " + hours + " h " + minutes + " m";
    }

    private static String ConvertSecondsToKWH(int seconds, double readOutputPower) {
        // Calculate energy in kWh
        double hours = seconds / 3600.0;
        double energyWh = hours * readOutputPower;
        double energyKWh = energyWh / 1000.0;

        return String.format("%.2f",energyKWh);
    }

    private String numToString(String[] table, int value) {
        if (value < 0 || value >= table.length) {
            return "Unknown (" + value + ")";
        } else {
            return table[value];
        }
    }

    private static final String[] SensorTypes = { "Aube 10K", "Devi 15K", "Eberle 33K", "Ensto 47K", "Fenix 10K",
            "OJ 12K", "Raychem 10K", "Teplolux 6.8K", "WarmUp 12K" };
    private static final String[] RegulationTypes = { "Room", "Floor", "Room + floor" };
    private static final String[] FloorTypes = { "Tiles", "Hardwood", "Laminate", "Carpet" };
    private static final String[] RoomTypes = { "Bathroom", "Kitchen", "Living room", "Bedroom" };

    @Override
    public void ping() {
        // Ping our device. Just request anything.
        // This method is called from within PeerConnectionHandler when
        // it notices that the communication seems to have stalled.
        connHandler.SendPacket(new Dominion.Packet(DOMINION_HEATING, HEATING_TEMPERATURE_FLOOR));
    }

    // Support method for SDGPeerConnector
    // Unfortunately Java doesn't support multiple inheritance, so this is
    // a small hack to simulate it

    @Override
    public void reportStatus(@NonNull ThingStatus status, @NonNull ThingStatusDetail statusDetail,
            @Nullable String description) {
        updateStatus(status, statusDetail, description);
    }
}
