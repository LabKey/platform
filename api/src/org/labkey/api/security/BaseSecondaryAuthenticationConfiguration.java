package org.labkey.api.security;

import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.AuthenticationConfiguration.SecondaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseSecondaryAuthenticationConfiguration<AP extends SecondaryAuthenticationProvider<?>> extends BaseAuthenticationConfiguration<AP> implements SecondaryAuthenticationConfiguration<AP>
{
    private final @NotNull AuthenticationConfiguration.SecondaryAuthenticationConfiguration.RequiredFor _requiredFor;

    public BaseSecondaryAuthenticationConfiguration(AP provider, Map<String, Object> standardSettings, Map<String, Object> properties)
    {
        super(provider, standardSettings);
        _requiredFor = EnumUtils.getEnum(RequiredFor.class, (String)properties.get("requiredFor"), RequiredFor.all);
    }

    @Override
    public @NotNull AuthenticationConfiguration.SecondaryAuthenticationConfiguration.RequiredFor getRequiredFor()
    {
        return _requiredFor;
    }

    @Override
    public boolean isRequired(User user)
    {
        return _requiredFor.isRequired(user);
    }

    @Override
    public @NotNull Map<String, Object> getCustomProperties()
    {
        Map<String, Object> map = new HashMap<>();
        map.put("requiredFor", getRequiredFor().name());

        return map;
    }
}
