/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.Arrays;
import java.util.List;

/*
* User: Karl Lum
* Date: Dec 2, 2008
* Time: 4:16:41 PM
*/
public class ExternalScriptEngineFactory implements ScriptEngineFactory
{
    protected ExternalScriptEngineDefinition _def;

    public ExternalScriptEngineFactory(ExternalScriptEngineDefinition def)
    {
        _def = def;
    }

    @Override
    public String getEngineName()
    {
        return _def.getName();
    }

    @Override
    public String getEngineVersion()
    {
        return "9.1";
    }

    @Override
    public List<String> getExtensions()
    {
        return Arrays.asList(_def.getExtensions());
    }

    @Override
    public List<String> getMimeTypes()
    {
        return null;
    }

    @Override
    public List<String> getNames()
    {
        return null;
    }

    @Override
    public String getLanguageName()
    {
        return _def.getLanguageName();
    }

    @Override
    public String getLanguageVersion()
    {
        return _def.getLanguageVersion();
    }

    @Override
    public Object getParameter(String key)
    {
        return null;
    }

    @Override
    public String getMethodCallSyntax(String obj, String m, String... args)
    {
        return null;
    }

    @Override
    public String getOutputStatement(String toDisplay)
    {
        return null;
    }

    @Override
    public String getProgram(String... statements)
    {
        return null;
    }

    @Override
    public ScriptEngine getScriptEngine()
    {
        return new ExternalScriptEngine(_def);
    }

    public ExternalScriptEngineDefinition getDefinition()
    {
        return _def;
    }
}