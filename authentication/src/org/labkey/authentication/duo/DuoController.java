package org.labkey.authentication.duo;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
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
}
