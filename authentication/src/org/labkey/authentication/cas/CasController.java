package org.labkey.authentication.cas;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.AuthenticationResult;
import org.labkey.api.security.AuthenticationManager.PrimaryAuthenticationResult;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by adam on 3/29/2015.
 */
public class CasController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(CasController.class);

    public CasController()
    {
        setActionResolver(_actionResolver);
    }

    public static ActionURL getValidateURL()
    {
        return new ActionURL(ValidateAction.class, ContainerManager.getRoot());
    }

    private static class CasForm
    {
        private String _ticket;

        public String getTicket()
        {
            return _ticket;
        }

        @SuppressWarnings("unused")
        public void setTicket(String ticket)
        {
            _ticket = ticket;
        }
    }

    // TODO: CAS server might POST to this action to invalidate sessions on logout; check with USF.

    @RequiresNoPermission
    public class ValidateAction extends SimpleViewAction<CasForm>
    {
        @Override
        public ModelAndView getView(CasForm form, BindException errors) throws Exception
        {
            // TODO: Check if provider is enabled
            HttpServletRequest request = getViewContext().getRequest();
            String ticket = form.getTicket();
            ValidEmail email = CasManager.getInstance().validate(ticket);

            if (null != email)
            {
                PrimaryAuthenticationResult result = AuthenticationManager.finalizePrimaryAuthentication(request, CasAuthenticationProvider.getInstance(), email);

                if (null != result.getUser())
                    AuthenticationManager.setPrimaryAuthenticationUser(request, result.getUser());
            }

            AuthenticationResult result = AuthenticationManager.handleAuthentication(request, getContainer());

            return HttpView.redirect(result.getRedirectURL());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static ActionURL getConfigureURL()
    {
        return new ActionURL(ConfigureAction.class, ContainerManager.getRoot());
    }


    @AdminConsoleAction
    @CSRF
    public class ConfigureAction extends FormViewAction<CasConfigureForm>
    {
        @Override
        public ModelAndView getView(CasConfigureForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/authentication/cas/configure.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("configureCas");
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure " + CasAuthenticationProvider.NAME + " Authentication");
            return root;
        }

        @Override
        public void validateCommand(CasConfigureForm form, Errors errors)
        {
            String url = form.getServerUrl();

            if (!StringUtils.isBlank(url))
            {
                try
                {
                    new URI(url);

                    if (URLHelper.isHttpURL(url))
                    {
                        if (!StringUtils.endsWithIgnoreCase(url, "/cas"))
                            errors.reject(ERROR_MSG, "Server URL must end with \"/cas\"");

                        return;
                    }
                }
                catch (URISyntaxException ignored)
                {
                }
            }
            else
            {
                // Reset the server URL if the entry was blank
                form.setServerUrl(CasManager.getInstance().getServerUrlProperty());
            }

            // One of the checks failed, so display a general error message
            errors.reject(ERROR_MSG, "Enter a valid HTTP URL to your Apereo CAS server (e.g., http://test.org/cas)");
        }

        @Override
        public boolean handlePost(CasConfigureForm form, BindException errors) throws Exception
        {
            if (!form.getServerUrl().equalsIgnoreCase(CasManager.getInstance().getServerUrlProperty()))
                CasManager.getInstance().saveServerUrlProperty(form.getServerUrl(), getUser());

            return true;
        }

        @Override
        public ActionURL getSuccessURL(CasConfigureForm form)
        {
            return getConfigureURL();  // Redirect to same action -- reload props from database
        }
    }

    public static class CasConfigureForm extends ReturnUrlForm
    {
        private String _serverUrl = CasManager.getInstance().getServerUrlProperty();

        public String getServerUrl()
        {
            return _serverUrl;
        }

        public void setServerUrl(String serverUrl)
        {
            _serverUrl = serverUrl;
        }
    }
}
