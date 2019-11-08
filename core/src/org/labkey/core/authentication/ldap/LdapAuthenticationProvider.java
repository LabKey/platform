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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.ldap.LdapAuthenticationManager;
import org.labkey.api.security.AuthenticationConfigureForm;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;
import org.labkey.api.security.ConfigurationSettings;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.naming.NamingException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Oct 12, 2007
 * Time: 1:31:18 PM
 */
public class LdapAuthenticationProvider implements LoginFormAuthenticationProvider<LdapConfiguration>
{
    private static final Logger LOG = Logger.getLogger(LdapAuthenticationProvider.class);

    @Override
    public List<LdapConfiguration> getAuthenticationConfigurations(@NotNull List<ConfigurationSettings> configurations)
    {
        List<LdapConfiguration> list = LoginFormAuthenticationProvider.super.getAuthenticationConfigurations(configurations);

        list.stream().findFirst().ifPresent(lc->{
            lc.setAllowLdapSearch(true);  // TODO: move LDAP search settings into normal configuration
            AuthenticationManager.setLdapDomain(lc.getDomain()); // TODO: AuthenticationConfigurationCollections should collect all mapped domains
        });

        return list;
    }

    @Override
    public LdapConfiguration getAuthenticationConfiguration(@NotNull ConfigurationSettings cs)
    {
        return new LdapConfiguration(this, cs.getStandardSettings(), cs.getProperties());
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
        return getConfigurationLink(null);
    }

    @Override
    public @Nullable ActionURL getConfigurationLink(@Nullable Integer rowId)
    {
        return LdapController.getConfigureURL(rowId, false);
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
        for (String server : configuration.getServers())
        {
            if (server.isEmpty())
                continue;

            try
            {
                if (LdapAuthenticationManager.authenticate(server, email, password, configuration.getPrincipalTemplate(), configuration.isSasl(), configuration.isAllowLdapSearch()))
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

    private static final String LDAP_AUTHENTICATION_CATEGORY_KEY = "LDAPAuthentication";
    private enum Key {Servers, Domain, PrincipalTemplate, SASL}

    @Override
    public @Nullable AuthenticationConfigureForm getFormFromOldConfiguration(boolean active)
    {
        LdapConfigureForm form = null;
        Map<String, String> props = PropertyManager.getProperties(LDAP_AUTHENTICATION_CATEGORY_KEY);

        if (active || !props.isEmpty())
        {
            form = new LdapConfigureForm();
            form.setServers(getProperty(props, Key.Servers, ""));
            form.setDomain(getProperty(props, Key.Domain, ""));
            form.setPrincipalTemplate(getProperty(props, Key.PrincipalTemplate, "${email}"));
            form.setSASL("TRUE".equalsIgnoreCase(getProperty(props, Key.SASL, "FALSE")));
        }

        return form;
    }

    private static String getProperty(Map<String, String> props, Key key, String defaultValue)
    {
        String value = props.get(key.toString());

        if (null != value)
            return value;
        else
            return defaultValue;
    }

    @Override
    public void handleStartupProperties()
    {
        saveStartupProperties(Collections.singleton(LDAP_AUTHENTICATION_CATEGORY_KEY), LdapConfigureForm.class);
    }
}
