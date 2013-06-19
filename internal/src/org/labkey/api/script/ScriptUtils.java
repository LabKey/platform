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

import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.mozilla.javascript.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A collection of Rhino utility methods.
 */
public class ScriptUtils {

    /**
     * Coerce/wrap a java object to a JS object, and mask Lists and Maps
     * as native JS objects.
     * @param obj the object to coerce/wrap
     * @param scope the scope
     * @return the wrapped/masked java object
     */
    public static Object javaToJS(Object obj, Scriptable scope) {
        if (obj instanceof Scriptable) {
            if (obj instanceof ScriptableObject
                    && ((Scriptable) obj).getParentScope() == null
                    && ((Scriptable) obj).getPrototype() == null) {
                ScriptRuntime.setObjectProtoAndParent((ScriptableObject) obj, scope);
            }
            return obj;
        } else if (obj instanceof List) {
            return new ScriptableList(scope, (List) obj);
        } else if (obj instanceof Map) {
            return new ScriptableMap(scope, (Map) obj);
        } else if (obj instanceof ValidationException) {
            return new ScriptableErrors(scope, (ValidationException) obj);
        } else if (obj instanceof BatchValidationException) {
            return new ScriptableErrorsList(scope, (BatchValidationException) obj);
//        } else if (obj instanceof Object[]) {
//            Object[] arr = (Object[])obj;
//            int len = arr.length;
//            Object[] wrapped = new Object[len];
//            Class<?> componentType = arr.getClass().getComponentType();
//            Context cx = Context.getCurrentContext();
//            WrapFactory wrapper = cx.getWrapFactory();
//            for (int i = 0; i < len; i++)
//                wrapped[i] = wrapper.wrap(cx, scope, arr[i], componentType);
//            NativeArray jsArray = new NativeArray(wrapped);
//            jsArray.setPrototype(ScriptableObject.getClassPrototype(scope, "Array"));
//            jsArray.setParentScope(scope);
//            return jsArray;
        } else {
            return Context.javaToJS(obj, scope);
        }
    }

    public static Object jsToJava(Object jsObj, Class<?> desiredType) {
        Object convertedValue = Context.jsToJava(jsObj, desiredType);
        if (convertedValue instanceof Scriptable)
        {
            return scriptableToJava((Scriptable)convertedValue);
        }
        return convertedValue;
    }

    public static Object scriptableToJava(Scriptable jsObj)
    {
        if (ScriptRuntime.isArrayObject(jsObj))
        {
            // unpack native java array
            final Object[] array = Context.getCurrentContext().getElements(jsObj);
            final Object[] ids = jsObj.getIds();
            if (ids.length == 0 || array.length == ids.length)
            {
                // Array is an associative array if the length is 0 or is the same length as the script object.
                ArrayList list = new ArrayList(array.length);
                for (int i = 0; i < array.length; i++)
                    list.add(jsToJava(array[i], ScriptRuntime.ObjectClass));
                return list;
            }
            else
            {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Object id : ids)
                {
                    String key = id.toString();
                    Object value = jsObj.get(key, jsObj);
                    if (value == UniqueTag.NOT_FOUND)
                    {
                        // try again with an integer index
                        value = jsObj.get(Integer.parseInt(key), jsObj);
                    }
                    map.put(key, jsToJava(value, ScriptRuntime.ObjectClass));
                }
                return map;
            }
        }
        else
        {
            final String jsClass = jsObj.getClassName();

            // Plain 'ol JavaScript Object.  Convert to a map.
            if ("Object".equals(jsClass))
            {
                return scriptableToMap(jsObj);
            }
            else if ("Date".equals(jsClass))
            {
                // Issue 10615: Context.jsToJava doesn't convert NativeDate to j.u.Date when desired type is Object.class as is requested by ScriptableMap and ScriptableList.
                return Context.jsToJava(jsObj, Date.class);
            }
        }

        return jsObj;
    }

    public static Map<String, Object> scriptableToMap(Scriptable jsObj)
    {
        final Object[] ids = jsObj.getIds();
        Map<String, Object> map = new LinkedHashMap<>();
        for (Object id : ids)
        {
            String key = id.toString();
            Object value = jsObj.get(key, jsObj);
            map.put(key, jsToJava(value, ScriptRuntime.ObjectClass));
        }
        return map;
    }

    /**
     * Return a class prototype, or the object prototype if the class
     * is not defined.
     * @param scope the scope
     * @param className the class name
     * @return the class or object prototype
     */
    public static Scriptable getClassOrObjectProto(Scriptable scope, String className) {
        Scriptable proto = ScriptableObject.getClassPrototype(scope, className);
        if (proto == null) {
            proto = ScriptableObject.getObjectPrototype(scope);
        }
        return proto;
    }

    /**
     * Make sure that number of arguments is valid.
     * @param args the argument array
     * @param min the minimum number of arguments
     * @param max the maximum number of arguments
     * @throws IllegalArgumentException if the number of arguments is not valid
     */
    public static void checkArguments(Object[] args, int min, int max) {
        if (min > -1 && args.length < min)
            throw new IllegalArgumentException();
        if (max > -1 && args.length > max)
            throw new IllegalArgumentException();
    }

    /**
     * Get an argument as ScriptableObject
     * @param args the argument array
     * @param pos the position of the requested argument
     * @return the argument as ScriptableObject
     * @throws IllegalArgumentException if the argument can't be converted to a map
     */
    public static ScriptableObject getScriptableArgument(Object[] args, int pos, boolean allowNull)
            throws IllegalArgumentException {
        if (pos >= args.length || args[pos] == null || args[pos] == Undefined.instance) {
            if (allowNull) return null;
            throw new IllegalArgumentException("Argument " + (pos + 1) + " must not be null");
        } if (args[pos] instanceof ScriptableObject) {
            return (ScriptableObject) args[pos];
        }
        throw new IllegalArgumentException("Can't convert to ScriptableObject: " + args[pos]);
    }

    /**
     * Get an argument as string
     * @param args the argument array
     * @param pos the position of the requested argument
     * @return the argument as string
     */
    public static String getStringArgument(Object[] args, int pos, boolean allowNull) {
        if (pos >= args.length || args[pos] == null || args[pos] == Undefined.instance) {
            if (allowNull) return null;
            throw new IllegalArgumentException("Argument " + (pos + 1) + " must not be null");
        }
        return ScriptRuntime.toString(args[pos].toString());
    }

    /**
     * Get an argument as Map
     * @param args the argument array
     * @param pos the position of the requested argument
     * @return the argument as map
     * @throws IllegalArgumentException if the argument can't be converted to a map
     */
    public static Map getMapArgument(Object[] args, int pos, boolean allowNull)
            throws IllegalArgumentException {
        if (pos >= args.length || args[pos] == null || args[pos] == Undefined.instance) {
            if (allowNull) return null;
            throw new IllegalArgumentException("Argument " + (pos + 1) + " must not be null");
        } if (args[pos] instanceof Map) {
            return (Map) args[pos];
        }
        throw new IllegalArgumentException("Can't convert to java.util.Map: " + args[pos]);
    }

    /**
     * Get an argument as object
     * @param args the argument array
     * @param pos the position of the requested argument
     * @return the argument as object
     */
    public static Object getObjectArgument(Object[] args, int pos, boolean allowNull) {
        if (pos >= args.length || args[pos] == null || args[pos] == Undefined.instance) {
            if (allowNull) return null;
            throw new IllegalArgumentException("Argument " + (pos + 1) + " must not be null");
        }
        return args[pos];
    }

    /**
     * Try to convert an object to an int value, returning the default value if conversion fails.
     * @param obj the value
     * @param defaultValue the default value
     * @return the converted value
     */
    public static int toInt(Object obj, int defaultValue) {
        double d = ScriptRuntime.toNumber(obj);
        if (d == ScriptRuntime.NaN || (int)d != d) {
            return defaultValue;
        }
        return (int) d;
    }

}
