package org.labkey.api.security;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ReturnUrlForm;

public abstract class AuthenticationConfigureForm<AC extends AuthenticationConfiguration> extends ReturnUrlForm
{
    private Integer _configuration;
    private AC _authenticationConfiguration;
    private String _description;
    private boolean _enabled = true;

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

    public @Nullable AC getAuthenticationConfiguration()
    {
        return _authenticationConfiguration;
    }

    public void setAuthenticationConfiguration(AC authenticationConfiguration)
    {
        _authenticationConfiguration = authenticationConfiguration;

        if (null != _authenticationConfiguration)
        {
            _description = _authenticationConfiguration.getDescription();
            _enabled = _authenticationConfiguration.isEnabled();
        }
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
}
