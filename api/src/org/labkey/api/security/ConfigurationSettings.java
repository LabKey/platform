package org.labkey.api.security;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.AES;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.logging.LogHelper;

import java.util.Map;

public class ConfigurationSettings
{
    private static final Logger LOG = LogHelper.getLogger(ConfigurationSettings.class, "Loading of authentication configuration properties");

    private final Map<String, Object> _standardSettings;
    private final Map<String, Object> _properties;
    private final Map<String, Object> _encryptedProperties;

    public ConfigurationSettings(Map<String, Object> settings)
    {
        _standardSettings = settings;
        String propertiesJson = (String) settings.get("Properties");
        _properties = null != propertiesJson ? new JSONObject(propertiesJson) : new JSONObject();
        String encryptedPropertiesJson = (String) settings.get("EncryptedProperties");
        Map<String, Object> encryptedProperties = new JSONObject();

        if (null != encryptedPropertiesJson)
        {
            if (Encryption.isMasterEncryptionPassPhraseSpecified())
            {
                try
                {
                    encryptedProperties = new JSONObject(AES.get().decrypt(Base64.decodeBase64(encryptedPropertiesJson)));
                }
                catch (Encryption.DecryptionException e)
                {
                    LOG.warn("Encrypted properties can't be read", e);
                }
            }
            else
            {
                LOG.warn("Encrypted properties can't be read: master encryption key has not been set in " + AppProps.getInstance().getWebappConfigurationFilename() + "!");
            }
        }

        _encryptedProperties = encryptedProperties;
    }

    public Map<String, Object> getStandardSettings()
    {
        return _standardSettings;
    }

    public Map<String, Object> getProperties()
    {
        return _properties;
    }

    public Map<String, Object> getEncryptedProperties()
    {
        return _encryptedProperties;
    }
}
