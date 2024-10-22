import os
import json

deviceSN = "${deviceSN}"
deviceNumber = "${deviceNumber}"  # Assuming deviceNumber is same as deviceSN for this example

topics = [
    "sys_serial_number",
    "sys_hardware_version",
    "sys_firmware_version",
    "sys_wifi_connect_ssid",
    "sys_wifi_strength",
    "sys_info_sensor_type",
    "sys_info_regulation_type",
    "sys_info_floor_type",
    "sys_info_room_type",
    "sys_info_output_power",
    "sys_room_name",
    "sys_house_name",
    "sys_run_time",
    "on_time_30_days",
    "on_time_7_days",
    "on_time_total",
    "on_time_30_days_kwh",
    "on_time_7_days_kwh",
    "on_time_total_kwh"
]


topics_units = {
    "sys_wifi_strength": "dBm",
#    "sys_run_time": "s",
    "on_time_30_days": "s",
    "on_time_7_days": "s",
    "on_time_total": "s",
    "on_time_30_days_kwh":"kwh",
    "on_time_7_days_kwh":"kwh",
    "on_time_total_kwh": "kwh"
}


template = {
    "topic": "",
    "template": {
        "state_topic": "",
        "device": {
            "identifiers": [
                deviceSN
            ],
            "manufacturer": "Danfoss",
            "name": f"DeviReg {deviceSN}",
            "model": "DeviReg Smart Thermostat"
        },
        "unique_id": "",
        "object_id": "",
        "name": ""
    }
}

output_dir = "autogen"
os.makedirs(output_dir, exist_ok=True)

for topic in topics:
    config = template.copy()
    # need this to reset template!
    config["template"] = template["template"].copy()
    
    config["topic"] = f"homeassistant/sensor/{deviceSN}_{topic}/config"
    config["template"]["state_topic"] = f"devi/state/{deviceSN}/{topic}"
    config["template"]["unique_id"] = f"id_{deviceSN}_{topic}"
    config["template"]["object_id"] = f"devireg_{deviceSN}_{topic}"
    config["template"]["name"] = topic.replace("_", " ").capitalize()
    
    if topic in topics_units:
        uofm = topics_units[topic]

        config["template"]["unit_of_measurement"] = uofm
        if uofm=='kwh':
            config["template"]["device_class"] = 'energy'
            config["template"]["state_class"]  = 'total_increasing'
        elif uofm=='dBm':
            config["template"]["device_class"] = 'signal_strength'
            config["template"]["state_class"]  = 'measurement'				
		
    output_file = f"{output_dir}/sensor_{topic}.json"
    with open(output_file, "w") as file:
        json.dump(config, file, indent=4)

output_dir

