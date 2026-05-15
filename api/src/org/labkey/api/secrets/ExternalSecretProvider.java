package org.labkey.api.secrets;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * SPI for external secret stores (e.g., AWS SSM Parameter Store).
 * Implementations are registered with {@link SecretService} and consulted at higher
 * priority than startup-property secrets. The provider is responsible for caching;
 * {@link #refreshAll} is called periodically by a timer managed by {@link SecretService}.
 */
public interface ExternalSecretProvider
{
    /** Returns the secret for the given property name, or null if not available in this provider. */
    @Nullable String getSecret(String propertyName);

    /** Refresh cached values for all registered property names. Called on a timer. */
    void refreshAll(Collection<String> propertyNames);
}
