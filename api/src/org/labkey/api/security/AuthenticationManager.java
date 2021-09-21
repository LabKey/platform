/*
 * Copyright (c) 2008-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.FormattedError;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Project;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationConfiguration.LoginFormAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.PrimaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.SecondaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationProvider.AuthenticationResponse;
import org.labkey.api.security.AuthenticationProvider.DisableLoginProvider;
import org.labkey.api.security.AuthenticationProvider.ExpireAccountProvider;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.PrimaryAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.ResetPasswordProvider;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.AuthenticationSettingsAuditTypeProvider.AuthSettingsAuditEvent;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdateUserPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Rate;
import org.labkey.api.util.RateLimiter;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.template.PageConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;
import static org.labkey.api.security.AuthenticationProvider.FailureReason.complexity;
import static org.labkey.api.security.AuthenticationProvider.FailureReason.expired;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:53:03 PM
 */
public class AuthenticationManager
{
    public static final String ALL_DOMAINS = "*";

    private static final Logger _log = LogManager.getLogger(AuthenticationManager.class);
    // All registered authentication providers (DbLogin, LDAP, SSO, etc.)
    private static final List<AuthenticationProvider> _allProviders = new CopyOnWriteArrayList<>();

    public enum AuthLogoType
    {
        HEADER("header", "auth_header_logo", "16"),
        LOGIN_PAGE("login page", "auth_login_page_logo", "32");

        private final String _label;
        private final String _fileName;
        private final String _height;

        AuthLogoType(String label, String fileName, String height)
        {
            _label = label;
            _fileName = fileName;
            _height = height;
        }

        public String getLabel()
        {
            return _label;
        }

        public String getFileName()
        {
            return _fileName;
        }

        public String getHeight()
        {
            return _height;
        }

        public String getOldPrefix()
        {
            return _fileName + "_";
        }

        public static @NotNull AuthLogoType getForFilename(String fileName)
        {
            for (AuthLogoType type : values())
                if (type.getFileName().equals(fileName))
                    return type;

            throw new NotFoundException("Unknown logo type");
        }
    }

//    We don't register any RequestAuthenticationProvider implementations right now, so don't bother converting this code
//    to handling configurations.
//
//    public static @Nullable User attemptRequestAuthentication(HttpServletRequest request)
//    {
//        for (RequestAuthenticationProvider provider : AuthenticationManager.getActiveProviders(RequestAuthenticationProvider.class))
//        {
//            AuthenticationResponse response = provider.authenticate(request);
//
//            if (response.isAuthenticated())
//            {
//                PrimaryAuthenticationResult result = finalizePrimaryAuthentication(request, response);
//                return result.getUser();
//            }
//        }
//
//        return null;
//    }

    // Called unconditionally on every server startup. Properties that are designated as "startup" will overwrite existing
    // values. Authentication configuration properties will overwrite an existing configuration if "Description" is provided
    // and matches an existing configuration description for the same provider; if "Description" is not provided or doesn't
    // match an existing configuration for that provider then a new configuration will be created. See #39474.
    // TODO: SSO logos. Auditing of configuration property changes.
    public static void populateSettingsWithStartupProps()
    {
        // Handle each provider's startup properties
        getAllProviders().forEach(AuthenticationProvider::handleStartupProperties);

        // Populate the general authentication properties (e.g., auto-create accounts, self registration, self-service email changes, default domain)
        ModuleLoader.getInstance().getConfigProperties(AUTHENTICATION_CATEGORY)
            .forEach(cp-> {
                switch(cp.getName())
                {
                    case SELF_REGISTRATION_KEY, AUTO_CREATE_ACCOUNTS_KEY, SELF_SERVICE_EMAIL_CHANGES_KEY -> saveAuthSetting(null, cp.getName(), Boolean.parseBoolean(cp.getValue()));
                    case DEFAULT_DOMAIN -> setDefaultDomain(null, cp.getValue());
                    default -> _log.warn("Property '" + cp.getName() + "' does not map to a known authentication property");
                }
            });
    }

    public enum Priority { High, Low }

    public static HtmlString getStandardSendVerificationEmailsMessage()
    {
        HtmlStringBuilder builder = HtmlStringBuilder.of("Send password verification emails to all new users");
        Collection<String> activeDomains = AuthenticationConfigurationCache.getActiveDomains();

        if (!activeDomains.isEmpty())
        {
            // At the moment, only LDAP configurations can be associated with a domain, so we call out LDAP below
            builder.append(" except those with email addresses that are configured for LDAP authentication (those ending in ");
            builder.append(
                activeDomains.stream()
                    .map(d->"@" + d)
                    .collect(Collectors.joining(", "))
            );

            builder.append(")");
        }

        return builder.getHtmlString();
    }

    // Ignores domain = "*"
    public static boolean isLdapEmail(ValidEmail email)
    {
        String emailAddress = email.getEmailAddress();
        return AuthenticationConfigurationCache.getActiveDomains().stream()
            .anyMatch(domain->StringUtils.endsWithIgnoreCase(emailAddress, "@" + domain));
    }

    public static boolean isRegistrationEnabled()
    {
        return getAuthSetting(SELF_REGISTRATION_KEY, false);
    }

    public static boolean isAutoCreateAccountsEnabled()
    {
        return isExternalConfigurationEnabled() && getAuthSetting(AUTO_CREATE_ACCOUNTS_KEY, true);
    }

    public static boolean isSelfServiceEmailChangesEnabled() { return getAuthSetting(SELF_SERVICE_EMAIL_CHANGES_KEY, false);}

    public static @NotNull String getDefaultDomain()
    {
        Map<String, String> props = PropertyManager.getProperties(AUTHENTICATION_CATEGORY);
        String value = props.get(DEFAULT_DOMAIN);

        return value == null ? "" : value;
    }

    public static void setDefaultDomain(User user, String value)
    {
        value = StringUtils.trimToEmpty(value);
        if (!Objects.equals(AuthenticationManager.getDefaultDomain(), value))
            saveAuthSetting(user, DEFAULT_DOMAIN, value, "set to " + value);
    }

    public static boolean getAuthSetting(String key, boolean defaultValue)
    {
        Map<String, String> props = PropertyManager.getProperties(AUTHENTICATION_CATEGORY);
        String value = props.get(key);

        return value == null ? defaultValue : Boolean.valueOf(value);
    }

    public static void saveAuthSetting(User user, String key, boolean value)
    {
        saveAuthSetting(user, key, Boolean.toString(value), value ? "enabled" : "disabled");
    }

    private static void saveAuthSetting(User user, String key, String value, String action)
    {
        PropertyMap props = PropertyManager.getWritableProperties(AUTHENTICATION_CATEGORY, true);
        props.put(key, value);
        addAuthSettingAuditEvent(user, key, action);
        props.save();
    }

    public static void saveAuthSettings(User user, Map<String, Boolean> map)
    {
        PropertyMap props = PropertyManager.getProperties(AUTHENTICATION_CATEGORY);

        map.entrySet().stream()
            .filter(e->!Boolean.toString(e.getValue()).equals(props.get(e.getKey())))
            .forEach(e->saveAuthSetting(user, e.getKey(), e.getValue()));
    }

    public static void reorderConfigurations(User user, String name, int[] rowIds)
    {
        if (null != rowIds && rowIds.length != 0)
        {
            TableInfo tinfo = CoreSchema.getInstance().getTableInfoAuthenticationConfigurations();
            MutableInt count = new MutableInt();
            Arrays.stream(rowIds)
                .filter(id->id > 0)
                .forEach(id->{
                    count.increment();
                    Table.update(user, tinfo, new HashMap<>(Map.of("SortOrder", count)), id); // Table.update() requires mutable map
                });
            AuthSettingsAuditEvent event = new AuthSettingsAuditEvent(name + " configurations were reordered");
            event.setChanges("reordered");
            AuditLogService.get().addEvent(user, event);
            AuthenticationConfigurationCache.clear();
        }
    }

    public static @Nullable HtmlString getHeaderLogoHtml(URLHelper currentURL)
    {
        return getAuthLogoHtml(currentURL, AuthLogoType.HEADER);
    }

    public static @Nullable HtmlString getLoginPageLogoHtml(URLHelper currentURL)
    {
        return getAuthLogoHtml(currentURL, AuthLogoType.LOGIN_PAGE);
    }

    public static Map<String, Object> getLoginPageConfiguration(Project project)
    {
        Map<String, Object> config = new HashMap<>();
        config.put("registrationEnabled", isRegistrationEnabled());
        config.put("requiresTermsOfUse", WikiTermsOfUseProvider.isTermsOfUseRequired(project));
        config.put("hasOtherLoginMechanisms", hasSSOAuthenticationConfiguration());
        return config;
    }

    /** Return all registered providers */
    static List<AuthenticationProvider> getAllProviders()
    {
        return _allProviders;
    }

    public static boolean hasSSOAuthenticationConfiguration()
    {
        return !AuthenticationConfigurationCache.getActive(SSOAuthenticationConfiguration.class).isEmpty();
    }

    private static @Nullable HtmlString getAuthLogoHtml(URLHelper currentURL, AuthLogoType logoType)
    {
        Collection<SSOAuthenticationConfiguration> ssoConfigurations = AuthenticationConfigurationCache.getActive(SSOAuthenticationConfiguration.class);

        if (ssoConfigurations.isEmpty())
            return null;

        HtmlStringBuilder html = HtmlStringBuilder.of("");

        for (SSOAuthenticationConfiguration configuration : ssoConfigurations)
        {
            if (!configuration.isAutoRedirect())
            {
                LinkFactory factory = configuration.getLinkFactory();
                html.startTag("li");
                html.append(factory.getLink(currentURL, logoType));
                html.endTag("li");
            }
        }

        return html.getHtmlString();
    }

    public static class AuthenticationConfigurationForm
    {
        private int _configuration;

        public int getConfiguration()
        {
            return _configuration;
        }

        @SuppressWarnings("unused")
        public void setConfiguration(int configuration)
        {
            _configuration = configuration;
        }
    }

    public static abstract class BaseSsoValidateAction<FORM extends AuthenticationConfigurationForm> extends SimpleViewAction<FORM>
    {
        @Override
        public ModelAndView getView(FORM form, BindException errors) throws Exception
        {
            AuthenticationResponse response = validateAuthentication(form, errors);

            // Show validation error(s), if any
            if (errors.hasErrors() || !response.isAuthenticated())
            {
                if (!errors.hasErrors())
                    errors.addError(new LabKeyError("Bad credentials"));
            }
            else
            {
                HttpServletRequest request = getViewContext().getRequest();

                final PrimaryAuthenticationResult primaryResult;

                // Some SSO protocols allow GET, but validation requires a secret so it's not susceptible to CSRF attacks
                try (var ignored = SpringActionController.ignoreSqlUpdates())
                {
                    primaryResult = AuthenticationManager.finalizePrimaryAuthentication(request, response);
                }

                if (null != primaryResult.getUser())
                {
                    AuthenticationManager.setPrimaryAuthenticationResult(request, primaryResult);
                    AuthenticationResult result = AuthenticationManager.handleAuthentication(request, getContainer());

                    return HttpView.redirect(result.getRedirectURL());
                }

                primaryResult.getStatus().addUserErrorMessage(errors, primaryResult);
            }

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setIncludeLoginLink(false);
            getPageConfig().setIncludeSearch(false);

            return new SimpleErrorView(errors, false);
        }

        @Override
        protected String getCommandClassMethodName()
        {
            return "validateAuthentication";
        }

        public abstract @NotNull AuthenticationResponse validateAuthentication(FORM form, BindException errors) throws Exception;

        @Override
        public final void addNavTrail(NavTree root)
        {
            root.addChild("Validate Authentication");
        }
    }


    public static void registerProvider(AuthenticationProvider authProvider)
    {
        registerProvider(authProvider, Priority.High);
    }


    public static void registerProvider(AuthenticationProvider authProvider, Priority priority)
    {
        if (Priority.High == priority)
            _allProviders.add(0, authProvider);
        else
            _allProviders.add(authProvider);

        AuthenticationProviderCache.clear();
        AuthenticationConfigurationCache.clear();
    }


    public static void deleteConfiguration(User user, int rowId)
    {
        // Delete any logos attached to the configuration
        AuthenticationConfiguration configuration = AuthenticationConfigurationCache.getConfiguration(AuthenticationConfiguration.class, rowId);
        AttachmentService.get().deleteAttachments(configuration);

        // Delete configuration
        Table.delete(CoreSchema.getInstance().getTableInfoAuthenticationConfigurations(), rowId);
        AuthSettingsAuditEvent event = new AuthSettingsAuditEvent(configuration.getAuthenticationProvider().getName() + " authentication configuration \"" + configuration.getDescription() + "\" (" + rowId + ") was deleted");
        event.setChanges("deleted");
        AuditLogService.get().addEvent(user, event);
        AuthenticationConfigurationCache.clear();
    }

    private static void addAuthSettingAuditEvent(User user, String name, String action)
    {
        AuthSettingsAuditEvent event = new AuthSettingsAuditEvent(name + " setting was " + action);
        event.setChanges(action);
        AuditLogService.get().addEvent(user, event);
    }

    public static @Nullable SSOAuthenticationConfiguration getActiveSSOConfiguration(@Nullable Integer key)
    {
        return null != key ? AuthenticationConfigurationCache.getActiveConfiguration(SSOAuthenticationConfiguration.class, key) : null;
    }

    public static @NotNull <AC extends AuthenticationConfiguration> Collection<AC> getActiveConfigurations(Class<AC> clazz)
    {
        return AuthenticationConfigurationCache.getActive(clazz);
    }

    public static @Nullable SSOAuthenticationConfiguration getSSOConfiguration(int rowId)
    {
        return AuthenticationConfigurationCache.getConfiguration(SSOAuthenticationConfiguration.class, rowId);
    }

    public static @Nullable ResetPasswordProvider getResetPasswordProvider(String name)
    {
        return AuthenticationProviderCache.getProvider(ResetPasswordProvider.class, name);
    }

    public static @Nullable DisableLoginProvider getEnabledDisableLoginProviderForUser(String id)
    {
        for (DisableLoginProvider provider : AuthenticationProviderCache.getProviders(DisableLoginProvider.class))
            if (provider.isEnabledForUser(id))
                return provider;

        return null;
    }

    /**
     * @return True if any ExpireAccountProvider is enabled, else false
     */
    public static boolean isAccountExpirationEnabled()
    {
        for (ExpireAccountProvider provider : AuthenticationProviderCache.getProviders(ExpireAccountProvider.class))
            if (provider.isEnabled())
                return true;
        return false;
    }

    /**
     * Check permissions for those allowed to edit expiration dates
     * @param userManager to check permissions for
     * @param container to check against
     * @return True if user is an Admin or UserManager and AccountExpiration is enabled, false otherwise
     */
    public static boolean canSetUserExpirationDate(User userManager, Container container)
    {
        boolean canUpdate = userManager.hasRootPermission(UpdateUserPermission.class);
        boolean isAdmin = container.hasPermission(userManager, AdminPermission.class);
        return (canUpdate || isAdmin) && AuthenticationManager.isAccountExpirationEnabled();
    }

    /**
     * @return true if only FICAM approved authentication providers are enabled
     */
    public static boolean isAcceptOnlyFicamProviders()
    {
        return getAuthSetting(ACCEPT_ONLY_FICAM_PROVIDERS_KEY, false);
    }

    public static void setAcceptOnlyFicamProviders(User user, boolean enable)
    {
        saveAuthSetting(user, ACCEPT_ONLY_FICAM_PROVIDERS_KEY, enable);
        AuthenticationConfigurationCache.clear();
    }

    // leave public until upgrade from 19.3 is no longer allowed (and CoreUpgrade migration method is removed)
    public static final String AUTHENTICATION_CATEGORY = "Authentication";
    public static final String PROVIDERS_KEY = "Authentication";

    public static final String SELF_REGISTRATION_KEY = "SelfRegistration";
    public static final String AUTO_CREATE_ACCOUNTS_KEY = "AutoCreateAccounts";
    public static final String DEFAULT_DOMAIN = "DefaultDomain";
    public static final String SELF_SERVICE_EMAIL_CHANGES_KEY = "SelfServiceEmailChanges";
    public static final String ACCEPT_ONLY_FICAM_PROVIDERS_KEY = "AcceptOnlyFicamProviders";

    /**
     * Return the first SSOAuthenticationConfiguration that is set to auto redirect from the login page.
     */
    public static @Nullable SSOAuthenticationConfiguration getAutoRedirectSSOAuthConfiguration()
    {
        for (SSOAuthenticationConfiguration config : AuthenticationConfigurationCache.getActive(SSOAuthenticationConfiguration.class))
        {
            if (config.isAutoRedirect())
                return config;
        }

        return null;
    }

    public enum AuthenticationStatus
    {
        Success
        {
            @Override
            public void addUserErrorMessage(BindException errors, PrimaryAuthenticationResult result)
            {
                throw new IllegalStateException("Shouldn't be adding an error message in success case");
            }
        },
        BadCredentials
        {
            @Override
            public void addUserErrorMessage(BindException errors, PrimaryAuthenticationResult result)
            {
                errors.reject(ERROR_MSG, "The email address and password you entered did not match any accounts on file.\nNote: Passwords are case sensitive; make sure your Caps Lock is off.");
            }
        },
        InactiveUser
        {
            @Override
            public void addUserErrorMessage(BindException errors, PrimaryAuthenticationResult result)
            {
                errors.addError(new FormattedError("Your account has been deactivated. " + AppProps.getInstance().getAdministratorContactHTML() + " if you need to reactivate this account."));
            }
        },
        LoginDisabled
        {
            @Override
            public void addUserErrorMessage(BindException errors, PrimaryAuthenticationResult result)
            {
                String errorMessage = result.getMessage() == null ? "Due to the number of recent failed login attempts, authentication has been temporarily paused.\nTry again in one minute." : result.getMessage();
                errors.reject(ERROR_MSG, errorMessage);
            }
        },
        LoginPaused
        {
            @Override
            public void addUserErrorMessage(BindException errors, PrimaryAuthenticationResult result)
            {
                errors.reject(ERROR_MSG, "Due to the number of recent failed login attempts, authentication has been temporarily paused.\nTry again in one minute.");
            }
        },
        UserCreationError
        {
            @Override
            public void addUserErrorMessage(BindException errors, PrimaryAuthenticationResult result)
            {
                errors.addError(new FormattedError("The server could not create your account. " + AppProps.getInstance().getAdministratorContactHTML() + " for assistance."));
            }
        },
        UserCreationNotAllowed
        {
            @Override
            public void addUserErrorMessage(BindException errors, PrimaryAuthenticationResult result)
            {
                errors.addError(new FormattedError(AppProps.getInstance().getAdministratorContactHTML() + " to have your account created."));
            }
        },
        PasswordExpired
        {
            @Override
            public boolean requiresRedirect()
            {
                return true;
            }
        },
        Complexity
        {
            @Override
            public boolean requiresRedirect()
            {
                return true;
            }
        };

        // Add an appropriate error message to display to the user
        public void addUserErrorMessage(BindException errors, PrimaryAuthenticationResult result)
        {
        }

        public boolean requiresRedirect()
        {
            return false;
        }
    }

    public static class PrimaryAuthenticationResult
    {
        private final User _user;
        private final AuthenticationResponse _response;
        private final AuthenticationStatus _status;
        private final URLHelper _redirectURL;
        private final String _message;

        // Success case
        private PrimaryAuthenticationResult(@NotNull User user, AuthenticationResponse response)
        {
            _user = user;
            _response = response;
            _status = AuthenticationStatus.Success;
            _redirectURL = null;
            _message = null;
        }

        // Failure case
        public PrimaryAuthenticationResult(@NotNull AuthenticationStatus status)
        {
            _user = null;
            _response = null;
            _status = status;
            _redirectURL = null;
            _message = null;
        }

        // Failure case with error message
        public PrimaryAuthenticationResult(@NotNull AuthenticationStatus status, String message)
        {
            _user = null;
            _response = null;
            _status = status;
            _redirectURL = null;
            _message = message;
        }

        // Failure case with redirect (e.g., reset password page)
        private PrimaryAuthenticationResult(@NotNull URLHelper redirectURL, AuthenticationStatus status)
        {
            _user = null;
            _response = null;
            _status = status;
            _redirectURL = redirectURL;
            _message = null;
        }

        @Nullable
        public User getUser()
        {
            return _user;
        }

        public AuthenticationResponse getResponse()
        {
            return _response;
        }

        @NotNull
        public AuthenticationStatus getStatus()
        {
            return _status;
        }

        @Nullable
        public URLHelper getRedirectURL()
        {
            return _redirectURL;
        }

        public String getMessage()
        {
            return _message;
        }
    }


    /** avoid spamming the audit log **/
    private static final Cache<String, String> AUTH_MESSAGES = CacheManager.getCache(100, TimeUnit.MINUTES.toMillis(10), "Authentication Messages");

    public static void addAuditEvent(@NotNull User user, HttpServletRequest request, String msg)
    {
        String key = user.getUserId() + "/" + ((null==request||null==request.getLocalAddr())?"":request.getLocalAddr());
        String prevMessage = AUTH_MESSAGES.get(key);
        if (StringUtils.equals(prevMessage, msg))
            return;
        AUTH_MESSAGES.put(key, msg);
        if (user.isGuest())
        {
            UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(ContainerManager.getRoot().getId(), msg, user);
            AuditLogService.get().addEvent(user, event);
        }
        else
            UserManager.addAuditEvent(user, ContainerManager.getRoot(), user, msg);
    }


    public static @NotNull PrimaryAuthenticationResult authenticate(HttpServletRequest request, String id, String password, URLHelper returnURL, boolean logFailures) throws InvalidEmailException
    {
        PrimaryAuthenticationResult result = null;
        try
        {
            result = _beforeAuthenticate(request, id, password);
            if (null != result)
                return result;
            result = _authenticate(request, id, password, returnURL, logFailures);
            return result;
        }
        finally
        {
            _afterAuthenticate(request, id, password, result);
        }
    }


    private static @NotNull PrimaryAuthenticationResult _authenticate(HttpServletRequest request, String id, String password, URLHelper returnURL, boolean logFailures) throws InvalidEmailException
    {
        if (areNotBlank(id, password))
        {
            AuthenticationResponse firstFailure = null;

            for (LoginFormAuthenticationConfiguration<LoginFormAuthenticationProvider<?>> configuration : AuthenticationConfigurationCache.getActive(LoginFormAuthenticationConfiguration.class))
            {
                AuthenticationResponse authResponse;

                try
                {
                    LoginFormAuthenticationProvider provider = configuration.getAuthenticationProvider();
                    authResponse = provider.authenticate(configuration, id, password, returnURL);
                }
                catch (RedirectException e)
                {
                    // Some authentication provider has chosen to redirect (e.g., to retrieve auth credentials from
                    // a different server or to force change password due to expiration).
                    throw new RuntimeException(e);
                }

                if (authResponse.isAuthenticated())
                {
                    return finalizePrimaryAuthentication(request, authResponse);
                }
                else
                {
                    AuthenticationProvider.FailureReason reason = authResponse.getFailureReason();

                    switch (reason.getReportType())
                    {
                        // Log the first one we encounter to the audit log, always
                        case always:
                            if (null == firstFailure)
                                firstFailure = authResponse;
                            break;

                        // Log the first one we encounter to the audit log, but only if the login fails (i.e., this
                        // login may succeed on another provider)
                        case onFailure:
                            if (logFailures && null == firstFailure)
                                firstFailure = authResponse;
                            break;

                        // Just not the right provider... ignore.
                        case never:
                            break;

                        default:
                            assert false : "Unknown AuthenticationProvider.ReportType: " + reason.getReportType();
                    }
                }
            }

            // Login failed all form configurations... log the first interesting failure (but only if logFailures == true)
            if (null != firstFailure)
            {
                User user = null;
                String emailAddress = null;

                // Try to determine who is attempting to login.
                if (!StringUtils.isBlank(id))
                {
                    emailAddress = id;
                    ValidEmail email = null;

                    try
                    {
                        email = new ValidEmail(id);
                        emailAddress = email.getEmailAddress();  // If this user doesn't exist we can still report the normalized email address
                    }
                    catch (InvalidEmailException e)
                    {
                    }

                    if (null != email)
                        user = UserManager.getUser(email);
                }

                String ipAddress = request.getHeader("X-FORWARDED-FOR");
                if (ipAddress == null)
                    ipAddress = request.getRemoteAddr();
                String message = " failed to login: " + firstFailure.getFailureReason().getMessage() + " (" + ipAddress + ")";

                if (null != user)
                {
                    addAuditEvent(user, request, user.getEmail() + message);
                    _log.warn(user.getEmail() + message);
                }
                else if (null != emailAddress)
                {
                    // Funny audit case -- user doesn't exist, so there's no user to associate with the event. Use guest.
                    addAuditEvent(User.guest, request, emailAddress + message);
                    _log.warn(emailAddress + message);
                }
                else
                {
                    // Funny audit case -- user doesn't exist, so there's no user to associate with the event. Use guest.
                    addAuditEvent(User.guest, request, message);
                    _log.warn("Unknown user" + message);
                }

                // For now, redirectURL is only checked in the failure case, see #19778 for some history on redirect handling
                ActionURL redirectURL = firstFailure.getRedirectURL();

                if (null != redirectURL)
                {
                    // if labkey db authenticate determines password has expired or that password does not meet complexity requirements then return url to redirect user
                    firstFailure.getFailureReason();
                    if (firstFailure.getFailureReason() == expired)
                    {
                        return new PrimaryAuthenticationResult(redirectURL, AuthenticationStatus.PasswordExpired);
                    }
                    else if (firstFailure.getFailureReason() == complexity)
                    {
                        return new PrimaryAuthenticationResult(redirectURL, AuthenticationStatus.Complexity);
                    }
                    else
                    {
                        throw new RedirectException(redirectURL);
                    }
                }
            }
        }

        return new PrimaryAuthenticationResult(AuthenticationStatus.BadCredentials);
    }

    @NotNull
    public static PrimaryAuthenticationResult finalizePrimaryAuthentication(HttpServletRequest request, AuthenticationResponse response)
    {
        ValidEmail email = response.getValidEmail();
        User user;

        try
        {
            user = UserManager.getUser(email);

            // If user is authenticated but doesn't exist in our system...
            if (null == user)
            {
                // ...are we configured to allow auto-creation?
                if (isAutoCreateAccountsEnabled())
                {
                    // Yes: add user to the database
                    SecurityManager.NewUserStatus bean = SecurityManager.addUser(email, null, false);
                    user = bean.getUser();
                    UserManager.addToUserHistory(user, user.getEmail() + " authenticated successfully and was added to the system automatically.");
                }
                else
                {
                    // No: log that we're not permitted to create accounts automatically
                    addAuditEvent(User.guest, request, "User " + email + " successfully authenticated via " + response.getConfiguration().getDescription() + ". Login failed because account creation is disabled.");
                    return new PrimaryAuthenticationResult(AuthenticationStatus.UserCreationNotAllowed);
                }
            }
            UserManager.updateLogin(user);
        }
        catch (SecurityManager.UserManagementException e)
        {
            // Make sure we record any unexpected problems during user creation; one goal is to help track down cause of #20712
            ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.ExtraMessage, email.getEmailAddress(), true);
            ExceptionUtil.logExceptionToMothership(request, e);

            return new PrimaryAuthenticationResult(AuthenticationStatus.UserCreationError);
        }

        if (!user.isActive())
        {
            addAuditEvent(user, request, "Inactive user " + user.getEmail() + " attempted to login");
            return new PrimaryAuthenticationResult(AuthenticationStatus.InactiveUser);
        }

        addAuditEvent(user, request, email + " " + UserManager.UserAuditEvent.LOGGED_IN + " successfully via the \"" + response.getConfiguration().getDescription() + "\" configuration.");

        return new PrimaryAuthenticationResult(user, response);
    }


    // limit one bad login per second averaged out over 60sec
    private static final Cache<Integer, RateLimiter> addrLimiter = CacheManager.getCache(1001, TimeUnit.MINUTES.toMillis(5), "login limiter");
    private static final Cache<Integer, RateLimiter> userLimiter = CacheManager.getCache(1001, TimeUnit.MINUTES.toMillis(5), "user limiter");
    private static final Cache<Integer, RateLimiter> pwdLimiter = CacheManager.getCache(1001, TimeUnit.MINUTES.toMillis(5), "password limiter");
    private static final CacheLoader<Integer, RateLimiter> addrLoader = (key, request) -> new RateLimiter("Addr limiter: " + String.valueOf(key), new Rate(60,TimeUnit.MINUTES));
    private static final CacheLoader<Integer, RateLimiter> pwdLoader = (key, request) -> new RateLimiter("Pwd limiter: " + String.valueOf(key), new Rate(20,TimeUnit.MINUTES));
    private static final CacheLoader<Integer, RateLimiter> userLoader = (key, request) -> new RateLimiter("User limiter: " + String.valueOf(key), new Rate(20,TimeUnit.MINUTES));


    private static Integer _toKey(String s)
    {
        return null==s ? 0 : s.toLowerCase().hashCode() % 1000;
    }


    private static PrimaryAuthenticationResult _beforeAuthenticate(HttpServletRequest request, String id, String pwd)
    {
        if (null == id || null == pwd)
            return null;
        RateLimiter rl;
        long delay = 0;

        // slow down login attempts when we detect more than 20/minute bad attempts per user, password, or ip address
        rl = addrLimiter.get(_toKey(request==null?null:request.getRemoteAddr()));
        if (null != rl)
            delay = Math.max(delay,rl.add(0, false));
         rl = pwdLimiter.get(_toKey(pwd));
        if (null != rl)
            delay = Math.max(delay, rl.add(0, false));

        try
        {
            delay = Math.max(delay, getUserLoginDelay(id));
        }
        catch (LoginDisabledException e)
        {
            return new PrimaryAuthenticationResult(AuthenticationStatus.LoginDisabled, e.getMessage());
        }

        if (delay > 15*1000)
            return new PrimaryAuthenticationResult(AuthenticationStatus.LoginPaused);
        if (delay > 0)
            try {Thread.sleep(delay);}catch(InterruptedException x){/* */}
        return null;
    }

    private static long getUserLoginDelay(String id) throws LoginDisabledException
    {
        DisableLoginProvider provider = AuthenticationManager.getEnabledDisableLoginProviderForUser(id);
        if (provider != null)
            return provider.getUserDelay(id);
        return getDefaultUserLoginDelay(id);
    }

    private static long getDefaultUserLoginDelay(String id)
    {
        RateLimiter rl = userLimiter.get(_toKey(id));
        if (null != rl)
            return rl.add(0, false);
        return 0;
    }

    private static void _afterAuthenticate(HttpServletRequest request, String id, String pwd, PrimaryAuthenticationResult result)
    {
        if (null == result || null == id || null ==pwd)
            return;
        if (result.getStatus() ==  AuthenticationStatus.BadCredentials || result.getStatus() == AuthenticationStatus.InactiveUser)
        {
            RateLimiter rl;
            rl = addrLimiter.get(_toKey(request.getRemoteAddr()),request, addrLoader);
            rl.add(1, false);
            rl = pwdLimiter.get(_toKey(pwd),request, pwdLoader);
            rl.add(1, false);

            addUserLoginDelay(request, id);
        }
        else if (result.getStatus() ==  AuthenticationStatus.Success)
        {
            resetModuleUserLoginDelay(id);
        }
    }

    private static void resetModuleUserLoginDelay(String id)
    {
        DisableLoginProvider provider = AuthenticationManager.getEnabledDisableLoginProviderForUser(id);
        if (provider != null)
            provider.resetUserDelay(id);
    }

    private static void addUserLoginDelay(HttpServletRequest request, String id)
    {
        DisableLoginProvider provider = AuthenticationManager.getEnabledDisableLoginProviderForUser(id);
        if (provider != null)
            provider.addUserDelay(request, id, 1);
        else
            addDefaultUserLoginDelay(request, id);
    }

    private static void addDefaultUserLoginDelay(HttpServletRequest request, String id)
    {
        RateLimiter rl = userLimiter.get(_toKey(id),request, userLoader);
        rl.add(1, false);
    }

    // Attempts to authenticate using only LoginFormAuthenticationProviders (e.g., DbLogin, LDAP). This is for the case
    // where you have an id & password in hand and want to ignore SSO and other delegated authentication mechanisms that
    // rely on cookies, browser redirects, etc. Current usages include basic auth and test cases. Note that this will
    // always fail if any secondary authentication is enabled (e.g., Duo).

    // Returns null if credentials are incorrect, user doesn't exist, user is inactive, or secondary auth is enabled.
    public static @Nullable User authenticate(HttpServletRequest request, String id, String password) throws InvalidEmailException
    {
        PrimaryAuthenticationResult primaryResult = authenticate(request, id, password, null, true);

        // If primary authentication is successful then look for secondary authentication. handleAuthentication() will
        // always return a failure result (i.e., null user) if secondary authentication is enabled. #22944
        if (primaryResult.getStatus() == AuthenticationStatus.Success)
        {
            AuthenticationManager.setPrimaryAuthenticationResult(request, primaryResult);
            return handleAuthentication(request, ContainerManager.getRoot()).getUser();
        }

        return null;
    }


    public static URLHelper logout(@NotNull User user, HttpServletRequest request, URLHelper returnURL)
    {
        URLHelper ret = null;
        HttpSession session = request.getSession(false);

        if (null != session && !user.isGuest())
        {
            // notify websocket clients associated with this http session, the user has logged out
            NotificationService.get().closeServerEvents(user.getUserId(), session, AuthNotify.SessionLogOut);

            // notify any remaining websocket clients for this user that were not closed that the user has logged out elsewhere
            NotificationService.get().sendServerEvent(user.getUserId(), AuthNotify.LoggedOut);

            addAuditEvent(user, request, user.getEmail() + " " + UserManager.UserAuditEvent.LOGGED_OUT + ".");

            Integer configurationId = (Integer)session.getAttribute(SecurityManager.PRIMARY_AUTHENTICATION_CONFIGURATION);

            if (null != configurationId)
            {
                PrimaryAuthenticationConfiguration<?> configuration = AuthenticationConfigurationCache.getConfiguration(PrimaryAuthenticationConfiguration.class, configurationId);

                if (null != configuration)
                {
                    ret = configuration.logout(request, returnURL);
                }
            }
        }

        return ret;
    }

    /**
     * @return A case-insensitive map of user attribute names and values that was stashed in the associated session at
     * authentication time. This map will often be empty but will never be null.
     */
    public static @NotNull Map<String, String> getAuthenticationAttributes(HttpServletRequest request)
    {
        HttpSession session = request.getSession(false);

        return getAuthenticationAttributes(session);
    }

    public static @NotNull Map<String, String> getAuthenticationAttributes(HttpSession session)
    {
        Map<String, String> attributeMap = null;

        if (null != session)
            attributeMap = (Map<String, String>)session.getAttribute(SecurityManager.AUTHENTICATION_ATTRIBUTES_KEY);

        return null != attributeMap ? attributeMap : Collections.emptyMap();
    }

    private static boolean areNotBlank(String id, String password)
    {
        return StringUtils.isNotBlank(id) && StringUtils.isNotBlank(password);
    }

    public static boolean isExternalConfigurationEnabled()
    {
        return getActiveConfigurations(AuthenticationConfiguration.class).size() > 1;
    }

    public static Collection<PrimaryAuthenticationProvider> getAllPrimaryProviders()
    {
        return AuthenticationProviderCache.getProviders(PrimaryAuthenticationProvider.class);
    }

    public static Collection<SecondaryAuthenticationProvider> getAllSecondaryProviders()
    {
        return AuthenticationProviderCache.getProviders(SecondaryAuthenticationProvider.class);
    }

    // This is like LoginForm... but doesn't contain any credentials
    public static class LoginReturnProperties
    {
        private final URLHelper _returnUrl;
        private final String _urlhash;
        private final boolean _skipProfile;

        private LoginReturnProperties()
        {
            this(null, null, false);
        }

        public LoginReturnProperties(URLHelper returnUrl, String urlhash, boolean skipProfile)
        {
            _returnUrl = returnUrl;
            _urlhash = urlhash;
            _skipProfile = skipProfile;
        }

        public URLHelper getReturnUrl()
        {
            return _returnUrl;
        }

        public String getUrlhash()
        {
            return _urlhash;
        }

        public boolean isSkipProfile()
        {
            return _skipProfile;
        }
    }


    public static void setLoginReturnProperties(HttpServletRequest request, LoginReturnProperties properties)
    {
        HttpSession session = request.getSession(true);
        if (null == properties)
            session.removeAttribute(getLoginReturnPropertiesSessionKey());
        else
            session.setAttribute(getLoginReturnPropertiesSessionKey(), properties);
    }


    public static @Nullable LoginReturnProperties getLoginReturnProperties(HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
        return (LoginReturnProperties)session.getAttribute(getLoginReturnPropertiesSessionKey());
    }


    public static String getLoginReturnPropertiesSessionKey()
    {
        return LoginReturnProperties.class.getName();
    }


    public static void setPrimaryAuthenticationResult(HttpServletRequest request, PrimaryAuthenticationResult result)
    {
        HttpSession session = request.getSession(true);
        session.setAttribute(getAuthenticationProcessSessionKey(), result);
    }


    public static @Nullable PrimaryAuthenticationResult getPrimaryAuthenticationResult(HttpSession session)
    {
        return (PrimaryAuthenticationResult)session.getAttribute(getAuthenticationProcessSessionKey());
    }


    public static void setSecondaryAuthenticationUser(HttpSession session, int configurationId, User user)
    {
        session.setAttribute(getAuthenticationProcessSessionKey(configurationId), user);
    }


    public static @Nullable User getSecondaryAuthenticationUser(HttpSession session, int configurationId)
    {
        return (User)session.getAttribute(getAuthenticationProcessSessionKey(configurationId));
    }


    private static final String AUTHENTICATION_PROCESS_PREFIX = "AuthenticationProcess$";

    private static String getAuthenticationProcessSessionKey()
    {
        return AUTHENTICATION_PROCESS_PREFIX + PrimaryAuthenticationProvider.class.getName();
    }

    private static String getAuthenticationProcessSessionKey(int configurationId)
    {
        return AUTHENTICATION_PROCESS_PREFIX + PrimaryAuthenticationConfiguration.class.getName() + "$" + configurationId;
    }


    // Clear all primary and secondary authentication results
    public static void clearAuthenticationProcessAttributes(HttpServletRequest request)
    {
        SessionHelper.clearAttributesWithPrefix(request, AUTHENTICATION_PROCESS_PREFIX);
    }

    // This key and the associated setter & getter give SSO providers the ability to stash an AuthenticationConfiguration
    // in session before redirect and retrieve it at validation time. Some protocols (SAML) reject URLs with parameters
    // that change, but we need some way to tell the validate action which configuration to use.
    private static final String CONFIGURATION_ID_KEY = AUTHENTICATION_PROCESS_PREFIX + "AuthenticationConfiguration";

    public static void setAuthenticationConfigurationId(HttpServletRequest request, int rowId)
    {
        HttpSession session = request.getSession(true);
        session.setAttribute(CONFIGURATION_ID_KEY, rowId);
    }

    public static @Nullable Integer getAuthenticationConfigurationId(HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
        return (Integer)session.getAttribute(CONFIGURATION_ID_KEY);
    }

    public static class AuthenticationResult
    {
        private final @Nullable User _user;
        private final URLHelper _redirectURL;

        public AuthenticationResult(@Nullable User user, URLHelper redirectURL)
        {
            _user = user;
            _redirectURL = redirectURL;
        }

        public AuthenticationResult(URLHelper redirectURL)
        {
            this(null, redirectURL);
        }

        // Returns null if authentication has failed or is not complete (e.g., still need to complete second factor auth)
        public @Nullable User getUser()
        {
            return _user;
        }

        public URLHelper getRedirectURL()
        {
            return _redirectURL;
        }
    }


    public static @NotNull AuthenticationResult handleAuthentication(HttpServletRequest request, Container c)
    {
        HttpSession session = request.getSession(true);
        PrimaryAuthenticationResult primaryAuthResult = AuthenticationManager.getPrimaryAuthenticationResult(session);
        User primaryAuthUser;

        if (null == primaryAuthResult || null == (primaryAuthUser = primaryAuthResult.getUser()))
        {
            // failed login because of expired password will have a return url set to the update password page so use that.
            LoginReturnProperties properties = getLoginReturnProperties(request);
            if (null != properties && null != properties.getReturnUrl())
            {
                return new AuthenticationResult(properties.getReturnUrl());
            }
            else
            {
                return new AuthenticationResult(PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(c, null));
            }
        }

        List<AuthenticationValidator> validators = new LinkedList<>();

        for (SecondaryAuthenticationConfiguration<?> configuration : getActiveConfigurations(SecondaryAuthenticationConfiguration.class))
        {
            User secondaryAuthUser = getSecondaryAuthenticationUser(session, configuration.getRowId());

            if (null == secondaryAuthUser)
            {
                SecondaryAuthenticationProvider<?> provider = configuration.getAuthenticationProvider();
                if (provider.bypass())
                {
                    _log.info("Per configuration, bypassing secondary authentication for provider: " + provider.getClass());
                    setSecondaryAuthenticationUser(session, configuration.getRowId(), primaryAuthUser);
                    continue;
                }

                return new AuthenticationResult(configuration.getRedirectURL(primaryAuthUser, c));
            }

            // Validate that secondary auth user matches primary auth user
            if (!secondaryAuthUser.equals(primaryAuthUser))
                throw new IllegalStateException("Wrong user");

            // validators.add();  TODO: provide mechanism for secondary auth providers to convey a validator
        }

        // Get the redirect URL from the current session
        LoginReturnProperties properties = getLoginReturnProperties(request);
        URLHelper url = getAfterLoginURL(c, properties, primaryAuthUser);

        // Prep the new session and set the user & authentication-related attributes
        session = SecurityManager.setAuthenticatedUser(request, primaryAuthResult.getResponse(), primaryAuthUser, true);

        if (session.isNew() && !primaryAuthUser.isGuest())
        {
            // notify the websocket clients a new http session for the user has been started
            NotificationService.get().sendServerEvent(primaryAuthUser.getUserId(), AuthNotify.LoggedIn);
        }

        // Set the authentication validators into the new session
        AuthenticationValidator primaryValidator = primaryAuthResult.getResponse().getValidator();
        if (null != primaryValidator)
            validators.add(primaryValidator);
        SecurityManager.setValidators(session, validators);

        return new AuthenticationResult(primaryAuthUser, url);
    }


    public static URLHelper getAfterLoginURL(Container current, @Nullable LoginReturnProperties properties, @NotNull User user)
    {
        if (null == properties)
            properties = new LoginReturnProperties();

        URLHelper returnURL;

        if (null != properties.getReturnUrl())
        {
            returnURL = properties.getReturnUrl();
        }
        else
        {
            // We don't have a returnURL. Try not to redirect to a folder where the user doesn't have permissions, #12947
            Container c = (null == current || current.isRoot() ? ContainerManager.getHomeContainer() : current);
            returnURL = !c.hasPermission(user, ReadPermission.class) ? getWelcomeURL() : c.getStartURL(user);
        }

        // if not explicitly skipping profile page and some required field is blank, then go to update profile page
        if (!properties.isSkipProfile() && PageFlowUtil.urlProvider(UserUrls.class).requiresProfileUpdate(user))
        {
            returnURL = PageFlowUtil.urlProvider(UserUrls.class).getUserUpdateURL(current, returnURL, user.getUserId());
        }

        // if this is the users first login, reset the user cache
        if (user.isFirstLogin())
        {
            UserManager.clearUserList();
        }

        if (null != properties.getUrlhash())
        {
            returnURL.setFragment(properties.getUrlhash().replace("#", ""));
        }

        return returnURL;
    }

    public static void registerMetricsProvider()
    {
        UsageMetricsService.get().registerUsageMetrics(ModuleLoader.getInstance().getCoreModule().getName(), () -> {
            Map<String, Long> map = AuthenticationConfigurationCache.getActive(AuthenticationConfiguration.class).stream()
                .collect(Collectors.groupingBy(config->config.getAuthenticationProvider().getName(), Collectors.counting()));

            return Collections.singletonMap("authenticationConfigurations", map);
        });
    }

    public static URLHelper getWelcomeURL()
    {
        // goto "/" and let the default redirect happen
        return new URLHelper(true);
    }


    // test() method should return true if the authentication is still valid
    public interface AuthenticationValidator extends Predicate<HttpServletRequest>
    {
    }


    public static class LinkFactory
    {
        private final SSOAuthenticationConfiguration _configuration;

        public LinkFactory(SSOAuthenticationConfiguration<? extends SSOAuthenticationProvider> configuration)
        {
            _configuration = configuration;
        }

        private @NotNull HtmlString getLink(URLHelper returnURL, AuthLogoType prefix)
        {
            String content = _configuration.getDescription();
            String img = getImg(prefix);

            if (null != img)
                content = img;

            return HtmlString.unsafe("<a href=\"" + PageFlowUtil.filter(getURL(returnURL, false)) + "\">" + content + "</a>");
        }

        @SuppressWarnings("ConstantConditions")
        public ActionURL getURL(URLHelper returnURL, boolean skipProfile)
        {
            return PageFlowUtil.urlProvider(LoginUrls.class).getSSORedirectURL(_configuration, returnURL, skipProfile);
        }

        public String getImg(AuthLogoType logoType)
        {
            try
            {
                String logoUrl = generateLogoUrl(_configuration, logoType);

                if (null != logoUrl)
                {
                    return "<img src=\"" + logoUrl + "\" alt=\"Sign in using " + _configuration.getDescription() + "\" height=\"" + logoType.getHeight() + "px\">";
                }
            }
            catch (RuntimeSQLException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }

            return null;
        }
    }

    public static @Nullable String generateLogoUrl(SSOAuthenticationConfiguration<?> configuration, AuthLogoType logoType)
    {
        Attachment logo = AttachmentService.get().getAttachment(configuration, logoType.getFileName());

        return null != logo ? AppProps.getInstance().getContextPath() + "/" + logoType.getFileName() + ".image?configuration=" + configuration.getRowId() + "&revision=" + AppProps.getInstance().getLookAndFeelRevision() : null;
    }

    public static Map<String, Object> getSsoConfigurationMap(SSOAuthenticationConfiguration<?> configuration)
    {
        Map<String, Object> map = getConfigurationMap(configuration);
        map.put("headerLogoUrl", AuthenticationManager.generateLogoUrl(configuration, AuthLogoType.HEADER));
        map.put("loginLogoUrl", AuthenticationManager.generateLogoUrl(configuration, AuthLogoType.LOGIN_PAGE));
        map.put("autoRedirect", configuration.isAutoRedirect());

        return map;
    }

    public static Map<String, Object> getConfigurationMap(AuthenticationConfiguration<?> configuration)
    {
        Map<String, Object> map = new HashMap<>();
        map.put("provider", configuration.getAuthenticationProvider().getName());
        map.put("description", configuration.getDescription());
        map.put("details", configuration.getDetails());
        map.put("enabled", configuration.isEnabled());
        map.put("configuration", configuration.getRowId());
        map.putAll(configuration.getCustomProperties());

        return map;
    }

    // TODO: Register this!
    public static class TestCase extends Assert
    {
        @Test
        public void throttleLogin() throws Exception
        {
            final String[] remoteAddr = {null};
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/project/home/begin.view")
            {
                @Override
                public String getRemoteAddr()
                {
                    return remoteAddr[0];
                }
            };
            MockHttpServletResponse res = new MockHttpServletResponse();
            long start, end, elapsed;
            int i;

            // test one user, different passwords, different locations
            start = HeartBeat.currentTimeMillis();
            for (i=0 ; i<20 ; i++)
            {
                remoteAddr[0] = "127.0.0.1" + (i%256);
                PrimaryAuthenticationResult r = AuthenticationManager.authenticate(req, "testA@localhost.test", "passwordA"+i, null, false);
                if (r.getStatus() == AuthenticationStatus.LoginPaused)
                    break;
            }
            end = HeartBeat.currentTimeMillis();
            elapsed = end-start;
            assertTrue("too fast: " + DateUtil.formatDuration(elapsed),
                    i<20 || elapsed > 30*1000);

            // test different users, same password, different locations
            start = HeartBeat.currentTimeMillis();
            for (i=0 ; i<20 ; i++)
            {
                remoteAddr[0] = "127.0.1.1" + (i%256);
                PrimaryAuthenticationResult r = AuthenticationManager.authenticate(req, "testB" + i + "@localhost.test", "passwordB", null, false);
                if (r.getStatus() == AuthenticationStatus.LoginPaused)
                    break;
            }
            end = HeartBeat.currentTimeMillis();
            elapsed = end-start;
            assertTrue("too fast: " + DateUtil.formatDuration(elapsed),
                    i<20 || elapsed > 30*1000);

            // test different users, different password, same location
            start = HeartBeat.currentTimeMillis();
            for (i=0 ; i<20 ; i++)
            {
                remoteAddr[0] = "127.0.2.1";
                PrimaryAuthenticationResult r = AuthenticationManager.authenticate(req, "testC@localhost.test", "passwordC", null, false);
                if (r.getStatus() == AuthenticationStatus.LoginPaused)
                    break;
            }
            end = HeartBeat.currentTimeMillis();
            elapsed = end-start;
            assertTrue("too fast: " + DateUtil.formatDuration(elapsed),
                    i<20 || elapsed > 30*1000);
        }
    }
}
