package org.labkey.api.security;

import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;

public abstract class SaveConfigurationForm
{
    private Integer _configuration;
    private String _description;
    private boolean _enabled = true;

    protected String _domain; // Member and getters for this are provided for convenience, but each supporting subclass must add this to its custom properties map

    public Integer getRowId()
    {
        return _configuration;
    }

    public void setRowId(Integer rowId)
    {
        _configuration = rowId;
    }

    public @Nullable Integer getConfiguration()
    {
        return _configuration;
    }

    public void setConfiguration(Integer configuration)
    {
        _configuration = configuration;
    }

    public abstract String getProvider();

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isEnabled()
    {
        return _enabled;
    }

    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }

    public String getDomain()
    {
        return _domain;
    }

    public void setDomain(String domain)
    {
        _domain = domain;
    }

    public @Nullable String getProperties()
    {
        return null;
    }

    public @Nullable String getEncryptedProperties()
    {
        return null;
    }

    protected String encodeEncryptedProperties(JSONObject map)
    {
        if (Encryption.isEncryptionPassPhraseSpecified())
        {
            return Base64.encodeBase64String(AES.get().encrypt(map.toString()));
        }
        else
        {
            throw new ConfigurationException("Can't save this configuration: EncryptionKey has not been specified in " + AppProps.getInstance().getWebappConfigurationFilename());
        }
    }
}
