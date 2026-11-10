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
package org.labkey.core.script;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaMethod;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

// Rhino passes JS arrays and objects to Java as-is, so concatenated strings inside them arrive as ConsString, not String
class StringFlatteningMethod extends BaseFunction
{
    private final NativeJavaMethod _method;

    private StringFlatteningMethod(NativeJavaMethod method)
    {
        super(method.getParentScope(), method.getPrototype());
        _method = method;
    }

    static Object wrap(Object member)
    {
        return member instanceof NativeJavaMethod method ? new StringFlatteningMethod(method) : member;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args)
    {
        flattenStrings(args);
        return _method.call(cx, scope, thisObj, args);
    }

    @Override
    public String getFunctionName()
    {
        return _method.getFunctionName();
    }

    @Override
    public String toString()
    {
        return _method.toString();
    }

    static void flattenStrings(Object[] args)
    {
        Set<Object> visited = null;
        for (Object arg : args)
        {
            if (arg instanceof NativeArray || arg instanceof NativeObject)
            {
                if (visited == null)
                    visited = Collections.newSetFromMap(new IdentityHashMap<>());
                flattenStrings((ScriptableObject) arg, visited);
            }
        }
    }

    private static void flattenStrings(ScriptableObject container, Set<Object> visited)
    {
        if (!visited.add(container))
            return;

        for (Object id : container.getIds())
        {
            Object value = id instanceof Integer i ? container.get(i, container) : container.get(id.toString(), container);
            if (value instanceof ConsString)
            {
                try
                {
                    if (id instanceof Integer i)
                        container.put(i, container, value.toString());
                    else
                        container.put(id.toString(), container, value.toString());
                }
                catch (RhinoException ignored)
                {
                    // read-only property
                }
            }
            else if (value instanceof NativeArray || value instanceof NativeObject)
            {
                flattenStrings((ScriptableObject) value, visited);
            }
        }
    }
}
