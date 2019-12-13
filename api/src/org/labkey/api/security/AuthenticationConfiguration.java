package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface AuthenticationConfiguration<AP extends AuthenticationProvider> extends AttachmentParent
{
    // All the AuthenticationProvider interfaces. This list is used by AuthenticationProviderCache to filter collections of providers.
    List<Class<? extends AuthenticationConfiguration>> ALL_CONFIGURATION_INTERFACES = Arrays.asList(
        AuthenticationConfiguration.class,
            SSOAuthenticationConfiguration.class,
            LoginFormAuthenticationConfiguration.class

            // TODO: More to come...
    );

    int getRowId();
    @NotNull String getDescription();
    @NotNull AP getAuthenticationProvider();
    boolean isEnabled();
    Map<String, Object> getCustomProperties();

    interface SSOAuthenticationConfiguration<AP extends SSOAuthenticationProvider> extends AuthenticationConfiguration<AP>
    {
        LinkFactory getLinkFactory();
        URLHelper getUrl(String secret, ViewContext ctx);

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
