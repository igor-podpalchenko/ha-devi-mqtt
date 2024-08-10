import paho.mqtt.client as mqtt
from paho.mqtt.client import CallbackAPIVersion

# Define the broker address
broker_address = "nas.local"

# Define the base topics and their suffixes
base_topics = {
    "homeassistant/sensor/10159384_": [
        "temperature_room",
        "temperature_floor",
        "sys_relay_on_count",
        "sys_info_room_type",
        "sys_firmware_version",
        "on_time_30_days_kwh",
        "sys_info_sensor_type",
        "sys_house_name",
        "sensor_heating_state",
        "on_time_7_days",
        "sys_info_floor_type",
        "on_time_30_days",
        "sys_wifi_strength",
        "sys_info_output_power",
        "sys_run_time",
        "on_time_7_days_kwh",
        "sys_room_name",
        "sys_hardware_version",
        "on_time_total",
        "sensor_window_open",
        "on_time_total_kwh",
        "sys_info_regulation_type",
        "sys_serial_number",
        "sys_wifi_connect_ssid"
    ],
    "homeassistant/switch/10159384_": [
        "forecast",
        "screen_lock",
        "window_detection"
    ],
    "homeassistant/number/10159384_": [
        "setpoint_warning",
        "setpoint_max_floor",
        "setpoint_manual",
        "setpoint_away",
        "setpoint_min_floor",
        "setpoint_temporary",
        "setpoint_antifreeze",
        "setpoint_economy",
        "setpoint_comfort"
    ],
    "homeassistant/climate/10159384_": [
        "devi_thermostat"
    ]
}

# Callback function on connect
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Connected to broker")
        delete_topics(client)
    else:
        print("Connection failed")

# Function to delete topics
def delete_topics(client):
    for base, suffixes in base_topics.items():
        for suffix in suffixes:
            topic = base + suffix + "/topic"
            print(f"Deleting topic: {topic}")
            #client.publish(topic, payload=None, retain=True)
            client.publish(topic, payload=None, qos=1, retain=True)
    client.disconnect()

# Create an MQTT client instance with the correct callback API version
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1)

# Assign event callbacks
client.on_connect = on_connect

# Connect to the broker
client.connect(broker_address)

# Start the loop
client.loop_forever()
