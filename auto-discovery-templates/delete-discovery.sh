#!/bin/bash

# Define the broker address
BROKER_ADDRESS="nas.local"

# Define the base topics
sensor_base="homeassistant/sensor/10159384_"
switch_base="homeassistant/switch/10159384_"
number_base="homeassistant/number/10159384_"
climate_base="homeassistant/climate/10159384_"
select_base="homeassistant/select/10159384_"
binary_sensor_base="homeassistant/binary_sensor/10159384_"

# Define the suffixes for each type
sensor_suffixes=(
  "temperature_room" "temperature_floor" "sys_relay_on_count" "sys_info_room_type"
  "sys_firmware_version" "on_time_30_days_kwh" "sys_info_sensor_type" "sys_house_name"
  "on_time_7_days" "sys_info_floor_type" "on_time_30_days" "sys_room_name" "sys_hardware_version"
  "sys_wifi_strength" "sys_info_output_power" "sys_run_time" "on_time_7_days_kwh"
  "on_time_total_kwh" "sys_info_regulation_type" "sys_serial_number" "sys_wifi_connect_ssid"
  "sensor_control_state" "on_time_total"
)

binary_sensor_suffixes=(
"binary_sensor_window_open" "binary_sensor_cloud_connected" "binary_sensor_heating_state"
"binary_sensor_warning_disconnected" "binary_sensor_warning_shorted" "binary_sensor_warning_overheat"
"binary_sensor_warning_unrecoverable"
)

switch_suffixes=("forecast" "screen_lock" "window_detection" "switch_min_floor_enable")

select_suffixes=("select_control_mode")

number_suffixes=(
  "setpoint_warning" "setpoint_max_floor" "setpoint_manual" "setpoint_away"
  "setpoint_min_floor" "setpoint_temporary" "setpoint_antifreeze" "setpoint_economy"
  "setpoint_comfort"
)

climate_suffixes=("devi_thermostat")

# Function to delete topics
delete_topics() {
  local base="$1"
  shift
  local suffixes=("$@")
  for suffix in "${suffixes[@]}"; do
    local topic="${base}${suffix}"
    echo "Deleting topic: $topic"
    mosquitto_pub -h "$BROKER_ADDRESS" -t "$topic/config" -r -n
  done
}

# Delete the topics
delete_topics "$sensor_base" "${sensor_suffixes[@]}"
delete_topics "$switch_base" "${switch_suffixes[@]}"
delete_topics "$number_base" "${number_suffixes[@]}"
delete_topics "$climate_base" "${climate_suffixes[@]}"
delete_topics "$select_base" "${select_suffixes[@]}"
delete_topics "$binary_sensor_base" "${binary_sensor_suffixes[@]}"