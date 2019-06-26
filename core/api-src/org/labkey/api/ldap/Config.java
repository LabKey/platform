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
package org.labkey.api.ldap;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.ReturnUrlForm;

public class Config extends ReturnUrlForm
{
    public boolean reshow = false;

    private String servers = StringUtils.join(LdapAuthenticationManager.getServers(), ";");
    private String domain = LdapAuthenticationManager.getDomain();
    private String principalTemplate = LdapAuthenticationManager.getPrincipalTemplate();
    private boolean useSASL = false;   // Always initialize to false because of checkbox behavior

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
        return useSASL;
    }

    public void setSASL(boolean useSASL)
    {
        this.useSASL = useSASL;
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
}
