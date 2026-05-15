package org.labkey.api.secrets;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;

/**
 * Internal service that provides access to secrets (API keys, passwords, etc.) without
 * requiring callers to know where the secret is stored. Secrets may come from startup
 * property files, process environment variables, or an external store such as AWS SSM.
 *
 * <p>Modules should:
 * <ol>
 *   <li>Declare each secret as a {@code static final SecretProperty} constant.</li>
 *   <li>Call {@link #register} in their {@code doStartup()} method.</li>
 *   <li>Call {@link #getSecret} wherever the value is needed.</li>
 * </ol>
 *
 * <p>Startup property file convention: {@code secret.<propertyName>=<value>}
 */
public interface SecretService
{
    static @NotNull SecretService get()
    {
        SecretService svc = ServiceRegistry.get().getService(SecretService.class);
        if (svc == null)
            throw new IllegalStateException("SecretService has not been initialized");
        return svc;
    }

    static void setInstance(SecretService service)
    {
        ServiceRegistry.get().registerService(SecretService.class, service);
    }

    /**
     * Declare that the calling module may request the named secret. Should be called
     * from {@code Module.doStartup()}. Registration is for documentation and filtering
     * (e.g., admin env-var page redaction); it does not affect whether a value is returned.
     */
    void register(@NotNull SecretProperty property);

    /**
     * Retrieve the value of a secret. Returns {@code null} if the secret has not been
     * configured in any source. Never logs or caches the returned value.
     *
     * <p><strong>Identity contract:</strong> the {@code property} argument must be the exact
     * {@code static final} instance that was passed to {@link #register}. A freshly constructed
     * {@code new SecretProperty("SOME_KEY")} will always return {@code null}, even if a secret
     * with that name is configured. This prevents unregistered callers from reading secrets
     * they did not declare.
     */
    @Nullable String getSecret(@NotNull SecretProperty property);

    /** Returns true if the given property name has been registered via {@link #register}. */
    boolean isRegisteredSecret(@NotNull String name);

    /**
     * Register an {@link ExternalSecretProvider} (e.g., AWS SSM). The provider is
     * consulted at higher priority than startup-property secrets. Should be called after
     * server startup is complete so that startup properties are loaded first.
     *
     * TODO This is a total place holder.  Secrets may need to be available very eary, so we
     * TODO need to consider a typical registerProvider() interface will work well.
     */
    void setExternalProvider(@NotNull ExternalSecretProvider provider);
}
