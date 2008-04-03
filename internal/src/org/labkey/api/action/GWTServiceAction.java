package org.labkey.api.action;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.labkey.api.view.NavTree;
import org.labkey.api.gwt.server.BaseRemoteService;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public abstract class GWTServiceAction extends SimpleViewAction
{
    public ModelAndView getView(Object o, BindException errors) throws Exception
    {
        BaseRemoteService service = createService();
        service.doPost(getViewContext().getRequest(), getViewContext().getResponse());
        return null;
    }

    protected abstract BaseRemoteService createService();

    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }

}
