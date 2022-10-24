/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.premium.PremiumFeatureNotEnabledException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.List;
import java.util.Map;

public interface LabKeyScriptEngineManager
{
    // represents the context in which a script engine is being invoked
    enum EngineContext
    {
        report,                 // basic report rendering
        pipeline                // pipeline job or transform script
    }

    static LabKeyScriptEngineManager get()
    {
        return ServiceRegistry.get().getService(LabKeyScriptEngineManager.class);
    }

    static void setInstance(LabKeyScriptEngineManager impl)
    {
        ServiceRegistry.get().registerService(LabKeyScriptEngineManager.class, impl);
    }

    ScriptEngine getEngineByName(@NotNull String name);
    List<ScriptEngineFactory> getEngineFactories();

    /**
     * Return a script engine appropriate for the specified extension.
     */
    @Nullable
    ScriptEngine getEngineByExtension(@NotNull Container c, @NotNull String extension) throws PremiumFeatureNotEnabledException;

    /**
     * Return a script engine appropriate for the specified extension and for a specific engine context. Folder and
     * project level mappings can be set for specific engine configurations and contexts.
     */
    @Nullable
    ScriptEngine getEngineByExtension(@NotNull Container c, @NotNull String extension, @NotNull EngineContext context) throws PremiumFeatureNotEnabledException;

    /**
     * Returns an engine (if any) that is scoped to a specific folder or project level. If there is no specific override then
     * null is returned.
     * @param includeProject look in both the folder and project
     */
    @Nullable
    ExternalScriptEngineDefinition getScopedEngine(@NotNull Container container, @NotNull String extension, @NotNull EngineContext context, boolean includeProject);

    void setEngineScope(@NotNull Container c, @NotNull ExternalScriptEngineDefinition def, @NotNull EngineContext context);
    void removeEngineScope(@NotNull Container c, @NotNull ExternalScriptEngineDefinition def, @NotNull EngineContext context);

    void deleteDefinition(@NotNull User user, @NotNull ExternalScriptEngineDefinition def);
    ExternalScriptEngineDefinition saveDefinition(@NotNull User user, @NotNull ExternalScriptEngineDefinition def);
    boolean isFactoryEnabled(@NotNull ScriptEngineFactory factory);

    List<ExternalScriptEngineDefinition> getEngineDefinitions();
    List<ExternalScriptEngineDefinition> getEngineDefinitions(@NotNull ExternalScriptEngineDefinition.Type type);
    List<ExternalScriptEngineDefinition> getEngineDefinitions(@NotNull ExternalScriptEngineDefinition.Type type, boolean enabled);
    ExternalScriptEngineDefinition getEngineDefinition(@NotNull String name, @NotNull ExternalScriptEngineDefinition.Type type);
    ExternalScriptEngineDefinition getEngineDefinition(int rowId, @NotNull ExternalScriptEngineDefinition.Type type);

    ExternalScriptEngineDefinition createEngineDefinition();

    Map<String,Object> getScriptEngineMetrics();
}
