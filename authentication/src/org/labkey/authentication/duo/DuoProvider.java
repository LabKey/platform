package org.labkey.authentication.duo;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: tgaluhn
 * Date: 3/6/2015
 */
public class DuoProvider implements AuthenticationProvider.RequestAuthenticationProvider
{
    private static final Logger LOG = Logger.getLogger(DuoProvider.class);

    @Override
    public AuthenticationResponse authenticate(HttpServletRequest request, HttpServletResponse response, URLHelper returnURL) throws ValidEmail.InvalidEmailException
    {
        throw new UnsupportedOperationException();
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
        return ("Duo 2 Factor Authentication");
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
        throw new UnsupportedOperationException();
    }

    @Override
    public void deactivate() throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPermanent()
    {
        return false;
    }
}
