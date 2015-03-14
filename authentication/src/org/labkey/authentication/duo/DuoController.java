package org.labkey.authentication.duo;

import com.duosecurity.duoweb.DuoWeb;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HttpUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

/**
 * User: tgaluhn
 * Date: 3/6/2015
 */
public class DuoController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(DuoController.class);

    public DuoController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    public static ActionURL getConfigureURL(boolean reshow)
    {
        ActionURL url = new ActionURL(ConfigureAction.class, ContainerManager.getRoot());

        if (reshow)
            url.addParameter("reshow", "1");

        return url;
    }

    @AdminConsoleAction
    @CSRF
    public class ConfigureAction extends FormViewAction<Config>
    {
        public ModelAndView getView(Config form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/authentication/duo/configure.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("configDuo");
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure Duo 2 Factor Authentication");
            return root;
        }

        public void validateCommand(Config target, Errors errors)
        {

        }

        public boolean handlePost(Config config, BindException errors) throws Exception
        {
            DuoManager.saveProperties(config);
            return true;
        }

        public ActionURL getSuccessURL(Config config)
        {
            return getConfigureURL(true);  // Redirect to same action -- reload props from database
        }
    }

    public static class Config extends ReturnUrlForm
    {
        public boolean reshow = false;

        private String integrationKey = DuoManager.getIntegrationKey();
        private String secretKey = DuoManager.getSecretKey();
        private String applicationKey = DuoManager.getApplicationKey();//Application key and Application Secret key (as sometimes used in Duo docs) are synonymous.
        private String apiHostname = DuoManager.getAPIHostname();

        public String getSecretKey()
        {
            return secretKey;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setSecretKey(String secretKey)
        {
            this.secretKey = secretKey;
        }

        public String getIntegrationKey()
        {
            return integrationKey;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setIntegrationKey(String integrationKey)
        {
            this.integrationKey = integrationKey;
        }

        public String getApiHostname()
        {
            return apiHostname;
        }

        public String getApplicationKey()
        {
            return applicationKey;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setApplicationKey(String applicationKey)
        {
            this.applicationKey = applicationKey;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setApiHostname(String apiHostname)
        {
            this.apiHostname = apiHostname;
        }
        @SuppressWarnings("UnusedDeclaration")
        public boolean isReshow()
        {
            return reshow;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setReshow(boolean reshow) {
            this.reshow = reshow;
        }

    }


    public static class DuoForm extends ReturnUrlForm
    {
        private boolean _valid = false;
        private String _message;

        public boolean isValid()
        {
            return _valid;
        }

        public void setValid(boolean valid)
        {
            _valid = valid;
        }

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }
    }

    public static ActionURL getValidateURL(Container c, URLHelper returnURL)
    {
        ActionURL url = new ActionURL(ValidateAction.class, c);
        url.addReturnURL(returnURL);

        return url;
    }

    @RequiresNoPermission
    public class ValidateAction extends FormViewAction<DuoForm>
    {
        @Override
        public void validateCommand(DuoForm form, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(DuoForm form, boolean reshow, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            form.setMessage("I'm " + this.getClass().getSimpleName());
            return new JspView<>("/org/labkey/authentication/duo/duoEntry.jsp", form, errors);
        }

        // TODO: Delete once we eliminate TestValidateAction
        protected Class<? extends SecondaryAuthenticationProvider> getProviderClass()
        {
            return DuoProvider.class;
        }

        @Override
        public boolean handlePost(DuoForm form, BindException errors) throws Exception
        {
            // TODO: Obviously, we need real Duo validation here
            boolean success = form.isValid();

            if (success)
            {
                AuthenticationManager.setSecondaryAuthenticationSuccess(getViewContext().getRequest(), getProviderClass());

                // This will throw redirect if there are more secondary providers. Perhaps setSecondaryAuthenticationSuccess() should throw instead.
                AuthenticationManager.handleSecondaryAuthentication(getUser(), getContainer(), getViewContext().getRequest(), form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL()));
            }

            return success;
        }

        @Override
        public URLHelper getSuccessURL(DuoForm form)
        {
            return form.getReturnActionURL(AppProps.getInstance().getHomePageActionURL());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static ActionURL getTestValidateURL(Container c, URLHelper returnURL)
    {
        ActionURL url = new ActionURL(TestValidateAction.class, c);
        url.addReturnURL(returnURL);

        return url;
    }

    @RequiresNoPermission
    public class TestValidateAction extends ValidateAction
    {
        @Override
        protected Class<? extends SecondaryAuthenticationProvider> getProviderClass()
        {
            return TestDuoProvider.class;
        }
    }

    @RequiresSiteAdmin
    public class TestDuoAction extends FormViewAction<TestDuoForm>
    {
        public void validateCommand(TestDuoForm target, Errors errors)
        {
        }

        public ModelAndView getView(TestDuoForm form, boolean reshow, BindException errors) throws Exception
        {
            HttpView view = new JspView<>("/org/labkey/authentication/duo/testDuo.jsp", form, errors);
            return view;
        }

        public boolean handlePost(TestDuoForm form, BindException errors) throws Exception
        {
            return false;
        }

        public ActionURL getSuccessURL(TestDuoForm testDuoAction)
        {
            return null;   // Always reshow form
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresNoPermission
    public class TestDuoResultAction extends FormViewAction<TestDuoForm>
    {
        public void validateCommand(TestDuoForm target, Errors errors)
        {
        }

        public ModelAndView getView(TestDuoForm form, boolean reshow, BindException errors) throws Exception
        {
            String sig_response = form.getSig_response();
            String authenticatedUserName = DuoWeb.verifyResponse(DuoManager.getIntegrationKey(), DuoManager.getSecretKey(), DuoManager.getApplicationKey() , sig_response);
            form.setUserName(authenticatedUserName);
            HttpView view = new JspView<>("/org/labkey/authentication/duo/testResultDuo.jsp", form, errors);
            return view;
        }

        public boolean handlePost(TestDuoForm form, BindException errors) throws Exception
        {
            return false;
        }

        public ActionURL getSuccessURL(TestDuoForm testDuoAction)
        {
            return null;   // Always reshow form
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class TestDuoForm extends ReturnUrlForm implements HasViewContext
    {

        String sig_response; // Response from Duo
        String userName;

        public String getUserName()
        {
            return userName;
        }

        public void setUserName(String userName)
        {
            this.userName = userName;
        }


        public String getSig_response()
        {
            return sig_response;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setSig_response(String sig_response)
        {
            this.sig_response = sig_response;
        }

        @Override
        public void setViewContext(ViewContext context)
        {

        }

        @Override
        public ViewContext getViewContext()
        {
            return null;
        }

    }
}

