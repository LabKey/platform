package org.labkey.authentication.duo;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

/**
 * User: adam
 * Date: 3/11/2015
 * Time: 7:45 AM
 */
public class TestDuoProvider extends DuoProvider
{
    @Override
    public ActionURL getRedirectURL(User candidate, Container c, URLHelper returnURL)
    {
        return DuoController.getTestValidateURL(c, returnURL);
    }

    @Override
    public String getName()
    {
        return "Duo 2 Factor Authentication (Test Provider)";
    }

    @Override
    public String getDescription()
    {
        return "Adds another annoying second factor authentication requirement";
    }
}
