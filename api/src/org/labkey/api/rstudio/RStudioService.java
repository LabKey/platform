package org.labkey.api.rstudio;

import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * NOTE: wouldn't need this if we had a way to register links for the developer menu
 */
public interface RStudioService
{
    // the no-explanation version, just return null if user is not eligible
    ActionURL getRStudioLink(User user);
}
