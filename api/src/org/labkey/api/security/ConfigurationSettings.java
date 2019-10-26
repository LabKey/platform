package org.labkey.api.security;

import org.json.JSONObject;

import java.util.Map;

public class ConfigurationSettings
{
    private final Map<String, Object> _standardSettings;
    private final Map<String, Object> _properties;
    private final Map<String, Object> _encryptedProperties;

    public ConfigurationSettings(Map<String, Object> settings)
    {
        _standardSettings = settings;
        String propertiesJson = (String)settings.get("Properties");
        _properties = null != propertiesJson ? new JSONObject(propertiesJson) : new JSONObject();
        String encryptedPropertiesJson = (String)settings.get("EncryptedProperties");
        _encryptedProperties = null != encryptedPropertiesJson ? new JSONObject(encryptedPropertiesJson) : new JSONObject();
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
