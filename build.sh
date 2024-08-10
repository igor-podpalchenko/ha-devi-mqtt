#!/bin/bash

mvn clean compile
mvn package
mvn verify

docker build -t ha-devi-mqtt .