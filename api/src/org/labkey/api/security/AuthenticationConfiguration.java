package org.labkey.api.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationManager.AuthLogoType;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.PrimaryAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface AuthenticationConfiguration<AP extends AuthenticationProvider> extends AttachmentParent
{
    Logger LOG = LogManager.getLogger(AuthenticationConfiguration.class);

    // All the AuthenticationProvider interfaces. This list is used by AuthenticationProviderCache to filter collections of providers.
    List<Class<? extends AuthenticationConfiguration>> ALL_CONFIGURATION_INTERFACES = Arrays.asList(
        AuthenticationConfiguration.class,
            PrimaryAuthenticationConfiguration.class,
                LoginFormAuthenticationConfiguration.class,
                SSOAuthenticationConfiguration.class,
            SecondaryAuthenticationConfiguration.class
    );

    int getRowId();
    @NotNull String getDescription();
    default @Nullable String getDetails()
    {
        return null;
    }
    @NotNull AP getAuthenticationProvider();
    boolean isEnabled();
    @NotNull Map<String, Object> getCustomProperties();
    default @Nullable String getDomain()
    {
        return null;
    }

    /**
     * @return Map of all property names and values that are updateable and appropriate for audit logging
     */
    default @NotNull Map<String, Object> getLoggingProperties()
    {
        HashMap<String, Object> map = new HashMap<>();
        map.put("description", getDescription());
        map.put("enabled", isEnabled());
        map.putAll(getCustomProperties());

        return map;
    }

    interface PrimaryAuthenticationConfiguration<AP extends PrimaryAuthenticationProvider> extends AuthenticationConfiguration<AP>
    {
        default @Nullable URLHelper logout(HttpServletRequest request, @Nullable URLHelper returnURL)
        {
            return null;
        }
    }

    interface LoginFormAuthenticationConfiguration<AP extends LoginFormAuthenticationProvider<?>> extends PrimaryAuthenticationConfiguration<AP>
    {
    }

    interface SSOAuthenticationConfiguration<AP extends SSOAuthenticationProvider> extends PrimaryAuthenticationConfiguration<AP>
    {
        LinkFactory getLinkFactory();
        URLHelper getUrl(String secret, ViewContext ctx);

        /**
         * Allows an SSO auth configuration to specify that it should be used automatically instead of showing the standard
         * login form with an SSO link. Ex. if CAS auth is the only option, allow autoRedirect to that provider URL instead
         * of showing the login page.
         * @return boolean indicates if this configuration is set to autoRedirect
         */
        boolean isAutoRedirect();

        void savePlaceholderLogos(User user);

        default void ensureLogos(SSOAuthenticationConfiguration configuration, User user, String prefix)
        {
            ensureLogo(configuration, user, AuthLogoType.HEADER, prefix + "_small.png");
            ensureLogo(configuration, user, AuthLogoType.LOGIN_PAGE, prefix + "_big.png");
        }

        default void ensureLogo(SSOAuthenticationConfiguration configuration, User user, AuthLogoType logoType, String filename)
        {
            AttachmentService svc = AttachmentService.get();
            Attachment att = svc.getAttachment(configuration, logoType.getFileName());

            if (null == att)
            {
                LOG.info("Saving a placeholder " + logoType.getLabel() + " logo for \"" + configuration.getDescription() + "\"");
                try (InputStream is = configuration.getClass().getResourceAsStream(filename))
                {
                    if (null != is)
                    {
                        AttachmentFile file = new InputStreamAttachmentFile(is, logoType.getFileName());
                        svc.addAttachments(configuration, Collections.singletonList(file), user);
                        SsoSaveConfigurationAction.logLogoAction(user, configuration, logoType, "saved");
                    }
                }
                catch (Exception e)
                {
                    LOG.warn("Error while attempting to save placeholder logo", e);
                }
            }
        }
    }

    interface SecondaryAuthenticationConfiguration<AP extends SecondaryAuthenticationProvider<? extends SecondaryAuthenticationConfiguration<?>>> extends AuthenticationConfiguration<AP>
    {
        @NotNull
        @Override
        AP getAuthenticationProvider();

        ActionURL getRedirectURL(User candidate, Container c);
    }
}
