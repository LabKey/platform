/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.labkey.api.query.ValidationException;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Wrapper;
import org.mozilla.javascript.EvaluatorException;

import java.util.List;
import java.util.Map;


/**
 * A JavaScript interface over ValidationException for adding field and global errors to a single row.
 *
 * User: kevink
 * Date: Mar 9, 2011
 */
public class ScriptableErrors extends NativeJavaObject
{
    static final String CLASSNAME = "Errors";
    ValidationException errors;

    public static void init(Scriptable scope) throws NoSuchMethodException
    {
        BaseFunction ctor = new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope))
        {
            @Override
            public Scriptable construct(Context cx, Scriptable scope, Object[] args)
            {
                if (args.length > 1)
                    throw new EvaluatorException("ScriptableErrors() requires a ValidationException argument");
                return new ScriptableErrors(scope, args.length == 0 ? new ValidationException() : args[0]);
            }

            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args)
            {
                return construct(cx, scope, args);
            }
        };

        ScriptableObject.defineProperty(scope, CLASSNAME, ctor,
                ScriptableObject.DONTENUM | ScriptableObject.READONLY);
    }

    private ScriptableErrors(Scriptable scope, Object obj)
    {
        this.parent = scope;
        if (obj instanceof Wrapper)
            obj = ((Wrapper)obj).unwrap();

        if (obj instanceof ValidationException)
            this.javaObject = this.errors = (ValidationException)obj;
        else if (obj instanceof Map)
            this.javaObject = this.errors = new ValidationException((Map<String, Object>)obj);
        else if (obj instanceof Scriptable)
            this.javaObject = this.errors = createErrors((Scriptable)obj);
        else
            throw new EvaluatorException("Invalid argument to ScriptableErrors(): " + obj);

        this.staticType = this.errors.getClass();
        initMembers();
        initPrototype(scope);
    }

    public ScriptableErrors(Scriptable scope, ValidationException errors)
    {
        super(scope, errors, errors.getClass());
        this.errors = errors;
        initPrototype(scope);
    }

    protected void initPrototype(Scriptable scope)
    {
        Scriptable proto = ScriptableObject.getClassPrototype(scope, "Object");
        if (proto != null)
            this.setPrototype(proto);
    }

    public static ValidationException createErrors(Scriptable s)
    {

        if ("Object".equals(s.getClassName()))
        {
            Map<String, Object> map = ScriptUtils.scriptableToMap(s);
            return new ValidationException(map);
        }
        /*
        else
        {
            ValidationException errors = new ValidationException();
            Object[] ids = s.getIds();
            for (Object id : ids)
            {
                String field = null;
                Object value = null;
                if (id instanceof String) {
                    field = (String)id;
                    value = s.get((String)id, s);
                } else if (id instanceof Number) {
                    field = String.valueOf(id);
                    value = s.get(((Number)id).intValue(), s);
                }

                String message = (String)ScriptUtils.jsToJava(value, ScriptRuntime.StringClass);
                if (field == null)
                    errors.addGlobalError(message);
                else
                    errors.addFieldError(field, message);
            }
        }
        */

        throw new EvaluatorException("Invalid argument: " + s);
    }

    @Override
    public Object get(String name, Scriptable start)
    {
        if (ValidationException.ERROR_ROW_NUMBER_KEY.equals(name))
            return ScriptUtils.javaToJS(errors.getRowNumber(), getParentScope());
        else if (ValidationException.ERROR_SCHEMA_KEY.equals(name))
            return ScriptUtils.javaToJS(errors.getSchemaName(), getParentScope());
        else if (ValidationException.ERROR_QUERY_KEY.equals(name))
            return ScriptUtils.javaToJS(errors.getQueryName(), getParentScope());
        else if (ValidationException.ERROR_ROW_KEY.equals(name))
            return ScriptUtils.javaToJS(errors.getRow(), getParentScope());
        else
            return getInternal(name);
    }

    @Override
    public Object get(int index, Scriptable start)
    {
        return getInternal(String.valueOf(index));
    }

    private Object getInternal(String name)
    {
        if (null == name || "null".equals(name))
        {
            List globalErrors = this.errors.getGlobalErrorStrings();
            if (globalErrors == null)
                return Scriptable.NOT_FOUND;
            return ScriptUtils.javaToJS(globalErrors, getParentScope());
        }
        else
        {
            List<String> fieldErrors = this.errors.getFieldErrors(name);
            if (fieldErrors == null)
                return Scriptable.NOT_FOUND;
            return ScriptUtils.javaToJS(fieldErrors, getParentScope());
        }
    }

    @Override
    public boolean has(String name, Scriptable start)
    {
        if (null == name || "null".equals(name))
            return this.errors.hasGlobalErrors();

        return this.errors.hasFieldErrors(name);
    }

    @Override
    public boolean has(int index, Scriptable start)
    {
        return this.errors.hasFieldErrors(String.valueOf(index));
    }

    @Override
    public void put(String name, Scriptable start, Object value)
    {
        if (ValidationException.ERROR_ROW_NUMBER_KEY.equals(name))
            errors.setRowNumber(ScriptRuntime.toInt32(value));
        else if (ValidationException.ERROR_SCHEMA_KEY.equals(name))
            errors.setSchemaName((String)ScriptUtils.jsToJava(value, ScriptRuntime.StringClass));
        else if (ValidationException.ERROR_QUERY_KEY.equals(name))
            errors.setQueryName((String)ScriptUtils.jsToJava(value, ScriptRuntime.StringClass));
        else if (ValidationException.ERROR_ROW_KEY.equals(name))
            ;//errors.setRow((String)ScriptUtils.jsToJava(value, ScriptRuntime.ObjectClass));
        else
            putInternal(name, value);
    }

    @Override
    public void put(int index, Scriptable start, Object value)
    {
        putInternal(String.valueOf(index), value);
    }

    private void putInternal(String name, Object value)
    {
        // Assignment to a field errors, e.g. "errors.FieldName = [one, two]"
        if (errors.hasFieldErrors(name))
            errors.removeFieldErrors(name);

        value = ScriptUtils.jsToJava(value, ScriptRuntime.ObjectClass);
        if (value instanceof String)
            putError(name, (String)value);
        else if (value instanceof List)
            putErrors(name, (List)value);
        else
            throw new IllegalArgumentException("Can only put String or Array of String into errors");
    }

    private void putErrors(String name, List messages)
    {
        for (Object value : messages)
        {
            String message = (String)ScriptUtils.jsToJava(value, ScriptRuntime.StringClass);
            putError(name, message);
        }
    }

    private void putError(String name, String message)
    {
        if (null == name || "null".equals(name))
            this.errors.addGlobalError(message);
        else
            this.errors.addFieldError(name, message);
    }

    @Override
    public void delete(String name)
    {
        Context.throwAsScriptRuntimeEx(new UnsupportedOperationException());
    }

    @Override
    public void delete(int index)
    {
        Context.throwAsScriptRuntimeEx(new UnsupportedOperationException());
    }

    @Override
    public Object[] getIds()
    {
        // XXX: add 'null' id for global errors?
        return this.errors.getFields().toArray();
    }

    @Override
    public String toString()
    {
        return this.errors.toString();
    }

    @Override
    public Object getDefaultValue(Class<?> hint)
    {
        return toString();
    }

    @Override
    public Object unwrap()
    {
        return errors;
    }

    public String getClassName()
    {
        return CLASSNAME;
    }
}
