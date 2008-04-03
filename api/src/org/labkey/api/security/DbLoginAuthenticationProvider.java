package org.labkey.api.security;

import org.labkey.api.security.AuthenticationProvider.*;
import org.labkey.api.view.ActionURL;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * User: adam
 * Date: Oct 12, 2007
 * Time: 1:31:18 PM
 */
public class DbLoginAuthenticationProvider implements LoginFormAuthenticationProvider
{
    private static final Logger _log = Logger.getLogger(DbLoginAuthenticationProvider.class);

    public boolean isPermanent()
    {
        return true;
    }

    public void initialize() throws Exception
    {
    }

    public String getName()
    {
        return "Database";
    }

    // id and password will not be blank (not null, not empty, not whitespace only)
    public ValidEmail authenticate(String id, String password) throws ValidEmail.InvalidEmailException
    {
        ValidEmail email = new ValidEmail(id);
        String hash = SecurityManager.getPasswordHash(email);

        if (null == hash)
            _log.error("DbLoginAuthenticationProvider: Invalid login. name=" + email + ", does not exist.");
        else if (!hash.equals(Crypt.digest(password)))
            _log.error("DbLoginAuthenticationProvider: Invalid login. name=" + email + ", bad password.");
        else
            return email;

        return null;
    }


    public ActionURL getConfigurationLink(ActionURL returnUrl)
    {
        return null;
    }


    public void logout(HttpServletRequest request)
    {
        // No special handling required
    }
}
