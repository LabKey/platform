/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

package org.labkey.authentication.ldap;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.URLHelper;
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
    private static final Logger LOG = Logger.getLogger(LdapAuthenticationProvider.class);

    public boolean isPermanent()
    {
        return false;
    }

    public void activate()
    {
        LdapAuthenticationManager.activate();
    }

    public void deactivate()
    {
        LdapAuthenticationManager.deactivate();
    }

    public String getName()
    {
        return "LDAP";
    }

    public String getDescription()
    {
        return "Uses the LDAP protocol to authenticate against an institution's directory server";
    }

    public ActionURL getConfigurationLink()
    {
        return LdapController.getConfigureURL(false);
    }

    // id and password will not be blank (not null, not empty, not whitespace only)
    public AuthenticationResponse authenticate(@NotNull String id, @NotNull String password, URLHelper returnURL) throws ValidEmail.InvalidEmailException
    {
        // Consider: allow user ids other than email
        ValidEmail email = new ValidEmail(id);

        if (!SecurityManager.isLdapEmail(email))
            return AuthenticationResponse.createFailureResponse(FailureReason.notApplicable);

        //
        // Attempt to authenticate by iterating through all the LDAP servers.
        // List of servers is stored as a site property in the database
        //
        String[] ldapServers = LdapAuthenticationManager.getServers();
        boolean saslAuthentication = LdapAuthenticationManager.useSASL();

        if (null == ldapServers)
            return AuthenticationResponse.createFailureResponse(FailureReason.configurationError);

        for (String server : ldapServers)
        {
            if (null == server || 0 == server.length())
                continue;

            try
            {
                if (LdapAuthenticationManager.authenticate(server, email, password, saslAuthentication))
                    return AuthenticationResponse.createSuccessResponse(email);
                else
                    return AuthenticationResponse.createFailureResponse(FailureReason.badCredentials);
            }
            catch (NamingException e)
            {
                // Can't find the server... log the exception and try the next one
                LOG.error("LDAPLogin: " + server + " failed.", e);
            }
        }

        return AuthenticationResponse.createFailureResponse(FailureReason.configurationError);
    }

    public void logout(HttpServletRequest request)
    {
        // No special handling required
    }
}
