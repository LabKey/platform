package org.labkey.authentication.duo;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;

/**
 * User: tgaluhn
 * Date: 3/6/2015
 */
public class DuoProvider implements SecondaryAuthenticationProvider
{
    private static final Logger LOG = Logger.getLogger(DuoProvider.class);

    @Override
    public ActionURL getRedirectURL(User candidate, Container c, URLHelper returnURL)
    {
        // TODO: Initiate duo process here

        return DuoController.getValidateURL(c, returnURL);
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return DuoController.getConfigureURL(false);
    }

    @Override
    public String getName()
    {
        return "Duo 2 Factor Authentication";
    }

    @Override
    public String getDescription()
    {
        return "Adds a second factor authentication requirement";
    }

    @Override
    public void logout(HttpServletRequest request)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void activate() throws Exception
    {
    }

    @Override
    public void deactivate() throws Exception
    {
    }

    @Override
    public boolean isPermanent()
    {
        return false;
    }
}
