/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
import org.labkey.api.data.PropertyManager;
import org.labkey.api.ldap.LdapAuthenticationManager;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.naming.NamingException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.ldap.LdapAuthenticationManager.LDAP_AUTHENTICATION_CATEGORY_KEY;

/**
 * User: adam
 * Date: Oct 12, 2007
 * Time: 1:31:18 PM
 */
public class LdapAuthenticationProvider implements LoginFormAuthenticationProvider<LdapConfiguration>
{
    private static final Logger LOG = Logger.getLogger(LdapAuthenticationProvider.class);

    @Override
    public LdapConfiguration getAuthenticationConfiguration(boolean active)
    {
        Map<String, String> props = PropertyManager.getProperties(LDAP_AUTHENTICATION_CATEGORY_KEY);
        Map<String, String> map = new HashMap<>(props);
        map.put("Provider", getName());
        map.put("Enabled", Boolean.toString(active));
        map.put("Name", getName());

        return new LdapConfiguration(LDAP_AUTHENTICATION_CATEGORY_KEY, this, map);
    }

    @Override
    public void activate()
    {
        LdapAuthenticationManager.activate();
    }

    @Override
    public void deactivate()
    {
        LdapAuthenticationManager.deactivate();
    }

    @Override
    @NotNull
    public String getName()
    {
        return "LDAP";
    }

    @Override
    @NotNull
    public String getDescription()
    {
        return "Uses the LDAP protocol to authenticate against an institution's directory server";
    }

    @Override
    public ActionURL getConfigurationLink()
    {
        return LdapController.getConfigureURL(false);
    }

    @Override
    // id and password will not be blank (not null, not empty, not whitespace only)
    public @NotNull AuthenticationResponse authenticate(LdapConfiguration configuration, @NotNull String id, @NotNull String password, URLHelper returnURL) throws ValidEmail.InvalidEmailException
    {
        // Consider: allow user ids other than email
        ValidEmail email = new ValidEmail(id);

        if (!configuration.isLdapEmail(email))
            return AuthenticationResponse.createFailureResponse(this, FailureReason.notApplicable);

        //
        // Attempt to authenticate by iterating through all the LDAP servers.
        //
        boolean saslAuthentication = configuration.isSasl();
        String principalTemplate = configuration.getPrincipalTemplate();

        for (String server : configuration.getServers())
        {
            if (server.isEmpty())
                continue;

            try
            {
                if (LdapAuthenticationManager.authenticate(server, email, password, principalTemplate, saslAuthentication))
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
        return Collections.singleton(LDAP_AUTHENTICATION_CATEGORY_KEY);
    }
}
