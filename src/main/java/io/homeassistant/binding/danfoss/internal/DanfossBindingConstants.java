package io.homeassistant.binding.danfoss.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link DanfossBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Pavel Fedin - Initial contribution
 */
@NonNullByDefault
public class DanfossBindingConstants {

    public static final String BINDING_ID = "danfoss";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_DEVIREG_SMART = new ThingTypeUID(BINDING_ID, "devismart");
    public static final ThingTypeUID THING_TYPE_ICON_WIFI = new ThingTypeUID(BINDING_ID, "icon_wifi");
    public static final ThingTypeUID THING_TYPE_ICON_ROOM = new ThingTypeUID(BINDING_ID, "icon_room");

    public static final int ICON_MAX_ROOMS = 45;

    // List of all Channel ids
    public static final String CHANNEL_TEMPERATURE_FLOOR = "temperature_floor";
    public static final String CHANNEL_TEMPERATURE_ROOM = "temperature_room";


    public static final String CHANNEL_SETPOINT_COMFORT = "setpoint_comfort";
    public static final String CHANNEL_SETPOINT_ECONOMY = "setpoint_economy";
    public static final String CHANNEL_SETPOINT_ASLEEP = "setpoint_asleep";
    public static final String CHANNEL_SETPOINT_MANUAL = "setpoint_manual";
    public static final String CHANNEL_SETPOINT_TEMPORARY = "setpoint_temporary";
    public static final String CHANNEL_SETPOINT_AWAY = "setpoint_away";
    public static final String CHANNEL_SETPOINT_ANTIFREEZE = "setpoint_antifreeze";
    public static final String CHANNEL_SETPOINT_MIN_FLOOR = "setpoint_min_floor";
    public static final String CHANNEL_SETPOINT_MAX_FLOOR = "setpoint_max_floor";
    public static final String CHANNEL_SETPOINT_WARNING = "setpoint_warning";

    public static final String[] TEMP_SETPOINTS = { CHANNEL_SETPOINT_COMFORT, CHANNEL_SETPOINT_ECONOMY, CHANNEL_SETPOINT_MANUAL,
            CHANNEL_SETPOINT_TEMPORARY, CHANNEL_SETPOINT_AWAY, CHANNEL_SETPOINT_ANTIFREEZE };

    // Select
    public static final String CHANNEL_CONTROL_MODE = "select_control_mode";


    // Switch
    public static final String CHANNEL_SETPOINT_MIN_FLOOR_ENABLE = "switch_min_floor_enable";
    public static final String CHANNEL_WINDOW_DETECTION = "switch_window_detection";
    public static final String CHANNEL_FORECAST = "switch_forecast";
    public static final String CHANNEL_SCREEN_LOCK = "switch_screen_lock";

    // Number
    public static final String CHANNEL_BRIGHTNESS = "number_brightness";


    // Sensors
    public static final String CHANNEL_CONTROL_STATE = "sensor_control_state";

    public static final String CHANNEL_ON_TIME_7_DAYS = "on_time_7_days";
    public static final String CHANNEL_ON_TIME_30_DAYS = "on_time_30_days";
    public static final String CHANNEL_ON_TIME_TOTAL = "on_time_total";
    public static final String CHANNEL_ON_TIME_7_DAYS_KWH = "on_time_7_days_kwh";
    public static final String CHANNEL_ON_TIME_30_DAYS_KWH = "on_time_30_days_kwh";
    public static final String CHANNEL_ON_TIME_TOTAL_KWH = "on_time_total_kwh";
    public static final String CHANNEL_WEEK_SCHEDULE = "sensor_week_schedule";

    // Binary sensors
    public static final String CHANNEL_HEATING_STATE = "binary_sensor_heating_state";
    public static final String CHANNEL_MDG_CONNECTED_TO_CLOUD = "binary_sensor_cloud_connected";
    public static final String CHANNEL_WINDOW_OPEN = "binary_sensor_window_open";

    public static final String CHANNEL_BREAKOUT = "binary_sensor_warning_breakout";
    public static final String CHANNEL_DISCONNECTED = "binary_sensor_warning_disconnected";
    public static final String CHANNEL_SHORTED = "binary_sensor_warning_shorted";
    public static final String CHANNEL_OVERHEAT = "binary_sensor_warning_overheat";
    public static final String CHANNEL_UNRECOVERABLE = "binary_sensor_warning_unrecoverable";


    // Virtual / computed states
    public static final String CHANNEL_ACTIVE_SETPOINT   = "sensor_active_setpoint";
    public static final String CHANNEL_THERMOSTAT_PRESET = "thermostat_preset";
    public static final String CHANNEL_TARGET_TEMP       = "temperature_target";
    public static final String CHANNEL_SET_TARGET_TEMP   = "set_temperature_target";
    public static final String CHANNEL_CURRENT_TEMP      = "temperature_current";  // floor or room
	
    // Icon specific
    public static final String CHANNEL_MANUAL_MODE = "manual_mode";
    public static final String CHANNEL_BATTERY = "battery";

    public static final String[] ALARM_CHANNELS = { CHANNEL_DISCONNECTED, CHANNEL_SHORTED, CHANNEL_OVERHEAT,
            CHANNEL_UNRECOVERABLE };

    public static final String CONTROL_MODE_MANUAL = "MANUAL";
    public static final String CONTROL_MODE_OVERRIDE = "OVERRIDE";
    public static final String CONTROL_MODE_SCHEDULE = "SCHEDULE";
    public static final String CONTROL_MODE_VACATION = "VACATION";
    public static final String CONTROL_MODE_PAUSE = "PAUSE";
    public static final String CONTROL_MODE_OFF = "OFF";
}
