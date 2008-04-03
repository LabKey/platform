package org.labkey.api.security;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:49:05 PM
 */
public abstract interface AuthenticationProvider
{
    public ActionURL getConfigurationLink(ActionURL returnUrl);
    public String getName();
    public void logout(HttpServletRequest request);
    public void initialize() throws Exception;
    public boolean isPermanent();

    public static interface RequestAuthenticationProvider extends AuthenticationProvider
    {
        public ValidEmail authenticate(HttpServletRequest request, HttpServletResponse response) throws ValidEmail.InvalidEmailException, RedirectException;
    }

    public static interface LoginFormAuthenticationProvider extends AuthenticationProvider
    {
        // id and password will not be blank (not null, not empty, not whitespace only)
        public ValidEmail authenticate(String id, String password) throws ValidEmail.InvalidEmailException;
    }
}
