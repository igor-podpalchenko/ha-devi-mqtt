package io.homeassistant.binding.danfoss.internal;

import static io.homeassistant.binding.danfoss.internal.DanfossBindingConstants.*;
import static io.homeassistant.binding.danfoss.internal.protocol.Icon.MsgClass.ROOM_FIRST;
import static io.homeassistant.binding.danfoss.internal.protocol.Icon.MsgCode.*;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNull;
import io.homeassistant.binding.danfoss.internal.protocol.Dominion;
import io.homeassistant.binding.danfoss.internal.protocol.Icon.RoomControl;
import io.homeassistant.binding.danfoss.internal.protocol.Icon.RoomMode;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IconRoomHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(IconRoomHandler.class);
    private int roomNumber;
    private SDGPeerConnector connHandler;
    private boolean isOnline;

    public IconRoomHandler(Thing thing) {
        super(thing);
        // TODO Auto-generated constructor stub
    }

    public int getNumber() {
        return roomNumber;
    }

    public void setConnectionHandler(SDGPeerConnector h) {
        connHandler = h;
    }

    public void setConfigError(String reason) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, reason);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String ch = channelUID.getId();

        logger.trace("Sending {} = {}", ch, command);

        switch (ch) {
            case CHANNEL_SETPOINT_COMFORT:
                setTemperature(ROOM_SETPOINTATHOME, command);
                break;
            case CHANNEL_SETPOINT_ECONOMY:
                setTemperature(ROOM_SETPOINTAWAY, command);
                break;
            case CHANNEL_SETPOINT_ASLEEP:
                setTemperature(ROOM_SETPOINTASLEEP, command);
                break;
            case CHANNEL_SETPOINT_MIN_FLOOR:
                setTemperature(ROOM_FLOORTEMPERATUREMINIMUM, command);
                break;
            case CHANNEL_SETPOINT_MAX_FLOOR:
                setTemperature(ROOM_FLOORTEMPERATUREMAXIMUM, command);
                break;
            case CHANNEL_MANUAL_MODE:
                if (command instanceof OnOffType) {
                    connHandler.SendPacket(new Dominion.Packet(ROOM_FIRST + roomNumber, ROOM_ROOMCONTROL,
                            command.equals(OnOffType.ON) ? RoomControl.Manual : RoomControl.Auto));
                } else {
                    connHandler.sendRefresh(ROOM_FIRST + roomNumber, ROOM_ROOMCONTROL, command);
                }
                break;
            // Read-only channels may send refreshType command
            case CHANNEL_TEMPERATURE_FLOOR:
                sendRefresh(ROOM_FLOORTEMPERATURE, command);
                break;
            case CHANNEL_TEMPERATURE_ROOM:
                sendRefresh(ROOM_ROOMTEMPERATURE, command);
                break;
        }
    }

    public void setTemperature(int msgCode, Command command) {
        if (connHandler != null) {
            connHandler.setTemperature(ROOM_FIRST + roomNumber, msgCode, command);
        }
    }

    public void sendRefresh(int msgCode, Command command) {
        if (connHandler != null) {
            connHandler.sendRefresh(ROOM_FIRST + roomNumber, msgCode, command);
        }
    }

    @Override
    public void initialize() {
        IconRoomConfiguration config = getConfigAs(IconRoomConfiguration.class);

        roomNumber = config.roomNumber;

        logger.trace("Initializing room {}", roomNumber);
        isOnline = false;
        updateStatus(ThingStatus.UNKNOWN);
    }

    public void handlePacket(Dominion.@NonNull Packet pkt) {
        if (!isOnline) {
            isOnline = true;
            updateStatus(ThingStatus.ONLINE);
        }

        switch (pkt.getMsgCode()) {
            case ROOM_FLOORTEMPERATURE:
                reportTemperature(CHANNEL_TEMPERATURE_FLOOR, pkt.getDecimal());
                break;
            case ROOM_ROOMTEMPERATURE:
                reportTemperature(CHANNEL_TEMPERATURE_ROOM, pkt.getDecimal());
                break;
            case ROOM_SETPOINTATHOME:
                reportTemperature(CHANNEL_SETPOINT_COMFORT, pkt.getDecimal());
                break;
            case ROOM_SETPOINTASLEEP:
                reportTemperature(CHANNEL_SETPOINT_ASLEEP, pkt.getDecimal());
                break;
            case ROOM_SETPOINTAWAY:
                reportTemperature(CHANNEL_SETPOINT_ECONOMY, pkt.getDecimal());
                break;
            case ROOM_FLOORTEMPERATUREMINIMUM:
                reportTemperature(CHANNEL_SETPOINT_MIN_FLOOR, pkt.getDecimal());
                break;
            case ROOM_FLOORTEMPERATUREMAXIMUM:
                reportTemperature(CHANNEL_SETPOINT_MAX_FLOOR, pkt.getDecimal());
                break;
            case ROOM_BATTERYINDICATIONPERCENT:
                reportDecimal(CHANNEL_BATTERY, pkt.getByte());
                break;
            case ROOM_ROOMMODE:
                reportControlState(pkt.getByte());
                break;
            case ROOM_ROOMCONTROL:
                reportSwitch(CHANNEL_MANUAL_MODE, pkt.getByte() == RoomControl.Manual);
                break;
            case ROOMNAME:
                updateProperty("roomName", pkt.getString());
                break;
        }
    }

    @SuppressWarnings("null")
    private void reportTemperature(String ch, double temp) {
        logger.trace("Received {} = {}", ch, temp);
        updateState(ch, new QuantityType<Temperature>(new DecimalType(temp), SIUnits.CELSIUS));
    }

    private void reportDecimal(String ch, long value) {
        logger.trace("Received {} = {}", ch, value);
        updateState(ch, new DecimalType(value));
    }

    private void reportSwitch(String ch, boolean on) {
        logger.trace("Received {} = {}", ch, on);
        updateState(ch, OnOffType.from(on));
    }

    private void reportControlState(byte info) {
        final String[] CONTROL_STATES = { "HOME", "AWAY", "ASLEEP", "FATAL" };
        String state;

        if (info >= RoomMode.AtHome && info <= RoomMode.Fatal) {
            state = CONTROL_STATES[info];
        } else {
            state = "";
        }

        logger.trace("Received {} = {}", CHANNEL_CONTROL_STATE, state);
        updateState(CHANNEL_CONTROL_STATE, StringType.valueOf(state));
    }
}
