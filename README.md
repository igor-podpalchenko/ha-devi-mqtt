# Home Assistant MQTT Danfoss Binding

This is Home Assistant service for DeviReg Smart thermostats.
Main idea behind - integration with Home Assistant via MQTT by auto discovery (not as HA addon or HACS integration).
Around 50 sensors are implemented and can be used in Home Assistant.

## Supported Things

- DeviReg(tm) Smart floor thermostat (__supported__, tested https://www.devismart.com/)
- Danfoss Icon controller (__unsupported__, requires real device to implement)
- Danfoss Icon room (__unsupported__, requires real device to implement)

If you have Icon device and know Java a little bit - you can fork and implement it by yourself.

## Installation and supported architectures

Relies on OpenSDG library (https://github.com/Sonic-Amiga/opensdg/releases) for communicating with the hardware.
The final JAR includes all dependencies compiled in and is ready to use without installing any other dependencies.
Tested on Linux, MacOS, but should work fine on Windows as well. Docker image is also available.

## Discovery

Discovery of configuration is __required__ step to use the software.
Thermostat is using cloud security and requires setup/pairing by OTP code entry.
As it's required user input, it can't be done in Docker.  

To pair the software with the thermostat, you need mobile application paired with the thermostat.
In mobile application, you have to go to Settings -> Share house -> Generate OTP.

Steps:

1. Run the discovery tool, follow the instructions from app.
```shell
java -cp ha-devi-mqtt.jar io.homeassistant.devi.mqtt.service.DiscoveryService
```
2. Launch mobile application, enter username and OTP code (it might be required to enter OTP more then once, due to bug in library).
3. The configuration file will be generated (devi_config.json).
4. Keep the configuration file in secure place, it contains private key required to control your device.


```json(file: devi_config.json)
{
  "peerId": "Peer ID, also known as Public key. All devices on the Grid are identified by these keys",
  "privateKey": "hex coded private key",
  "houseName": "name of the house (not used anywhere in code)",
  "rooms": [
    {
      "serialNumber": "(int) serial number (used to build MQTT topic path)",
      "devicePeerID": "peer id of the thermostat",
      "name": "name of the room (not used anywhere in code, device is bound to room)"
    }
  ],
  "userName": "unique username, that was entered during the pairing"
}
```
```json(file: mqtt_config.json)
{
  "host": "IP or hostname of the MQTT broker",
  "port": "1883",
  "user": "mqtt-user",
  "password": "mqtt-password"
}
```
Note: the pairing is performed with the home, not with the concrete device. 
If you have multiple devices, all devices will be paired with software.


Protocol is called SecureDeviceGrid(tm)
(http://securedevicegrid.com/).

Original source (https://github.com/Sonic-Amiga/org.openhab.binding.devireg)