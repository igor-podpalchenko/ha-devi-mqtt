#!/bin/bash

docker run -it --rm  \
 -v $(pwd)/devi_config.json:/app/config/devi_config.json \
 -v $(pwd)/mqtt_config.json:/app/config/mqtt_config.json \
 -v $(pwd)/auto-discovery-templates:/app/config/auto-discovery-templates \
  ha-devi-mqtt