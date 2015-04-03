package org.labkey.authentication.test;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: adam
 * Date: 3/27/2015
 * Time: 5:40 PM
 */
public class TestSecondaryController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TestSecondaryController.class);

    public TestSecondaryController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    public static ActionURL getTestSecondaryURL(Container c)
    {
        return new ActionURL(TestSecondaryAction.class, c);
    }

    public static class TestSecondaryForm extends ReturnUrlForm
    {
        private boolean _valid;

        public boolean isValid()
        {
            return _valid;
        }

        @SuppressWarnings("unused")
        public void setValid(boolean valid)
        {
            _valid = valid;
        }
    }

    @RequiresNoPermission
    public class TestSecondaryAction extends FormViewAction<TestSecondaryForm>
    {
        @Override
        public void validateCommand(TestSecondaryForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(TestSecondaryForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!getUser().isGuest())
                HttpView.redirect(AuthenticationManager.getAfterLoginURL(getContainer(), null, getUser()));

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setIncludeLoginLink(false);
            return new JspView<>("/org/labkey/authentication/test/testSecondary.jsp", null, errors);
        }

        @Override
        public boolean handlePost(TestSecondaryForm form, BindException errors) throws Exception
        {
            if (form.isValid())
            {
                User user = AuthenticationManager.getPrimaryAuthenticationUser(getViewContext().getSession());

                if (null != user)
                    AuthenticationManager.setSecondaryAuthenticationUser(getViewContext().getSession(), TestSecondaryProvider.class, user);

                return true;
            }

            return false;
        }

        @Override
        public URLHelper getSuccessURL(TestSecondaryForm form)
        {
            return AuthenticationManager.handleAuthentication(getViewContext().getRequest(), getContainer()).getRedirectURL();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }
}
