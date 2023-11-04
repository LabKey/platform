package org.labkey.core.login;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.AuthenticationConfiguration;
import org.labkey.api.security.BaseAuthenticationConfiguration;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.PasswordRule;

import java.util.Map;

public class DbLoginConfiguration extends BaseAuthenticationConfiguration<DbLoginAuthenticationProvider> implements AuthenticationConfiguration.LoginFormAuthenticationConfiguration<DbLoginAuthenticationProvider>
{
    private static final PasswordRule DEFAULT_RULE = PasswordRule.Strong;
    private static final PasswordExpiration DEFAULT_EXPIRATION = PasswordExpiration.Never;

    private final PasswordRule _passwordRule;
    private final PasswordExpiration _expiration;

    protected DbLoginConfiguration(DbLoginAuthenticationProvider provider, Map<String, String> stringProperties, Map<String, Object> properties)
    {
        super(provider, properties);

        String ruleProp = stringProperties.getOrDefault(DbLoginManager.Key.Strength.toString(), DEFAULT_RULE.toString());
        String expProp = stringProperties.getOrDefault(DbLoginManager.Key.Expiration.toString(), DEFAULT_EXPIRATION.toString());

        PasswordRule tempRule = DEFAULT_RULE;
        try
        {
            tempRule = PasswordRule.valueOf(ruleProp);
        }
        catch (IllegalArgumentException ignore)
        {
            LOG.warn("%s: Unable to load saved password rule '%s'. Using default: %s".formatted(getDescription(), ruleProp, DEFAULT_RULE));
        }
        _passwordRule = tempRule;

        PasswordExpiration tempExpiration = DEFAULT_EXPIRATION;
        try
        {
            tempExpiration = PasswordExpiration.valueOf(expProp);
        }
        catch (IllegalArgumentException ignore)
        {
            LOG.warn("%s: Unable to load saved password expiration '%s'. Using default: %s".formatted(getDescription(), expProp, DEFAULT_EXPIRATION));
        }
        _expiration = tempExpiration;
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
