package org.labkey.api.security;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.attachments.*;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.AuthenticationProvider.RequestAuthenticationProvider;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.WriteableAppProps;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:53:03 PM
 */
public class AuthenticationManager
{
    // All registered authentication providers (DbLogin, LDAP, SSO, etc.)
    private static List<AuthenticationProvider> _allProviders = new ArrayList<AuthenticationProvider>();
    private static List<AuthenticationProvider> _activeProviders = null;
    // Map of user id to login provider.  This is needed to handle clean up on logout.
    private static Map<Integer, AuthenticationProvider> _userProviders = new HashMap<Integer, AuthenticationProvider>();
    private static LoginURLFactory _loginURLFactory = null;
    private static ActionURL _logoutURL = null;

    private static final Logger _log = Logger.getLogger(AuthenticationManager.class);
    private static Map<String, LinkFactory> _linkFactories = new HashMap<String, LinkFactory>();
    public static final String HEADER_LOGO_PREFIX = "auth_header_logo_";
    public static final String LOGIN_PAGE_LOGO_PREFIX = "auth_login_page_logo_";

    public interface LoginURLFactory
    {
        ActionURL getURL(ActionURL returnURL);
        ActionURL getURL(String returnURL);
    }


    public static void initialize()
    {
        // Load active providers and authentication logos.  Each active provider is initialized at load time. 
        loadProperties();
    }


    private static LoginURLFactory getLoginURLFactory()
    {
        if (null == _loginURLFactory)
            throw new IllegalArgumentException("Login URL factory has not been set");

        return _loginURLFactory;
    }


    public static ActionURL getLoginURL(ActionURL returnURL)
    {
        if (null == returnURL)
            returnURL = AppProps.getInstance().getHomePageActionURL();

        return getLoginURLFactory().getURL(returnURL);
    }

    /**
     * Additional login url method to handle non-action url strings
     */
    public static ActionURL getLoginURL(String returnURL)
    {
        return getLoginURLFactory().getURL(returnURL);
    }


    public static void setLoginURLFactory(LoginURLFactory loginURLFactory)
    {
        if (null != _loginURLFactory)
            throw new IllegalArgumentException("Login URL factory has already been set");

        _loginURLFactory = loginURLFactory;
    }


    public static ActionURL getLogoutURL()
    {
        if (null == _logoutURL)
            throw new IllegalArgumentException("Logout URL has not been set");

        return _logoutURL;
    }


    public static void setLogoutURL(ActionURL logoutURL)
    {
        if (null != _logoutURL)
            throw new IllegalArgumentException("Logout URL has already been set");

        _logoutURL = logoutURL;
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


    private static String getAuthLogoHtml(ActionURL currentUrl, String prefix)
    {
        if (_linkFactories.size() == 0)
            return null;

        StringBuilder html = new StringBuilder();

        for (LinkFactory factory : _linkFactories.values())
        {
            String link = factory.getLink(currentUrl, prefix);

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


    public static void registerProvider(AuthenticationProvider authProvider)
    {
        _allProviders.add(0, authProvider);
    }


    public static List<AuthenticationProvider> getActiveProviders()
    {
        assert (null != _activeProviders);

        return _activeProviders;
    }


    public static void enableProvider(String name)
    {
        AuthenticationProvider provider = getProvider(name);
        try
        {
            provider.initialize();
        }
        catch (Exception e)
        {
            _log.error("Can't initialize provider " + provider.getName(), e);
        }
        _activeProviders.add(provider);

        saveActiveProviders();
    }


    public static void disableProvider(String name)
    {
        AuthenticationProvider provider = getProvider(name);
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

        Map<String, String> props = PropertyManager.getWritableProperties(AUTHENTICATION_SET, true);
        props.put(PROVIDERS_KEY, sb.toString());
        PropertyManager.saveProperties(props);
        loadProperties();
    }


    private static final String AUTH_LOGO_URL_SET = "AuthenticationLogoUrls";

    private static void saveAuthLogoURL(String name, String url)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(AUTH_LOGO_URL_SET, true);
        props.put(name, url);
        PropertyManager.saveProperties(props);
    }


    private static Map<String, String> getAuthLogoURLs()
    {
        return PropertyManager.getProperties(AUTH_LOGO_URL_SET, true);
    }


    public static String getAuthLogoHtml(String name, String prefix)
    {
        LinkFactory factory = new LinkFactory("", name);
        String logo = factory.getImg(prefix);

        if (null == logo)
            return "Logo is not set";
        else
            return "Current logo: " + logo;
    }


    private static void loadProperties()
    {
        Map<String, String> props = PropertyManager.getProperties(AUTHENTICATION_SET, true);
        String activeProviderProp = props.get(PROVIDERS_KEY);
        String[] providerNames = (null != activeProviderProp ? activeProviderProp.split(PROP_SEPARATOR) : new String[0]);

        List<AuthenticationProvider> activeProviders = new ArrayList<AuthenticationProvider>(_allProviders.size());

        // Add all the saved providers in order
        for (String name : providerNames)
        {
            AuthenticationProvider provider = getProvider(name);

            if (null != provider)
                addProvider(activeProviders, provider);
        }

        // Now add any permanent providers that haven't been included yet
        for (AuthenticationProvider provider : _allProviders)
            if (provider.isPermanent() && !activeProviders.contains(provider))
                addProvider(activeProviders, provider);

        props = getAuthLogoURLs();
        Map<String, LinkFactory> factories = new HashMap<String, LinkFactory>();

        for (String key : props.keySet())
            if (activeProviders.contains(getProvider(key)))
                factories.put(key, new LinkFactory(props.get(key), key));

        _activeProviders = activeProviders;
        _linkFactories = factories;
    }


    private static void addProvider(List<AuthenticationProvider> providers, AuthenticationProvider provider)
    {
        try
        {
            provider.initialize();
            providers.add(provider);
        }
        catch (Exception e)
        {
            _log.error("Couldn't initialize provider", e);
        }
    }


    public static User authenticate(HttpServletRequest request, HttpServletResponse response, String id, String password) throws ValidEmail.InvalidEmailException
    {
        ValidEmail email = null;

        for (AuthenticationProvider authProvider : getActiveProviders())
        {
            if (authProvider instanceof LoginFormAuthenticationProvider)
            {
                if (areNotBlank(id, password))
                    email = ((LoginFormAuthenticationProvider)authProvider).authenticate(id, password);
            }
            else
            {
                if (areNotNull(request, response))
                {
                    try
                    {
                        email = ((RequestAuthenticationProvider)authProvider).authenticate(request, response);
                    }
                    catch (RedirectException e)
                    {
                        throw new RuntimeException(e);  // Some authentication provider has seen a hint and chosen to redirect
                    }
                }
            }

            if (email != null)
            {
                User user = SecurityManager.createUserIfNecessary(email);
                _userProviders.put(user.getUserId(), authProvider);
                AuditLogService.get().addEvent(user, null, UserManager.USER_AUDIT_EVENT, user.getUserId(),
                        "User: " + email + " logged in successfully.");
                return user;
            }
        }

        return null;
    }


    // Attempts to authenticate using only LoginFormAuthenticationProviders (e.g., DbLogin, LDAP).  This is for the case
    //  where you have an id & password in hand (from a post or get) and want to ignore SSO and other delegated
    //  authentication mechanisms that rely on cookies, browser redirects, etc.
    public static User authenticate(String id, String password) throws ValidEmail.InvalidEmailException
    {
        return authenticate(null, null, id, password);
    }


    public static void logout(User user, HttpServletRequest request)
    {
        AuthenticationProvider provider = _userProviders.get(user.getUserId());

        if (null != provider)
            provider.logout(request);

        AuditLogService.get().addEvent(user, null, UserManager.USER_AUDIT_EVENT, user.getUserId(),
                "User: " + user.getEmail() + " logged out.");
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


    public static HttpView getConfigurationView(ActionURL currentUrl, ActionURL returnUrl, URLFactory enable, URLFactory disable)
    {
        StringBuilder sb = new StringBuilder("These are the installed authentication providers:<br><br>\n");
        sb.append("<table>\n");

        List<AuthenticationProvider> activeProviders = getActiveProviders();

        for (AuthenticationProvider authProvider : _allProviders)
        {
            sb.append("<tr><td>").append(PageFlowUtil.filter(authProvider.getName())).append("</td>");

            if (authProvider.isPermanent())
            {
                sb.append("<td>&nbsp</td>");
            }
            else if (activeProviders.contains(authProvider))
            {
                sb.append("<td>[<a href=\"");
                sb.append(disable.getActionURL(authProvider).getEncodedLocalURIString());
                sb.append("\">");
                sb.append("disable");
                sb.append("</a>]</td>");
            }
            else
            {
                sb.append("<td>[<a href=\"");
                sb.append(enable.getActionURL(authProvider).getEncodedLocalURIString());
                sb.append("\">");
                sb.append("enable");
                sb.append("</a>]</td>");
            }

            ActionURL url = authProvider.getConfigurationLink(currentUrl);

            if (null != url)
            {
                sb.append("<td>[<a href=\"");
                sb.append(url.getEncodedLocalURIString());
                sb.append("\">");
                sb.append("configure");
                sb.append("</a>]</td>");
            }

            sb.append("</tr>\n");
        }

        sb.append("</table><br>\n");
        sb.append(PageFlowUtil.buttonLink("Done", returnUrl));

        return new HtmlView(sb.toString());
    }


    public abstract static class PickAuthLogoAction extends FormViewAction<AuthLogoForm>
    {
        abstract protected String getProviderName();

        public void validateCommand(AuthLogoForm target, Errors errors)
        {
        }

        public ModelAndView getView(AuthLogoForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<AuthLogoBean>("/org/labkey/core/login/pickAuthLogo.jsp", new AuthLogoBean(getProviderName(), form.getReturnUrl(), reshow));
        }

        public boolean handlePost(AuthLogoForm form, BindException errors) throws Exception
        {
            Map<String, MultipartFile> fileMap = getFileMap();

            // Using inclusive OR to avoid short-circuiting
            boolean newLogo = handleLogo(fileMap, HEADER_LOGO_PREFIX) | handleLogo(fileMap, LOGIN_PAGE_LOGO_PREFIX);

            // If user changed one or both logos then...
            if (newLogo)
            {
                // Clear the image cache so the web server sends the new logo
                AttachmentCache.clearAuthLogoCache();
                // Bump the look & feel revision to force browsers to retrieve new logo
                WriteableAppProps.incrementLookAndFeelRevision2();
            }

            saveAuthLogoURL(getProviderName(), form.getUrl());
            loadProperties();

            return false;  // Always reshow the page so user can view updates.  After post, second button will change to "Done".
        }

        // Returns true if a new logo has been saved
        private boolean handleLogo(Map<String, MultipartFile> fileMap, String prefix) throws IOException, SQLException
        {
            MultipartFile file = fileMap.get(prefix + "file");

            if (!file.isEmpty())
            {
                AttachmentFile aFile = new SpringAttachmentFile(file);
                String logoName = prefix + getProviderName();
                aFile.setFilename(logoName);

                // Delete logo if it already exists
                AttachmentService.get().delete(getViewContext().getUser(), ContainerManager.RootContainer.get(), logoName);
                AttachmentService.get().add(getViewContext().getUser(), ContainerManager.RootContainer.get(), Arrays.asList(aFile));

                return true;
            }

            return false;
        }

        public ActionURL getSuccessURL(AuthLogoForm form)
        {
            return null;  // Should never get here
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    public static class AuthLogoBean
    {
        public String name;
        public String returnUrl;
        public String url;
        public String headerLogo;
        public String loginPageLogo;
        public boolean reshow;

        private AuthLogoBean(String name, String returnUrl, boolean reshow)
        {
            this.name = name;
            this.returnUrl = returnUrl;
            this.reshow = reshow;
            url = getAuthLogoURLs().get(name);
            headerLogo = AuthenticationManager.getAuthLogoHtml(name, HEADER_LOGO_PREFIX);
            loginPageLogo = AuthenticationManager.getAuthLogoHtml(name, LOGIN_PAGE_LOGO_PREFIX);
        }
    }


    public static class AuthLogoForm extends ViewForm
    {
        private String _returnUrl;
        private String _url;

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(String returnUrl)
        {
            _returnUrl = returnUrl;
        }

        public String getUrl()
        {
            return _url;
        }

        public void setUrl(String url)
        {
            _url = url;
        }
    }


    public static class LinkFactory
    {
        private final String NO_LOGO = "NO_LOGO";
        private Matcher _redirectURLMatcher;
        private String _name;

        // Need to check the attachments service to see if logo exists... use map to check this once and cache result
        private Map<String, String> _imgMap = new HashMap<String, String>();

        private LinkFactory(String redirectUrl, String name)
        {
            _name = name;
            _redirectURLMatcher = Pattern.compile("%returnURL%", Pattern.CASE_INSENSITIVE).matcher(redirectUrl);
        }

        private String getLink(ActionURL returnURL, String prefix)
        {
            String img = getImg(prefix);

            if (null == img)
                return null;
            else
                return "<a href=\"" + PageFlowUtil.filter(getURL(returnURL)) + "\">" + img + "</a>";
        }

        public String getURL(ActionURL returnURL)
        {
            ActionURL loginUrl = getLoginURL(returnURL);
            return _redirectURLMatcher.replaceFirst(PageFlowUtil.encode(loginUrl.getURIString()));
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
                catch(SQLException e)
                {
                    // TODO: log to mothership
                }

                _imgMap.put(prefix, img);
            }

            return (NO_LOGO.equals(img) ? null : img);
        }
    }
}
