package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider.REQUIRED_FOR;

public abstract class SecondarySaveConfigurationForm extends SaveConfigurationForm
{
    private String _requiredFor = null;

    public String getRequiredFor()
    {
        return _requiredFor;
    }

    @SuppressWarnings("unused")
    public void setRequiredFor(String requiredFor)
    {
        _requiredFor = requiredFor;
    }

    @Override
    public @NotNull Map<String, Object> getPropertyMap()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(REQUIRED_FOR, getRequiredFor());
        return map;
    }
}
