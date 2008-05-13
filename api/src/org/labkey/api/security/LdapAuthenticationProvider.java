/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.security;

import org.apache.log4j.Logger;
import org.labkey.api.util.AppProps;
import org.labkey.api.view.ActionURL;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;

/**
 * User: adam
 * Date: Oct 12, 2007
 * Time: 1:31:18 PM
 */
public class LdapAuthenticationProvider implements AuthenticationProvider.LoginFormAuthenticationProvider
{
    private static final Logger _log = Logger.getLogger(LdapAuthenticationProvider.class);

    public boolean isPermanent()
    {
        return true;
    }

    public void initialize() throws Exception
    {
    }

    public String getName()
    {
        return "LDAP";
    }

    public ActionURL getConfigurationLink(ActionURL returnUrl)
    {
        return null;  // Migrate LDAP specific config into separate controller
    }

    // id and password will not be blank (not null, not empty, not whitespace only)
    public ValidEmail authenticate(String id, String password) throws ValidEmail.InvalidEmailException
    {
        // Consider: allow user ids other than email
        ValidEmail email = new ValidEmail(id);

        if (SecurityManager.isLdapEmail(email) && LDAPLogin(email, password))
            return email;

        return null;
    }

    // id and password will not be blank (not null, not empty, not whitespace on

    //
    // Attempt to log in by iterating through all the LDAP servers.
    // List of servers is stored as a site property in the database
    //
    public static boolean LDAPLogin(ValidEmail email, String password)
    {
        String[] ldapServers = AppProps.getInstance().getLDAPServersArray();
        boolean saslAuthentication = AppProps.getInstance().useSASLAuthentication();
        if (null == ldapServers)
            return false;

        for (String server : ldapServers)
        {
            if (null == server || 0 == server.length())
                continue;

            try
            {
                return LDAPAuthenticate(server, email, password, saslAuthentication);
            }
            catch (NamingException e)
            {
                // Can't find the server... log the exception and try the next one
                _log.error("LDAPLogin: " + server + " failed.", e);
            }
        }

        return false;   // Can't connect to any of the servers
    }

    //
    // Attempt LDAP authentication on a single server
    //
    private static boolean LDAPAuthenticate(String url, ValidEmail email, String password, boolean saslAuthentication)
            throws NamingException
    {
        // Don't pass blank or null password or email to connect -- it will ALWAYS SUCCEED
        // We've already verfied above that password and email are not blank

        return SecurityManager.LDAPConnect(url, SecurityManager.emailToLdapPrincipal(email), password, saslAuthentication);
    }

    public void logout(HttpServletRequest request)
    {
        // No special handling required
    }    
}
