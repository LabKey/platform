package org.labkey.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationConfiguration.SecondaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;

import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider.REQUIRED_FOR;

public abstract class BaseSecondaryAuthenticationConfiguration<AP extends SecondaryAuthenticationProvider<?>> extends BaseAuthenticationConfiguration<AP> implements SecondaryAuthenticationConfiguration<AP>
{
    private final @NotNull AuthenticationConfiguration.SecondaryAuthenticationConfiguration.RequiredFor _requiredFor;

    public BaseSecondaryAuthenticationConfiguration(AP provider, Map<String, Object> standardSettings, Map<String, Object> properties)
    {
        super(provider, standardSettings);
        _requiredFor = EnumUtils.getEnum(RequiredFor.class, (String)properties.get(REQUIRED_FOR), RequiredFor.all);
    }

    @Override
    public @Nullable String getNotRequiredMessage(User user, HttpServletRequest request)
    {
        return _requiredFor.isRequired(user) ? null : "lacks the \"Require Secondary Authentication\" role";
    }

    @Override
    public @NotNull Map<String, Object> getCustomProperties()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(REQUIRED_FOR, _requiredFor.name());

        return map;
    }
}
