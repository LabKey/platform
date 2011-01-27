/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.mozilla.javascript.*;
import org.mozilla.javascript.tools.shell.Global;

/**
 * User: kevink
 */
public class TopLevel extends ImporterTopLevel
{
    protected TopLevel() { }

    public TopLevel(Context cx, RhinoEngine engine, boolean sealed)
    {
        super(cx, sealed);
        init(cx, engine);
    }

    public void init(Context cx, RhinoEngine engine)
    {
        String[] names = {
            "require",
        };
        defineFunctionProperties(names, TopLevel.class, ScriptableObject.DONTENUM);
    }

    /*
    public static Object require(Context cx, Scriptable thisObj,
                               Object[] args, Function funObj)
    {
        if (args == null || args.length < 1)
            throw Context.reportRuntimeError("require() requires a module id argument");

        String id = (String)Context.jsToJava(args[0], String.class);
        id = resolveId(cx, thisObj, id);

        RhinoEngine engine;
        engine.loadModule(cx, (String)args[0], moduleScope);
        return module.getExports();
    }

    public static String resolveId(Context cx, Scriptable thisObj, String id)
    {
        if (id.startsWith("./") || id.startsWith("../"))
        {
            String moduleId = getModuleId(thisObj);
            if (moduleId == null)
                throw Context.reportRuntimeError("Can't resolve relative module id from current scope");

            
        }
    }
    */
}
