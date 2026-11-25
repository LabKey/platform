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
package org.labkey.api.reports;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;
import org.springframework.validation.Errors;

import javax.script.ScriptEngine;

/** Builds script engines for one language and engine kind; see {@link ScriptEngineProviderRegistry}. */
public interface ScriptEngineProvider
{
    boolean isAvailable();

    /** Throws when this kind can't run on this server; never falls back to another kind. */
    void ensureAvailable(@NotNull ExternalScriptEngineDefinition def);

    @Nullable ScriptEngine create(@NotNull ExternalScriptEngineDefinition def);

    /** Site defaults and folder mappings may only name a context this returns true for. */
    default boolean supportsContext(@NotNull ExternalScriptEngineDefinition def, @NotNull LabKeyScriptEngineManager.EngineContext context)
    {
        return true;
    }

    default void validate(@NotNull ExternalScriptEngineDefinition def, @NotNull Errors errors)
    {
    }

    /** Called by the admin save action before the definition is written. */
    default void beforeSave(@NotNull ExternalScriptEngineDefinition def, @NotNull User user)
    {
    }
}
