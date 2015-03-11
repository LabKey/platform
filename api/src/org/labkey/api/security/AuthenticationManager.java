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
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.*;
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
import org.labkey.api.security.AuthenticationProvider.RequestAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Rate;
import org.labkey.api.util.RateLimiter;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static volatile Map<String, LinkFactory> _linkFactories = new HashMap<>();

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
        return _linkFactories.get(providerName);
    }


    public static String getHeaderLogoHtml(ActionURL currentURL)
    {
        return getAuthLogoHtml(currentURL, HEADER_LOGO_PREFIX);
    }


    public static String getLoginPageLogoHtml(ActionURL currentURL)
    {
        return getAuthLogoHtml(currentURL, LOGIN_PAGE_LOGO_PREFIX);
    }


    private static String getAuthLogoHtml(ActionURL currentURL, String prefix)
    {
        if (_linkFactories.isEmpty())
            return null;

        StringBuilder html = new StringBuilder();

        for (LinkFactory factory : _linkFactories.values())
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


    public static void enableProvider(String name)
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
    }


    public static void disableProvider(String name) throws Exception
    {
        AuthenticationProvider provider = getProvider(name);
        provider.deactivate();
        _activeProviders.remove(provider);

        saveActiveProviders();
    }


    private static AuthenticationProvider getProvider(String name)
    {
        for (AuthenticationProvider provider : _allProviders)
            if (provider.getName().equals(name))
                return provider;

        return null;
    }


    private static final String AUTHENTICATION_SET = "Authentication";
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

        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(AUTHENTICATION_SET, true);
        props.put(PROVIDERS_KEY, sb.toString());
        props.save();
        loadProperties();
    }


    private static final String AUTH_LOGO_URL_SET = "AuthenticationLogoUrls";

    private static void saveAuthLogoURL(String name, String url)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(AUTH_LOGO_URL_SET, true);
        props.put(name, url);
        props.save();
    }


    private static Map<String, String> getAuthLogoURLs()
    {
        return PropertyManager.getProperties(AUTH_LOGO_URL_SET);
    }


    private static void loadProperties()
    {
        Map<String, String> props = PropertyManager.getProperties(AUTHENTICATION_SET);
        String activeProviderProp = props.get(PROVIDERS_KEY);
        List<String> activeNames = Arrays.asList(null != activeProviderProp ? activeProviderProp.split(PROP_SEPARATOR) : new String[0]);
        List<AuthenticationProvider> activeProviders = new ArrayList<>(_allProviders.size());

        // For now, auth providers are always handled in order of registration: LDAP, OpenSSO, DB.  TODO: Provide admin with mechanism for ordering

        // Add all permanent & active providers to the activeProviders list
        for (AuthenticationProvider provider : _allProviders)
            if (provider.isPermanent() || activeNames.contains(provider.getName()))
                addProvider(activeProviders, provider);

        props = getAuthLogoURLs();
        Map<String, LinkFactory> factories = new HashMap<>();

        for (String key : props.keySet())
            if (activeProviders.contains(getProvider(key)))
                factories.put(key, new LinkFactory(props.get(key), key));

        _activeProviders.clear();
        _activeProviders.addAll(activeProviders);
        _linkFactories = factories;
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

    public static class AuthenticationResult
    {
        private final User _user;
        private final AuthenticationStatus _status;

        // Success case
        private AuthenticationResult(@NotNull User user)
        {
            _user = user;
            _status = AuthenticationStatus.Success;
        }

        // Failure case
        private AuthenticationResult(@NotNull AuthenticationStatus status)
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
    AuthenticationResult authenticate(HttpServletRequest request, HttpServletResponse response, String id, String password, URLHelper returnURL, boolean logFailures) throws InvalidEmailException
    {
        AuthenticationResult result = null;
        try
        {
            result = _beforeAuthenticate(request, id, password);
            if (null != result)
                return result;
            result = _authenticate(request, response,id,password,returnURL,logFailures);
            return result;
        }
        finally
        {
            _afterAuthenticate(request, id, password, result);
        }
    }


    private static @NotNull
    AuthenticationResult _authenticate(HttpServletRequest request, HttpServletResponse response, String id, String password, URLHelper returnURL, boolean logFailures) throws InvalidEmailException
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
                else if (authProvider instanceof RequestAuthenticationProvider)
                {
                    if (areNotNull(request, response))
                        authResponse = ((RequestAuthenticationProvider)authProvider).authenticate(request, response, returnURL);
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
                    ValidEmail email = authResponse.getValidEmail();
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

                        return new AuthenticationResult(AuthenticationStatus.UserCreationError);
                    }

                    if (!user.isActive())
                    {
                        addAuditEvent(user, request, "Inactive user " + user.getEmail() + " attempted to login");
                        return new AuthenticationResult(AuthenticationStatus.InactiveUser);
                    }

                    _userProviders.put(user.getUserId(), authProvider);
                    addAuditEvent(user, request, email + " logged in successfully via " + authProvider.getName() + " authentication.");
                    return new AuthenticationResult(user);
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

        return new AuthenticationResult(AuthenticationStatus.BadCredentials);
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


    private static AuthenticationResult _beforeAuthenticate(HttpServletRequest request, String id, String pwd)
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
            return new AuthenticationResult(AuthenticationStatus.LoginPaused);
        if (delay > 0)
            try {Thread.sleep(delay);}catch(InterruptedException x){/* */}
        return null;
    }


    private static void _afterAuthenticate(HttpServletRequest request, String id, String pwd, AuthenticationResult result)
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


    // Attempts to authenticate using only LoginFormAuthenticationProviders (e.g., DbLogin, LDAP).  This is for the case
    // where you have an id & password in hand and want to ignore SSO and other delegated authentication mechanisms that
    // rely on cookies, browser redirects, etc.  Current usages include basic auth and test cases.

    // Returns null if credentials are incorrect, user doesn't exist, or user is inactive
    public static User authenticate(HttpServletRequest request, String id, String password) throws InvalidEmailException
    {
        AuthenticationResult result = authenticate(request, null, id, password, null, true);

        return result.getUser();
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


    public static interface URLFactory
    {
        ActionURL getActionURL(AuthenticationProvider provider);
    }


    public static boolean isActive(String providerName)
    {
        AuthenticationProvider provider = getProvider(providerName);

        return null != provider && isActive(provider);
    }


    private static boolean isActive(AuthenticationProvider authProvider)
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


    public static HttpView getConfigurationView(URLFactory enable, URLFactory disable)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>\n");
        sb.append("<tr><td colspan=\"4\">These are the installed primary authentication providers:<br><br></td></tr>\n");

        appendProviders(getAllPrimaryProviders(), enable, disable, sb);

        List<SecondaryAuthenticationProvider> secondary = getAllSecondaryProviders();

        if (!secondary.isEmpty())
        {
            sb.append("<tr><td colspan=\"4\">&nbsp;</td></tr>");
            sb.append("<tr><td colspan=\"4\">These are the installed secondary authentication providers:<br><br></td></tr>\n");
            appendProviders(secondary, enable, disable, sb);
        }

        sb.append("<tr><td colspan=\"4\">&nbsp;</td></tr>");
        sb.append("<tr><td colspan=\"4\">");
        sb.append(PageFlowUtil.button("Done").href(PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()));
        sb.append("</td></tr></table>\n");

        return new HtmlView(sb.toString());
    }

    private static void appendProviders(List<? extends AuthenticationProvider> providers, URLFactory enable, URLFactory disable, StringBuilder sb)
    {
        for (AuthenticationProvider authProvider : providers)
        {
            sb.append("<tr><td>").append(PageFlowUtil.filter(authProvider.getName())).append("</td>");

            if (authProvider.isPermanent())
            {
                sb.append("<td>&nbsp;</td>");
            }
            else
            {
                if (isActive(authProvider))
                {
                    sb.append("<td>");
                    sb.append(PageFlowUtil.textLink("disable", disable.getActionURL(authProvider).getEncodedLocalURIString()));
                    sb.append("</td>");
                }
                else
                {
                    sb.append("<td>");
                    sb.append(PageFlowUtil.textLink("enable", enable.getActionURL(authProvider).getEncodedLocalURIString()));
                    sb.append("</td>");
                }
            }

            ActionURL url = authProvider.getConfigurationLink();

            if (null == url)
            {
                sb.append("<td>&nbsp;</td>");
            }
            else
            {
                sb.append("<td>");
                sb.append(PageFlowUtil.textLink("configure", url.getEncodedLocalURIString()));
                sb.append("</td>");
            }

            sb.append("<td>");
            sb.append(authProvider.getDescription());
            sb.append("</td>");

            sb.append("</tr>\n");
        }
    }


    public static void setSecondaryAuthenticationSuccess(HttpServletRequest request, Class<? extends SecondaryAuthenticationProvider> clazz)
    {
        HttpSession session = request.getSession(true);
        session.setAttribute(clazz.getName(), true);
    }


    public static boolean getSecondaryAuthenticationSuccess(HttpServletRequest request, Class<? extends SecondaryAuthenticationProvider> clazz)
    {
        HttpSession session = request.getSession(true);

        return Boolean.TRUE.equals(session.getAttribute(clazz.getName()));
    }


    public static void handleSecondaryAuthentication(User candidate, Container c, HttpServletRequest request, URLHelper afterLogin)
    {
        for (SecondaryAuthenticationProvider provider : getActiveSecondaryProviders())
        {
            if (!getSecondaryAuthenticationSuccess(request, provider.getClass()))
            {
                throw new RedirectException(provider.getRedirectURL(candidate, c, afterLogin));
            }
        }
    }


    // Implementers should annotate with @AdminConsoleAction
    public abstract static class AbstractPickAuthLogoAction extends FormViewAction<AuthLogoForm>
    {
        abstract protected String getProviderName();
        abstract protected ActionURL getReturnURL();
        abstract protected ActionURL getPostURL();

        public void validateCommand(AuthLogoForm target, Errors errors)
        {
        }

        public ModelAndView getView(AuthLogoForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/login/pickAuthLogo.jsp", new AuthLogoBean(getProviderName(), getPostURL(), getReturnURL(), reshow), errors);
        }

        public boolean handlePost(AuthLogoForm form, BindException errors) throws Exception
        {
            Map<String, MultipartFile> fileMap = getFileMap();

            boolean changedLogos = deleteLogos(form);

            try
            {
                changedLogos |= handleLogo(fileMap, HEADER_LOGO_PREFIX);
                changedLogos |= handleLogo(fileMap, LOGIN_PAGE_LOGO_PREFIX);
            }
            catch (Exception e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                return false;
            }

            // If user changed one or both logos then...
            if (changedLogos)
            {
                // Clear the image cache so the web server sends the new logo
                AttachmentCache.clearAuthLogoCache();
                // Bump the look & feel revision to force browsers to retrieve new logo
                WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            }

            saveAuthLogoURL(getProviderName(), form.getUrl());
            loadProperties();

            return false;  // Always reshow the page so user can view updates.  After post, second button will change to "Done".
        }

        // Returns true if a new logo is saved
        private boolean handleLogo(Map<String, MultipartFile> fileMap, String prefix) throws IOException, SQLException, ServletException
        {
            MultipartFile file = fileMap.get(prefix + "file");

            if (null == file || file.isEmpty())
                return false;

            if (!file.getContentType().startsWith("image/"))
                throw new ServletException(file.getOriginalFilename() + " does not appear to be an image file");

            AttachmentFile aFile = new SpringAttachmentFile(file, prefix + getProviderName());
            AttachmentService.get().addAttachments(ContainerManager.RootContainer.get(), Collections.singletonList(aFile), getUser());

            return true;
        }

        // Returns true if a logo is deleted
        public boolean deleteLogos(AuthLogoForm form) throws SQLException
        {
            String[] deletedLogos = form.getDeletedLogos();

            if (null == deletedLogos)
                return false;

            for (String logoName : deletedLogos)
                AttachmentService.get().deleteAttachment(ContainerManager.RootContainer.get(), logoName, getUser());

            return true;
        }

        public ActionURL getSuccessURL(AuthLogoForm form)
        {
            return null;  // Should never get here
        }
    }


    public static class AuthLogoBean
    {
        public final String name;
        public final ActionURL returnURL;
        public final ActionURL postURL;
        public final String url;
        public final String headerLogo;
        public final String loginPageLogo;
        public final boolean reshow;

        private AuthLogoBean(String name, ActionURL postURL, ActionURL returnURL, boolean reshow)
        {
            this.name = name;
            this.postURL = postURL;
            this.returnURL = returnURL;
            this.reshow = reshow;
            url = getAuthLogoURLs().get(name);
            headerLogo = getAuthLogoHtml(name, HEADER_LOGO_PREFIX);
            loginPageLogo = getAuthLogoHtml(name, LOGIN_PAGE_LOGO_PREFIX);
        }

        public String getAuthLogoHtml(String name, String prefix)
        {
            LinkFactory factory = new LinkFactory("", name);
            String logo = factory.getImg(prefix);

            if (null == logo)
            {
                return "<td colspan=\"2\"><input name=\"" + prefix + "file\" type=\"file\" size=\"60\"></td>";
            }
            else
            {
                StringBuilder html = new StringBuilder();

                String id1 = prefix + "td1";
                String id2 = prefix + "td2";

                html.append("<td id=\"").append(id1).append("\">");
                html.append(logo);
                html.append("</td><td id=\"").append(id2).append("\" width=\"100%\">");
                html.append(PageFlowUtil.textLink("delete", "javascript:{}", "deleteLogo('" + prefix + "');", "")); // RE_CHECK
                html.append("</td>\n");

                return html.toString();
            }
        }
    }


    public static class AuthLogoForm
    {
        private String _url;
        private String[] _deletedLogos;

        public String getUrl()
        {
            return _url;
        }

        public void setUrl(String url)
        {
            _url = url;
        }

        public String[] getDeletedLogos()
        {
            return _deletedLogos;
        }

        public void setDeletedLogos(String[] deletedLogos)
        {
            _deletedLogos = deletedLogos;
        }
    }


    public static class LinkFactory
    {
        private final String NO_LOGO = "NO_LOGO";
        private boolean _isFixedURL;
        private String _urlPrefix = null;
        private String _urlSuffix = null;
        private String _name;

        // Need to check the attachments service to see if logo exists... use map to check this once and cache result
        private Map<String, String> _imgMap = new HashMap<>();

        private LinkFactory(String redirectUrl, String name)
        {
            _name = name;
            Matcher matcher = Pattern.compile("%returnURL%", Pattern.CASE_INSENSITIVE).matcher(redirectUrl);

            _isFixedURL = !matcher.find();

            if (_isFixedURL)
            {
                _urlPrefix = redirectUrl;
            }
            else
            {
                _urlPrefix = redirectUrl.substring(0, matcher.start());
                _urlSuffix = redirectUrl.substring(matcher.end(), redirectUrl.length());
            }
        }

        private String getLink(ActionURL returnURL, String prefix)
        {
            String img = getImg(prefix);

            if (null == img)
                return null;
            else
                return "<a href=\"" + PageFlowUtil.filter(getURL(returnURL)) + "\">" + img + "</a>";
        }

        public String getURL(URLHelper returnURL)
        {
            if (_isFixedURL)
            {
                return _urlPrefix;
            }
            else
            {
                ActionURL loginURL = PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(returnURL);
                return _urlPrefix + loginURL.getURIString() + _urlSuffix;
            }
        }

        private String getImg(String prefix)
        {
            String img = _imgMap.get(prefix);

            if (null == img)
            {
                img = NO_LOGO;

                try
                {
                    Attachment logo = AttachmentService.get().getAttachment(ContainerManager.RootContainer.get(), prefix + _name);

                    if (null != logo)
                        img = "<img src=\"" + AppProps.getInstance().getContextPath() + "/" + prefix + _name + ".image?revision=" + AppProps.getInstance().getLookAndFeelRevision() + "\" alt=\"Sign in using " + _name + "\">";
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
                AuthenticationResult r = AuthenticationManager.authenticate(req, res, "testA@localhost.test", "passwordA"+i, null, false);
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
                AuthenticationResult r = AuthenticationManager.authenticate(req, res, "testB" + i + "@localhost.test", "passwordB", null, false);
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
                AuthenticationResult r = AuthenticationManager.authenticate(req, res, "testC@localhost.test", "passwordC", null, false);
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
