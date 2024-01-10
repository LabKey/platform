package org.labkey.core.security;

import org.labkey.api.external.tools.ExternalToolsViewProvider;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.view.JspView;
import org.labkey.api.view.PopupUserView;
import org.labkey.api.view.WebPartView;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedList;
import java.util.List;

public class ApiKeyViewProvider implements ExternalToolsViewProvider
{
    @Override
    public List<ModelAndView> getViews(User user)
    {
        List<ModelAndView> views = new LinkedList<>();
        if (PopupUserView.allowApiKeyPage(user))
        {
            views.add(ModuleHtmlView.get(ModuleLoader.getInstance().getModule("core"), ModuleHtmlView.getGeneratedViewPath("apiKeys")));
        }

        return views;
    }
}
