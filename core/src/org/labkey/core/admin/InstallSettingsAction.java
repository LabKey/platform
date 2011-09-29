package org.labkey.core.admin;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: jeckels
 * Date: Sep 23, 2011
 */
public class InstallSettingsAction extends FormViewAction<FileSettingsForm>
{
    @Override
    public void validateCommand(FileSettingsForm target, Errors errors)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModelAndView getView(FileSettingsForm fileSettingsForm, boolean reshow, BindException errors) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean handlePost(FileSettingsForm fileSettingsForm, BindException errors) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URLHelper getSuccessURL(FileSettingsForm fileSettingsForm)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        throw new UnsupportedOperationException();
    }
}
