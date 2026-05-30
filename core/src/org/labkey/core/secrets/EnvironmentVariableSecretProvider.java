package org.labkey.core.secrets;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.secrets.SecretProvider;

class EnvironmentVariableSecretProvider implements SecretProvider
{
    @Override
    public @Nullable String getSecret(String propertyName)
    {
        String value = System.getenv(propertyName);
        return StringUtils.isNotBlank(value) ? value : null;
    }

    @Override
    public @NotNull String getDescription()
    {
        return "Environment variable";
    }
}
