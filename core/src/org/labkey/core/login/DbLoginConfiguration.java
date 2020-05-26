package org.labkey.core.login;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.AuthenticationConfiguration;
import org.labkey.api.security.BaseAuthenticationConfiguration;
import org.labkey.api.security.PasswordExpiration;

import java.util.Map;

public class DbLoginConfiguration extends BaseAuthenticationConfiguration<DbLoginAuthenticationProvider> implements AuthenticationConfiguration.LoginFormAuthenticationConfiguration<DbLoginAuthenticationProvider>
{
    private final PasswordRule _passwordRule;
    private final PasswordExpiration _expiration;

    protected DbLoginConfiguration(DbLoginAuthenticationProvider provider, Map<String, String> stringProperties, Map<String, Object> properties)
    {
        super(provider, properties);
        _passwordRule = PasswordRule.valueOf(stringProperties.getOrDefault(DbLoginManager.Key.Strength.toString(), PasswordRule.Weak.toString()));
        _expiration = PasswordExpiration.valueOf(stringProperties.getOrDefault(DbLoginManager.Key.Expiration.toString(), PasswordExpiration.Never.toString()));
    }

    @Override
    public @NotNull String getDescription()
    {
        return "Standard database authentication";
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
