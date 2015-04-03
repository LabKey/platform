package org.labkey.authentication.cas;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by adam on 3/29/2015.
 */
public class CasAuthenticationProvider implements SSOAuthenticationProvider
{
    private static final CasAuthenticationProvider INSTANCE = new CasAuthenticationProvider();
    static final String NAME = "Apereo CAS";

    private CasAuthenticationProvider()
    {
    }

    public static CasAuthenticationProvider getInstance()
    {
        return INSTANCE;
    }

    @Override
    public AuthenticationResponse authenticate(HttpServletRequest request, HttpServletResponse response, URLHelper returnURL) throws ValidEmail.InvalidEmailException
    {
        return null;
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return CasController.getConfigureURL();
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Apereo Central Authentication Service (CAS)";
    }

    @Override
    public void logout(HttpServletRequest request)
    {
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
