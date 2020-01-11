package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.annotations.RemoveIn20_1;

@RemoveIn20_1 // Going forward, no need to extend ReturnUrlForm. Rename this to SaveConfigurationForm.
public abstract class AuthenticationConfigureForm<AC extends AuthenticationConfiguration> extends ReturnUrlForm
{
    private Integer _configuration;
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

    @RemoveIn20_1 // Remove once FormViewAction configuration pages are gone
    public void setAuthenticationConfiguration(@NotNull AC authenticationConfiguration)
    {
        _description = authenticationConfiguration.getDescription();
        _enabled = authenticationConfiguration.isEnabled();
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
