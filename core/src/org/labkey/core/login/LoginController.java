/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.core.login;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.FormattedError;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Project;
import org.labkey.api.module.AllowedBeforeInitialUserIsSet;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.AuthenticationResult;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.Group;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityMessage;
import org.labkey.api.security.TokenAuthentication;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.core.admin.AdminController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;

/**
 * User: adam
 * Date: Nov 25, 2007
 * Time: 8:22:37 PM
 */
public class LoginController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(LoginController.class);
    private static final ActionResolver _actionResolver = new DefaultActionResolver(LoginController.class);
    private static final int secondsPerYear = 60 * 60 * 24 * 365;

    public LoginController()
    {
        setActionResolver(_actionResolver);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig ret = super.defaultPageConfig();
        ret.setFrameOption(PageConfig.FrameOption.DENY);
        return ret;
    }

    public static class LoginUrlsImpl implements LoginUrls
    {
        public NavTree appendAuthenticationNavTrail(NavTree root)
        {
            root.addChild("Admin Console", AdminController.getShowAdminURL()).addChild("Authentication", getConfigureURL());
            return root;
        }

        public ActionURL getConfigureURL()
        {
            return new ActionURL(ConfigureAction.class, ContainerManager.getRoot());
        }

        public ActionURL getConfigureDbLoginURL()
        {
            return LoginController.getConfigureDbLoginURL(false);
        }

        public ActionURL getInitialUserURL()
        {
            return new ActionURL(InitialUserAction.class, ContainerManager.getRoot());
        }

        public ActionURL getVerificationURL(Container c, ValidEmail email, String verification, @Nullable Pair<String, String>[] extraParameters)
        {
            //FIX: 6021, use project container for this URL so it remains short but maintains the project look & feel settings 
            ActionURL url = new ActionURL(SetPasswordAction.class, LookAndFeelProperties.getSettingsContainer(c));
            url.addParameter("verification", verification);
            url.addParameter("email", email.getEmailAddress());

            if (null != extraParameters)
                url.addParameters(extraParameters);

            return url;
        }

        public ActionURL getChangePasswordURL(Container c, User user, URLHelper returnURL, @Nullable String message)
        {
            ActionURL url = new ActionURL(ChangePasswordAction.class, LookAndFeelProperties.getSettingsContainer(c));
            url.addParameter("email", user.getEmail());     // TODO: seems peculiar... why not user id?

            if (null != message)
                url.addParameter("message", message);

            url.addReturnURL(returnURL);

            return url;
        }

        public ActionURL getLoginURL()
        {
            return getLoginURL(HttpView.getContextContainer(), HttpView.getContextURLHelper());
        }

        public ActionURL getLoginURL(URLHelper returnURL)
        {
            // Use root as placeholder; extra path of returnURL determines the real login URL path
            ActionURL url = new ActionURL(LoginAction.class, ContainerManager.getRoot());

            if (null == returnURL)
                returnURL = AppProps.getInstance().getHomePageActionURL();

            if (returnURL instanceof ActionURL)
                url.setExtraPath(((ActionURL)returnURL).getExtraPath());

            url.addReturnURL(returnURL);
            return url;
        }


        public ActionURL getLoginURL(Container c, URLHelper returnURL)
        {
            ActionURL url = new ActionURL(LoginAction.class, c);
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getLogoutURL(Container c)
        {
            return new ActionURL(LogoutAction.class, c);
        }

        public ActionURL getLogoutURL(Container c, URLHelper returnURL)
        {
            ActionURL url = getLogoutURL(c);
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getStopImpersonatingURL(Container c, @Nullable URLHelper returnURL)
        {
            if (null != returnURL)
                return getLogoutURL(c, returnURL);
            else
                return getLogoutURL(c);
        }

        public ActionURL getAgreeToTermsURL(Container c, URLHelper returnURL)
        {
            ActionURL url = new ActionURL(AgreeToTermsAction.class, c);
            url.addReturnURL(returnURL);
            return url;
        }
    }


    private static LoginUrlsImpl getUrls()
    {
        return new LoginUrlsImpl();
    }

    @Nullable
    private static User authenticate(LoginForm form, BindException errors, ViewContext viewContext, boolean logFailures)
    {
        User user = null;

        HttpServletRequest request = viewContext.getRequest();
        HttpServletResponse response = viewContext.getResponse();

        try
        {
            // Attempt authentication with all registered providers
            AuthenticationResult result = AuthenticationManager.authenticate(request, response, form.getEmail(), form.getPassword(), form.getReturnURLHelper(), logFailures);
            LookAndFeelProperties laf;

            switch (result.getStatus())
            {
                case Success:
                    user = result.getUser();
                    SecurityManager.setAuthenticatedUser(request, user);
                    break;
                case InactiveUser:
                    laf = LookAndFeelProperties.getInstance(viewContext.getContainer());
                    errors.addError(new FormattedError("Your account has been deactivated. Please <a href=\"mailto:" + PageFlowUtil.filter(laf.getSystemEmailAddress()) + "\">contact a system administrator</a> if you need to reactivate this account."));
                    break;
                case BadCredentials:
                    if (null != form.getEmail() || null != form.getPassword())
                    {
                        // Email & password were specified, but authentication failed...
                        // display either invalid email address error or generic "couldn't authenticate" message
                        new ValidEmail(form.getEmail());
                        errors.reject(ERROR_MSG, "The e-mail address and password you entered did not match any accounts on file.\nNote: Passwords are case sensitive; make sure your Caps Lock is off.");
                    }
                    break;
                case LoginPaused:
                    if (null != form.getEmail() || null != form.getPassword())
                    {
                        new ValidEmail(form.getEmail());
                        errors.reject(ERROR_MSG, "Due to the number of recent failed login attempts, authentication has been temporarily paused.\nTry again in one minute.");
                    }
                    break;
                case UserCreationError:
                    laf = LookAndFeelProperties.getInstance(viewContext.getContainer());
                    errors.addError(new FormattedError("The server could not create your account. Please <a href=\"mailto:" + PageFlowUtil.filter(laf.getSystemEmailAddress()) + "\">contact a system administrator</a> for assistance."));
                    break;
                default:
                    throw new IllegalStateException("Unknown authentication status: " + result.getStatus());
            }
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            String defaultDomain = ValidEmail.getDefaultDomain();
            StringBuilder sb = new StringBuilder();
            sb.append("Please sign in using your full email address, for example: ");
            if (defaultDomain != null && defaultDomain.length() > 0)
            {
                sb.append("employee@");
                sb.append(defaultDomain);
                sb.append(" or ");
            }
            sb.append("employee@domain.com");
            errors.reject(ERROR_MSG, sb.toString());
        }

        return user;
    }


    public static boolean deauthenticate(User user, ViewContext context)
    {
        if (user.isImpersonated())
            SecurityManager.stopImpersonating(context.getRequest(), user.getImpersonationContext().getFactory());
        else
            SecurityManager.logoutUser(context.getRequest(), user);

        return true;
    }


    @RequiresNoPermission
    @ActionNames("login, showLogin")
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    // @CSRF don't need CSRF for actions that require a password
    public class LoginAction extends FormViewAction<LoginForm>
    {
        private User _user = null;

        public void validateCommand(LoginForm form, Errors errors)
        {
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            HttpServletResponse response = getViewContext().getResponse();

            // If we're reshowing, the user must have entered incorrect credentials.
            // Set the response code accordingly
            if (reshow)
            {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }

            // If user is already logged in, then redirect immediately.  This is necessary because of Excel's link
            // behavior (see #9246).  Next, check to see if any authentication credentials already exist (passed as URL
            // param, in a cookie, etc.)
            if (!reshow && (isLoggedIn() || authenticate(form, errors, getViewContext(), false) != null))
                return HttpView.redirect(getSuccessURL(form));
            else
                return showLogin(form, errors, request, getPageConfig());
        }

        public boolean handlePost(LoginForm form, BindException errors) throws Exception
        {
            Project termsProject = getTermsOfUseProject(form);

            if (null != termsProject)
            {
                if (!isTermsOfUseApproved(form))
                {
                    errors.reject(ERROR_MSG, "To use the " + termsProject.getName() + " project, you must log in and approve the terms of use.");
                    return false;
                }
            }

            _user = authenticate(form, errors, getViewContext(), true);
            boolean success = (null != _user);

            if (success)
            {
                // Terms of use are approved only if we've posted from the login page.  In SSO case, we will attempt
                // to access the page and will get a TermsOfUseException if terms of use approval is required.
                if (null != termsProject)
                    SecurityManager.setTermsOfUseApproved(getViewContext(), termsProject, true);

                // Login page is container qualified, but we need to store the cookie at /labkey/login/ or /cpas/login/ or /login/
                String path = StringUtils.defaultIfEmpty(getViewContext().getContextPath(), "/");

                HttpServletResponse response = getViewContext().getResponse();

                if (form.isRemember())
                {
                    // Write cookies to save email.
                    // Starting in LabKey 9.1, the cookie value is URL encoded to allow for special characters like @.  See #6736.
                    String unencodedValue = form.getEmail();
                    String encodedValue = PageFlowUtil.encode(unencodedValue);
                    Cookie emailCookie = new Cookie("email", encodedValue);
                    emailCookie.setMaxAge(secondsPerYear);
                    emailCookie.setPath(path);
                    response.addCookie(emailCookie);
                }
                else
                {
                    // Clear the cookie
                    Cookie emailCookie = new Cookie("email", "");
                    emailCookie.setMaxAge(0);
                    emailCookie.setPath(path);
                    response.addCookie(emailCookie);
                }
            }

            return success;
        }

        private boolean isLoggedIn()
        {
            if (getUser().isGuest())
                return false;

            _user = getUser();
            return true;
        }

        public URLHelper getSuccessURL(LoginForm form)
        {
            return getAfterLoginURL(form, _user, form.getSkipProfile());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresNoPermission
    @IgnoresTermsOfUse
    // @AllowedDuringUpgrade
    public class LoginApiAction extends MutatingApiAction<LoginForm>
    {
        @Override
        public Object execute(LoginForm form, BindException errors) throws Exception
        {
            // TODO: check during upgrade?
            Project termsProject = getTermsOfUseProject(form);

            if (null != termsProject)
            {
                if (!isTermsOfUseApproved(form))
                {
                    if (!form.isApprovedTermsOfUse())
                    {
                        // Determine if the user is already logged in
                        if (!getUser().isGuest())
                        {
                            errors.rejectValue("approvedTermsOfUse", ERROR_MSG, "To use the " + termsProject.getName() + " project, you must approve the terms of use.");
                        }
                        else
                        {
                            errors.rejectValue("approvedTermsOfUse", ERROR_MSG, "To use the " + termsProject.getName() + " project, you must log in and approve the terms of use.");
                        }
                    }
                }
            }

            ApiSimpleResponse response = null;
            User user = authenticate(form, errors, getViewContext(), true);

            if (!errors.hasErrors())
            {
                boolean success = (null != user);

                if (success)
                {
                    response = new ApiSimpleResponse();
                    response.put("success", success);
                    response.put("user", User.getUserProps(user, getContainer()));

                    if (form.isApprovedTermsOfUse())
                    {
                        SecurityManager.setTermsOfUseApproved(getViewContext(), termsProject, true);
                        response.put("approvedTermsOfUse", true);
                    }
                }
            }

            return response;
        }
    }


    protected HttpView showLogin(LoginForm form, BindException errors, HttpServletRequest request, PageConfig page) throws Exception
    {
        String email = form.getEmail();
        boolean remember = false;

        // If email is null, check to see if email is saved in cookie
        // If email is already filled in (e.g., from setPassword), use it instead of the cookie

        if (null == email)
        {
            email = getEmailFromCookie(request);
            form.setEmail(email);
        }

        if (null != email)
            remember = true;

        VBox vBox = new VBox();

        if (ModuleLoader.getInstance().isUpgradeRequired() || ModuleLoader.getInstance().isUpgradeInProgress())
        {
            HtmlView updateMessageView = new HtmlView("Server upgrade in progress",
                    "This server is being upgraded to a new version of LabKey Server.<br/>" +
                    "Only Site Administrators are permitted to log in during the upgrade process.");
            vBox.addView(updateMessageView);
        }
        else if (!ModuleLoader.getInstance().isStartupComplete())
        {
            HtmlView startupMessageView = new HtmlView("Server startup in progress",
                    "This server is starting up.<br/>" +
                    "Only Site Administrators are permitted to log in during the startup process.");
            vBox.addView(startupMessageView);
        }
        else if (isAdminOnlyMode())
        {
            String content = "The site is currently undergoing maintenance.";
            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            if (null != wikiService)
            {
                content = wikiService.getFormattedHtml(WikiRendererType.RADEOX, ModuleLoader.getInstance().getAdminOnlyMessage());
            }
            HtmlView adminMessageView = new HtmlView("The site is currently undergoing maintenance", content);
            vBox.addView(adminMessageView);
        }

        page.setTemplate(PageConfig.Template.Dialog);
        page.setIncludeLoginLink(false);
        page.setTitle("Sign In");

        LoginView view = new LoginView(form, errors, remember, form.isApprovedTermsOfUse());
        vBox.addView(view);

        return vBox;
    }


    private URLHelper getAfterLoginURL(ReturnUrlForm form, @Nullable User user, boolean skipProfile)
    {
        Container current = getContainer();
        Container c = (null == current || current.isRoot() ? ContainerManager.getHomeContainer() : getContainer());

        // Default redirect if returnURL is not specified.  Try not to redirect to a folder where the user doesn't have permissions, #12947
        URLHelper defaultURL =
                null == current || current.isRoot() || (null != user && !c.hasPermission(user, ReadPermission.class)) ? getWelcomeURL() :
                null != user ? c.getStartURL(user) :
                AppProps.getInstance().getHomePageActionURL();

        // After successful login (and possibly profile update) we'll end up here
        URLHelper returnURL = form.getReturnURLHelper(defaultURL);

        // If this is user's first log in or some required field isn't filled in then go to update page first
        if (!skipProfile && null != user)
        {
            returnURL = PageFlowUtil.urlProvider(UserUrls.class).getCheckUserUpdateURL(c, returnURL, user.getUserId(), !user.isFirstLogin());
        }

        if (null != form.getUrlhash())
        {
            returnURL.setFragment(form.getUrlhash().replace("#", ""));
        }

        return returnURL;
    }


    public boolean isAdminOnlyMode()
    {
        return AppProps.getInstance().isUserRequestedAdminOnlyMode() || (ModuleLoader.getInstance().isUpgradeRequired() && !UserManager.hasNoUsers());
    }


    @Nullable
    private String getEmailFromCookie(HttpServletRequest request)
    {
        String email = null;
        Cookie[] cookies = request.getCookies();

        if (null != cookies)
        {
            // Starting in LabKey 9.1, the cookie value is URL encoded to allow for special characters like @.  See #6736.
            String encodedEmail = PageFlowUtil.getCookieValue(cookies, "email", null);

            if (null != encodedEmail)
                email = PageFlowUtil.decode(encodedEmail);
        }

        return email;
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @CSRF
    public class AgreeToTermsAction extends FormViewAction<LoginForm>
    {
        public void validateCommand(LoginForm target, Errors errors)
        {
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors) throws Exception
        {
            AgreeToTermsView view = new AgreeToTermsView(form, errors);

            PageConfig page = getPageConfig();

            page.setTemplate(PageConfig.Template.Dialog);
            page.setTitle("Terms Of Use");
            page.setIncludeLoginLink(false);

            return view;
        }

        public boolean handlePost(LoginForm form, BindException errors) throws Exception
        {
            Project project = getTermsOfUseProject(form);

            if (null != project)
            {
                if (!form.isApprovedTermsOfUse())
                {
                    errors.reject(ERROR_MSG, "To use the " + project.getName() +  " project, you must check the box to approve the terms of use.");
                    return false;
                }

                SecurityManager.setTermsOfUseApproved(getViewContext(), project, true);
            }

            return true;
        }

        public URLHelper getSuccessURL(LoginForm form)
        {
            return form.getReturnURLHelper();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private abstract class BaseLoginView extends JspView<LoginBean>
    {
        private BaseLoginView(LoginForm form, BindException errors, boolean agreeOnly, boolean remember, boolean termsOfUseChecked)
        {
            super("/org/labkey/core/login/login.jsp", new LoginBean(form, agreeOnly, remember, termsOfUseChecked), errors);
            setFrame(FrameType.NONE);
        }
    }


    public class LoginBean
    {
        public LoginForm form;
        public boolean agreeOnly;
        public boolean remember;
        public String termsOfUseHTML = null;
        public boolean termsOfUseChecked;

        private LoginBean(LoginForm form, boolean agreeOnly, boolean remember, boolean termsOfUseChecked)
        {
            this.form = form;
            this.agreeOnly = agreeOnly;
            this.remember = remember;
            this.termsOfUseChecked = termsOfUseChecked;

            try
            {
                Project project = getTermsOfUseProject(form);

                // Display the terms of use if this is the terms-of-use page or user hasn't already approved them. #4684
                if (agreeOnly || !SecurityManager.isTermsOfUseApproved(getViewContext(), project))
                    termsOfUseHTML = SecurityManager.getTermsOfUseHtml(project);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }


    private class LoginView extends BaseLoginView
    {
        private LoginView(LoginForm form, BindException errors, boolean remember, boolean termsOfUseChecked)
        {
            super(form, errors, false, remember, termsOfUseChecked);
        }
    }


    private class AgreeToTermsView extends BaseLoginView
    {
        private AgreeToTermsView(LoginForm form, BindException errors)
        {
            super(form, errors, true, false, false);
        }
    }


    @Nullable
    private Project getTermsOfUseProject(LoginForm form) throws ServletException
    {
        Container termsContainer = null;

        URLHelper returnURL = form.getReturnURLHelper(null);

        if (null != returnURL)
        {
            try
            {
                Container redirContainer = ContainerManager.getForPath(new ActionURL(returnURL.getLocalURIString()).getExtraPath());
                if (null != redirContainer)
                    termsContainer = redirContainer.getProject();
            }
            catch (IllegalArgumentException iae)
            {
                // the redirect URL isn't an action url, so we can't get the container. Ignore.
            }
        }

        if (null == termsContainer)
        {
            Container c = getContainer();

            if (c.isRoot())
                return null;
            else
                termsContainer = c.getProject();
        }

        return new Project(termsContainer);
    }


    private boolean isTermsOfUseApproved(LoginForm form) throws ServletException, URISyntaxException
    {
        Project termsProject = getTermsOfUseProject(form);
        return termsProject == null  || form.isApprovedTermsOfUse() || !SecurityManager.isTermsOfUseRequired(termsProject) || SecurityManager.isTermsOfUseApproved(getViewContext(), termsProject);
    }


    private static abstract class AbstractLoginForm extends ReturnUrlForm
    {
        private boolean _skipProfile = false;

        public boolean getSkipProfile()
        {
            return _skipProfile;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSkipProfile(boolean skipProfile)
        {
            _skipProfile = skipProfile;
        }
    }


    public static class LoginForm extends AbstractLoginForm
    {
        private boolean remember;
        private String email;
        private String password;
        private boolean approvedTermsOfUse;

        public void setEmail(String email)
        {
            this.email = email;
        }

        public String getEmail()
        {
            return this.email;
        }

        public String getPassword()
        {
            return password;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPassword(String password)
        {
            this.password = password;
        }

        public boolean isRemember()
        {
            return this.remember;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRemember(boolean remember)
        {
            this.remember = remember;
        }

        public boolean isApprovedTermsOfUse()
        {
            return approvedTermsOfUse;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setApprovedTermsOfUse(boolean approvedTermsOfUse)
        {
            this.approvedTermsOfUse = approvedTermsOfUse;
        }
    }


    private static URLHelper getWelcomeURL()
    {
        // goto "/" and let the default redirect happen
        return new URLHelper(true);
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class LogoutAction extends RedirectAction<ReturnUrlForm>
    {
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            // Override allows logout from any folder, even when impersonating within a project
        }

        public URLHelper getSuccessURL(ReturnUrlForm form)
        {
            return form.getReturnURLHelper(getWelcomeURL());
        }

        public boolean doAction(ReturnUrlForm form, BindException errors) throws Exception
        {
            return deauthenticate(getUser(), getViewContext());
        }

        public void validateCommand(ReturnUrlForm form, Errors errors)
        {
        }
    }

    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class LogoutApiAction extends MutatingApiAction
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            return new ApiSimpleResponse("success", deauthenticate(getUser(), getViewContext()));
        }
    }


    public static final String PASSWORD1_TEXT_FIELD_NAME = "password";
    public static final String PASSWORD2_TEXT_FIELD_NAME = "password2";

    private abstract class AbstractSetPasswordAction extends FormViewAction<SetPasswordForm>
    {
        protected ValidEmail _email = null;
        private boolean _skipProfile = true;  // In most set password scenarios we skip the profile page
        protected boolean _unrecoverableError = false;

        public void validateCommand(SetPasswordForm form, Errors errors)
        {
            ActionURL currentUrl = getViewContext().getActionURL();
            String rawEmail = form.getEmail();

            // Some plain text email clients get confused by the encoding... explicitly look for encoded name
            if (null == rawEmail)
                rawEmail = currentUrl.getParameter("amp;email");

            ValidEmail email;

            try
            {
                email = new ValidEmail(rawEmail);
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject("setPassword", "Invalid email address: " + (null == rawEmail ? "" : rawEmail));
                _unrecoverableError = true;
                return;
            }

            verify(form, email, errors);
        }

        protected void verifyBeforeView(SetPasswordForm form, boolean reshow, BindException errors) throws RedirectException
        {
            if (!reshow)
                validateCommand(form, errors);

            if (errors.hasErrors())
            {
                if (_unrecoverableError)
                    _log.warn("Verification failed: " + form.getEmail() + " " + form.getVerification());
                else
                    _log.warn("Password entry error: " + form.getEmail());
            }
        }

        protected String getEmailForForm(SetPasswordForm form)
        {
            return null != _email ? _email.getEmailAddress() : form.getEmail();
        }

        public ModelAndView getView(SetPasswordForm form, boolean reshow, BindException errors) throws Exception
        {
            verifyBeforeView(form, reshow, errors);

            NamedObjectList nonPasswordInputs = getNonPasswordInputs(form);
            NamedObjectList passwordInputs = getPasswordInputs(form);
            String buttonText = getButtonText();
            SetPasswordBean bean = new SetPasswordBean(form, getEmailForForm(form), _unrecoverableError, getMessage(form), nonPasswordInputs, passwordInputs, getClass(), isCancellable(form), buttonText);
            HttpView view = new JspView<>("/org/labkey/core/login/setPassword.jsp", bean, errors);

            PageConfig page = getPageConfig();
            page.setTemplate(PageConfig.Template.Dialog);
            page.setTitle(getTitle());
            page.setIncludeLoginLink(false);

            // If we're going to display the form, then set focus on the first input.
            if (!_unrecoverableError)
            {
                String firstInput = (String)(nonPasswordInputs.isEmpty() ? passwordInputs.get(0) : nonPasswordInputs.get(0));
                page.setFocusId(firstInput);
            }

            return view;
        }

        public boolean handlePost(SetPasswordForm form, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();

            // Pull straight from the request to minimize logging of passwords (in Spring, bean utils, etc.)
            String password = request.getParameter("password");
            String password2 = request.getParameter("password2");

            Collection<String> messages = new LinkedList<>();
            User user = UserManager.getUser(_email);

            if (!DbLoginManager.getPasswordRule().isValidToStore(password, password2, user, messages))
            {
                for (String message : messages)
                    errors.reject("password", message);

                return false;
            }

            try
            {
                SecurityManager.setPassword(_email, password);
            }
            catch (SecurityManager.UserManagementException e)
            {
                errors.reject("password", "Setting password failed: " + e.getMessage() + ".  Contact the " + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName() + " team.");
                return false;
            }

            afterPasswordSet(errors, user);

            if (errors.hasErrors())
                return false;

            // Should log user in only for initial user, choose password, and forced change password scenarios, but not for scenarios
            // where a user is already logged in (normal change password, admins initializing another user's password, etc.)
            if (getUser().isGuest())
            {
                AuthenticationResult result = AuthenticationManager.authenticate(request, getViewContext().getResponse(), _email.getEmailAddress(), password, form.getReturnURLHelper(), true);

                if (result.getStatus() == AuthenticationManager.AuthenticationStatus.Success)
                {
                    // Log the user into the system
                    User authenticatedUser = result.getUser();
                    SecurityManager.setAuthenticatedUser(request, authenticatedUser);
                    getViewContext().setUser(authenticatedUser);
                    _skipProfile = form.getSkipProfile();
                }
            }

            return true;
        }

        public URLHelper getSuccessURL(SetPasswordForm form)
        {
            return getAfterLoginURL(form, getUser(), _skipProfile);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        protected NamedObjectList getNonPasswordInputs(SetPasswordForm form)
        {
            return new NamedObjectList();
        }

        protected String getTitle()
        {
            return "Choose a Password";
        }

        protected String getButtonText()
        {
            return "Set Password";
        }

        protected abstract void verify(SetPasswordForm form, ValidEmail email, Errors errors);
        protected abstract String getMessage(SetPasswordForm form);
        protected abstract NamedObjectList getPasswordInputs(SetPasswordForm form);
        protected abstract void afterPasswordSet(BindException errors, User user) throws SQLException;
        protected abstract boolean isCancellable(SetPasswordForm form);
    }


    @RequiresNoPermission
    @AllowedDuringUpgrade
    @CSRF
    public class SetPasswordAction extends AbstractSetPasswordAction
    {
        @Override
        protected void verify(SetPasswordForm form, ValidEmail email, Errors errors)
        {
            try
            {
                String verification = form.getVerification();

                if (SecurityManager.verify(email, verification))
                {
                    _email = email;

                    if (UserManager.getUser(_email) == null)
                    {
                        errors.reject("setPassword", "This user doesn't exist.  Make sure you've copied the entire link into your browser's address bar.");
                        _unrecoverableError = true;
                    }
                }
                else
                {
                    if (!SecurityManager.loginExists(email))
                        errors.reject("setPassword", "This email address doesn't exist.  Make sure you've copied the entire link into your browser's address bar.");
                    else if (SecurityManager.isVerified(email))
                        errors.reject("setPassword", "This email address has already been verified.");
                    else if (null == verification || verification.length() < SecurityManager.tempPasswordLength)
                        errors.reject("setPassword", "Make sure you've copied the entire link into your browser's address bar.");
                    else
                        // Incorrect verification string
                        errors.reject("setPassword", "Verification failed.  Make sure you've copied the entire link into your browser's address bar.");

                    _unrecoverableError = true;
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected String getMessage(SetPasswordForm form)
        {
            return "Choose a password you'll use to access this server.";
        }

        @Override
        protected boolean isCancellable(SetPasswordForm form)
        {
            return false;
        }

        @Override
        protected NamedObjectList getPasswordInputs(SetPasswordForm form)
        {
            NamedObjectList list = new NamedObjectList();
            list.put(new SimpleNamedObject("Password", PASSWORD1_TEXT_FIELD_NAME));
            list.put(new SimpleNamedObject("Confirm Password", PASSWORD2_TEXT_FIELD_NAME));

            return list;
        }

        @Override
        public void afterPasswordSet(BindException errors, User user)
        {
            try
            {
                SecurityManager.setVerification(_email, null);
                UserManager.addToUserHistory(user, "Verified and chose a password.");
            }
            catch (SecurityManager.UserManagementException e)
            {
                errors.reject("password", "Resetting verification failed.  Contact the " + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName() + " team.");
            }
        }
    }


    @RequiresNoPermission
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public class InitialUserAction extends AbstractSetPasswordAction
    {
        @Override
        protected void verify(SetPasswordForm form, ValidEmail email, Errors errors)
        {
            if (!UserManager.hasNoUsers())
                throw new RedirectException(AdminController.getModuleStatusURL(null));

            _email = email;
            _unrecoverableError = false;
        }

        @Override
        protected void verifyBeforeView(SetPasswordForm form, boolean reshow, BindException errors) throws RedirectException
        {
            verify(form, null, errors);
        }

        @Override
        protected String getMessage(SetPasswordForm form)
        {
            return "Welcome! We see that this is your first time logging in. This wizard will guide you through " +
                    "creating a Site Administrator account that has full control over this server, installing the modules " +
                    "required to use LabKey Server, and setting some basic configuration.";
        }

        @Override
        protected NamedObjectList getNonPasswordInputs(SetPasswordForm form)
        {
            NamedObjectList list = new NamedObjectList();
            list.put(new SimpleNamedObject("Email Address", "email", form.getEmail()));

            return list;
        }

        @Override
        protected NamedObjectList getPasswordInputs(SetPasswordForm form)
        {
            NamedObjectList list = new NamedObjectList();
            list.put(new SimpleNamedObject("Password", PASSWORD1_TEXT_FIELD_NAME));
            list.put(new SimpleNamedObject("Retype Password", PASSWORD2_TEXT_FIELD_NAME));

            return list;
        }

        @Override
        protected void afterPasswordSet(BindException errors, User user) throws SQLException
        {
            // Put it here to get ordering right... "Added to the system" gets logged before first login
            UserManager.addToUserHistory(user, "Added to the system via the initial user page.");
        }

        @Override
        public ModelAndView getView(SetPasswordForm form, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView result = super.getView(form, reshow, errors);
            getPageConfig().setNavTrail(AdminController.getInstallUpgradeWizardSteps());
            getPageConfig().setTitle("Account Setup");
            getPageConfig().setTemplate(PageConfig.Template.Wizard);
            return result;
        }

        @Override
        public boolean handlePost(SetPasswordForm form, BindException errors) throws Exception
        {
            boolean success = false;
            DbScope scope = CoreSchema.getInstance().getSchema().getScope();

           // All initial user creation steps need to be transacted
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                ValidEmail email = new ValidEmail(form.getEmail());

                // Add the initial user
                SecurityManager.NewUserStatus newUserBean = SecurityManager.addUser(email);
                // Set the password
                success = super.handlePost(form, errors);

                // If successful, add audit event, make site admin, set some properties based on email domain, and commit
                if (success)
                {
                    SecurityManager.addMember(SecurityManager.getGroup(Group.groupAdministrators), newUserBean.getUser());

                    //set default "from" address for system emails to first registered user
                    WriteableLookAndFeelProperties laf = LookAndFeelProperties.getWriteableInstance(ContainerManager.getRoot());
                    laf.setSystemEmailAddress(newUserBean.getEmail());
                    laf.save();

                    //set default domain and default LSID authority to user email domain
                    String userEmailAddress = newUserBean.getEmail().getEmailAddress();
                    int atSign = userEmailAddress.indexOf("@");

                    //did user most likely enter a valid email address? if so, set default domain
                    if (atSign > 0 && atSign < userEmailAddress.length() - 1)
                    {
                        String defaultDomain = userEmailAddress.substring(atSign + 1, userEmailAddress.length());
                        WriteableAppProps appProps = AppProps.getWriteableInstance();
                        appProps.setDefaultDomain(defaultDomain);
                        appProps.setDefaultLsidAuthority(defaultDomain);
                        appProps.save();
                    }

                    transaction.commit();
                }
            }
            catch (SecurityManager.UserManagementException e)
            {
                errors.reject(ERROR_MSG, "Unable to create user '" + PageFlowUtil.filter(e.getEmail()) + "': " + e.getMessage());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.rejectValue("email", ERROR_MSG, "The string '" + PageFlowUtil.filter(form.getEmail()) + "' is not a valid email address.  Please enter an email address in this form: user@domain.tld");
            }

            return success;
        }

        @Override
        protected boolean isCancellable(SetPasswordForm form)
        {
            return false;
        }

        @Override
        protected String getTitle()
        {
            return "Register First User";
        }

        @Override
        protected String getButtonText()
        {
            return "Next";
        }

        @Override
        protected String getEmailForForm(SetPasswordForm form)
        {
            return null;
        }
    }


    @RequiresNoPermission
    @AllowedDuringUpgrade
    // @CSRF don't need CSRF for actions that require a password
    public class ChangePasswordAction extends AbstractSetPasswordAction
    {
        @Override
        protected void verify(SetPasswordForm form, ValidEmail email, Errors errors)
        {
            if (!SecurityManager.loginExists(email))
            {
                errors.reject("setPassword", "This email address doesn't exist.");
                _unrecoverableError = true;
            }
            else
            {
                _email = email;
            }
        }

        @Override
        protected String getMessage(SetPasswordForm form)
        {
            return null != form.getMessage() ? form.getMessage() : "Choose a new password.";
        }

        @Override
        protected boolean isCancellable(SetPasswordForm form)
        {
            // No message => user clicked "change password" button -- allow cancel in this case
            return null == form.getMessage();
        }

        @Override
        protected NamedObjectList getPasswordInputs(SetPasswordForm form)
        {
            NamedObjectList list = new NamedObjectList();
            list.put(new SimpleNamedObject("Old Password", "oldPassword"));
            list.put(new SimpleNamedObject("New Password", PASSWORD1_TEXT_FIELD_NAME));
            list.put(new SimpleNamedObject("Retype New Password", PASSWORD2_TEXT_FIELD_NAME));

            return list;
        }

        @Override
        public boolean handlePost(SetPasswordForm form, BindException errors) throws Exception
        {
            // Verify the old password on post
            HttpServletRequest request = getViewContext().getRequest();
            String oldPassword = request.getParameter("oldPassword");

            String hash = SecurityManager.getPasswordHash(new ValidEmail(form.getEmail()));

            if (!SecurityManager.matchPassword(oldPassword, hash))
            {
                errors.reject("password", "Incorrect old password.");
                return false;
            }

            return super.handlePost(form, errors);
        }

        @Override
        public void afterPasswordSet(BindException errors, User user)
        {
            UserManager.addToUserHistory(user, "Changed password.");
        }
    }


    public static class SetPasswordBean
    {
        public final String email;
        public final SetPasswordForm form;
        public final boolean unrecoverableError;
        public final String message;
        public final NamedObjectList nonPasswordInputs;
        public final NamedObjectList passwordInputs;
        public final Class action;
        public final boolean cancellable;
        public final String buttonText;

        private SetPasswordBean(SetPasswordForm form, @Nullable String emailForForm, boolean unrecoverableError, String message, NamedObjectList nonPasswordInputs, NamedObjectList passwordInputs, Class<? extends AbstractSetPasswordAction> clazz, boolean cancellable, String buttonText)
        {
            this.form = form;
            this.email = emailForForm;
            this.unrecoverableError = unrecoverableError;
            this.message = message;
            this.nonPasswordInputs = nonPasswordInputs;
            this.passwordInputs = passwordInputs;
            this.action = clazz;
            this.cancellable = cancellable;
            this.buttonText = buttonText;
        }
    }


    public static class SetPasswordForm extends AbstractLoginForm
    {
        private String _verification;
        private String _email;
        private String _message;

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEmail(String email)
        {
            _email = email;
        }

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setVerification(String verification)
        {
            _verification = verification;
        }

        public String getVerification()
        {
            return _verification;
        }

        public String getMessage()
        {
            return _message;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setMessage(String message)
        {
            _message = message;
        }
    }


    @RequiresNoPermission
    @AllowedDuringUpgrade
    @CSRF
    public class ResetPasswordAction extends FormViewAction<LoginForm>
    {
        private ValidEmail _email = null;
        private User _user = null;
        private HttpView _finishView = null;

        public void validateCommand(LoginForm form, Errors errors)
        {
            final String rawEmail = form.getEmail();

            if (null == rawEmail)
            {
                errors.reject("reset", "You must enter an email address");
                return;
            }

            try
            {
                _email = new ValidEmail(rawEmail);

                if (SecurityManager.isLdapEmail(_email))
                    // ldap authentication users must reset through their ldap administrator
                    errors.reject("reset", "Reset Password failed: " + _email + " is an LDAP email address. Please contact your LDAP administrator to reset the password for this account.");
                else if (!SecurityManager.loginExists(_email))
                    errors.reject("reset", "Reset Password failed: " + _email + " does not have a password.");
                else
                {
                    _user = UserManager.getUser(_email);

                    // We've validated that a login exists, so the user better not be null... but crashweb #8379 indicates this can happen.
                    if (null == _user)
                        errors.reject("reset", "This account does not exist.");
                    else if (!_user.isActive())
                        errors.reject("reset", "The password for this account may not be reset because this account has been deactivated. Please contact your administrator to re-activate this account.");
                }
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject("reset", "Reset Password failed: " + rawEmail + " is not a valid email address.");
            }
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setHelpTopic(new HelpTopic("passwordReset"));

            if (null != _finishView)
                return _finishView;

            JspView view = new JspView<>("/org/labkey/core/login/resetPassword.jsp", form, errors);

            if (null == form.getEmail())
            {
                form.setEmail(getEmailFromCookie(getViewContext().getRequest()));
            }

            return view;
        }

        public boolean handlePost(LoginForm form, BindException errors) throws Exception
        {
            StringBuilder sbReset = new StringBuilder();

            try
            {
                // Create a placeholder password that's impossible to guess and a separate email
                // verification key that gets emailed.
                String verification = SecurityManager.createTempPassword();

                SecurityManager.setVerification(_email, verification);

                try
                {
                    Container c = getContainer();
                    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
                    final SecurityMessage message = SecurityManager.getResetMessage(false);
                    ActionURL verificationURL = SecurityManager.createVerificationURL(c, _email, verification, null);

                    final User system = new User(laf.getSystemEmailAddress(), 0);
                    system.setFirstName(laf.getCompanyName());
                    SecurityManager.sendEmail(c, system, message, _email.getEmailAddress(), verificationURL);

                    if (!_user.getEmail().equals(_email.getEmailAddress()))
                    {
                        final SecurityMessage adminMessage = SecurityManager.getResetMessage(true);
                        message.setTo(_email.getEmailAddress());
                        SecurityManager.sendEmail(c, _user, adminMessage, _user.getEmail(), verificationURL);
                    }
                    sbReset.append("An email has been sent to you with instructions for how to reset your password. ");
                    UserManager.addToUserHistory(UserManager.getUser(_email), _email + " reset the password.");
                }
                catch (ConfigurationException e)
                {
                    sbReset.append("Failed to send password reset email at this time due to a server configuration problem. <br>");
                    sbReset.append("Please contact your administrator at <a href=mailto:\"" + LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress()
                            + "\">" + LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress() + "</a>");
                    UserManager.addToUserHistory(UserManager.getUser(_email), _email + " reset the password, but sending the email failed.");
                }
                catch (MessagingException e)
                {
                    sbReset.append("Failed to send email due to: <pre>").append(e.getMessage()).append("</pre>");
                    UserManager.addToUserHistory(UserManager.getUser(_email), _email + " reset the password, but sending the email failed.");
                }
            }
            catch (SecurityManager.UserManagementException e)
            {
                sbReset.append(": failed to reset password due to: ").append(e.getMessage());
                UserManager.addToUserHistory(UserManager.getUser(_email), _email + " attempted to reset the password, but the reset failed: " + e.getMessage());
            }

            _finishView = new JspView<>("/org/labkey/core/login/finishResetPassword.jsp", sbReset.toString());

            return false;
        }

        public ActionURL getSuccessURL(LoginForm loginForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresLogin
    public class CreateTokenAction extends SimpleViewAction<TokenAuthenticationForm>
    {
        public ModelAndView getView(TokenAuthenticationForm form, BindException errors) throws Exception
        {
            URLHelper returnUrl = form.getValidReturnUrl();

            if (null == returnUrl)
            {
                PageConfig page = getPageConfig();
                page.setTemplate(PageConfig.Template.Dialog);
                page.setTitle("Token Authentication Error");
                return new HtmlView("Error: a valid returnUrl was not specified.");
            }

            String token = TokenAuthentication.createToken(getViewContext().getRequest(), getUser());
            returnUrl.addParameter("labkeyToken", token);
            returnUrl.addParameter("labkeyEmail", getUser().getEmail());

            getViewContext().getResponse().sendRedirect(returnUrl.getURIString());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    public class VerifyTokenAction extends SimpleViewAction<TokenAuthenticationForm>
    {
        public ModelAndView getView(TokenAuthenticationForm form, BindException errors) throws Exception
        {
            String message = null;
            User user = null;

            if (null == form.getLabkeyToken())
            {
                message = "Token was not specified";
            }
            else
            {
                user = TokenAuthentication.getUserForToken(form.getLabkeyToken());

                if (null == user)
                    message = "Unknown token";
            }

            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("text/xml");
            PrintWriter out = response.getWriter();

            if (null != user)
            {
                out.print("<TokenAuthentication success=\"true\" ");
                out.print("token=\"" + form.getLabkeyToken() + "\" ");
                out.print("email=\"" + user.getEmail() + "\" ");
                out.print("permissions=\"" + getContainer().getPolicy().getPermsAsOldBitMask(user) + "\"/>");
            }
            else
            {
                out.print("<TokenAuthentication success=\"false\" ");
                out.print("message=\"" + message + "\"/>");
            }

            out.close();
            response.flushBuffer();

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresNoPermission
    public class InvalidateTokenAction extends SimpleViewAction<TokenAuthenticationForm>
    {
        public ModelAndView getView(TokenAuthenticationForm form, BindException errors) throws Exception
        {
            if (null != form.getLabkeyToken())
                TokenAuthentication.invalidateToken(form.getLabkeyToken());

            URLHelper returnUrl = form.getValidReturnUrl();

            if (null != returnUrl)
                getViewContext().getResponse().sendRedirect(returnUrl.getURIString());

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class TokenAuthenticationForm
    {
        String _labkeyToken;
        ReturnURLString _returnUrl;

        public ReturnURLString getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(ReturnURLString returnUrl)
        {
            _returnUrl = returnUrl;
        }

        public String getLabkeyToken()
        {
            return _labkeyToken;
        }

        public void setLabkeyToken(String labkeyToken)
        {
            _labkeyToken = labkeyToken;
        }

        public URLHelper getValidReturnUrl()
        {
            URLHelper returnUrl = null;

            if (null != getReturnUrl())
            {
                try
                {
                    returnUrl = new URLHelper(getReturnUrl());
                }
                catch (URISyntaxException e)
                {
                    // Bad URL case -- return null
                }
            }

            return returnUrl;
        }
    }


    private static abstract class ConfigURLFactory implements AuthenticationManager.URLFactory
    {
        public ActionURL getActionURL(AuthenticationProvider provider)
        {
            ActionURL url = new ActionURL(getActionClass(), ContainerManager.getRoot());
            url.addParameter("name", provider.getName());
            return url;
        }

        protected abstract Class<? extends Controller> getActionClass();
    }


    private static class EnableURLFactory extends ConfigURLFactory
    {
        private EnableURLFactory()
        {
            super();
        }

        protected Class<? extends Controller> getActionClass()
        {
            return EnableAction.class;
        }
    }


    private static class DisableURLFactory extends ConfigURLFactory
    {
        protected Class<? extends Controller> getActionClass()
        {
            return DisableAction.class;
        }
    }



    @AdminConsoleAction
    public class ConfigureAction extends SimpleViewAction<ReturnUrlForm>
    {
        public ModelAndView getView(ReturnUrlForm form, BindException errors) throws Exception
        {
            return AuthenticationManager.getConfigurationView(new EnableURLFactory(), new DisableURLFactory());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("authenticationModule"));
            return getUrls().appendAuthenticationNavTrail(root);
        }
    }


    @RequiresSiteAdmin
    public class EnableAction extends RedirectAction<ProviderConfigurationForm>
    {
        public ActionURL getSuccessURL(ProviderConfigurationForm form)
        {
            return getUrls().getConfigureURL();
        }

        public boolean doAction(ProviderConfigurationForm form, BindException errors) throws Exception
        {
            AuthenticationManager.enableProvider(form.getName());
            return true;
        }

        public void validateCommand(ProviderConfigurationForm target, Errors errors)
        {
        }
    }


    @RequiresSiteAdmin
    public class DisableAction extends RedirectAction<ProviderConfigurationForm>
    {
        public ActionURL getSuccessURL(ProviderConfigurationForm form)
        {
            return getUrls().getConfigureURL();
        }

        public boolean doAction(ProviderConfigurationForm form, BindException errors) throws Exception
        {
            AuthenticationManager.disableProvider(form.getName());
            return true;
        }

        public void validateCommand(ProviderConfigurationForm target, Errors errors)
        {
        }
    }


    public static class ProviderConfigurationForm
    {
        String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }


    public static ActionURL getConfigureDbLoginURL(boolean reshow)
    {
        ActionURL url = new ActionURL(ConfigureDbLoginAction.class, ContainerManager.getRoot());

        if (reshow)
            url.addParameter("reshow", "1");

        return url;
    }


    @AdminConsoleAction
    public class ConfigureDbLoginAction extends FormViewAction<Config>
    {
        public ModelAndView getView(Config form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/login/configureDbLogin.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure Database Authentication");
            setHelpTopic(new HelpTopic("configDbLogin"));
            return root;
        }

        public void validateCommand(Config form, Errors errors)
        {
        }

        public boolean handlePost(Config form, BindException errors) throws Exception
        {
            DbLoginManager.saveProperties(form);
            return true;
        }

        public ActionURL getSuccessURL(Config form)
        {
            return getConfigureDbLoginURL(true);  // Redirect to same action -- want to reload props from database
        }
    }


    @RequiresNoPermission
    public static class WhoAmIAction extends MutatingApiAction // require POST
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            User user = getUser();
            ApiSimpleResponse res = new ApiSimpleResponse();
            res.put("id", user.getUserId());
            res.put("displayName", user.getDisplayName(user));
            res.put("email", user.getEmail());
            res.put("success", true);
            return res;
        }
    }


    public static class Config extends ReturnUrlForm
    {
        public PasswordRule currentRule = DbLoginManager.getPasswordRule();
        public PasswordExpiration currentExpiration = DbLoginManager.getPasswordExpiration();
        public boolean reshow = false;

        private String strength = "Weak";
        private String expiration = "Never";

        public boolean isReshow()
        {
            return reshow;
        }

        public void setReshow(boolean reshow)
        {
            this.reshow = reshow;
        }

        public String getStrength()
        {
            return strength;
        }

        public void setStrength(String strength)
        {
            this.strength = strength;
        }

        public String getExpiration()
        {
            return expiration;
        }

        public void setExpiration(String expiration)
        {
            this.expiration = expiration;
        }
    }
}
