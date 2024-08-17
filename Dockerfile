# Stage 1: Build the application using Maven
FROM maven:3.9.4-eclipse-temurin-21 AS build

LABEL org.opencontainers.image.description="$(cat README.md)"

# Set the working directory inside the container
WORKDIR /app

# Copy the pom.xml file and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the entire project into the container
COPY src ./src

# Build the project
RUN mvn clean package -DskipTests

# Stage 2: Create the final image to run the application
FROM eclipse-temurin:22-jre-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/target/ha-devi-mqtt.jar ./ha-devi-mqtt.jar

# Create a directory for configuration files
RUN mkdir /app/config

# Command to run the application
#CMD ["java", "-jar", "your-app-name.jar"]
# Set the entry point to run the application
# java -cp target/danfoss.jar io.homeassistant.devi.mqtt.service.ConsoleRunner --auto-discovery-templates auto-discovery-templates --mqtt-config mqtt_config.json --devi-config devi_config.json

CMD ["java", "-cp", "ha-devi-mqtt.jar", "io.homeassistant.devi.mqtt.service.ConsoleRunner", "--auto-discovery-templates", "/app/config/auto-discovery-templates", "--mqtt-config", "/app/config/mqtt_config.json", "--devi-config", "/app/config/devi_config.json"]
