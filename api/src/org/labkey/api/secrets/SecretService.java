/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.secrets;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;

import java.util.List;

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
     * Register a high-priority {@link SecretProvider} (e.g., AWS SSM). This provider is
     * consulted before the built-in startup-property and environment-variable providers.
     */
    void setExternalProvider(@NotNull SecretProvider provider);

    /**
     * Returns read-only status for every registered secret, sorted by name.
     * Never includes secret values — safe to display in admin UI.
     */
    @NotNull List<SecretStatus> getSecretStatuses();

    /**
     * Returns a human-readable description of the active external provider (e.g.,
     * "AWS SSM Parameter Store"), or {@code null} if no external provider is registered.
     * The external provider takes priority over startup-property and environment-variable sources.
     */
    @Nullable String getExternalProviderDescription();

    /**
     * Returns the application name reported by the highest-priority provider that has one
     * (see {@link SecretProvider#getAppName()}), or {@code null} if no active provider reports one.
     */
    @Nullable String getAppName();
}
