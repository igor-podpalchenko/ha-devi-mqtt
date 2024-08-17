package io.homeassistant.devi.mqtt.service;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
//import org.osgi.service.cm.ConfigurationEvent;
//import org.osgi.service.cm.ConfigurationListener;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

public class MockConfigurationAdmin implements ConfigurationAdmin {

    @Override
    public Configuration createFactoryConfiguration(String factoryPid) throws IOException {
        return new MockConfiguration();
    }

    @Override
    public Configuration createFactoryConfiguration(String factoryPid, String location) throws IOException {
        return new MockConfiguration();
    }

    @Override
    public Configuration getConfiguration(String pid) throws IOException {
        return new MockConfiguration();
    }

    @Override
    public Configuration getConfiguration(String pid, String location) throws IOException {
        return new MockConfiguration();
    }

    @Override
    public Configuration[] listConfigurations(String filter) throws IOException {
        return new Configuration[]{new MockConfiguration()};
    }


    class MockConfiguration implements Configuration {
        private Dictionary<String, Object> properties = new Hashtable<>();

        @Override
        public String getPid() {
            return "mock-pid";
        }

        @Override
        public Dictionary<String, Object> getProperties() {
            return properties;
        }

        @Override
        public void update(Dictionary<String, ?> properties) throws IOException {
            this.properties = (Dictionary<String, Object>) properties;
        }

        @Override
        public void delete() throws IOException {
            // Do nothing
        }

        @Override
        public void update() throws IOException {
            // Do nothing
        }

        @Override
        public void setBundleLocation(String location) {
            // Do nothing
        }

        @Override
        public String getBundleLocation() {
            return "mock-location";
        }

        @Override
        public long getChangeCount() {
            return 0;
        }

        @Override
        public String getFactoryPid() {
            return null; // Return null or a mock factory PID as appropriate
        }
    }
}
