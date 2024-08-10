package io.homeassistant.binding.danfoss.internal;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import  jakarta.xml.bind.DatatypeConverter;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.core.config.core.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sonic_amiga.opensdg.java.SDG;

public class DanfossBindingConfig {
    private static final Logger logger = LoggerFactory.getLogger(DanfossBindingConfig.class);
    private static DanfossBindingConfig g_Config = new DanfossBindingConfig();

    public String privateKey;
    public String publicKey;
    public String userName;

    public static DanfossBindingConfig get() {
        return g_Config;
    }

    private void update(DanfossBindingConfig newConfig) {
        String newKey = newConfig.privateKey;
        byte[] newPrivkey;

        userName = newConfig.userName;

        if (newKey == null || newKey.isEmpty()) {
            newPrivkey = SDG.createPrivateKey();
            newKey = DatatypeConverter.printHexBinary(newPrivkey);

            logger.debug("Created new private key: {}", newKey);
        } else if (newKey.equals(privateKey)) {
            return;
        } else {
            // Validate the new key and revert back to the old one if validation fails
            // It is rather dangerous to inadvertently damage it, you'll lose all
            // your thermostats and probably have to set everything up from scratch.
            newPrivkey = SDGUtils.ParseKey(newKey);

            if (newPrivkey == null) {
                logger.warn("Invalid private key configured: {}; reverting back to old one", newKey);
                return;
            }

            logger.debug("Got private key from configuration: {}", newKey);
        }

        privateKey = newKey;
        publicKey = DatatypeConverter.printHexBinary(SDG.calcPublicKey(newPrivkey));
        userName = newConfig.userName;

        GridConnectionKeeper.UpdatePrivateKey(newKey);
    }

    public static void update(@NonNull Map<@NonNull String, @NonNull Object> config, ConfigurationAdmin admin) {
        @SuppressWarnings("null")
        DanfossBindingConfig newConfig = new Configuration(config).as(DanfossBindingConfig.class);

        // Kludge for OpenHAB 2.4. Early development versions of this binding didn't have
        // this parameter. OpenHAB apparently cached parameter structure and doesn't present
        // the new option in binding config. Consequently, the field in DeviRegBindingConfig
        // object stays null.
        if (newConfig.userName == null) {
            newConfig.userName = "OpenHAB";
        }

        g_Config.update(newConfig);

        if (!(g_Config.privateKey.equals(newConfig.privateKey) && g_Config.publicKey.equals(newConfig.publicKey)
                && g_Config.userName.equals(newConfig.userName))) {
            // Some value has been updated by the validation, save the validated version
            g_Config.Save(admin);
        }
    }

    public void Save(ConfigurationAdmin confAdmin) {
        Dictionary<String, Object> data = new Hashtable<String, Object>();

        data.put("privateKey", privateKey);
        data.put("publicKey", publicKey);
        data.put("userName", userName);

        try {
            confAdmin.getConfiguration("binding." + DanfossBindingConstants.BINDING_ID, null).update(data);
        } catch (IOException e) {
            logger.error("Failed to update binding config: {}", e.getLocalizedMessage());
        }
    }
}
