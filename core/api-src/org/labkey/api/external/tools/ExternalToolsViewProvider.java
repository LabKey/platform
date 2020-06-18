package org.labkey.api.external.tools;

import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.security.User;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

public interface ExternalToolsViewProvider
{
    //TODO: add javadoc
    public List<ModelAndView> getViews(User user);
}
