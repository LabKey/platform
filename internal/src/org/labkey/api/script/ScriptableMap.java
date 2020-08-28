/*
 *  Copyright 2006 Hannes Wallnoefer <hannes@helma.at>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.labkey.api.script;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * ScriptableMap is a wrapper for java.util.Map instances that allows developers
 * to interact with them as if it were a native JavaScript object.
 */
public class ScriptableMap extends NativeJavaObject {

    boolean reflect;
    Map<Object,Object> map;
    final static String CLASSNAME = "ScriptableMap";

    // Set up a custom constructor, for this class is somewhere between a host class and
    // a native wrapper, for which no standard constructor class exists
    public static void init(Scriptable scope)
    {
        BaseFunction ctor = new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {
            @Override
            public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
                boolean reflect = false;
                if (args.length > 2) {
                    throw new EvaluatorException("ScriptableMap() called with too many arguments");
                } if (args.length == 2) {
                    reflect = ScriptRuntime.toBoolean(args[1]);
                }
                return new ScriptableMap(scope, args.length == 0 ? null : args[0], reflect);
            }
            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                return construct(cx, scope, args);
            }
        };
        ScriptableObject.defineProperty(scope, CLASSNAME, ctor,
                ScriptableObject.DONTENUM | ScriptableObject.READONLY);
    }

    private ScriptableMap(Scriptable scope, Object obj, boolean reflect) {
        this.parent = scope;
        this.reflect = reflect;
        if (obj instanceof Wrapper) {
            obj = ((Wrapper) obj).unwrap();
        }
        if (obj instanceof Map) {
            this.map = (Map) obj;
        } else if (obj == null || obj == Undefined.instance) {
            this.map = new HashMap<>();
        } else if (obj instanceof Scriptable) {
            this.map = new HashMap<>();
            Scriptable s = (Scriptable) obj;
            Object[] ids = s.getIds();
            for (Object id: ids) {
                if (id instanceof String) {
                    map.put(id, s.get((String)id, s));
                } else if (id instanceof Number) {
                    map.put(id, s.get(((Number)id).intValue(), s));
                }
            }
        } else {
            throw new EvaluatorException("Invalid argument to ScriptableMap(): " + obj);
        }
        this.javaObject = this.map;
        this.staticType = this.map.getClass();
        initMembers();
        initPrototype(scope);

    }

    public ScriptableMap(Scriptable scope, Map<Object, Object> map) {
        super(scope, map, map.getClass());
        this.map = map;
        initPrototype(scope);
    }

    /**
     * Set the prototype to the Array prototype so we can use array methds such as
     * push, pop, shift, slice etc.
     * @param scope the global scope for looking up the Array constructor
     */
    protected void initPrototype(Scriptable scope) {
        Scriptable arrayProto = ScriptableObject.getClassPrototype(scope, "Object");
        if (arrayProto != null) {
            this.setPrototype(arrayProto);
        }
    }

    @Override
    public Object get(String name, Scriptable start) {
        if (map == null || (reflect && super.has(name, start))) {
            return super.get(name, start);
        }
        return getInternal(name);
    }

    @Override
    public Object get(int index, Scriptable start) {
        if (map == null) {
            return super.get(index, start);
        }
        return getInternal(Integer.valueOf(index));
    }

    private Object getInternal(Object key) {
        Object value = map.get(key);
        if (value == null) {
            return Scriptable.NOT_FOUND;
        }
        return ScriptUtils.javaToJS(value, getParentScope());
    }

    @Override
    public boolean has(String name, Scriptable start) {
        if (map == null || (reflect && super.has(name, start))) {
            return super.has(name, start);
        } else {
            return map.containsKey(name);
        }
    }

    @Override
    public boolean has(int index, Scriptable start) {
        if (map == null) {
            return super.has(index, start);
        } else {
            return map.containsKey(Integer.valueOf(index));
        }
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        if (map == null || (reflect && super.has(name, start))) {
            super.put(name, start, value);
        } else {
            putInternal(name, value);
        }
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        if (map == null) {
             super.put(index, start, value);
         } else {
             putInternal(Integer.valueOf(index), value);
        }
    }

    private void putInternal(Object key, Object value) {
        try {
            // kevink: use our converter
            map.put(key, ScriptUtils.jsToJava(value,
                    ScriptRuntime.ObjectClass));
        } catch (RuntimeException e) {
            Context.throwAsScriptRuntimeEx(e);
        }
    }

    @Override
    public void delete(String name) {
        if (map != null) {
            try {
                map.remove(name);
            } catch (RuntimeException e) {
                Context.throwAsScriptRuntimeEx(e);
            }
        } else {
            super.delete(name);
        }
    }

    @Override
    public void delete(int index) {
        if (map != null) {
            try {
                map.remove(Integer.valueOf(index));
            } catch (RuntimeException e) {
                Context.throwAsScriptRuntimeEx(e);
            }
        } else {
            super.delete(index);
        }
    }

    @Override
    public Object[] getIds() {
        if (map == null) {
            return super.getIds();
        } else {
            return map.keySet().toArray();
        }
    }

    public String toString() {
        if (map == null)
            return super.toString();
        return map.toString();
    }

    @Override
    public Object getDefaultValue(Class typeHint) {
        return toString();
    }

    @Override
    public Object unwrap() {
        return map;
    }

    public Map getMap() {
        return map;
    }

    @Override
    public String getClassName() {
        return CLASSNAME;
    }
}
