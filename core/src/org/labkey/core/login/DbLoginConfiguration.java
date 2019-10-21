package org.labkey.core.login;

import org.labkey.api.security.BaseAuthenticationConfiguration;
import org.labkey.api.security.LoginFormAuthenticationConfiguration;
import org.labkey.api.security.PasswordExpiration;

import java.util.Map;

public class DbLoginConfiguration extends BaseAuthenticationConfiguration<DbLoginAuthenticationProvider> implements LoginFormAuthenticationConfiguration<DbLoginAuthenticationProvider>
{
    private final PasswordRule _passwordRule;
    private final PasswordExpiration _expiration;

    protected DbLoginConfiguration(String key, DbLoginAuthenticationProvider provider, Map<String, String> props)
    {
        super(key, provider, props);
        _passwordRule = PasswordRule.valueOf(props.getOrDefault(DbLoginManager.Key.Strength.toString(), PasswordRule.Weak.toString()));
        _expiration = PasswordExpiration.valueOf(props.getOrDefault(DbLoginManager.Key.Expiration.toString(), PasswordExpiration.Never.toString()));
    }

    public PasswordRule getPasswordRule()
    {
        return _passwordRule;
    }

    public PasswordExpiration getExpiration()
    {
        return _expiration;
    }
}
