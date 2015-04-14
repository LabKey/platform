/*
 * Copyright (c) 2007-2015 LabKey Corporation
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
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.security.AuthenticationProvider.AuthenticationResponse;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Rate;
import org.labkey.api.util.RateLimiter;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

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
    private static final List<AuthenticationProvider> _activeProviders = new CopyOnWriteArrayList<>();
    // Map of user id to login provider.  This is needed to handle clean up on logout.
    private static final Map<Integer, AuthenticationProvider> _userProviders = new ConcurrentHashMap<>();

    private static volatile Map<String, LinkFactory> _ssoLogoLinkFactories = new HashMap<>();

    public static final String HEADER_LOGO_PREFIX = "auth_header_logo_";
    public static final String LOGIN_PAGE_LOGO_PREFIX = "auth_login_page_logo_";

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
        // Load active providers and authentication logos.  Each active provider is initialized at load time. 
        loadProperties();
    }


    public static LinkFactory getLinkFactory(String providerName)
    {
        return _ssoLogoLinkFactories.get(providerName);
    }


    public static @Nullable String getHeaderLogoHtml(ActionURL currentURL)
    {
        return getAuthLogoHtml(currentURL, HEADER_LOGO_PREFIX);
    }


    public static @Nullable String getLoginPageLogoHtml(ActionURL currentURL)
    {
        return getAuthLogoHtml(currentURL, LOGIN_PAGE_LOGO_PREFIX);
    }


    private static @Nullable String getAuthLogoHtml(ActionURL currentURL, String prefix)
    {
        if (_ssoLogoLinkFactories.isEmpty())
            return null;

        StringBuilder html = new StringBuilder();

        for (LinkFactory factory : _ssoLogoLinkFactories.values())
        {
            String link = factory.getLink(currentURL, prefix);

            if (null != link)
            {
                if (html.length() > 0)
                    html.append("&nbsp;");

                html.append(link);
            }
        }

        if (html.length() > 0)
            return html.toString();
        else
            return null;
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

            ValidEmail email = validate(form);
            HttpServletRequest request = getViewContext().getRequest();

            if (null != email)
            {
                PrimaryAuthenticationResult result = AuthenticationManager.finalizePrimaryAuthentication(request, provider, email);

                if (null != result.getUser())
                    AuthenticationManager.setPrimaryAuthenticationUser(request, result.getUser());
            }

            AuthenticationResult result = AuthenticationManager.handleAuthentication(request, getContainer());

            return HttpView.redirect(result.getRedirectURL());
        }

        @Override
        protected String getCommandClassMethodName()
        {
            return "validate";
        }

        public abstract @NotNull String getProviderName();
        public abstract @Nullable ValidEmail validate(FORM form) throws Exception;

        @Override
        public final NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static void registerProvider(AuthenticationProvider authProvider, Priority priority)
    {
        if (Priority.High == priority)
            _allProviders.add(0, authProvider);
        else
            _allProviders.add(authProvider);
    }


    public static List<AuthenticationProvider> getActiveProviders()
    {
        return _activeProviders;
    }


    public static void enableProvider(String name, User user)
    {
        AuthenticationProvider provider = getProvider(name);
        try
        {
            provider.activate();
        }
        catch (Exception e)
        {
            _log.error("Can't initialize provider " + provider.getName(), e);
        }
        _activeProviders.add(provider);

        saveActiveProviders();
        addProviderAuditEvent(user, name, "enabled");
    }

    public static void disableProvider(String name, User user)
    {
        AuthenticationProvider provider = getProvider(name);
        provider.deactivate();
        _activeProviders.remove(provider);

        saveActiveProviders();
        addProviderAuditEvent(user, name, "disabled");
    }

    private static void addProviderAuditEvent(User user, String name,  String action)
    {
        AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent event = new AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent(
                ContainerManager.getRoot().getId(), new StringBuilder(name).append(" provider was ").append(action).toString());
        event.setChanges(action);
        AuditLogService.get().addEvent(user, event);
    }

    /**
     * @throws org.labkey.api.view.NotFoundException if there is no registered provider with the given name
     */
    @NotNull
    public static AuthenticationProvider getProvider(String name)
    {
        for (AuthenticationProvider provider : _allProviders)
            if (provider.getName().equals(name))
                return provider;

        throw new NotFoundException("No such AuthenticationProvider available: " + name);
    }


    public static @Nullable SSOAuthenticationProvider getActiveSSOProvider(String name)
    {
        for (AuthenticationProvider provider : _activeProviders)
        {
            if (provider.getName().equals(name))
            {
                if (provider instanceof SSOAuthenticationProvider)
                    return (SSOAuthenticationProvider)provider;
                else
                    return null; // Not an SSO provider
            }
        }

        return null;
    }


    public static @Nullable SSOAuthenticationProvider getSSOProvider(String name)
    {
        AuthenticationProvider provider = getProvider(name);

        if (provider instanceof SSOAuthenticationProvider)
            return (SSOAuthenticationProvider)provider;
        else
            return null; // Not an SSO provider
    }


    private static final String AUTHENTICATION_PROVIDERS_SET = "Authentication";
    private static final String PROVIDERS_KEY = "Authentication";
    private static final String PROP_SEPARATOR = ":";

    public static void saveActiveProviders()
    {
        StringBuilder sb = new StringBuilder();
        String sep = "";

        for (AuthenticationProvider provider : _activeProviders)
        {
            sb.append(sep);
            sb.append(provider.getName());
            sep = PROP_SEPARATOR;
        }

        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(AUTHENTICATION_PROVIDERS_SET, true);
        props.put(PROVIDERS_KEY, sb.toString());
        props.save();
        loadProperties();
    }


    public static void loadProperties()
    {
        Map<String, String> props = PropertyManager.getProperties(AUTHENTICATION_PROVIDERS_SET);
        String activeProviderProp = props.get(PROVIDERS_KEY);
        List<String> activeNames = Arrays.asList(null != activeProviderProp ? activeProviderProp.split(PROP_SEPARATOR) : new String[0]);
        List<AuthenticationProvider> activeProviders = new ArrayList<>(_allProviders.size());

        // For now, auth providers are always handled in order of registration: LDAP, OpenSSO, DB.  TODO: Provide admin with mechanism for ordering

        // Add all permanent & active providers to the activeProviders list
        for (AuthenticationProvider provider : _allProviders)
            if (provider.isPermanent() || activeNames.contains(provider.getName()))
                addProvider(activeProviders, provider);

        Map<String, LinkFactory> factories = new HashMap<>();

        for (AuthenticationProvider provider : activeProviders)
            if (provider instanceof SSOAuthenticationProvider)
                factories.put(provider.getName(), new LinkFactory((SSOAuthenticationProvider)provider));

        _activeProviders.clear();
        _activeProviders.addAll(activeProviders);
        _ssoLogoLinkFactories = factories;
    }


    private static void addProvider(List<AuthenticationProvider> providers, AuthenticationProvider provider)
    {
        try
        {
            provider.activate();
            providers.add(provider);
        }
        catch (Exception e)
        {
            _log.error("Couldn't initialize provider", e);
        }
    }


    public enum AuthenticationStatus {Success, BadCredentials, InactiveUser, LoginPaused, UserCreationError}

    public static class PrimaryAuthenticationResult
    {
        private final User _user;
        private final AuthenticationStatus _status;

        // Success case
        private PrimaryAuthenticationResult(@NotNull User user)
        {
            _user = user;
            _status = AuthenticationStatus.Success;
        }

        // Failure case
        private PrimaryAuthenticationResult(@NotNull AuthenticationStatus status)
        {
            _user = null;
            _status = status;
        }

        @Nullable
        public User getUser()
        {
            return _user;
        }

        @NotNull
        public AuthenticationStatus getStatus()
        {
            return _status;
        }
    }


    /** avoid spamming the audit log **/
    private static Cache<String, String> authMessages = CacheManager.getCache(100, TimeUnit.MINUTES.toMillis(10), "Authentication Messages");

    private static void addAuditEvent(@NotNull User user, HttpServletRequest request, String msg)
    {
        String key = user.getUserId() + "/" + ((null==request||null==request.getLocalAddr())?"":request.getLocalAddr());
        String prevMessage = authMessages.get(key);
        if (StringUtils.equals(prevMessage, msg))
            return;
        authMessages.put(key, msg);
        if (user.isGuest())
            AuditLogService.get().addEvent(user, ContainerManager.getRoot(), UserManager.USER_AUDIT_EVENT, null, msg);
        else
            UserManager.addAuditEvent(user, ContainerManager.getRoot(), user, msg);
    }


    public static @NotNull
    PrimaryAuthenticationResult authenticate(HttpServletRequest request, HttpServletResponse response, String id, String password, URLHelper returnURL, boolean logFailures) throws InvalidEmailException
    {
        PrimaryAuthenticationResult result = null;
        try
        {
            result = _beforeAuthenticate(request, id, password);
            if (null != result)
                return result;
            result = _authenticate(request, response, id, password, returnURL, logFailures);
            return result;
        }
        finally
        {
            _afterAuthenticate(request, id, password, result);
        }
    }


    private static @NotNull
    PrimaryAuthenticationResult _authenticate(HttpServletRequest request, HttpServletResponse response, String id, String password, URLHelper returnURL, boolean logFailures) throws InvalidEmailException
    {
        AuthenticationResponse firstFailure = null;

        for (AuthenticationProvider authProvider : getActiveProviders())
        {
            AuthenticationResponse authResponse = null;

            try
            {
                if (authProvider instanceof LoginFormAuthenticationProvider)
                {
                    if (areNotBlank(id, password))
                        authResponse = ((LoginFormAuthenticationProvider)authProvider).authenticate(id, password, returnURL);
                }
                else if (authProvider instanceof SSOAuthenticationProvider)
                {
                    if (areNotNull(request, response))
                        authResponse = ((SSOAuthenticationProvider)authProvider).authenticate(request, response, returnURL);
                }
            }
            catch (RedirectException e)
            {
                // Some authentication provider has chosen to redirect (e.g., to retrieve auth credentials from
                // a different server or to force change password due to expiration).
                throw new RuntimeException(e);
            }

            // Null only if params are blank... just ignore that case
            if (null != authResponse)
            {
                if (authResponse.isAuthenticated())
                {
                    return finalizePrimaryAuthentication(request, authProvider, authResponse.getValidEmail());
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
                throw new RedirectException(redirectURL);
        }

        return new PrimaryAuthenticationResult(AuthenticationStatus.BadCredentials);
    }

    @NotNull
    public static PrimaryAuthenticationResult finalizePrimaryAuthentication(HttpServletRequest request, AuthenticationProvider authProvider, ValidEmail email)
    {
        User user;

        try
        {
            user = SecurityManager.afterAuthenticate(email);
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

        _userProviders.put(user.getUserId(), authProvider);
        addAuditEvent(user, request, email + " logged in successfully via " + authProvider.getName() + " authentication.");

        return new PrimaryAuthenticationResult(user);
    }


    // limit one bad login per second averaged out over 60sec
    private static final Cache<Integer,RateLimiter> addrLimiter = CacheManager.getCache(1001, TimeUnit.MINUTES.toMillis(5), "login limiter");
    private static final Cache<Integer,RateLimiter> userLimiter = CacheManager.getCache(1001, TimeUnit.MINUTES.toMillis(5), "user limiter");
    private static final Cache<Integer,RateLimiter> pwdLimiter = CacheManager.getCache(1001, TimeUnit.MINUTES.toMillis(5), "password limiter");
    private static final CacheLoader<Integer,RateLimiter> addrLoader = new CacheLoader<Integer,RateLimiter>()
        {
            @Override
            public RateLimiter load(Integer key, @Nullable Object request)
            {
                return new RateLimiter("Addr limiter: " + String.valueOf(key), new Rate(60,TimeUnit.MINUTES));
            }
        };
    static final CacheLoader<Integer,RateLimiter> pwdLoader = new CacheLoader<Integer,RateLimiter>()
        {
            @Override
            public RateLimiter load(Integer key, @Nullable Object request)
            {
                return new RateLimiter("Pwd limiter: " + String.valueOf(key), new Rate(20,TimeUnit.MINUTES));
            }
        };
    static final CacheLoader<Integer,RateLimiter> userLoader = new CacheLoader<Integer,RateLimiter>()
        {
            @Override
            public RateLimiter load(Integer key, @Nullable Object request)
            {
                return new RateLimiter("User limiter: " + String.valueOf(key), new Rate(20,TimeUnit.MINUTES));
            }
        };


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
        rl = userLimiter.get(_toKey(id));
        if (null != rl)
            delay = Math.max(delay, rl.add(0, false));
        rl = pwdLimiter.get(_toKey(pwd));
        if (null != rl)
            delay = Math.max(delay, rl.add(0, false));

        if (delay > 15*1000)
            return new PrimaryAuthenticationResult(AuthenticationStatus.LoginPaused);
        if (delay > 0)
            try {Thread.sleep(delay);}catch(InterruptedException x){/* */}
        return null;
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
            rl = userLimiter.get(_toKey(id),request, userLoader);
            rl.add(1, false);
            rl = pwdLimiter.get(_toKey(pwd),request, pwdLoader);
            rl.add(1, false);
        }
    }


    // Attempts to authenticate using only LoginFormAuthenticationProviders (e.g., DbLogin, LDAP). This is for the case
    // where you have an id & password in hand and want to ignore SSO and other delegated authentication mechanisms that
    // rely on cookies, browser redirects, etc. Current usages include basic auth and test cases. Note that this will
    // always fail if any secondary authentication is enabled (e.g., Duo).

    // Returns null if credentials are incorrect, user doesn't exist, user is inactive, or secondary auth is enabled.
    public static User authenticate(HttpServletRequest request, String id, String password) throws InvalidEmailException
    {
        PrimaryAuthenticationResult primaryResult = authenticate(request, null, id, password, null, true);

        // If primary authentication is successful then look for secondary authentication. handleAuthentication() will
        // always return a failure result (i.e., null user) if secondary authentication is enabled. #22944
        if (primaryResult.getStatus() == AuthenticationStatus.Success)
        {
            AuthenticationManager.setPrimaryAuthenticationUser(request, primaryResult.getUser());
            return handleAuthentication(request, ContainerManager.getRoot()).getUser();
        }

        return null;
    }


    public static void logout(@NotNull User user, HttpServletRequest request)
    {
        AuthenticationProvider provider = _userProviders.get(user.getUserId());

        if (null != provider)
            provider.logout(request);

        if (!user.isGuest())
            addAuditEvent(user, request, user.getEmail() + " logged out.");
    }


    private static boolean areNotBlank(String id, String password)
    {
        return StringUtils.isNotBlank(id) && StringUtils.isNotBlank(password);
    }


    private static boolean areNotNull(HttpServletRequest request, HttpServletResponse response)
    {
        return null != request && null != response;
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


    public static List<AuthenticationProvider> getAllPrimaryProviders()
    {
        List<AuthenticationProvider> list = new LinkedList<>();

        for (AuthenticationProvider provider : _allProviders)
            if (!(provider instanceof SecondaryAuthenticationProvider))
                list.add(provider);

        return list;
    }


    public static List<SecondaryAuthenticationProvider> getAllSecondaryProviders()
    {
        List<SecondaryAuthenticationProvider> list = new LinkedList<>();

        for (AuthenticationProvider provider : _allProviders)
            if (provider instanceof SecondaryAuthenticationProvider)
                list.add((SecondaryAuthenticationProvider)provider);

        return list;
    }


    public static List<SecondaryAuthenticationProvider> getActiveSecondaryProviders()
    {
        List<SecondaryAuthenticationProvider> list = new LinkedList<>();

        for (AuthenticationProvider provider : getActiveProviders())
            if (provider instanceof SecondaryAuthenticationProvider)
                list.add((SecondaryAuthenticationProvider)provider);

        return list;
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


    public static void setPrimaryAuthenticationUser(HttpServletRequest request, User user)
    {
        HttpSession session = request.getSession(true);
        session.setAttribute(getSessionKey(AuthenticationProvider.class), user);
    }


    public static @Nullable User getPrimaryAuthenticationUser(HttpSession session)
    {
        return (User)session.getAttribute(getSessionKey(AuthenticationProvider.class));
    }


    public static void setSecondaryAuthenticationUser(HttpSession session, Class<? extends SecondaryAuthenticationProvider> clazz, User user)
    {
        session.setAttribute(getSessionKey(clazz), user);
    }


    public static @Nullable User getSecondaryAuthenticationUser(HttpSession session, Class<? extends SecondaryAuthenticationProvider> clazz)
    {
        return (User)session.getAttribute(getSessionKey(clazz));
    }


    private static String getSessionKey(Class<? extends AuthenticationProvider> clazz)
    {
        return clazz.getName() + "$User";
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


    public static AuthenticationResult handleAuthentication(HttpServletRequest request, Container c)
    {
        HttpSession session = request.getSession(true);

        User primaryAuthUser = AuthenticationManager.getPrimaryAuthenticationUser(session);

        if (null == primaryAuthUser)
            return new AuthenticationResult(PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(c, null));

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
        }

        LoginReturnProperties properties = getLoginReturnProperties(request);
        SecurityManager.setAuthenticatedUser(request, primaryAuthUser);
        URLHelper url = getAfterLoginURL(c, properties, primaryAuthUser);

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


    public static class LinkFactory
    {
        private final static String NO_LOGO = "NO_LOGO";
        private final static LoginUrls URLS = PageFlowUtil.urlProvider(LoginUrls.class);

        private final SSOAuthenticationProvider _provider;
        private final String _providerName;

        // Need to check the attachments service to see if logo exists... use map to check this once and cache result
        private Map<String, String> _imgMap = new HashMap<>();

        public LinkFactory(SSOAuthenticationProvider provider)
        {
            _provider = provider;
            _providerName = provider.getName(); // Just for convenience
        }

        private String getLink(ActionURL returnURL, String prefix)
        {
            String img = getImg(prefix);

            if (null == img)
                return null;
            else
                return "<a href=\"" + PageFlowUtil.filter(getURL(returnURL)) + "\">" + img + "</a>";
        }

        public ActionURL getURL(URLHelper returnURL)
        {
            return URLS.getSSORedirectURL(_provider, returnURL);
        }

        public String getImg(String prefix)
        {
            String img = _imgMap.get(prefix);

            if (null == img)
            {
                img = NO_LOGO;

                try
                {
                    Attachment logo = AttachmentService.get().getAttachment(ContainerManager.RootContainer.get(), prefix + _providerName);

                    if (null != logo)
                    {
                        img = "<img src=\"" + AppProps.getInstance().getContextPath() + "/" + prefix + _providerName + ".image?revision=" + AppProps.getInstance().getLookAndFeelRevision() + "\" alt=\"Sign in using " + _providerName + "\"";

                        if(HEADER_LOGO_PREFIX.equals(prefix))
                            img += " height=\"16px\"";

                        else if(LOGIN_PAGE_LOGO_PREFIX.equals(prefix))
                            img += " height=\"32px\"";

                        img += ">";
                    }
                }
                catch (RuntimeSQLException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }

                _imgMap.put(prefix, img);
            }

            return (NO_LOGO.equals(img) ? null : img);
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
                PrimaryAuthenticationResult r = AuthenticationManager.authenticate(req, res, "testA@localhost.test", "passwordA"+i, null, false);
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
                PrimaryAuthenticationResult r = AuthenticationManager.authenticate(req, res, "testB" + i + "@localhost.test", "passwordB", null, false);
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
                PrimaryAuthenticationResult r = AuthenticationManager.authenticate(req, res, "testC@localhost.test", "passwordC", null, false);
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
