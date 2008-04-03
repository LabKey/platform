package org.labkey.api.action;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.AjaxResponse;

/**
 * User: kevink
 * Date: Jan 4, 2008 12:18:02 PM
 */
public abstract class AjaxAction<FORM> extends SimpleViewAction<FORM>
{
    public AjaxAction()
    {
        super();
    }

    public AjaxAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    public ModelAndView getView(FORM form, BindException errors) throws Exception
    {
        getPageConfig().setTemplate(PageConfig.Template.None);
        return getResponse(form, errors);
    }

    public abstract AjaxResponse getResponse(FORM form, BindException errors) throws Exception;

    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }
}
