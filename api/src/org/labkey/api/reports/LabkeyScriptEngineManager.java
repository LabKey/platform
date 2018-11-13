/*
 * Copyright (c) 2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.ArrayList;
import java.util.List;

public interface LabkeyScriptEngineManager
{
    ScriptEngine getEngineByName(String name);
    List<ScriptEngineFactory> getEngineFactories();
    /**
     * Return a script engine appropriate for the specified extension.
     */
    @Nullable
    ScriptEngine getEngineByExtension(Container c, String extension);

    /**
     * Return a script engine appropriate for the specified extension.
     * @param requestRemote R reports can pass a hint if they can run using a remote engine (Rserve). If there
     *                      is a remote engine available it will be returned over a local engine.
     */
    @Nullable
    ScriptEngine getEngineByExtension(Container c, String extension, boolean requestRemote);

    @Nullable
    @Deprecated
    ScriptEngine getEngineByExtension(Container c, String extension, boolean requestRemote, boolean requestDocker);

    List<ExternalScriptEngineDefinition> getScopedEngines(Container container);
    void setEngineScope(Container c, ExternalScriptEngineDefinition def);
    void removeEngineScope(Container c, ExternalScriptEngineDefinition def);

    void deleteDefinition(User user, ExternalScriptEngineDefinition def);
    ExternalScriptEngineDefinition saveDefinition(User user, ExternalScriptEngineDefinition def);
    boolean isFactoryEnabled(ScriptEngineFactory factory);

    List<ExternalScriptEngineDefinition> getEngineDefinitions();
    List<ExternalScriptEngineDefinition> getEngineDefinitions(ExternalScriptEngineDefinition.Type type);
    List<ExternalScriptEngineDefinition> getEngineDefinitions(ExternalScriptEngineDefinition.Type type, boolean enabled);
    ExternalScriptEngineDefinition getEngineDefinition(String name, ExternalScriptEngineDefinition.Type type);
    ExternalScriptEngineDefinition getEngineDefinition(int rowId, ExternalScriptEngineDefinition.Type type);

    ExternalScriptEngineDefinition createEngineDefinition();
}
