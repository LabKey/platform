package org.labkey.core.secrets;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.secrets.SecretProvider;
import org.labkey.api.settings.StartupPropertyEntry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class StartupPropertySecretProvider implements SecretProvider
{
    private final Map<String, String> _secrets = new HashMap<>();

    void load(Collection<StartupPropertyEntry> entries)
    {
        entries.forEach(entry -> _secrets.put(entry.getName(), entry.getValue()));
    }

    void loadDirect(String name, String value)
    {
        _secrets.put(name, value);
    }

    @Override
    public @Nullable String getSecret(String propertyName)
    {
        return _secrets.get(propertyName);
    }

    @Override
    public @NotNull String getDescription()
    {
        return "Startup property file";
    }
}
