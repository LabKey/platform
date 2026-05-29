package org.labkey.api.secrets;

import org.jetbrains.annotations.Nullable;

/**
 * Read-only status of a registered secret — suitable for admin UI display.
 * Never contains the secret value itself.
 *
 * @param source description of the provider that holds this secret
 *               (e.g. "Startup property file", "Environment variable"), or
 *               {@code null} if no provider has a value for it
 */
public record SecretStatus(String name, String description, @Nullable String source)
{
    public boolean isSet()
    {
        return source != null;
    }
}
