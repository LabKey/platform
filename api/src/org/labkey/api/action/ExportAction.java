package org.labkey.api.action;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.labkey.api.view.NavTree;

import javax.servlet.http.HttpServletResponse;

/**
* User: adam
* Date: Jan 10, 2008
* Time: 1:32:35 PM
*/
public abstract class ExportAction<FORM> extends SimpleViewAction<FORM>
{
    protected String getCommandClassMethodName()
    {
        return "export";
    }

    public final ModelAndView getView(FORM form, BindException errors) throws Exception
    {
        export(form, getViewContext().getResponse());
        return null;
    }

    public final NavTree appendNavTrail(NavTree root)
    {
        return null;
    }

    public abstract void export(FORM form, HttpServletResponse response) throws Exception;
}
