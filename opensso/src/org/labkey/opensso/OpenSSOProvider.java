package org.labkey.opensso;

import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.iplanet.sso.SSOTokenManager;
import org.apache.log4j.Logger;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 7:01:10 PM
 */
public class OpenSSOProvider implements AuthenticationProvider.RequestAuthenticationProvider
{
    private static final Logger _log = Logger.getLogger(OpenSSOProvider.class);
    public static final String NAME = "OpenSSO";

    public boolean isPermanent()
    {
        return false;
    }

    public void initialize() throws Exception
    {
        OpenSSOManager.get().initialize();
    }

    public String getName()
    {
        return NAME;
    }

    public ActionURL getConfigurationLink(ActionURL returnUrl)
    {
        return OpenSSOController.getCurrentSettingsURL(returnUrl);
    }

    public ValidEmail authenticate(HttpServletRequest request, HttpServletResponse response) throws ValidEmail.InvalidEmailException, RedirectException
    {
        try
        {
            SSOTokenManager manager = SSOTokenManager.getInstance();
            SSOToken token = manager.createSSOToken(request);
            if (SSOTokenManager.getInstance().isValidToken(token))
            {
                String principalName = token.getPrincipal().getName();
                int i = principalName.indexOf(',');
                String email = principalName.substring(3, i);
                return new ValidEmail(email);
            }
        }
        catch (SSOException e)
        {
            _log.debug("Invalid, expired, or missing OpenSSO token", e);
        }

        String referrerPrefix = OpenSSOManager.get().getReferrerPrefix();

        if (null != referrerPrefix)
        {
            // Note to developers: this is difficult to test/debug because (in my experience) "referer" is null when linking
            // to http://localhost.  Use an actual domain name to test this code (e.g., http://dhcp155191.fhcrc.org).
            String referer = request.getHeader("Referer");

            if (null != referer && referer.startsWith(referrerPrefix))
            {
                AuthenticationManager.LinkFactory factory = AuthenticationManager.getLinkFactory(NAME);

                if (null != factory)
                {
                    String returnURL = request.getParameter("URI");

                    if (null != returnURL)
                    {
                        String url = factory.getURL(new ActionURL(returnURL));
                        throw new RedirectException(url);
                    }
                }
            }
        }

        return null;     // Rely on login screen to present link to OpenSSO
    }


    public void logout(HttpServletRequest request)
    {
        try
        {
            SSOTokenManager manager = SSOTokenManager.getInstance();
            SSOToken token = manager.createSSOToken(request);
            manager.destroyToken(token);
        }
        catch (SSOException e)
        {
            // Ignore
        }
    }
}
