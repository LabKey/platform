/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.FormattedError;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Project;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationProvider.AuthenticationResponse;
import org.labkey.api.security.AuthenticationProvider.DisableLoginProvider;
import org.labkey.api.security.AuthenticationProvider.ExpireAccountProvider;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.PrimaryAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.RequestAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.ResetPasswordProvider;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HeartBeat;
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:53:03 PM
 */
public class AuthenticationManager
{
    public static final String ALL_DOMAINS = "*";

    private static final Logger _log = Logger.getLogger(AuthenticationManager.class);
    // All registered authentication providers (DbLogin, LDAP, SSO, etc.)
    private static final List<AuthenticationProvider> _allProviders = new CopyOnWriteArrayList<>();
    // Map of user id to login provider. This is needed to handle clean up on logout.
    private static final Map<Integer, PrimaryAuthenticationProvider> _userProviders = new ConcurrentHashMap<>();

    public static final String HEADER_LOGO_PREFIX = "auth_header_logo_";
    public static final String LOGIN_PAGE_LOGO_PREFIX = "auth_login_page_logo_";

    public static @Nullable User attemptRequestAuthentication(HttpServletRequest request)
    {
        for (RequestAuthenticationProvider provider : AuthenticationManager.getActiveProviders(RequestAuthenticationProvider.class))
        {
            AuthenticationResponse response = provider.authenticate(request);

            if (response.isAuthenticated())
            {
                PrimaryAuthenticationResult result = finalizePrimaryAuthentication(request, response);
                return result.getUser();
            }
        }

        return null;
    }

    // Called unconditionally on every server startup. At some point, might want to make this bootstrap only.
    // TODO: SSO logos. Auditing of configuration property changes.
    public static void populateSettingsWithStartupProps()
    {
        // Configure each authentication provider with startup properties
        populateProviderProperties(PropertyManager.getNormalStore(), AuthenticationProvider::getPropertyCategories);
        populateProviderProperties(PropertyManager.getEncryptedStore(), AuthenticationProvider::getEncryptedPropertyCategories);

        // Handle the general authentication properties: enable all the providers listed and populate the other general
        // authentication properties (e.g., auto-create accounts, self registration, self-service email changes).
        ModuleLoader.getInstance().getConfigProperties(AUTHENTICATION_CATEGORY)
            .forEach(cp -> {
                if (cp.getName().equals(PROVIDERS_KEY))
                {
                    Arrays.stream(cp.getValue().split(":"))
                        .forEach(name ->
                        {
                            try
                            {
                                enableProvider(name, null);
                            }
                            catch (NotFoundException e)
                            {
                                _log.warn("Authentication startup properties attempted to enable an authentication provider (\"" + name + "\") that is not present on this server");
                            }
                        });
                }
                else
                {
                    setAuthConfigProperty(null, cp.getName(), Boolean.parseBoolean(cp.getValue()));
                }
            });
    }

    private static void populateProviderProperties(PropertyStore store, Function<AuthenticationProvider, Collection<String>> function)
    {
        // For each provider, use function to collect the desired property categories
        List<String> categories = getAllProviders().stream()
            .map(function)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

        // For each category that matches a ConfigProperty scope, write all the properties to the provided PropertyStore
        categories.forEach(category -> {
            Collection<ConfigProperty> configProperties = ModuleLoader.getInstance().getConfigProperties(category);

            if (!configProperties.isEmpty())
            {
                PropertyMap map = store.getWritableProperties(category, true);
                configProperties.forEach(cp -> map.put(cp.getName(), cp.getValue()));
                map.save();
            }
        });
    }

    public enum Priority { High, Low }

    // TODO: Replace this with a generic domain-claiming mechanism
    public static String _ldapDomain = null;

    public static String getLdapDomain()
    {
        return _ldapDomain;
    }

    public static void setLdapDomain(String ldapDomain)
    {
        _ldapDomain = StringUtils.trimToNull(ldapDomain);
    }

    public static void initialize()
    {
        // Activate all the currently enabled providers
        AuthenticationProviderCache.getActiveProviders(AuthenticationProvider.class).forEach(AuthenticationProvider::activate);
    }

    public static boolean isRegistrationEnabled()
    {
        return getAuthConfigProperty(SELF_REGISTRATION_KEY, false);
    }

    public static boolean isAutoCreateAccountsEnabled()
    {
        return isExternalProviderEnabled() && getAuthConfigProperty(AUTO_CREATE_ACCOUNTS_KEY, true);
    }

    public static boolean isSelfServiceEmailChangesEnabled() { return getAuthConfigProperty(SELF_SERVICE_EMAIL_CHANGES_KEY, false);}

    public static boolean getAuthConfigProperty(String key, boolean defaultValue)
    {
        Map<String, String> props = PropertyManager.getProperties(AUTHENTICATION_CATEGORY);
        String value = props.get(key);

        return value == null ? defaultValue : Boolean.valueOf(value);
    }

    public static void setAuthConfigProperty(User user, String key, boolean value)
    {
        PropertyMap props = PropertyManager.getWritableProperties(AUTHENTICATION_CATEGORY, true);
        props.put(key, Boolean.toString(value));
        props.save();

        addConfigurationAuditEvent(user, key, value ? "enabled" : "disabled");
    }

    public static @Nullable String getHeaderLogoHtml(URLHelper currentURL)
    {
        return getAuthLogoHtml(currentURL, HEADER_LOGO_PREFIX);
    }


    public static @Nullable String getLoginPageLogoHtml(URLHelper currentURL)
    {
        return getAuthLogoHtml(currentURL, LOGIN_PAGE_LOGO_PREFIX);
    }

    public static Map<String, Object> getLoginPageConfiguration(Project project)
    {
        Map<String, Object> config = new HashMap<>();
        config.put("registrationEnabled", isRegistrationEnabled());
        config.put("requiresTermsOfUse", WikiTermsOfUseProvider.isTermsOfUseRequired(project));
        config.put("hasOtherLoginMechanisms", hasSSOAuthenticationProvider());
        return config;
    }

    static List<AuthenticationProvider> getAllProviders()
    {
        return _allProviders;
    }

    public static boolean hasSSOAuthenticationProvider()
    {
        return !AuthenticationProviderCache.getActiveProviders(SSOAuthenticationProvider.class).isEmpty();
    }

    private static @Nullable String getAuthLogoHtml(URLHelper currentURL, String prefix)
    {
        Collection<SSOAuthenticationProvider> ssoProviders = AuthenticationProviderCache.getActiveProviders(SSOAuthenticationProvider.class);

        if (ssoProviders.isEmpty())
            return null;

        StringBuilder html = new StringBuilder();

        for (SSOAuthenticationProvider provider : ssoProviders)
        {
            if (!provider.isAutoRedirect())
            {
                LinkFactory factory = provider.getLinkFactory();
                html.append("<li>").append(factory.getLink(currentURL, prefix)).append("</li>");
            }
        }

        return html.toString();
    }


    public static abstract class BaseSsoValidateAction <FORM> extends SimpleViewAction<FORM>
    {
        @Override
        public ModelAndView getView(FORM form, BindException errors) throws Exception
        {
            // Must specify an active SSO provider
            SSOAuthenticationProvider provider = getActiveSSOProvider(getProviderName());

            // Not valid, not SSO, or not active... bail out
            if (null == provider)
                throw new NotFoundException("Authentication provider is not valid");

            AuthenticationResponse response = validateAuthentication(form, provider, errors);

            // Show validation error(s), if any
            if (errors.hasErrors() || !response.isAuthenticated())
            {
                if (!errors.hasErrors())
                    errors.addError(new LabKeyError("Bad credentials"));
            }
            else
            {
                HttpServletRequest request = getViewContext().getRequest();
                PrimaryAuthenticationResult primaryResult = AuthenticationManager.finalizePrimaryAuthentication(request, response);

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

            return new SimpleErrorView(errors, false);
        }

        @Override
        protected String getCommandClassMethodName()
        {
            return "validateAuthentication";
        }

        public abstract @NotNull String getProviderName();
        public abstract @NotNull AuthenticationResponse validateAuthentication(FORM form, SSOAuthenticationProvider provider, BindException errors) throws Exception;

        @Override
        public final NavTree appendNavTrail(NavTree root)
        {
            return null;
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
    }


    public static Collection<AuthenticationProvider> getActiveProviders()
    {
        return AuthenticationProviderCache.getActiveProviders(AuthenticationProvider.class);
    }


    public static <T extends AuthenticationProvider> Collection<T> getActiveProviders(Class<T> clazz)
    {
        return AuthenticationProviderCache.getActiveProviders(clazz);
    }


    public static void enableProvider(String name, User user)
    {
        AuthenticationProvider provider = getProvider(name);
        Set<String> activeNames = getActiveProviders().stream()
            .map(AuthenticationProvider::getName)
            .collect(Collectors.toSet());

        if (!activeNames.contains(name))
        {
            try
            {
                provider.activate();
                activeNames.add(name);
                saveActiveProviders(activeNames);
                addProviderAuditEvent(user, name, "enabled");
            }
            catch (Exception e)
            {
                _log.error("Can't initialize provider " + provider.getName(), e);
            }
        }
    }

    public static void disableProvider(String name, User user)
    {
        AuthenticationProvider provider = getProvider(name);
        Set<String> activeNames = getActiveProviders().stream()
            .map(AuthenticationProvider::getName)
            .collect(Collectors.toSet());

        if (activeNames.contains(name))
        {
            provider.deactivate();
            activeNames.remove(name);
            saveActiveProviders(activeNames);
            addProviderAuditEvent(user, name, "disabled");
        }
    }

    private static void addConfigurationAuditEvent(User user, String name, String action)
    {
        AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent event = new AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent(
                ContainerManager.getRoot().getId(), name + " was " + action);
        event.setChanges(action);
        AuditLogService.get().addEvent(user, event);
    }

    private static void addProviderAuditEvent(User user, String name,  String action)
    {
        AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent event = new AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent(
                ContainerManager.getRoot().getId(), name + " provider was " + action);
        event.setChanges(action);
        AuditLogService.get().addEvent(user, event);
    }

    /**
     * @throws org.labkey.api.view.NotFoundException if there is no registered provider with the given name
     */
    @NotNull
    public static AuthenticationProvider getProvider(String name)
    {
        AuthenticationProvider provider = AuthenticationProviderCache.getProvider(AuthenticationProvider.class, name);

        if (null != provider)
            return provider;
        else
            throw new NotFoundException("No such AuthenticationProvider available: " + name);
    }


    public static @Nullable SSOAuthenticationProvider getActiveSSOProvider(String name)
    {
        return AuthenticationProviderCache.getActiveProvider(SSOAuthenticationProvider.class, name);
    }


    public static @Nullable SSOAuthenticationProvider getSSOProvider(String name)
    {
        return AuthenticationProviderCache.getProvider(SSOAuthenticationProvider.class, name);
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
     * @return true if only FICAM approved authentication providers are enabled
     */
    public static boolean isAcceptOnlyFicamProviders()
    {
        return getAuthConfigProperty(ACCEPT_ONLY_FICAM_PROVIDERS_KEY, false);
    }

    public static void setAcceptOnlyFicamProviders(User user, boolean enable)
    {
        setAuthConfigProperty(user, ACCEPT_ONLY_FICAM_PROVIDERS_KEY, enable);
        AuthenticationProviderCache.clear();
    }

    private static final String AUTHENTICATION_CATEGORY = "Authentication";
    private static final String PROVIDERS_KEY = "Authentication";
    private static final String PROP_SEPARATOR = ":";
    public static final String SELF_REGISTRATION_KEY = "SelfRegistration";
    public static final String AUTO_CREATE_ACCOUNTS_KEY = "AutoCreateAccounts";
    public static final String SELF_SERVICE_EMAIL_CHANGES_KEY = "SelfServiceEmailChanges";
    public static final String ACCEPT_ONLY_FICAM_PROVIDERS_KEY = "AcceptOnlyFicamProviders";

    private static void saveActiveProviders(Set<String> activeNames)
    {
        StringBuilder sb = new StringBuilder();
        String sep = "";

        for (String name : activeNames)
        {
            sb.append(sep);
            sb.append(name);
            sep = PROP_SEPARATOR;
        }

        PropertyMap props = PropertyManager.getWritableProperties(AUTHENTICATION_CATEGORY, true);
        props.put(PROVIDERS_KEY, sb.toString());
        props.save();
        AuthenticationProviderCache.clear();
    }

    // Provider names stored in properties; they're not necessarily all valid providers
    static Set<String> getActiveProviderNamesFromProperties()
    {
        Map<String, String> props = PropertyManager.getProperties(AUTHENTICATION_CATEGORY);
        String activeProviderProp = props.get(PROVIDERS_KEY);

        Set<String> set = new HashSet<>();
        Collections.addAll(set, null != activeProviderProp ? activeProviderProp.split(PROP_SEPARATOR) : new String[0]);

        return set;
    }

    /**
     * Return the first SSOAuthenticationProvider that is set to auto redirect from the login page.
     * @return
     */
    public static SSOAuthenticationProvider getSSOAuthProviderAutoRedirect()
    {
        for (SSOAuthenticationProvider provider : getActiveProviders(SSOAuthenticationProvider.class))
        {
            if (provider.isAutoRedirect())
                return provider;
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
    private static Cache<String, String> authMessages = CacheManager.getCache(100, TimeUnit.MINUTES.toMillis(10), "Authentication Messages");

    public static void addAuditEvent(@NotNull User user, HttpServletRequest request, String msg)
    {
        String key = user.getUserId() + "/" + ((null==request||null==request.getLocalAddr())?"":request.getLocalAddr());
        String prevMessage = authMessages.get(key);
        if (StringUtils.equals(prevMessage, msg))
            return;
        authMessages.put(key, msg);
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

            for (LoginFormAuthenticationProvider authProvider : getActiveProviders(LoginFormAuthenticationProvider.class))
            {
                AuthenticationResponse authResponse;

                try
                {
                    authResponse = authProvider.authenticate(id, password, returnURL);
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

            // Login failed all providers... log the first interesting failure (but only if logFailures == true)
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

                String message = " failed to login: " + firstFailure.getFailureReason().getMessage();

                if (null != user)
                {
                    addAuditEvent(user, request, user.getEmail() + message);
                    _log.warn(user.getEmail() + message);
                }
                else if (null != emailAddress)
                {
                    // Funny audit case -- user doesn't exist, so there's no user to associate with the event.  Use guest.
                    addAuditEvent(User.guest, request, emailAddress + message);
                    _log.warn(emailAddress + message);
                }
                else
                {
                    // Funny audit case -- user doesn't exist, so there's no user to associate with the event.  Use guest.
                    addAuditEvent(User.guest, request, message);
                    _log.warn("Unknown user " + message);
                }

                // For now, redirectURL is only checked in the failure case, see #19778 for some history on redirect handling
                ActionURL redirectURL = firstFailure.getRedirectURL();

                if (null != redirectURL)
                {
                    // if labkey db authenticate determines password has expired or that password does not meet complexity requirements then return url to redirect user
                    if (null != firstFailure.getFailureReason().name() && firstFailure.getFailureReason().name().equals("expired"))
                    {
                        return new PrimaryAuthenticationResult(redirectURL, AuthenticationStatus.PasswordExpired);
                    }
                    else if (null != firstFailure.getFailureReason().name() && firstFailure.getFailureReason().name().equals("complexity"))
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
                    addAuditEvent(User.guest, request, "User " + email + " successfully authenticated via " + response.getProvider().getName() + ". Login failed because account creation is disabled.");
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

        _userProviders.put(user.getUserId(), response.getProvider());  // TODO: This should go into session (handle in caller)!
        addAuditEvent(user, request, email + " " + UserManager.UserAuditEvent.LOGGED_IN + " successfully via " + response.getProvider().getName() + " authentication.");

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
    public static User authenticate(HttpServletRequest request, String id, String password) throws InvalidEmailException
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


    public static void logout(@NotNull User user, HttpServletRequest request)
    {
        PrimaryAuthenticationProvider provider = _userProviders.get(user.getUserId());

        if (null != provider)
            provider.logout(request);

        if (!user.isGuest())
        {
            // notify websocket clients associated with this http session, the user has logged out
            HttpSession session = request.getSession(false);
            NotificationService.get().closeServerEvents(user.getUserId(), session, AuthNotify.LoggedOut);

            // notify any remaining websocket clients for this user that were not closed that the user has logged out elsewhere
            if (session != null)
                NotificationService.get().sendServerEvent(user.getUserId(), AuthNotify.LoggedOut);

            addAuditEvent(user, request, user.getEmail() + " " + UserManager.UserAuditEvent.LOGGED_OUT + ".");
        }
    }


    private static boolean areNotBlank(String id, String password)
    {
        return StringUtils.isNotBlank(id) && StringUtils.isNotBlank(password);
    }

    public static boolean isExternalProviderEnabled()
    {
        return getActiveProviders().size() > 1;
    }

    public static boolean isActive(String providerName)
    {
        AuthenticationProvider provider = getProvider(providerName);

        return isActive(provider);
    }


    public static boolean isActive(AuthenticationProvider authProvider)
    {
        return getActiveProviders().contains(authProvider);
    }


    public static Collection<PrimaryAuthenticationProvider> getAllPrimaryProviders()
    {
        return AuthenticationProviderCache.getProviders(PrimaryAuthenticationProvider.class);
    }


    public static Collection<SecondaryAuthenticationProvider> getAllSecondaryProviders()
    {
        return AuthenticationProviderCache.getProviders(SecondaryAuthenticationProvider.class);
    }


    public static Collection<SecondaryAuthenticationProvider> getActiveSecondaryProviders()
    {
        return AuthenticationProviderCache.getActiveProviders(SecondaryAuthenticationProvider.class);
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
        session.setAttribute(getAuthenticationProcessSessionKey(PrimaryAuthenticationProvider.class), result);
    }


    public static @Nullable PrimaryAuthenticationResult getPrimaryAuthenticationResult(HttpSession session)
    {
        return (PrimaryAuthenticationResult)session.getAttribute(getAuthenticationProcessSessionKey(PrimaryAuthenticationProvider.class));
    }


    public static void setSecondaryAuthenticationUser(HttpSession session, Class<? extends SecondaryAuthenticationProvider> clazz, User user)
    {
        session.setAttribute(getAuthenticationProcessSessionKey(clazz), user);
    }


    public static @Nullable User getSecondaryAuthenticationUser(HttpSession session, Class<? extends SecondaryAuthenticationProvider> clazz)
    {
        return (User)session.getAttribute(getAuthenticationProcessSessionKey(clazz));
    }


    private static final String AUTHENTICATION_PROCESS_PREFIX = "AuthenticationProcess$";

    private static String getAuthenticationProcessSessionKey(Class<? extends AuthenticationProvider> clazz)
    {
        return AUTHENTICATION_PROCESS_PREFIX + clazz.getName() + "$User";
    }


    // Clear all primary and secondary authentication results
    public static void clearAuthenticationProcessAttributes(HttpServletRequest request)
    {
        SessionHelper.clearAttributesWithPrefix(request, AUTHENTICATION_PROCESS_PREFIX);
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

        for (SecondaryAuthenticationProvider provider : getActiveSecondaryProviders())
        {
            User secondaryAuthUser = getSecondaryAuthenticationUser(session, provider.getClass());

            if (null == secondaryAuthUser)
            {
                if (provider.bypass())
                {
                    _log.info("Per configuration, bypassing secondary authentication for provider: " + provider.getClass());
                    setSecondaryAuthenticationUser(session, provider.getClass(), primaryAuthUser);
                    continue;
                }

                return new AuthenticationResult(provider.getRedirectURL(primaryAuthUser, c));
            }

            // Validate that secondary auth user matches primary auth user
            if (!secondaryAuthUser.equals(primaryAuthUser))
            {
                throw new IllegalStateException("Wrong user");
            }
            else
            {
                // validators.add();  TODO: provide mechanism for secondary auth providers to convey a validator
            }
        }

        // Get the redirect URL from the current session
        LoginReturnProperties properties = getLoginReturnProperties(request);
        URLHelper url = getAfterLoginURL(c, properties, primaryAuthUser);

        // Prep the new session and set the user attribute
        session = SecurityManager.setAuthenticatedUser(request, primaryAuthUser, true);

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

        // If this is user's first login or some required field is blank then go to update page first
        if (!properties.isSkipProfile())
        {
            if (user.isFirstLogin() || PageFlowUtil.urlProvider(UserUrls.class).requiresProfileUpdate(user))
                returnURL = PageFlowUtil.urlProvider(UserUrls.class).getUserUpdateURL(current, returnURL, user.getUserId());
        }
        // Else if we are told to skipProfile, reset the user cache since it may not think this user has logged in yet
        else if (user.getLastLogin() == null)
        {
            UserManager.clearUserList();
        }

        if (null != properties.getUrlhash())
        {
            returnURL.setFragment(properties.getUrlhash().replace("#", ""));
        }

        return returnURL;
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
        private final SSOAuthenticationProvider _provider;
        private final String _providerName;

        public LinkFactory(SSOAuthenticationProvider provider)
        {
            _provider = provider;
            _providerName = provider.getName(); // Just for convenience
        }

        private @NotNull String getLink(URLHelper returnURL, String prefix)
        {
            String content = _providerName;
            String img = getImg(prefix);

            if (null != img)
                content = img;

            return "<a href=\"" + PageFlowUtil.filter(getURL(returnURL)) + "\">" + content + "</a>";
        }

        public ActionURL getURL(URLHelper returnURL)
        {
            //noinspection ConstantConditions
            return PageFlowUtil.urlProvider(LoginUrls.class).getSSORedirectURL(_provider, returnURL);
        }

        public String getImg(String prefix)
        {
            try
            {
                Attachment logo = AttachmentService.get().getAttachment(AuthenticationLogoAttachmentParent.get(), prefix + _providerName);

                if (null != logo)
                {
                    String img = "<img src=\"" + AppProps.getInstance().getContextPath() + "/" + prefix + _providerName + ".image?revision=" + AppProps.getInstance().getLookAndFeelRevision() + "\" alt=\"Sign in using " + _providerName + "\"";

                    if (HEADER_LOGO_PREFIX.equals(prefix))
                        img += " height=\"16px\"";

                    else if (LOGIN_PAGE_LOGO_PREFIX.equals(prefix))
                        img += " height=\"32px\"";

                    img += ">";

                    return img;
                }
            }
            catch (RuntimeSQLException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }

            return null;
        }
    }


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
