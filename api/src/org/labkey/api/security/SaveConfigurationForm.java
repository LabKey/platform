package org.labkey.api.security;

import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;

import java.util.Map;
import java.util.Objects;

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

    public @NotNull Map<String, Object> getPropertyMap()
    {
        return Map.of();
    }

    @SuppressWarnings("UnusedDeclaration")
    public final @Nullable String getProperties()
    {
        Map<String, Object> map = getPropertyMap();
        assert Objects.equals(map.get("domain"), getDomain());
        return map.isEmpty() ? null : new JSONObject(map).toString();
    }

    public @NotNull Map<String, Object> getEncryptedPropertyMap()
    {
        return Map.of();
    }

    @SuppressWarnings("UnusedDeclaration")
    public final @Nullable String getEncryptedProperties()
    {
        Map<String, Object> map = getEncryptedPropertyMap();
        return map.isEmpty() ? null : encodeEncryptedProperties(new JSONObject(map));
    }

    private String encodeEncryptedProperties(JSONObject map)
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
