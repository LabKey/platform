package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.util.URLHelper;

import java.util.Arrays;
import java.util.List;

public interface AuthenticationConfiguration<AP extends AuthenticationProvider>
{
    // All the AuthenticationProvider interfaces. This list is used by AuthenticationProviderCache to filter collections of providers.
    List<Class<? extends AuthenticationConfiguration>> ALL_CONFIGURATION_INTERFACES = Arrays.asList(
        AuthenticationConfiguration.class,
        SSOAuthenticationConfiguration.class
        // TODO: More to come...
    );

    @NotNull String getKey();
    @NotNull String getName();
    @NotNull AP getAuthenticationProvider();
    boolean enabled();

    interface SSOAuthenticationConfiguration<AP extends SSOAuthenticationProvider> extends AuthenticationConfiguration
    {
        @NotNull
        @Override
        AP getAuthenticationProvider();
        LinkFactory getLinkFactory();
        URLHelper getUrl(String secret);

        /**
         * Allows an SSO auth configuration to specify that it should be used automatically instead of showing the standard
         * login form with an SSO link. Ex. if CAS auth is the only option, allow autoRedirect to that provider URL instead
         * of showing the login page.
         * @return boolean indicates if this configuration is set to autoRedirect
         */
        default boolean isAutoRedirect()
        {
            return false;
        }
    }
}
