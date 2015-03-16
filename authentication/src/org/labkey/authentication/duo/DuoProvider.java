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
        ActionURL validateURL = DuoController.getValidateURL(c, returnURL);
        validateURL.addParameter("sig_request", DuoManager.generateSignedRequest(candidate));
        return validateURL;
    }

    @Override
    public boolean bypass()
    {
        return !DuoManager.isConfigured() || DuoManager.bypassDuoAuthentication();
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
        return ("Duo 2 Factor");
    }

    @Override
    public String getDescription()
    {
        return "Require two-factor authentication via Duo";
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
