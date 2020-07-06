package org.labkey.api.external.tools;

import org.labkey.api.security.User;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

public interface ExternalToolsViewProvider
{
    /**
     *
     * @param user with access to the views
     * @return a list of views to be accessed via 'External Tools Setting'
     */
    List<ModelAndView> getViews(User user);
}
