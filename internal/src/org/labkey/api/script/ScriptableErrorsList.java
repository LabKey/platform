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

import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Wrapper;
import org.mozilla.javascript.Scriptable;

import java.util.List;

/**
 * A JavaScript interface over BatchValidationException for adding errors to a batch of rows.
 *
 * User: kevink
 * Date: Mar 9, 2011
 */
public class ScriptableErrorsList extends ScriptableList
{
    static final String CLASSNAME = "ErrorsList";
    BatchValidationException errors;

    public static void init(Scriptable scope) throws NoSuchMethodException
    {
        BaseFunction ctor = new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope))
        {
            @Override
            public Scriptable construct(Context cx, Scriptable scope, Object[] args)
            {
                if (args.length > 1)
                    throw new EvaluatorException("ScriptableErrorsList() requires a BatchValidationException argument");
                return create(scope, args.length == 0 ? new BatchValidationException() : args[0]);
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

    private static ScriptableErrorsList create(Scriptable scope, Object obj)
    {
        BatchValidationException errors;
        if (obj instanceof Wrapper)
            obj = ((Wrapper)obj).unwrap();

        if (obj instanceof BatchValidationException)
            errors = (BatchValidationException)obj;
        else
            throw new EvaluatorException("Invalid argument to ScriptableErrorsList(): " + obj);

        return new ScriptableErrorsList(scope, errors);
    }

    public ScriptableErrorsList(Scriptable scope, BatchValidationException errors)
    {
        super(scope, (List)errors.getRowErrors());
        this.errors = errors;
        initPrototype(scope);
    }

    @Override
    public void put(int index, Scriptable start, Object value)
    {
        if (value instanceof Scriptable)
            value = ScriptableErrors.createErrors((Scriptable)value);

        if (value instanceof ValidationException)
        {
            // XXX: set schema/query on ValidationException if not set
            super.put(index, start, value);
        }
        else
            throw new EvaluatorException("Invalid argument to ScriptableErrorsList(): " + value);
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
