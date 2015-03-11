package org.labkey.authentication.duo;

import org.labkey.api.action.FormViewAction;
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

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
            return true;
        }

        public ActionURL getSuccessURL(Config config)
        {
            return getConfigureURL(true);  // Redirect to same action -- reload props from database
        }
    }

    public static class Config extends ReturnUrlForm
    {

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
}
