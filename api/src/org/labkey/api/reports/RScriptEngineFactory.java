/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import java.util.HashSet;

/*
* User: dax
* Date: May 16, 2013
* Time: 4:16:41 PM
*/
public class RScriptEngineFactory extends ExternalScriptEngineFactory
{
    private static HashSet<String> _supportedExtensions = new HashSet<>();

    static {
        _supportedExtensions.add("r");
        _supportedExtensions.add("rmd");
        _supportedExtensions.add("rhtml");
    }

    public RScriptEngineFactory(ExternalScriptEngineDefinition def)
    {
        super(def);
    }

    public ScriptEngine getScriptEngine()
    {
        return new RScriptEngine(_def);
    }

    public static boolean isRScriptEngine(String[] extensions)
    {
        for (String ext : extensions)
        {
            if (_supportedExtensions.contains(ext.toLowerCase()))
                return true;
        }

        return false;
    }
}