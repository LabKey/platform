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

package org.labkey.core.login;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
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
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.AuthLogoType;
import org.labkey.api.security.AuthenticationManager.AuthenticationResult;
import org.labkey.api.security.AuthenticationManager.AuthenticationStatus;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.AuthenticationManager.LoginReturnProperties;
import org.labkey.api.security.AuthenticationManager.PrimaryAuthenticationResult;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.Group;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityManager.UserManagementException;
import org.labkey.api.security.SecurityMessage;
import org.labkey.api.security.TokenAuthenticationManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.WikiTermsOfUseProvider;
import org.labkey.api.security.WikiTermsOfUseProvider.TermsOfUseType;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.ApplicationAdminPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiRenderingService;
import org.labkey.core.CoreUpgradeCode;
import org.labkey.core.admin.AdminController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.labkey.api.security.AuthenticationManager.AuthenticationStatus.Success;

/**
 * User: adam
 * Date: Nov 25, 2007
 * Time: 8:22:37 PM
 */
public class LoginController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(LoginController.class);
    private static final ActionResolver _actionResolver = new DefaultActionResolver(LoginController.class);

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

    @Override
    protected void beforeAction(Controller action) throws ServletException
    {
        ActionURL url = getViewContext().getActionURL();
        if (isNotBlank(url.getParameter("password")))
            throw new BadRequestException(HttpServletResponse.SC_BAD_REQUEST, "password is not allowed on URL", null);
    }

    public static class LoginUrlsImpl implements LoginUrls
    {
        @Override
        public NavTree appendAuthenticationNavTrail(NavTree root)
        {
            root.addChild("Admin Console", AdminController.getShowAdminURL());
            root.addChild("Authentication", getConfigureURL());
            return root;
        }

        @Override
        public ActionURL getConfigureURL()
        {
            return new ActionURL(ConfigureAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getConfigureDbLoginURL()
        {
            return LoginController.getConfigureDbLoginURL(false);
        }

        @Override
        public ActionURL getEnableConfigParameterURL(String paramName)
        {
            return new ActionURL(SetAuthenticationParameterAction.class, ContainerManager.getRoot()).addParameter("parameter", paramName).addParameter("enabled", true);
        }

        @Override
        public ActionURL getDisableConfigParameterURL(String paramName)
        {
            return new ActionURL(SetAuthenticationParameterAction.class, ContainerManager.getRoot()).addParameter("parameter", paramName).addParameter("enabled", false);
        }

        @Override
        public ActionURL getInitialUserURL()
        {
            return new ActionURL(InitialUserAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getVerificationURL(Container c, ValidEmail email, String verification, @Nullable List<Pair<String, String>> extraParameters)
        {
            //FIX: 6021, use project container for this URL so it remains short but maintains the project look & feel settings 
            ActionURL url = new ActionURL(SetPasswordAction.class, LookAndFeelProperties.getSettingsContainer(c));
            url.addParameter("verification", verification);
            url.addParameter("email", email.getEmailAddress());

            if (null != extraParameters)
                url.addParameters(extraParameters);

            return url;
        }

        @Override
        public ActionURL getChangePasswordURL(Container c, User user, URLHelper returnURL, @Nullable String message)
        {
            ActionURL url = new ActionURL(ChangePasswordAction.class, LookAndFeelProperties.getSettingsContainer(c));
            url.addParameter("email", user.getEmail());     // TODO: seems peculiar... why not user id?

            if (null != message)
                url.addParameter("message", message);

            url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public ActionURL getLoginURL()
        {
            return getLoginURL(HttpView.getContextContainer(), HttpView.getContextURLHelper());
        }

        @Override
        public ActionURL getLoginURL(URLHelper returnURL)
        {
            // Use root as placeholder; extra path of returnURL determines the real login URL path
            ActionURL url = new ActionURL(LoginAction.class, ContainerManager.getRoot());

            if (null == returnURL)
                returnURL = AppProps.getInstance().getHomePageActionURL();

            if (returnURL instanceof ActionURL)
                url.setExtraPath(((ActionURL) returnURL).getExtraPath());

            url.addReturnURL(returnURL);
            return url;
        }

        @Override
        public ActionURL getLoginURL(Container c, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(LoginAction.class, c);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public ActionURL getLogoutURL(Container c)
        {
            return new ActionURL(LogoutAction.class, c);
        }

        @Override
        public ActionURL getLogoutURL(Container c, URLHelper returnURL)
        {
            ActionURL url = getLogoutURL(c);
            url.addReturnURL(returnURL);
            return url;
        }

        @Override
        public ActionURL getStopImpersonatingURL(Container c, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(StopImpersonatingAction.class, c);

            if (null != returnURL)
                url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public ActionURL getAgreeToTermsURL(Container c, URLHelper returnURL)
        {
            ActionURL url = new ActionURL(AgreeToTermsAction.class, c);
            url.addReturnURL(returnURL);
            return url;
        }

        @Override
        public ActionURL getEnableProviderURL(AuthenticationProvider provider)
        {
            return getProviderEnabledActionURL(provider, true);
        }

        @Override
        public ActionURL getDisableProviderURL(AuthenticationProvider provider)
        {
            return getProviderEnabledActionURL(provider, false);
        }

        private ActionURL getProviderEnabledActionURL(AuthenticationProvider provider, boolean enabled)
        {
            ActionURL url = new ActionURL(SetProviderEnabledAction.class, ContainerManager.getRoot());
            url.addParameter("provider", provider.getName());
            url.addParameter("enabled", enabled);
            return url;
        }

        @Override
        public ActionURL getSSORedirectURL(SSOAuthenticationConfiguration configuration, URLHelper returnURL, boolean skipProfile)
        {
            ActionURL url = new ActionURL(SsoRedirectAction.class, ContainerManager.getRoot());
            url.addParameter("configuration", configuration.getRowId());
            if (skipProfile)
            {
                url.addParameter("skipProfile", 1);
            }
            if (null != returnURL)
            {
                String fragment = returnURL.getFragment();
                if (!returnURL.isReadOnly())
                    returnURL.setFragment(null);
                url.addReturnURL(returnURL);
                if (!StringUtils.isBlank(fragment))
                    url.replaceParameter("urlhash", "#" + fragment);
            }
            return url;
        }

        @Override
        public ModelAndView getPickLogosView(@Nullable Integer rowId, boolean reshow, boolean formatInTable, BindException errors)
        {
            SSOAuthenticationConfiguration configuration = null != rowId ? AuthenticationManager.getSSOConfiguration(rowId) : null;
            return new JspView<>("/org/labkey/core/login/pickAuthLogo.jsp", new AuthLogoBean(configuration, reshow, formatInTable), errors);
        }
    }


    private static LoginUrlsImpl getUrls()
    {
        return new LoginUrlsImpl();
    }

    private static boolean authenticate(LoginForm form, BindException errors, HttpServletRequest request)
    {
        if (request != null)
        {
            // 31000: fail login actions if parameters present on URL
            for (Pair<String, String> param : PageFlowUtil.fromQueryString(request.getQueryString()))
            {
                if ("email".equalsIgnoreCase(param.getKey()) || "password".equalsIgnoreCase(param.getKey()))
                {
                    errors.reject(ERROR_MSG, "Invalid request. email and/or password are not allowed on the URL.");
                    return false;
                }
            }
        }

        if (null == form.getEmail() || null == form.getPassword())
        {
            errors.reject(ERROR_MSG, "Please sign in using your email address and password");
        }
        else
        {
            try
            {
                // Attempt authentication with all active form providers
                PrimaryAuthenticationResult result = AuthenticationManager.authenticate(request, form.getEmail(), form.getPassword(), form.getReturnURLHelper(), true);
                AuthenticationStatus status = result.getStatus();

                if (Success == status)
                {
                    AuthenticationManager.setPrimaryAuthenticationResult(request, result);
                    return true;   // Only success case... all other cases return false
                }
                else
                {
                    // Explicit test for valid email
                    new ValidEmail(form.getEmail());

                    if (status.requiresRedirect())
                    {
                        AuthenticationManager.setLoginReturnProperties(request, new LoginReturnProperties(result.getRedirectURL(), form.getUrlhash(), form.getSkipProfile()));
                    }
                    else
                    {
                        status.addUserErrorMessage(errors, result);
                    }
                }
            }
            catch (InvalidEmailException e)
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
        }

        return false;
    }


    public static boolean deauthenticate(User user, ViewContext context)
    {
        SecurityManager.logoutUser(context.getRequest(), user);

        return true;
    }

    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    public class RegisterAction extends SimpleViewAction<RegisterForm>
    {
        public ModelAndView getView(RegisterForm form, BindException errors)
        {
            if (!AuthenticationManager.isRegistrationEnabled())
                throw new NotFoundException("Registration is not enabled");
            PageConfig config = getPageConfig();
            config.setTitle("Register");
            config.setTemplate(PageConfig.Template.Dialog);
            config.setIncludeLoginLink(false);

            JspView jsp = new JspView("/org/labkey/core/login/register.jsp");

            WebPartView view = ModuleHtmlView.get(ModuleLoader.getInstance().getCoreModule(), "register");
            view.setFrame(WebPartView.FrameType.NONE);
            jsp.setView("registerView", view);
            return jsp;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class RegisterUserAction extends MutatingApiAction<RegisterForm>
    {
        @Override
        public void validateForm(RegisterForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getEmail()) || StringUtils.isEmpty(form.getEmailConfirmation()))
            {
                errors.reject(ERROR_REQUIRED, "You must verify your email address");
            }
            else
            {
                try
                {
                    ValidEmail email = new ValidEmail(form.getEmail());
                    if (!form.getEmail().equals(form.getEmailConfirmation()))
                        errors.reject(ERROR_MSG, "The email addresses you have entered do not match.  Please verify your email addresses below.");
                    else if (UserManager.userExists(email))
                    {
                        errors.reject(ERROR_MSG, "The email address you have entered is already associated with an account.  If you have forgotten your password, you can <a href=\"login-resetPassword.view?\">reset your password</a>.  Otherwise, please contact your administrator.");
                    }
                }
                catch (InvalidEmailException e)
                {
                    errors.reject(ERROR_MSG, "Your email address is not valid. Please verify your email address below.");
                }
            }

            String expectedKatpcha = (String)getViewContext().getRequest().getSession(true).getAttribute("KAPTCHA_SESSION_KEY");
            if (expectedKatpcha == null)
            {
                logger.error("Captcha not initialized for self-registration attempt");
                errors.reject(ERROR_MSG,"Captcha not initialized, please retry");
            }
            else
            {
                if (!expectedKatpcha.equalsIgnoreCase(StringUtils.trimToNull(form.getKaptchaText())))
                {
                    logger.warn("Captcha text did not match for self-registration attempt for " + form.getEmail());
                    errors.reject(ERROR_MSG,"Verification text does not match, please retry");
                }
            }
        }

        @Override
        public Object execute(RegisterForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (!AuthenticationManager.isRegistrationEnabled())
            {
                _log.warn("Attempt to register user using email " + form.getEmail() + " with registration not enabled");
                throw new NotFoundException("Registration is not enabled");
            }

            ValidEmail email = new ValidEmail(form.getEmail());

            try
            {
                List<Pair<String, String>> extraParameters = form.getExtraParametersList();
                SecurityManager.addSelfRegisteredUser(getViewContext(), email, extraParameters, form.getProvider());
            }
            catch (ConfigurationException e)
            {
                errors.reject(ERROR_MSG, "There was a problem sending the registration email.  Please contact your administrator.");
                _log.error("Error adding self registered user", e);
            }
            response.put("success", !errors.hasErrors());
            if (!errors.hasErrors())
                response.put("email", email.getEmailAddress());
            return response;
        }
    }

    public static class RegisterForm extends AbstractLoginForm
    {
        private String _email;
        private String _emailConfirmation;
        private boolean _isConfirmation;
        private String _kaptchaText;
        private String _provider = null;

        public void setEmail(String email)
        {
            _email = email;
        }

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings("unused")
        public void setEmailConfirmation(String email)
        {
            _emailConfirmation = email;
        }

        public String getEmailConfirmation()
        {
            return _emailConfirmation;
        }

        public boolean isConfirmation()
        {
            return _isConfirmation;
        }

        @SuppressWarnings("unused")
        public void setIsConfirmation(boolean isConfirmation)
        {
            _isConfirmation = isConfirmation;
        }

        public String getKaptchaText()
        {
            return _kaptchaText;
        }

        @SuppressWarnings("unused")
        public void setKaptchaText(String kaptchaText)
        {
            _kaptchaText = kaptchaText;
        }

        public String getProvider()
        {
            return _provider;
        }

        @SuppressWarnings("unused")
        public void setProvider(String provider)
        {
            _provider = provider;
        }
    }


    @RequiresNoPermission
    @ActionNames("login, showLogin")
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class LoginAction extends SimpleViewAction<LoginForm>
    {
        public ModelAndView getView(LoginForm form, BindException errors)
        {
            // If user is already logged in, then redirect immediately. This handles users clicking on stale login links
            // (e.g., multiple tab scenario) but is also necessary because of Excel's link behavior (see #9246).
            if (!getUser().isGuest())
            {
                URLHelper returnURL = form.getReturnURLHelper();

                // Create LoginReturnProperties if we have a returnURL or skipProfile param
                LoginReturnProperties properties = null != returnURL || form.getSkipProfile()
                        ? new LoginReturnProperties(returnURL, form.getUrlhash(), form.getSkipProfile()) : null;

                return HttpView.redirect(AuthenticationManager.getAfterLoginURL(getContainer(), properties, getUser()), true);
            }

            HttpServletRequest request = getViewContext().getRequest();

            // Clear authentication state. If, for example, user hits back button in the midst of two-factor auth workflow
            // then start all over with the authentication process. Preserve other session attributes (e.g., login return
            // properties, terms of use acceptance, etc.)
            AuthenticationManager.clearAuthenticationProcessAttributes(request);

            return showLogin(form, errors, request, getPageConfig());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    @CSRF(CSRF.Method.NONE) // don't need CSRF for actions that require a password
    public class LoginApiAction extends MutatingApiAction<LoginForm>
    {
        @Override
        public Object execute(LoginForm form, BindException errors)
        {
            HttpServletRequest request = getViewContext().getRequest();

            // Store passed in returnURL and skipProfile param at the start of the login so we can redirect to it after
            // any password resets, secondary logins, profile updates, etc. have finished
            URLHelper returnURL = form.getReturnURLHelper();
            if (null != returnURL || form.getSkipProfile())
            {
                LoginReturnProperties properties = new LoginReturnProperties(returnURL, form.getUrlhash(), form.getSkipProfile());
                AuthenticationManager.setLoginReturnProperties(request, properties);
            }

            // TODO: check during upgrade?
            Project termsProject = getTermsOfUseProject(form);
            boolean isGuest = getUser().isGuest();

            if (!isTermsOfUseApproved(form) && !form.isApprovedTermsOfUse())
            {
                if (null != termsProject)
                {
                    // Adjust message for guest vs. already logged in
                    errors.rejectValue("approvedTermsOfUse", ERROR_MSG, "To use the " + termsProject.getName() + " project, you must " + (isGuest ? "log in and " : "") + "approve the terms of use.");
                }
                else
                {
                    errors.rejectValue("approvedTermsOfUse", ERROR_MSG, "To use this site, you must check the box to approve the terms of use.");
                }
                return false;
            }

            ApiSimpleResponse response = null;
            boolean success = authenticate(form, errors, request);

            // TODO: Handle errors? Handle setPassword? SSO? Update profile?

            if (success)
            {
                AuthenticationResult authResult = AuthenticationManager.handleAuthentication(request, getContainer());
                // getUser will return null if authentication is incomplete as is the case when secondary authentication is required
                User user = authResult.getUser();
                URLHelper redirectUrl = authResult.getRedirectURL();
                response = new ApiSimpleResponse();
                response.put("success", true);

                if (form.isApprovedTermsOfUse())
                {
                    if (form.getTermsOfUseType() == TermsOfUseType.PROJECT_LEVEL)
                        WikiTermsOfUseProvider.setTermsOfUseApproved(getViewContext(), termsProject, true);
                    else if (form.getTermsOfUseType() == TermsOfUseType.SITE_WIDE)
                        WikiTermsOfUseProvider.setTermsOfUseApproved(getViewContext(), null, true);
                    response.put("approvedTermsOfUse", true);
                }

                // Use the full hostname in the URL if we have one, otherwise just go with a local URI
                String redirectString = redirectUrl.getHost() != null && redirectUrl.getScheme() != null ? redirectUrl.getURIString() : redirectUrl.toString();

                if (null != user)
                {
                    response.put("user", User.getUserProps(user, getContainer()));
                    if (!StringUtils.isEmpty(redirectUrl.getURIString()))
                        response.put("returnUrl", redirectString);
                    else
                        response.put("returnUrl", StringUtils.defaultIfEmpty(form.getReturnUrl(),  AppProps.getInstance().getHomePageActionURL().getPath()));
                }
                else
                {
                    // AuthenticationResult returned by AuthenticationManager.handleAuthentication indicated that a secondary authentication is needed
                    // in the ajax response inform js handler to load page from secondary authenticator url
                    response.put("returnUrl", redirectString);
                }
            }
            else if (!errors.hasErrors())
            {
                // If no errors and failure includes a redirect URL then send it to the client -- for example, password is expired or fails complexity test
                AuthenticationResult authResult = AuthenticationManager.handleAuthentication(getViewContext().getRequest(), getContainer());
                if (null != authResult.getRedirectURL() && !authResult.getRedirectURL().getPath().isEmpty())
                {
                    URLHelper redirectUrl = authResult.getRedirectURL();

                    // Use the full hostname in the URL if we have one, otherwise just go with a local URI
                    String redirectString = redirectUrl.getHost() != null && redirectUrl.getScheme() != null ? redirectUrl.getURIString() : redirectUrl.toString();

                    response = new ApiSimpleResponse();
                    response.put("success", false);
                    response.put("returnUrl", redirectString);
                    AuthenticationManager.setLoginReturnProperties(getViewContext().getRequest(), null);
                }
            }

            /* add CSRF token here, might help some callers avoid second call to whoami.api */
            if (null != response)
                response.put("CSRF", CSRFUtil.getExpectedToken(getViewContext()));
            return response;
        }
    }


    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class SetPasswordApiAction extends MutatingApiAction<SetPasswordForm>
    {
        @Override
        public Object execute(SetPasswordForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            ValidEmail email = form.getValidEmail(getViewContext(), errors);

            if (!errors.hasErrors())
            {
                if (attemptVerification(form, email, errors))
                {
                    AuthenticationResult result = attemptSetPassword(email, form.getReturnURLHelper(), "Verified and chose a password.", true, errors);
                    if (result != null)
                        response.put("returnUrl", result.getRedirectURL());
                }
            }

            response.put("success", !errors.hasErrors());
            if (errors.hasErrors())
                response.put("message", errors.getMessage());

            return response;
        }
    }

    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class ResetPasswordApiAction extends MutatingApiAction<LoginForm>
    {
        @Override
        public Object execute(LoginForm form, BindException errors)
        {
            final ApiSimpleResponse response = new ApiSimpleResponse();

            Pair<Boolean, String> pair = attemptReset(form.getEmail(), form.getProvider());
            response.put("success", pair.first);
            response.put("message", pair.second);

            return response;
        }
    }

    private Pair<Boolean, String> attemptReset(String rawEmail, String providerName)
    {
        if (null == rawEmail)
        {
            return Pair.of(false, "You must enter an email address");
        }

        ValidEmail email;

        try
        {
            email = new ValidEmail(rawEmail);
        }
        catch (InvalidEmailException e)
        {
            return Pair.of(false, "Reset Password failed: " + rawEmail + " is not a valid email address.");
        }

        if (SecurityManager.isLdapEmail(email))
        {
            // ldap authentication users must reset through their ldap administrator
            return Pair.of(false, "Reset Password failed: " + email + " is an LDAP email address. Please contact your LDAP administrator to reset the password for this account.");
        }

        // Every case below this point should result in the same, generic message being displayed to the user to avoid revealing any details about accounts, #33907

        final User user = UserManager.getUser(email);

        if (null == user)
        {
            _log.error("Password reset attempted for an email that doesn't match an existing account: " + email);
            return resetPasswordResponse(user, null, null);
        }

        if (!SecurityManager.loginExists(email))
        {
            _log.error("Password reset attempted for an account that doesn't have a password: " + email);
            return resetPasswordResponse(user, "You cannot reset the password for your account because it doesn't have a password. This usually means you log in via a single sign-on provider. Contact a server administrator if you have questions.", "Reset Password failed: " + email + " does not have a password");
        }

        if (!user.isActive())
        {
            return resetPasswordResponse(user, "You cannot reset your password because your account has been deactivated. Contact a server administrator if you have questions.", email + " attempted to reset the password for an inactive account" );
        }

        try
        {
            // Create a verification key to email the user
            String verification = SecurityManager.createTempPassword();
            SecurityManager.setVerification(email, verification);
            try
            {
                Container c = getContainer();
                LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
                final SecurityMessage message = SecurityManager.getResetMessage(false, user, providerName);
                ActionURL verificationURL = SecurityManager.createModuleVerificationURL(c, email, verification, null, providerName, false);

                final User system = new User(laf.getSystemEmailAddress(), 0);
                system.setFirstName(laf.getCompanyName());
                SecurityManager.sendEmail(c, system, message, email.getEmailAddress(), verificationURL);

                // TODO: When is this ever true? Admin reset goes through a different code path!
                if (!user.getEmail().equals(email.getEmailAddress()))
                {
                    final SecurityMessage adminMessage = SecurityManager.getResetMessage(true, user, providerName);
                    message.setTo(email.getEmailAddress());
                    SecurityManager.sendEmail(c, user, adminMessage, user.getEmail(), verificationURL);
                }
            }
            catch (ConfigurationException | MessagingException e)
            {
                _log.error("Password reset email could not be sent", e);
                return resetPasswordResponse(user, "Your reset password email could not be sent due to a server configuration problem. Contact a server administrator.", email + " reset the password, but sending the email failed");
            }
        }
        catch (UserManagementException e)
        {
            _log.error("Password reset failed", e);
            return resetPasswordResponse(user, null, email + " attempted to reset the password, but the reset failed: " + e.getMessage());
        }

        return resetPasswordResponse(user, null, null);
    }

    // Generic message that we display to users for most success and failure situations, to avoid revealing whether an account exists or not, #33907
    private static final String GENERIC_RESET_PASSWORD_MESSAGE = "Password reset was attempted. If an active account with this email address exists on the server then you will receive an email message with password reset instructions.";

    private Pair<Boolean, String> resetPasswordResponse(User user, @Nullable String failureEmailMessage, @Nullable String failureLogMessage)
    {
        if (null != failureEmailMessage)
        {
            try
            {
                Container c = getContainer();
                LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
                Message msg = MailHelper.createMessage(laf.getSystemEmailAddress(), user.getEmail());
                msg.setSubject(laf.getShortName() + " Password Reset Attempt Failed");
                msg.setText(failureEmailMessage + "\n\nThe " + laf.getCompanyName() + " " + laf.getShortName() + " home page is " + ActionURL.getBaseServerURL() + ".");
                MailHelper.send(msg, user, c);
            }
            catch (MessagingException e)
            {
                _log.error("Error sending failure message email to password reset user", e);
            }
        }

        if (null != failureLogMessage)
        {
            UserManager.addToUserHistory(user, failureLogMessage);
        }

        return Pair.of(true, GENERIC_RESET_PASSWORD_MESSAGE);
    }

    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class AcceptTermsOfUseApiAction extends MutatingApiAction<AgreeToTermsForm>
    {
        @Override
        public Object execute(AgreeToTermsForm form, BindException errors)
        {
            Project project = getTermsOfUseProject(form);
            if (!form.isApprovedTermsOfUse())
            {
                if (null != project)
                {
                    errors.reject(ERROR_MSG, "To use the " + project.getName() + " project, you must check the box to approve the terms of use.");
                }
                else
                {
                    errors.reject(ERROR_MSG, "To use this site, you must check the box to approve the terms of use.");
                }
                return false;
            }
            if (form.getTermsOfUseType() == TermsOfUseType.PROJECT_LEVEL)
                WikiTermsOfUseProvider.setTermsOfUseApproved(getViewContext(), project, true);
            else if (form.getTermsOfUseType() == TermsOfUseType.SITE_WIDE)
                WikiTermsOfUseProvider.setTermsOfUseApproved(getViewContext(), null, true);
            else
            {
                errors.reject(ERROR_MSG, "Unable to determine the terms of use type from the information submitted on the form.");
            }
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("success", true);
            return response;
        }
    }

    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class GetTermsOfUseApiAction extends MutatingApiAction<AgreeToTermsForm>
    {
        @Override
        public Object execute(AgreeToTermsForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            WikiTermsOfUseProvider.TermsOfUse tou = WikiTermsOfUseProvider.getTermsOfUse(getTermsOfUseProject(form));
            response.put("termsOfUseContent", tou.getHtml());
            response.put("termsOfUseType", tou.getType());
            return response;
        }
    }

    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class GetLoginMechanismsApiAction extends MutatingApiAction<LoginForm>
    {
        @Override
        public Object execute(LoginForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            URLHelper returnURL = form.getReturnURLHelper();
            if (null != returnURL && null != form.getUrlhash())
            {
                returnURL.setFragment(form.getUrlhash().replace("#", ""));
            }
            HtmlString otherLoginMechanisms = AuthenticationManager.getLoginPageLogoHtml(returnURL);
            response.put("otherLoginMechanismsContent", null != otherLoginMechanisms ? otherLoginMechanisms.toString() : null);
            return response;
        }
    }

    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class GetRegistrationConfigApiAction extends ReadOnlyApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("enabled", AuthenticationManager.isRegistrationEnabled());
            return response;
        }
    }


    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    // @AllowedDuringUpgrade
    public class IsAgreeOnlyApiAction extends MutatingApiAction<AgreeToTermsForm>
    {
        @Override
        public Object execute(AgreeToTermsForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            boolean isGuest = getUser().isGuest();
            if (!isGuest) {
                response.put("isAgreeOnly", true);
            }
            return response;
        }
    }

    private HttpView showLogin(LoginForm form, BindException errors, HttpServletRequest request, PageConfig page)
    {
        String email = form.getEmail();

        // If email is null, check to see if email is saved in cookie
        // If email is already filled in (e.g., from setPassword), use it instead of the cookie

        if (null == email)
        {
            email = getEmailFromCookie(request);
            form.setEmail(email);
        }

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
            WikiRenderingService wikiService = WikiRenderingService.get();
            content = wikiService.getFormattedHtml(WikiRendererType.RADEOX, ModuleLoader.getInstance().getAdminOnlyMessage());
            HtmlView adminMessageView = new HtmlView("The site is currently undergoing maintenance", content);
            vBox.addView(adminMessageView);
        }
        else if (request.getParameter("_skipAutoRedirect") == null)
        {
            // see if any of the SSO auth providers are set to autoRedirect from the login action
            SSOAuthenticationConfiguration ssoAuthenticationConfiguration = AuthenticationManager.getAutoRedirectSSOAuthConfiguration();
            if (ssoAuthenticationConfiguration != null)
                return HttpView.redirect(ssoAuthenticationConfiguration.getLinkFactory().getURL(form.getReturnURLHelper(), form.getSkipProfile()));
        }

        page.setTemplate(PageConfig.Template.Dialog);
        page.setIncludeLoginLink(false);
        page.setTitle("Sign In");

        WebPartView view = getLoginView(errors);

        vBox.addView(view);

        return vBox;
    }

    private WebPartView getLoginView(BindException errors)
    {
        // Get the login page specified by controller-action in the Look and Feel Settings
        // This is placed in showLogin() instead of the getLoginURL() to ensure that the logic above
        // regarding 'server upgrade' and 'server startup' is executed regardless of the custom login action the user specified.
        String loginController = "login";
        String loginAction = "login";
        String customLogin = StringUtils.trimToNull(LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getCustomLogin());
        WebPartView view = null;
        if (null != customLogin)
        {
            ActionURL url = new ActionURL(customLogin);
            String customLoginAction = url.getAction();
            String customLoginController =  url.getController();
            if (null != customLoginController && !customLoginController.equals("") && null != customLoginAction && !customLoginAction.equals(""))
            {
                loginController = customLoginController;
                loginAction = customLoginAction;
            }
        }
        if (!loginController.equals("login") || !loginAction.equals("login"))
        {
            Module loginModule = ModuleLoader.getInstance().getModule(loginController);
            // custom login
            if (null != loginModule)
                view = ModuleHtmlView.get(loginModule, loginAction);
            if (null == view )
            {
                // custom failed so default to login-login html with error message
                errors.reject(ERROR_MSG, "Custom login page specified via Look and Feel Settings as: '" + customLogin + "' was not found. Default login page being used instead.");
                view = ModuleHtmlView.get(ModuleLoader.getInstance().getCoreModule(), "login");
            }
        }
        else
        {
            // the login.html is in the core/resources/views
            view = ModuleHtmlView.get(ModuleLoader.getInstance().getCoreModule(), loginAction);
        }
        if (null != view)
        {
            view.setFrame(WebPartView.FrameType.NONE);
        }
        else
        {
            // Neither the default login page at core/resources/views/login.html or the custom login described in admin console were found
            throw new NotFoundException("Neither the custom login page specified via Look and Feel Settings as: " + customLogin + " or the default login page were found.");
        }
        return view;
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
    public class AgreeToTermsAction extends FormViewAction<AgreeToTermsForm>
    {
        @Override
        public void validateCommand(AgreeToTermsForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(AgreeToTermsForm form, boolean reshow, BindException errors)
        {
            // Consider: replace with a getCustomAgreeToTermsView() that reads a custom agreeToTerms.html file
            AgreeToTermsView view = new AgreeToTermsView(form, errors);

            PageConfig page = getPageConfig();

            page.setTemplate(PageConfig.Template.Dialog);
            page.setTitle("Terms Of Use");
            page.setIncludeLoginLink(false);

            return view;
        }

        @Override
        public boolean handlePost(AgreeToTermsForm form, BindException errors)
        {
            Project project = getTermsOfUseProject(form);

            if (!form.isApprovedTermsOfUse())
            {
                if (null != project)
                {
                    errors.reject(ERROR_MSG, "To use the " + project.getName() + " project, you must check the box to approve the terms of use.");
                }
                else
                {
                    errors.reject(ERROR_MSG, "To use this site, you must check the box to approve the terms of use.");
                }
                return false;
            }

            if (form.getTermsOfUseType() == TermsOfUseType.PROJECT_LEVEL)
                WikiTermsOfUseProvider.setTermsOfUseApproved(getViewContext(), project, true);
            else if (form.getTermsOfUseType() == TermsOfUseType.SITE_WIDE)
                WikiTermsOfUseProvider.setTermsOfUseApproved(getViewContext(), null, true);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(AgreeToTermsForm form)
        {
            return form.getReturnURLHelper();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private class AgreeToTermsView extends JspView<AgreeToTermsBean>
    {
        private AgreeToTermsView(AgreeToTermsForm form, BindException errors)
        {
            super("/org/labkey/core/login/termsOfUse.jsp", new AgreeToTermsBean(form), errors);
            setFrame(FrameType.NONE);
        }
    }


    public class AgreeToTermsBean
    {
        public final AgreeToTermsForm form;
        public String termsOfUseHTML = null;

        private AgreeToTermsBean(AgreeToTermsForm form)
        {
            this.form = form;

            try
            {
                Project project = getTermsOfUseProject(form);

                // Display the terms of use if this is the terms-of-use page or user hasn't already approved them. #4684
                WikiTermsOfUseProvider.TermsOfUse terms = WikiTermsOfUseProvider.getTermsOfUse(project);
                if (terms.getType() != TermsOfUseType.NONE)
                {
                    this.form.setTermsOfUseType(terms.getType());
                    termsOfUseHTML = terms.getHtml();
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }


    @Nullable
    private Project getTermsOfUseProject(AgreeToTermsForm form)
    {
        if (null != form.getTermsOfUseType() && (form.getTermsOfUseType() == TermsOfUseType.SITE_WIDE))
            return null;
        else
            return PageFlowUtil.getTermsOfUseProject(getContainer(), form.getReturnUrl());
    }


    private boolean isTermsOfUseApproved(AgreeToTermsForm form)
    {
        Project termsProject = getTermsOfUseProject(form);
        return form.isApprovedTermsOfUse() || !WikiTermsOfUseProvider.isTermsOfUseRequired(termsProject) || WikiTermsOfUseProvider.isTermsOfUseApproved(getViewContext(), termsProject);
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

        public List<Pair<String, String>> getExtraParametersList()
        {
            List<Pair<String, String>> extraParams = new ArrayList<>();
            if (getSkipProfile())
                extraParams.add(new Pair<>("skipProfile", "1"));

            if (null != getReturnUrl())
                extraParams.add(new Pair<>(ActionURL.Param.returnUrl.name(), getReturnUrl()));

            return extraParams;
        }
    }


    public static class AgreeToTermsForm extends AbstractLoginForm
    {
        private boolean approvedTermsOfUse;
        private TermsOfUseType termsOfUseType;

        public void setTermsOfUseType(TermsOfUseType type) { this.termsOfUseType = type; }

        public TermsOfUseType getTermsOfUseType() { return this.termsOfUseType; }

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


    public static class LoginForm extends AgreeToTermsForm
    {
        private boolean remember;
        private String email;
        private String password;
        private String provider;

        public void setProvider(String provider)
        {
            this.provider = provider;
        }
        public void setEmail(String email)
        {
            this.email = email;
        }

        public String getProvider()
        {
            return this.provider;
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
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class LogoutAction extends FormHandlerAction<ReturnUrlForm>
    {
        @Override
        public void validateCommand(ReturnUrlForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ReturnUrlForm returnUrlForm, BindException errors) throws Exception
        {
            return deauthenticate(getUser(), getViewContext());
        }

        @Override
        public URLHelper getSuccessURL(ReturnUrlForm form)
        {
            return form.getReturnURLHelper(AuthenticationManager.getWelcomeURL());
        }
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class StopImpersonatingAction extends FormHandlerAction<ReturnUrlForm>
    {
        @Override
        public void validateCommand(ReturnUrlForm form, Errors errors)
        {
            if (!getUser().isImpersonated())
                errors.reject(ERROR_MSG, "Error: You are not impersonating!");
        }

        @Override
        public boolean handlePost(ReturnUrlForm form, BindException errors) throws Exception
        {
            SecurityManager.stopImpersonating(getViewContext().getRequest(), getUser().getImpersonationContext().getFactory());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ReturnUrlForm form)
        {
            return form.getReturnURLHelper(AuthenticationManager.getWelcomeURL());
        }
    }


    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class LogoutApiAction extends MutatingApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            return new ApiSimpleResponse("success", deauthenticate(getUser(), getViewContext()));
        }
    }


    public static class SsoRedirectForm extends AbstractLoginForm
    {
        private String _provider;
        private int _configuration;

        public String getProvider()
        {
            return _provider;
        }

        @SuppressWarnings("unused")
        public void setProvider(String provider)
        {
            _provider = provider;
        }

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


    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class SsoRedirectAction extends SimpleViewAction<SsoRedirectForm>
    {
        @Override
        public ModelAndView getView(SsoRedirectForm form, BindException errors)
        {
            // If logged in then redirect immediately
            if (!getUser().isGuest())
                return HttpView.redirect(form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL()));

            // If we have a returnURL or skipProfile param then create and stash LoginReturnProperties
            URLHelper returnURL = form.getReturnURLHelper();
            if (null != returnURL || form.getSkipProfile())
            {
                LoginReturnProperties properties = new LoginReturnProperties(returnURL, form.getUrlhash(), form.getSkipProfile());
                AuthenticationManager.setLoginReturnProperties(getViewContext().getRequest(), properties);
            }

            String csrf = CSRFUtil.getExpectedToken(getViewContext());

            final URLHelper url;
            int rowId = form.getConfiguration();

            SSOAuthenticationConfiguration configuration = AuthenticationManager.getActiveSSOConfiguration(rowId);

            if (null == configuration)
                throw new NotFoundException("Authentication configuration is not valid");

            url = configuration.getUrl(csrf, getViewContext());

            return HttpView.redirect(url.getURIString());
        }

        @Override
        public final NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static final String PASSWORD1_TEXT_FIELD_NAME = "password";
    public static final String PASSWORD2_TEXT_FIELD_NAME = "password2";

    private abstract class AbstractSetPasswordAction extends FormViewAction<SetPasswordForm>
    {
        protected ValidEmail _email = null;
        protected boolean _unrecoverableError = false;
        protected URLHelper _successUrl = null;

        @Override
        public void validateCommand(SetPasswordForm form, Errors errors)
        {
            ValidEmail email = form.getValidEmail(getViewContext(), errors);

            if (errors.hasErrors())
                _unrecoverableError = true;
            else
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

        @Override
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

            // If we have a returnURL or skipProfile param then create and stash LoginReturnProperties
            URLHelper returnURL = form.getReturnURLHelper();
            if (null != returnURL || form.getSkipProfile())
            {
                LoginReturnProperties properties = new LoginReturnProperties(returnURL, form.getUrlhash(), form.getSkipProfile());
                AuthenticationManager.setLoginReturnProperties(getViewContext().getRequest(), properties);
            }

            // If we're going to display the form, then set focus on the first input.
            if (!_unrecoverableError)
            {
                String firstInput = (String)(nonPasswordInputs.isEmpty() ? passwordInputs.get(0) : nonPasswordInputs.get(0));
                page.setFocusId(firstInput);
            }

            return view;
        }

        @Override
        public boolean handlePost(SetPasswordForm form, BindException errors) throws Exception
        {
            AuthenticationResult result = attemptSetPassword(_email, form.getReturnURLHelper(), getAuditMessage(), clearVerification(), errors);

            if (errors.hasErrors())
                return false;
            if (result != null)
                _successUrl = result.getRedirectURL();
            return true;
        }

        @Override
        public URLHelper getSuccessURL(SetPasswordForm form)
        {
            return _successUrl;
        }

        @Override
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
        protected boolean clearVerification()
        {
            return false;
        }
        protected abstract String getAuditMessage();
        protected abstract boolean isCancellable(SetPasswordForm form);
    }

    public AuthenticationResult attemptSetPassword(ValidEmail email, URLHelper returnUrlHelper, String auditMessage, boolean clearVerification, BindException errors) throws InvalidEmailException
    {
        HttpServletRequest request = getViewContext().getRequest();
        String password = request.getParameter("password");
        String password2 = request.getParameter("password2");

        Collection<String> messages = new LinkedList<>();
        User user = UserManager.getUser(email);

        if (!DbLoginManager.getPasswordRule().isValidToStore(password, password2, user, messages))
        {
            for (String message : messages)
                errors.reject("setPassword", message);
            return null;
        }

        try
        {
            SecurityManager.setPassword(email, password);
        }
        catch (UserManagementException e)
        {
            errors.reject("setPassword", "Setting password failed: " + e.getMessage() + ".  Contact the " + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName() + " team.");
            return null;
        }

        try
        {
            if (clearVerification)
                SecurityManager.setVerification(email, null);
            UserManager.addToUserHistory(user, auditMessage);
        }
        catch (UserManagementException e)
        {
            errors.reject("setPassword", "Resetting verification failed.  Contact the " + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getShortName() + " team.");
            return null;
        }

        // Should log user in only for initial user, choose password, and forced change password scenarios, but not for scenarios
        // where a user is already logged in (normal change password, admins initializing another user's password, etc.)
        if (getUser().isGuest())
        {
            PrimaryAuthenticationResult result = AuthenticationManager.authenticate(request, email.getEmailAddress(), password, returnUrlHelper, true);

            if (result.getStatus() == Success)
            {
                // This user has passed primary authentication
                AuthenticationManager.setPrimaryAuthenticationResult(request, result);
            }
        }

        return AuthenticationManager.handleAuthentication(getViewContext().getRequest(), getContainer());
    }

    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class SetPasswordAction extends AbstractSetPasswordAction
    {
        @Override
        protected void verify(SetPasswordForm form, ValidEmail email, Errors errors)
        {
            if (!attemptVerification(form, email, errors))
            {
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
            return "Your email address has been verified! Create an account password below.";
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
        protected boolean clearVerification()
        {
            return true;
        }

        @Override
        protected String getAuditMessage()
        {
            return "Verified and chose a password.";
        }

        @Override
        public URLHelper getSuccessURL(SetPasswordForm form)
        {
            return null;
        }

        @Override
        public ModelAndView getSuccessView(SetPasswordForm form)
        {
            // Issue 33599: allow the returnUrl for this action to redirect to an absolute URL (ex. labkey.org back to accounts.trial.labkey.host)
            return HttpView.redirect(_successUrl, true);
        }
    }


    private static boolean attemptVerification(SetPasswordForm form, ValidEmail email, Errors errors)
    {
        String verification = form.getVerification();
        boolean isVerified = SecurityManager.verify(email, verification);

        User user = UserManager.getUser(email);
        LoginController.checkVerificationErrors(isVerified, user, email, verification, errors);

        return isVerified && !errors.hasErrors();
    }


    public static void checkVerificationErrors(boolean isVerified, User user, ValidEmail email, String verification, Errors errors)
    {
        if(isVerified)
        {
            if (user == null)
            {
                errors.reject("setPassword", "This user doesn't exist.  Make sure you've copied the entire link into your browser's address bar.");
            }
            else if (!user.isActive())
            {
                errors.reject("setPassword", "This user account has been deactivated. Please contact a system "
                        + "administrator if you need to reactivate this account.");
            }
        }
        else
        {
            if (!SecurityManager.loginExists(email))
            {
                if (SecurityManager.isLdapEmail(email))
                    errors.reject("setPassword", "Your account will use your institution's LDAP authentication server and you do not need to set a separate password.");
                else
                    errors.reject("setPassword", "This email address is not associated with an account.  Make sure you've copied the entire link into your browser's address bar.");
            }
            else if (SecurityManager.isVerified(email))
                errors.reject("setPassword", "This email address has already been verified.");
            else if (null == verification || verification.length() < SecurityManager.tempPasswordLength)
                errors.reject("setPassword", "Make sure you've copied the entire link into your browser's address bar.");
            else
                // Incorrect verification string
                errors.reject("setPassword", "Verification failed.  Make sure you've copied the entire link into your browser's address bar.");
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
            if (!UserManager.hasNoRealUsers())
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
        protected String getAuditMessage()
        {
            // Put it here to get ordering right... "Added to the system" gets logged before first login
            return "Added to the system via the initial user page.";
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

                synchronized (ModuleLoader.SCRIPT_RUNNING_LOCK)
                {
                    // Add the initial user
                    SecurityManager.NewUserStatus newUserBean = SecurityManager.addUser(email, null);
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

                        //set default domain to user email domain
                        String userEmailAddress = newUserBean.getEmail().getEmailAddress();
                        int atSign = userEmailAddress.indexOf("@");

                        //did user most likely enter a valid email address? if so, set default domain
                        if (atSign > 0 && atSign < userEmailAddress.length() - 1)
                        {
                            String defaultDomain = userEmailAddress.substring(atSign + 1);
                            WriteableAppProps appProps = AppProps.getWriteableInstance();
                            appProps.setDefaultDomain(defaultDomain);
                            appProps.save(null);
                        }

                        transaction.commit();
                    }
                }
            }
            catch (UserManagementException e)
            {
                errors.reject(ERROR_MSG, "Unable to create user '" + PageFlowUtil.filter(e.getEmail()) + "': " + e.getMessage());
            }
            catch (InvalidEmailException e)
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

        @Override
        public URLHelper getSuccessURL(SetPasswordForm form)
        {
            // Call super to login the admin user, but ignore the usual password reset redirect
            super.getSuccessURL(form);

            // Always go to module status to continue the wizard
            return AdminController.getModuleStatusURL(null);
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
                errors.reject("setPassword", "This email address is not associated with an account.");
                _unrecoverableError = true;
            }
            else
            {
                _email = email;
            }

            // Issue 33321: this action does make sense if the server is set to auto redirect from the login page
            if (AuthenticationManager.getAutoRedirectSSOAuthConfiguration() != null)
            {
                errors.reject("setPassword", "This action is invalid for a server set to use SSO auto redirect.");
                _unrecoverableError = true;
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
        protected String getAuditMessage()
        {
            return "Changed password.";
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

        // Actions should use this method for consistency
        public ValidEmail getValidEmail(ViewContext context, Errors errors)
        {
            String rawEmail = getEmail();

            // Some plain text email clients get confused by the encoding... explicitly look for encoded name
            if (null == rawEmail)
                rawEmail = context.getActionURL().getParameter("amp;email");

            ValidEmail email = null;

            try
            {
                email = new ValidEmail(rawEmail);
            }
            catch (InvalidEmailException e)
            {
                errors.reject("setPassword", "Invalid email address" + (null == rawEmail ? "" : ": " + rawEmail));
            }

            return email;
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
    public class ResetPasswordAction extends FormViewAction<LoginForm>
    {
        private HttpView _finishView = null;

        public void validateCommand(LoginForm form, Errors errors)
        {
            // All validation is handled in attemptReset()
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors)
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setHelpTopic(new HelpTopic("passwordReset"));
            getPageConfig().setNoIndex();

            if (null != _finishView)
                return _finishView;

            JspView view = new JspView<>("/org/labkey/core/login/resetPassword.jsp", form, errors);

            if (null == form.getEmail())
            {
                form.setEmail(getEmailFromCookie(getViewContext().getRequest()));
            }

            return view;
        }

        public boolean handlePost(LoginForm form, BindException errors)
        {
            Pair<Boolean, String> pair = attemptReset(form.getEmail(), form.getProvider());

            if (pair.first)
                _finishView = new JspView<>("/org/labkey/core/login/finishResetPassword.jsp", pair.second);
            else
                errors.reject("reset", pair.second);

            return false;
        }

        public ActionURL getSuccessURL(LoginForm loginForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Reset Password");
        }
    }


    @SuppressWarnings("unused")
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

            String token = TokenAuthenticationManager.get().createKey(getViewContext().getRequest(), getUser());
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


    @SuppressWarnings("unused")
    @RequiresNoPermission
    @IgnoresTermsOfUse
    @CSRF(CSRF.Method.NONE)
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
                user = TokenAuthenticationManager.get().getContext(form.getLabkeyToken());

                if (null == user)
                    message = "Unknown token";
            }

            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("text/xml");

            try (PrintWriter out = response.getWriter())
            {
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
            }

            response.flushBuffer();

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @SuppressWarnings("unused")
    @RequiresNoPermission
    // This action has historically accepted GET. Technically, it is a mutating operation, but only in the case
    // where the caller has a secret (the authentication token).
    public class InvalidateTokenAction extends SimpleRedirectAction<TokenAuthenticationForm>
    {
        public @Nullable URLHelper getRedirectURL(TokenAuthenticationForm form)
        {
            if (null != form.getLabkeyToken())
                TokenAuthenticationManager.get().invalidateKey(form.getLabkeyToken());
            URLHelper returnUrl = form.getValidReturnUrl();
            if (null != returnUrl)
                return returnUrl;
            return AppProps.getInstance().getHomePageActionURL();
        }
    }


    public static class TokenAuthenticationForm extends ReturnUrlForm
    {
        private String _labkeyToken;

        public String getLabkeyToken()
        {
            return _labkeyToken;
        }

        @SuppressWarnings("unused")
        public void setLabkeyToken(String labkeyToken)
        {
            _labkeyToken = labkeyToken;
        }

        public URLHelper getValidReturnUrl()
        {
            return getReturnURLHelper();
        }
    }


    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ConfigureAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm form, BindException errors)
        {
            return new JspView<>("/org/labkey/core/login/configuration.jsp", form);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("authenticationModule"));
            return getUrls().appendAuthenticationNavTrail(root);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class SetAuthenticationParameterAction extends FormHandlerAction<AuthParameterForm>
    {
        @Override
        public void validateCommand(AuthParameterForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(AuthParameterForm form, BindException errors) throws Exception
        {
            AuthenticationManager.setAuthConfigProperty(getUser(), form.getParameter(), form.isEnabled());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(AuthParameterForm form)
        {
            return getUrls().getConfigureURL();
        }
    }

    public static class AuthParameterForm
    {
        private String _parameter;
        private boolean _enabled;

        public boolean isEnabled()
        {
            return _enabled;
        }

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }

        public String getParameter()
        {
            return _parameter;
        }

        public void setParameter(String parameter)
        {
            _parameter = parameter;
        }
    }

    @Deprecated
    @RequiresPermission(AdminOperationsPermission.class)
    public class SetProviderEnabledAction extends FormHandlerAction<ProviderForm>
    {
        @Override
        public void validateCommand(ProviderForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ProviderForm form, BindException errors) throws Exception
        {
            if (form.isEnabled())
                AuthenticationManager.enableProvider(form.getProvider(), getUser());
            else
                AuthenticationManager.disableProvider(form.getProvider(), getUser());
            return true;
        }

        @Override
        public ActionURL getSuccessURL(ProviderForm form)
        {
            return getUrls().getConfigureURL();
        }
    }

    @Deprecated
    public static class ProviderForm
    {
        private String _provider;
        private boolean _enabled;

        public String getProvider()
        {
            return _provider;
        }

        @SuppressWarnings("unused")
        public void setProvider(String provider)
        {
            _provider = provider;
        }

        public boolean isEnabled()
        {
            return _enabled;
        }

        @SuppressWarnings("unused")
        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }
    }

    public static class DeleteConfigurationForm
    {
        private int _configuration;

        public int getConfiguration()
        {
            return _configuration;
        }

        public void setConfiguration(int configuration)
        {
            _configuration = configuration;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class DeleteConfigurationAction extends FormHandlerAction<DeleteConfigurationForm>
    {
        @Override
        public void validateCommand(DeleteConfigurationForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(DeleteConfigurationForm form, BindException errors) throws Exception
        {
            AuthenticationManager.deleteConfiguration(form.getConfiguration());
            return true;
        }

        @Override
        public ActionURL getSuccessURL(DeleteConfigurationForm form)
        {
            return getUrls().getConfigureURL();
        }
    }

    public static ActionURL getConfigureDbLoginURL(boolean reshow)
    {
        ActionURL url = new ActionURL(ConfigureDbLoginAction.class, ContainerManager.getRoot());

        if (reshow)
            url.addParameter("reshow", "1");

        return url;
    }


    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ConfigureDbLoginAction extends FormViewAction<Config>
    {
        public ModelAndView getView(Config form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/core/login/configureDbLogin.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getUrls().appendAuthenticationNavTrail(root).addChild("Configure Database Authentication");
            setHelpTopic(new HelpTopic("configDbLogin"));
            return root;
        }

        public void validateCommand(Config form, Errors errors)
        {
        }

        public boolean handlePost(Config form, BindException errors)
        {
            DbLoginManager.saveProperties(form);
            return true;
        }

        public ActionURL getSuccessURL(Config form)
        {
            return getConfigureDbLoginURL(true);  // Redirect to same action -- want to reload props from database
        }
    }


    @SuppressWarnings("unused")
    @RequiresNoPermission
    public static class WhoAmIAction extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            User user = getUser();
            ApiSimpleResponse res = new ApiSimpleResponse();
            res.put("id", user.getUserId());
            res.put("displayName", user.getDisplayName(user));
            res.put("email", user.getEmail());
            res.put("CSRF", CSRFUtil.getExpectedToken(getViewContext()));
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

        @SuppressWarnings("unused")
        public void setStrength(String strength)
        {
            this.strength = strength;
        }

        public String getExpiration()
        {
            return expiration;
        }

        @SuppressWarnings("unused")
        public void setExpiration(String expiration)
        {
            this.expiration = expiration;
        }
    }


    public static class AuthLogoBean
    {
        public final @Nullable SSOAuthenticationConfiguration configuration;
        public final String headerLogo;
        public final String loginPageLogo;
        public final boolean reshow;
        public final boolean formatInTable;

        private AuthLogoBean(@Nullable SSOAuthenticationConfiguration configuration, boolean reshow, boolean formatInTable)
        {
            this.configuration = configuration;
            this.reshow = reshow;
            this.formatInTable = formatInTable;
            headerLogo = getAuthLogoHtml(AuthLogoType.HEADER);
            loginPageLogo = getAuthLogoHtml(AuthLogoType.LOGIN_PAGE);
        }

        public String getAuthLogoHtml(AuthLogoType logoType)
        {
            if (null != configuration)
            {
                LinkFactory factory = configuration.getLinkFactory();
                String logo = factory.getImg(logoType);

                if (null != logo)
                {
                    StringBuilder html = new StringBuilder();

                    String id1 = logoType.getFileName() + "d1";
                    String id2 = logoType.getFileName() + "d2";

                    html.append("<div id=\"").append(id1).append("\">");
                    html.append(logo);
                    html.append("</div>\n<div id=\"").append(id2).append("\">");
                    html.append(PageFlowUtil.link("delete").onClick("deleteLogo('" + logoType.getFileName() + "');").toString()); // RE_CHECK
                    html.append("</div>\n");

                    return html.toString();
                }
            }

            return "<input name=\"" + logoType.getFileName() + "\" type=\"file\" size=\"60\">";
        }
    }

    @RequiresPermission(ApplicationAdminPermission.class)
    public class MigrateAuthenticationConfigurations extends ConfirmAction
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors)
        {
            return new HtmlView(HtmlString.of("Are you sure you want to re-run the authentication configuration migration? This may cause duplicate configurations (which can be deleted)."));
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            new CoreUpgradeCode().migrateAuthenticationConfigurations(getUser());

            return true;
        }

        @Override
        public @NotNull URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(LoginUrls.class).getConfigureURL();
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            LoginController controller = new LoginController();

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                controller.new SetAuthenticationParameterAction(),
                controller.new SetProviderEnabledAction(),
                controller.new MigrateAuthenticationConfigurations()
            );

            // @AdminConsoleAction
            assertForAdminPermission(ContainerManager.getRoot(), user,
                controller.new ConfigureAction(),
                controller.new ConfigureDbLoginAction()
            );
        }
    }
}
