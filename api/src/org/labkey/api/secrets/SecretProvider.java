package org.labkey.api.secrets;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * SPI for a secret source. Implementations cover built-in sources (startup property files,
 * environment variables) and external stores (e.g., AWS SSM Parameter Store).
 * Providers are consulted in priority order by {@link SecretService}.
 */
public interface SecretProvider
{
    /** Returns the secret for the given property name, or null if not available from this source. */
    @Nullable String getSecret(String propertyName);

    /** Human-readable name for this source, shown on the admin secrets page. */
    @NotNull String getDescription();
}
