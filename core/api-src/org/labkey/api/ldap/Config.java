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
