/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.core.authentication.ldap;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.naming.NamingException;
import java.util.Collection;
import java.util.Collections;

/**
 * User: adam
 * Date: Oct 12, 2007
 * Time: 1:31:18 PM
 */
public class LdapAuthenticationProvider implements LoginFormAuthenticationProvider
{
    private static final Logger LOG = Logger.getLogger(LdapAuthenticationProvider.class);

    public void activate()
    {
        LdapAuthenticationManager.activate();
    }

    public void deactivate()
    {
        LdapAuthenticationManager.deactivate();
    }

    @NotNull
    public String getName()
    {
        return "LDAP";
    }

    @NotNull
    public String getDescription()
    {
        return "Uses the LDAP protocol to authenticate against an institution's directory server";
    }

    public ActionURL getConfigurationLink()
    {
        return LdapController.getConfigureURL(false);
    }

    // id and password will not be blank (not null, not empty, not whitespace only)
    public @NotNull AuthenticationResponse authenticate(@NotNull String id, @NotNull String password, URLHelper returnURL) throws ValidEmail.InvalidEmailException
    {
        // Consider: allow user ids other than email
        ValidEmail email = new ValidEmail(id);

        if (!SecurityManager.isLdapEmail(email))
            return AuthenticationResponse.createFailureResponse(this, FailureReason.notApplicable);

        //
        // Attempt to authenticate by iterating through all the LDAP servers.
        // List of servers is stored as a site property in the database
        //
        String[] ldapServers = LdapAuthenticationManager.getServers();
        boolean saslAuthentication = LdapAuthenticationManager.useSASL();

        for (String server : ldapServers)
        {
            if (null == server || 0 == server.length())
                continue;

            try
            {
                if (LdapAuthenticationManager.authenticate(server, email, password, saslAuthentication))
                    return AuthenticationResponse.createSuccessResponse(this, email);
                else
                    return AuthenticationResponse.createFailureResponse(this, FailureReason.badCredentials);
            }
            catch (NamingException e)
            {
                // Can't find the server... log the exception and try the next one
                LOG.error("LDAPLogin: " + server + " failed.", e);
            }
        }

        return AuthenticationResponse.createFailureResponse(this, FailureReason.configurationError);
    }

    @Override
    public @NotNull Collection<String> getPropertyCategories()
    {
        return Collections.singleton(LdapAuthenticationManager.LDAP_AUTHENTICATION_CATEGORY_KEY);
    }
}
