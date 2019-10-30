/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.security.AuthenticationConfigureForm;

import java.util.Map;

public class LdapConfigureForm extends AuthenticationConfigureForm<LdapConfiguration>
{
    public boolean reshow = false;

    private String servers;
    private String domain;
    private String principalTemplate = "${email}";
    private boolean sasl = false;

    public LdapConfigureForm()
    {
        setDescription("LDAP Configuration");
    }

    public String getServers()
    {
        return servers;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setServers(String servers)
    {
        this.servers = servers;
    }

    public String getDomain()
    {
        return domain;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setDomain(String domain)
    {
        this.domain = domain;
    }

    public String getPrincipalTemplate()
    {
        return principalTemplate;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setPrincipalTemplate(String principalTemplate)
    {
        this.principalTemplate = principalTemplate;
    }

    public boolean getSASL()
    {
        return sasl;
    }

    public void setSASL(boolean sasl)
    {
        this.sasl = sasl;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isReshow()
    {
        return reshow;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setReshow(boolean reshow) {
        this.reshow = reshow;
    }

    @Override
    public void setAuthenticationConfiguration(@NotNull LdapConfiguration authenticationConfiguration)
    {
        super.setAuthenticationConfiguration(authenticationConfiguration);
        servers = String.join(";", authenticationConfiguration.getServers());
        domain = authenticationConfiguration.getDomain();
        principalTemplate = authenticationConfiguration.getPrincipalTemplate();
        sasl = authenticationConfiguration.isSasl();
    }

    public String getProperties()
    {
        return new JSONObject(Map.of(
            "servers", servers,
            "domain", domain,
            "principalTemplate", principalTemplate,
            "sasl", sasl
        )).toString();
    }
}
