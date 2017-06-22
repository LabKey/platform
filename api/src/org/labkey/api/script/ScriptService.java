/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.script;

import org.labkey.api.module.Module;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

public interface ScriptService extends ScriptEngineFactory
{
    String SCRIPTS_DIR = "scripts";

    static ScriptService get()
    {
        return ServiceRegistry.get().getService(ScriptService.class);
    }

    // marker class for server script logging (see log4j.xml)
    class Console {}

    /**
     * Compiles the JS file at the specified location into a script and caches the result.
     * This is mostly equivalent to calling <code>getScriptEngine().compile()</code>
     * except the {@link javax.script.CompiledScript} will be cached on your behalf.
     * Each call to compile() returns a new {@link ScriptReference} and contains
     * its own execution context while sharing the same {@link javax.script.CompiledScript}.
     *
     * @return The compiled script.
     */
    ScriptReference compile(Module module, Path path) throws ScriptException;
}
