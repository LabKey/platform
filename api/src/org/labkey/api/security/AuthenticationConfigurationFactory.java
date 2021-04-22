package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;

public interface AuthenticationConfigurationFactory<AC extends AuthenticationConfiguration<?>>
{
    // Translate a single ConfigurationSettings into an AuthenticationConfiguration
    AC getAuthenticationConfiguration(@NotNull ConfigurationSettings cs);
}
