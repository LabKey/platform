package org.labkey.core.authentication.ldap;

import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.BaseAuthenticationConfiguration;
import org.labkey.api.security.LoginFormAuthenticationConfiguration;
import org.labkey.api.security.ValidEmail;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class LdapConfiguration extends BaseAuthenticationConfiguration<LdapAuthenticationProvider> implements LoginFormAuthenticationConfiguration<LdapAuthenticationProvider>
{
    private final String _domain;
    private final Collection<String> _servers;
    private final String _principalTemplate;
    private final boolean _sasl;

    private boolean _allowLdapSearch = false;

    protected LdapConfiguration(LdapAuthenticationProvider provider, Map<String, Object> standardSettings, Map<String, Object> properties)
    {
        super(provider, standardSettings);

        _domain = (String)properties.get("domain");
        _servers = Arrays.asList(((String)properties.getOrDefault("servers", "")).split(";"));
        _principalTemplate = (String)properties.getOrDefault("principalTemplate", "${email}");
        _sasl = (boolean)properties.get("sasl");
    }

    public String getDomain()
    {
        return _domain;
    }

    public Collection<String> getServers()
    {
        return _servers;
    }

    public String getPrincipalTemplate()
    {
        return _principalTemplate;
    }

    public boolean isSasl()
    {
        return _sasl;
    }

    public boolean isLdapEmail(ValidEmail email)
    {
        return AuthenticationManager.ALL_DOMAINS.equals(_domain) || _domain != null && email.getEmailAddress().endsWith("@" + _domain.toLowerCase());
    }

    // TODO: Remove when we move LDAP search settings into configuration UI
    public void setAllowLdapSearch(boolean allow)
    {
        _allowLdapSearch = allow;
    }

    public boolean isAllowLdapSearch()
    {
        return _allowLdapSearch;
    }
}
