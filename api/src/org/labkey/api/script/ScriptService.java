/*
 * Copyright (c) 2010 LabKey Corporation
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
import org.labkey.api.resource.Resource;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

public interface ScriptService extends ScriptEngineFactory
{
    /**
     * Compiles the Resource into a script and caches the result.
     * This is mostly equivalent to calling <code>getScriptEngine().compile()</code>
     * except the {@link javax.script.CompiledScript} will be cached on your behalf.
     * Each call to compile() returns a new {@link ScriptReference} and contains
     * it's own execution context while sharing the same {@link javax.script.CompiledScript}.
     * 
     * @param m module
     * @param r resource
     * @return The compiled script.
     */
    ScriptReference compile(Module m, Resource r) throws ScriptException;
}
