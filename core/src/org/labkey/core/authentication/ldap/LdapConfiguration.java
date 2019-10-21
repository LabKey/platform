package org.labkey.core.authentication.ldap;

import org.labkey.api.ldap.LdapAuthenticationManager.Key;
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

    protected LdapConfiguration(String key, LdapAuthenticationProvider provider, Map<String, String> props)
    {
        super(key, provider, props);
        _domain = props.get(Key.Domain.toString());
        _servers = Arrays.asList(props.getOrDefault(Key.Servers.toString(), "").split(";"));
        _principalTemplate = props.getOrDefault(Key.PrincipalTemplate.toString(), "${email}");
        _sasl = Boolean.getBoolean(props.get(Key.SASL.toString()));
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
}
