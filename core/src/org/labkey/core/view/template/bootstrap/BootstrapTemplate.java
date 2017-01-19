package org.labkey.core.view.template.bootstrap;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import java.util.Set;

public class BootstrapTemplate extends HomeTemplate
{
    public BootstrapTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/BootstrapTemplate.jsp", context, context.getContainer(), body, page);
    }


    public ActionURL getPermaLink()
    {
        ActionURL url = getViewContext().cloneActionURL();
        return url.setExtraPath("__r" + Integer.toString(getViewContext().getContainer().getRowId()));
    }


    public boolean includeGWT()
    {
        Set<String> modules = GWTView.getModulesForRootContext();
        return null != modules && modules.size() > 0;
    }
}
