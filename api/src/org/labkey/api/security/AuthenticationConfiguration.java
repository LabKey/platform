package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationManager.AuthLogoType;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.PrimaryAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.labkey.api.security.SsoSaveConfigurationAction.logLogoAction;

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
     * Implement any Configuration-specific startup property handling
     */
    default void handleStartupProperties(Map<String, String> propertyMap)
    {
    }

    /**
     * @return Map of all property names and values that are updatable and appropriate for audit logging
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

        @Override
        default void handleStartupProperties(Map<String, String> map)
        {
            boolean changed = handleStartupLogo(map, "HeaderLogo", AuthLogoType.HEADER);
            changed |= handleStartupLogo(map, "LoginPageLogo", AuthLogoType.LOGIN_PAGE);

            if (changed)
            {
                // Clear the image cache so the web server sends the new logos
                AttachmentCache.clearAuthLogoCache();
                // Bump the look & feel revision to force browsers to retrieve new logos
                WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            }
        }

        default boolean handleStartupLogo(Map<String, String> map, String key, AuthLogoType type)
        {
            boolean changed = false;
            String value = map.get(key);

            if (null != value)
            {
                User user = User.getSearchUser();
                AttachmentService.get().deleteAttachment(this, type.getFileName(), user);
                logLogoAction(user, this, type, "deleted");
                changed = true;

                if (!StringUtils.isBlank(value))
                {
                    final Supplier<InputStream> supplier;

                    try
                    {
                        if ("PLACEHOLDER".equals(value))
                        {
                            supplier = () -> getClass().getResourceAsStream(type.getPlaceholderFilename(this));
                        }
                        else
                        {
                            supplier = () -> {
                                try
                                {
                                    return new FileInputStream(new File(ModuleLoader.getInstance().getStartupPropDirectory(), value));
                                }
                                catch (FileNotFoundException e)
                                {
                                    throw new RuntimeException(e);
                                }
                            };
                        }

                        try (InputStream is = supplier.get())
                        {
                            saveLogo(user, type, is);
                        }
                    }
                    catch (Exception e)
                    {
                        LOG.error("Failed to save authentication logo " + value, e);
                    }
                }
            }

            return changed;
        }

        // Note: Callers must close the InputStream
        default void saveLogo(User user, AuthLogoType logoType, InputStream logoStream)
        {
            if (null != logoStream)
            {
                try
                {
                    AttachmentService svc = AttachmentService.get();
                    AttachmentFile file = new InputStreamAttachmentFile(logoStream, logoType.getFileName());
                    svc.addAttachments(this, Collections.singletonList(file), user);
                    logLogoAction(user, this, logoType, "saved");
                }
                catch (Exception e)
                {
                    LOG.warn("Error while attempting to save logo", e);
                }
            }
        }

        // TODO: Delete everything below once earliest upgrade is later than 21.008. See #43979.
        void savePlaceholderLogos(User user);

        default void ensureLogos(User user, String prefix)
        {
            ensureLogo(user, AuthLogoType.HEADER,  prefix + "_small.png");
            ensureLogo(user, AuthLogoType.LOGIN_PAGE, prefix + "_big.png");
        }

        default void ensureLogo(User user, AuthLogoType logoType, String filename)
        {
            AttachmentService svc = AttachmentService.get();
            Attachment att = svc.getAttachment(this, logoType.getFileName());

            if (null == att)
            {
                LOG.info("Saving a placeholder " + logoType.getLabel() + " logo for \"" + getDescription() + "\"");
                try (InputStream is = getClass().getResourceAsStream(filename))
                {
                    saveLogo(user, logoType, is);
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
