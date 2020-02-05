package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public interface AuthenticationConfigurationFactory<AC extends AuthenticationConfiguration<?>>
{
    // Providers that need to do special batch-wide processing can override this method
    default List<AC> getAuthenticationConfigurations(@NotNull List<ConfigurationSettings> configurations)
    {
        return configurations.stream()
            .map(this::getAuthenticationConfiguration)
            .collect(Collectors.toList());
    }

    // Most providers need to override this method to translate a single ConfigurationSettings into an AuthenticationConfiguration
    default AC getAuthenticationConfiguration(@NotNull ConfigurationSettings cs)
    {
        throw new IllegalStateException("Shouldn't invoke this method for " + getClass().getName());
    }
}
