/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
