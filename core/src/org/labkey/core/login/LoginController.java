/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Project;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.WriteableAppProps;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.core.admin.AdminController;
import org.labkey.core.user.UserController;
import org.mule.MuleManager;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Nov 25, 2007
 * Time: 8:22:37 PM
 */
public class LoginController extends SpringActionController
{
    private static Logger _log = Logger.getLogger(LoginController.class);
    private static ActionResolver _actionResolver = new DefaultActionResolver(LoginController.class);
    private static final int secondsPerYear = 60 * 60 * 24 * 365;

    public LoginController()
    {
        super();
        setActionResolver(_actionResolver);
    }


    public static class LoginURLFactoryImpl implements AuthenticationManager.LoginURLFactory
    {
        public ActionURL getURL(ActionURL returnURL)
        {
            return addReturnURL(getLoginURL(), returnURL);
        }

        public ActionURL getURL(String returnURL)
        {
            ActionURL loginURL = getLoginURL();
            loginURL.addParameter("URI", returnURL);
            return loginURL;
        }
    }


    private static ActionURL addReturnURL(ActionURL loginURL, ActionURL returnURL)
    {
        if (null != returnURL)
            loginURL.addParameter("URI", returnURL.getLocalURIString());

        return loginURL;
    }


    private static ActionURL getLoginURL(ActionURL returnURL, String email)
    {
        return addReturnURL(getLoginURL(), returnURL).addParameter("email", email);
    }


    public static ActionURL getLoginURL()
    {
        return new ActionURL(LoginAction.class, ContainerManager.getRoot());
    }

    @RequiresPermission(ACL.PERM_NONE) @ActionNames("login, showLogin")
    public class LoginAction extends FormViewAction<LoginForm>
    {
        private User _user = null;

        public void validateCommand(LoginForm target, Errors errors)
        {
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            HttpServletResponse response = getViewContext().getResponse();

            // Check to see if any authentication credentials already exist (passed as URL param, in a cookie, etc.)
            if (!reshow && authenticate(form, request, response))
                return HttpView.redirect(getSuccessURLAsString(form));
            else
                return showLogin(form, request, getPageConfig(), false);
        }

        public boolean handlePost(LoginForm form, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            HttpServletResponse response = getViewContext().getResponse();

            if (!isTermsOfUseApproved(form))
            {
                form.errorHtml = "To use the " + getTermsOfUseProject(form).getName() +  " project, you must log in and approve the terms of use.";
                return false;
            }

            boolean success = authenticate(form, request, response);

            if (success)
            {
                // Terms of use are approved only if we've posted from the login page.  In SSO case, we will attempt
                // to access the page and will get a TermsOfUseException if terms of use approval is required.
                SecurityManager.setTermsOfUseApproved(getViewContext(), getTermsOfUseProject(form), true);

                if (form.isRemember())
                {
                    // Write cookies to save email
                    Cookie emailCookie = new Cookie("email", form.getEmail());
                    // EmailCookie.setSecure(true);
                    emailCookie.setMaxAge(secondsPerYear);
                    response.addCookie(emailCookie);

                    // TODO: If SSL is required and admin allows, we could save password as well (uncomment lines below)
                    // Cookie PasswordCookie = new Cookie("password", password);
                    // PasswordCookie.setSecure(true);
                    // response.addCookie(PasswordCookie);
                }
                else
                {
                    // Clear the cookie
                    Cookie emailCookie = new Cookie("email", "");
                    // emailCookie.setSecurity(true);
                    emailCookie.setMaxAge(0);
                    response.addCookie(emailCookie);
                }
            }
            else
            {
                return false;
            }

            try
            {
                HttpView.throwRedirect(getSuccessURLAsString(form));
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            // should never get here, as the success options throw RedirectExceptions
            throw new IllegalStateException("Should have redirected");
        }

        private boolean authenticate(LoginForm form, HttpServletRequest request, HttpServletResponse response)
        {
            try
            {
                // Attempt authentication with all registered providers
                _user = AuthenticationManager.authenticate(request, response, form.getEmail(), form.getPassword());

                if (null != _user)
                {
                    SecurityManager.setAuthenticatedUser(request, _user);

                    return true;
                }
                else if (null != form.getEmail() && null != form.getPassword())
                {
                    // Email & password were specified, but authentication failed... display either invalid email address error or generic "couldn't authenticate" message
                    new ValidEmail(form.getEmail());
                    form.errorHtml = "The e-mail address and password you entered did not match any accounts on file.<br><br>\n" +
                            "Note: Passwords are case sensitive; make sure your Caps Lock is off.";
                }
            }
            catch(ValidEmail.InvalidEmailException e)
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
                form.errorHtml = sb.toString();
            }

            return false;
        }

        public ActionURL getSuccessURL(LoginForm form)
        {
            // No longer possible, as the handlePost() should redirect.
            throw new UnsupportedOperationException("This should never occur. Success should redirect.");
        }

        private String getSuccessURLAsString(LoginForm form) throws SQLException, ServletException
        {
            // If this is user's first log in or some required field isn't filled in then go to update page
            if (_user.isFirstLogin() || UserController.requiresUpdate(_user))
            {
                return UserController.getUpdateURL(form.getReturnActionURL()).getLocalURIString();  // TODO: Add the redirect param so user eventually gets back to requested page
            }
            else
            {
                 return form.getReturnUrl();
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    protected HttpView showLogin(LoginForm form, HttpServletRequest request, PageConfig page, boolean omitAdminOnlyMessage) throws Exception
    {
        String email = form.getEmail();
        boolean remember = false;

        // If email is null, check to see if email is saved in cookie
        // If email is already filled in (e.g., from setPassword), use it instead of the cookie

        if (null == email)
        {
            Cookie[] cookies = request.getCookies();

            if (null != cookies)
            {
                email = PageFlowUtil.getCookieValue(cookies, "email", null);
                if (null != email)
                    form.setEmail(email);
            }
        }

        if (null != email)
            remember = true;

        LoginView view = new LoginView(form, remember, form.isApprovedTermsOfUse());
        VBox vBox = new VBox();
        vBox.addView(view);

        if (!omitAdminOnlyMessage && ModuleLoader.getInstance().isAdminOnlyMode())
        {
            WikiRenderer formatter = WikiService.get().getRenderer(WikiRendererType.RADEOX);
            String content = formatter.format(ModuleLoader.getInstance().getAdminOnlyMessage()).getHtml();
            HtmlView adminMessageView = new HtmlView("The site is currently undergoing maintenance", content);
            vBox.addView(adminMessageView);
        }

        page.setTemplate(PageConfig.Template.Dialog);
        page.setIncludeLoginLink(false);
        page.setFocusId(null != form.getEmail() ? "password" : "email");
        page.setTitle("Sign In");

        return view;
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class AgreeToTermsAction extends FormViewAction<LoginForm>
    {
        public void validateCommand(LoginForm target, Errors errors)
        {
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors) throws Exception
        {
            AgreeToTermsView view = new AgreeToTermsView(form);

            PageConfig page = getPageConfig();

            page.setTemplate(PageConfig.Template.Dialog);
            page.setTitle("Terms Of Use");
            page.setIncludeLoginLink(false);

            return view;
        }

        public boolean handlePost(LoginForm form, BindException errors) throws Exception
        {
            if (!form.isApprovedTermsOfUse())
            {
                form.errorHtml = "To use the " + getTermsOfUseProject(form).getName() +  " project, you must check the box to approve the terms of use.";
                return false;
            }

            SecurityManager.setTermsOfUseApproved(getViewContext(), getTermsOfUseProject(form), true);

            return true;
        }

        public ActionURL getSuccessURL(LoginForm form)
        {
            return form.getReturnActionURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private abstract class BaseLoginView extends JspView<LoginBean>
    {
        private BaseLoginView(LoginForm form, boolean agreeOnly, boolean remember, boolean termsOfUseChecked)
        {
            super("/org/labkey/core/login/login.jsp", new LoginBean(form, agreeOnly, remember, termsOfUseChecked));
            setFrame(FrameType.NONE);
        }
    }


    public class LoginBean
    {
        public LoginForm form;
        public boolean agreeOnly;
        public boolean remember;
        public String termsOfUseHtml = null;
        public boolean termsOfUseChecked;

        private LoginBean(LoginForm form, boolean agreeOnly, boolean remember, boolean termsOfUseChecked)
        {
            this.form = form;
            this.agreeOnly = agreeOnly;
            this.remember = remember;
            this.termsOfUseChecked = termsOfUseChecked;

            try
            {
                termsOfUseHtml = SecurityManager.getTermsOfUseHtml(getTermsOfUseProject(form));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }


    private class LoginView extends BaseLoginView
    {
        private LoginView(LoginForm form, boolean remember, boolean termsOfUseChecked)
        {
            super(form, false, remember, termsOfUseChecked);
        }
    }


    private class AgreeToTermsView extends BaseLoginView
    {
        private AgreeToTermsView(LoginForm form)
        {
            super(form, true, false, false);
        }
    }


    private Project getTermsOfUseProject(LoginForm form) throws ServletException
    {
        Container termsContainer = null;

        String redir = form.getURI();
        if (null != redir)
        {
            try
            {
                URI uri = new URI(redir);
            }
            catch (URISyntaxException e)
            {
                redir = null;
            }
        }
        if (null != redir)
        {
            try
            {
                Container redirContainer = ContainerManager.getForPath(new ActionURL(redir).getExtraPath());
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
        return termsProject == null || !SecurityManager.isTermsOfUseRequired(termsProject) || form.isApprovedTermsOfUse();
    }


    public static class LoginForm extends ReturnUrlForm
    {
        private String errorHtml = "";
        private boolean remember;
        private String email;
        private String password;
        private String URI;       // TODO: Remove this once we convert URI -> returnURL
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

        public void setPassword(String password)
        {
            this.password = password;
        }

        public void setRemember(boolean remember)
        {
            this.remember = remember;
        }

        public boolean isRemember()
        {
            return this.remember;
        }

        public void setErrorHtml(String errorHtml)
        {
            this.errorHtml = errorHtml;
        }

        public String getErrorHtml()
        {
            return this.errorHtml;
        }

        public String getURI()
        {
            return URI;
        }

        public void setURI(String URI)
        {
            this.URI = URI;
            setReturnUrl(URI);
        }

        public ActionURL getReturnActionURL()
        {
            if (null == getReturnUrl())
                return AppProps.getInstance().getHomePageActionURL();
            else
                return super.getReturnActionURL();
        }

        public boolean isApprovedTermsOfUse()
        {
            return approvedTermsOfUse;
        }

        public void setApprovedTermsOfUse(boolean approvedTermsOfUse)
        {
            this.approvedTermsOfUse = approvedTermsOfUse;
        }
    }


    public static ActionURL getLogoutURL()
    {
        return new ActionURL(LogoutAction.class, ContainerManager.getRoot());
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class LogoutAction extends RedirectAction
    {
        public ActionURL getSuccessURL(Object o)
        {
            return AppProps.getInstance().getHomePageActionURL();
        }

        public boolean doAction(Object o, BindException errors) throws Exception
        {
            SecurityManager.logoutUser(getViewContext().getRequest());
            return true;
        }

        public void validateCommand(Object target, Errors errors)
        {
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class SetPasswordAction extends FormViewAction<VerifyForm>
    {
        String _error = null;
        ValidEmail _email = null;

        public void validateCommand(VerifyForm form, Errors errors)
        {
            ActionURL currentUrl = getViewContext().getActionURL();
            String verification = form.getVerification();
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
                errors.reject("verify", "Invalid email address");
                return;
            }

            try
            {
                if (SecurityManager.verify(email, verification))
                {
                    // logout any current user
                    if (getUser() != null && !getUser().isGuest())
                    {
                        SecurityManager.logoutUser(getViewContext().getRequest());
                        HttpView.redirect(currentUrl);
                    }

                    // Success
                    _email = email;
                    return;
                }

                if (!SecurityManager.loginExists(email))
                    errors.reject("verify", "This email address doesn't exist.  Make sure you've copied the entire link into your browser's address bar.");
                else if (SecurityManager.isVerified(email))
                    errors.reject("verify", "This email address has already been verified.");
                else if (verification.length() < SecurityManager.tempPasswordLength)
                    errors.reject("verify", "Make sure you've copied the entire link into your browser's address bar.");
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        public ModelAndView getView(VerifyForm form, boolean reshow, BindException errors) throws Exception
        {
            validateCommand(form, errors);

            if (errors.hasErrors())
            {
                _log.debug("Verification failed: " + form.getEmail());
            }
            else
            {
                _log.debug("Verified: " + _email);
            }

            HttpView view = new JspView<VerifyBean>("/org/labkey/core/login/setPassword.jsp", new VerifyBean(_email, form), errors);

            PageConfig page = getPageConfig();
            page.setTemplate(PageConfig.Template.Dialog);
            page.setTitle("Choose a Password");
            page.setIncludeLoginLink(false);
            page.setFocusId("password");

            return view;
        }

        public boolean handlePost(VerifyForm form, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();

            String password = request.getParameter("password");
            String password2 = request.getParameter("password2");

            if (null == password || null == password2)
                errors.reject("password", "You must enter two passwords");
            else if (!SecurityManager.isValidPassword(password, _email))
                errors.reject("password", "Enter a valid password.  " + SecurityManager.passwordRule);
            else if (!password.equals(password2))
                errors.reject("password", "Your password entries didn't match.");

            if (errors.hasErrors())
                return false;

            try
            {
                SecurityManager.setPassword(_email, password);
            }
            catch (SecurityManager.UserManagementException e)
            {
                errors.reject("password", "Resetting password failed.  Contact the " + AppProps.getInstance().getSystemShortName() + " team.");
                return false;
            }

            try
            {
                SecurityManager.setVerification(_email, null);
                UserManager.addToUserHistory(UserManager.getUser(_email), "Verified and chose a password.");
            }
            catch (SecurityManager.UserManagementException e)
            {
                errors.reject("password", "Resetting verification failed.  Contact the " + AppProps.getInstance().getSystemShortName() + " team.");
                return false;
            }

            return true;
        }

        public ActionURL getSuccessURL(VerifyForm form)
        {
            return getLoginURL(null, _email.getEmailAddress());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class VerifyBean
    {
        public String email;
        public String verification;

        private VerifyBean(ValidEmail email, VerifyForm form)
        {
            this.email = (null != email ? email.getEmailAddress() : form.getEmail());
            verification = form.verification;
        }
    }


    public static class VerifyForm
    {
        private String verification;
        private String email;

        public void setEmail(String email)
        {
            this.email = email;
        }

        public String getEmail()
        {
            return email;
        }

        public void setVerification(String verification)
        {
            this.verification = verification;
        }

        public String getVerification()
        {
            return verification;
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class ResetPasswordAction extends FormViewAction<LoginForm>
    {
        private ValidEmail _email = null;
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

                if (SecurityManager.isLdapEmail(_email))  // TODO: Address SSO
                    // ldap authentication users must reset through their ldap administrator
                    errors.reject("reset", "Reset Password failed: " + _email + " is an LDAP email. Please contact your administrator to reset the password for this account.");
                else if (!SecurityManager.loginExists(_email))
                    errors.reject("reset", "Reset Password failed: " + _email + " is not a registered user.");
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject("reset", "Reset Password failed: " + rawEmail + " is not a valid email address.");
            }
        }

        public ModelAndView getView(LoginForm form, boolean reshow, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);

            if (null != _finishView)
                return _finishView;

            JspView view = new JspView<LoginForm>("/org/labkey/core/login/resetPassword.jsp", form, errors);

            if (form.getEmail() == null)
            {
                Cookie[] cookies = getViewContext().getRequest().getCookies();

                if (null != cookies)
                    form.setEmail(PageFlowUtil.getCookieValue(cookies, "email", ""));
            }

            return view;
        }

        public boolean handlePost(LoginForm form, BindException errors) throws Exception
        {
            // Create a placeholder password that's impossible to guess and a separate email
            // verification key that gets emailed.
            String verification = SecurityManager.createTempPassword();

            StringBuilder sbReset = new StringBuilder();
            sbReset.append("<p>").append(form.getEmail());

            final User user = UserManager.getUser(_email);

            try
            {
                SecurityManager.setVerification(_email, verification);
                sbReset.append(": request password reset.</p>");

                try
                {
                    final SecurityMessage message = SecurityManager.getResetMessage(false);
                    message.setHideContact(true);
                    String verificationUrl = SecurityManager.createVerificationUrl(getContainer(), _email.getEmailAddress(),
                            verification).getURIString();

                    final User system = new User(AppProps.getInstance().getSystemEmailAddress(), 0);
                    system.setFirstName(AppProps.getInstance().getCompanyName());
                    SecurityManager.sendEmail(system, message, _email.getEmailAddress(), verificationUrl);

                    if (!user.getEmail().equals(_email.getEmailAddress()))
                    {
                        final SecurityMessage adminMessage = SecurityManager.getResetMessage(true);
                        message.setHideContact(true);
                        message.setTo(_email.getEmailAddress());
                        SecurityManager.sendEmail(user, adminMessage, user.getEmail(), verificationUrl);
                    }
                    sbReset.append("An email has been sent to you with instructions on how to reset your password. ");
                    UserManager.addToUserHistory(UserManager.getUser(_email), _email + " reset the password.");
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

            _finishView = new JspView<String>("/org/labkey/core/login/finishResetPassword.jsp", sbReset.toString());

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


    public static ActionURL getInitialUserURL()
    {
        return new ActionURL(InitialUserAction.class, ContainerManager.getRoot());
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class InitialUserAction extends FormViewAction<InitialUserForm>
    {
        private ActionURL _verificationUrl = null;

        public void validateCommand(InitialUserForm target, Errors errors)
        {
            if (!UserManager.hasNoUsers())
                errors.reject(ERROR_MSG, "Initial user has already been created.");
        }

        public ModelAndView getView(InitialUserForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow)
                validateCommand(form, errors);

            HttpView view = new JspView<String>("/org/labkey/core/login/initialUser.jsp", form.getEmail(), errors);

            if (MuleManager.isInstanciated())
            {
                JspView view_attrib = new JspView("/org/labkey/core/login/attribution.jsp");
                view = new VBox(view, view_attrib);
            }

            PageConfig page = getPageConfig();
            page.setTemplate(PageConfig.Template.Dialog);
            page.setTitle("Register First User");
            page.setIncludeLoginLink(false);
            page.setFocusId("email");

            return view;
        }

        public boolean handlePost(InitialUserForm form, BindException errors) throws Exception
        {
            try
            {
                ValidEmail email = new ValidEmail(form.getEmail());

                SecurityManager.NewUserBean newUserBean = SecurityManager.addUser(email);
                UserManager.addToUserHistory(newUserBean.getUser(), "Added to the system via the initial user page.");

                SecurityManager.addMember(Group.groupAdministrators, newUserBean.getUser());

                //set default "from" address for system emails to first registered user
                String userEmail = newUserBean.getEmail();
                WriteableAppProps appProps = AppProps.getWriteableInstance();
                appProps.setSystemEmailAddresses(userEmail);

                //set default domain and default LSID authority to user email domain
                int atSign = userEmail.indexOf("@");
                //did user most likely enter a valid email address? if so, set default domain
                if (atSign > 0 && atSign < userEmail.length() - 1)
                {
                    String defaultDomain = userEmail.substring(atSign + 1, userEmail.length());
                    appProps.setDefaultDomain(defaultDomain);
                    appProps.setDefaultLsidAuthority(defaultDomain);
                }
                appProps.save();

                _verificationUrl = SecurityManager.createVerificationUrl(getContainer(), newUserBean.getEmail(), newUserBean.getVerification());

                return true;
            }
            catch (SecurityManager.UserManagementException e)
            {
                errors.reject(ERROR_MSG, "Unable to create user '" + PageFlowUtil.filter(e.getEmail()) + "': " + e.getMessage());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.rejectValue("email", ERROR_MSG, "The string '" + PageFlowUtil.filter(form.getEmail()) + "' is not a valid email address.  Please enter an email address in this form: user@domain.tld");
            }

            return false;
        }

        public ActionURL getSuccessURL(InitialUserForm registerForm)
        {
            return _verificationUrl;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class InitialUserForm
    {
        private String email;

        public void setEmail(String email)
        {
            this.email = email;
        }

        public String getEmail()
        {
            return email;
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


    @RequiresPermission(ACL.PERM_NONE)
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
                out.print("permissions=\"" + getContainer().getAcl().getPermissions(user) + "\"/>");
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


    @RequiresPermission(ACL.PERM_NONE)
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
        String _returnUrl;

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(String returnUrl)
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
    }


    @RequiresSiteAdmin
    public class ConfigureAction extends SimpleViewAction<ReturnUrlForm>
    {
        public ModelAndView getView(ReturnUrlForm form, BindException errors) throws Exception
        {
            return AuthenticationManager.getConfigurationView(new EnableURLFactory(), new DisableURLFactory());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return getUrls().appendAuthenticationNavTrail(root);
        }
    }


    private static LoginUrls getUrls()
    {
        return PageFlowUtil.urlProvider(LoginUrls.class);
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
}
