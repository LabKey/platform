/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.data.triggers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.script.ScriptReference;
import org.labkey.api.util.UnexpectedException;

import javax.script.ScriptException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/21/15
 */
/* package */ class ScriptTrigger implements Trigger
{
    @NotNull protected final Container _container;
    @NotNull protected final TableInfo _table;
    @NotNull protected final ScriptReference _script;

    protected ScriptTrigger(@NotNull Container c, @NotNull TableInfo table, @NotNull ScriptReference script)
    {
        _container = c;
        _table = table;
        _script = script;
    }

    @Override
    public String getDebugName()
    {
        return _script.toString();
    }

    @Override
    public boolean canStream()
    {
        // TODO: introspect the script to see if we can stream
        return false;
    }

    @Override
    public void init(TableInfo table, Container c, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table, c, "init", errors, extraContext, event.name().toLowerCase());
    }

    @Override
    public void complete(TableInfo table, Container c, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table, c, "complete", errors, extraContext, event.name().toLowerCase());
    }


    @Override
    public void beforeInsert(TableInfo table, Container c,
                             @Nullable Map<String, Object> newRow,
                             ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        invokeTableScript(table, c, "beforeInsert", errors, extraContext, newRow);
    }

    @Override
    public void beforeUpdate(TableInfo table, Container c,
                             @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                             ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        invokeTableScript(table, c, "beforeUpdate", errors, extraContext, newRow, oldRow);
    }

    @Override
    public void beforeDelete(TableInfo table, Container c,
                             @Nullable Map<String, Object> oldRow,
                             ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        invokeTableScript(table, c, "beforeDelete", errors, extraContext, oldRow);
    }

    @Override
    public void afterInsert(TableInfo table, Container c,
                            @Nullable Map<String, Object> newRow,
                            ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        invokeTableScript(table, c, "afterInsert", errors, extraContext, newRow);
    }

    @Override
    public void afterUpdate(TableInfo table, Container c,
                            @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                            ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        invokeTableScript(table, c, "afterUpdate", errors, extraContext, newRow, oldRow);
    }

    @Override
    public void afterDelete(TableInfo table, Container c,
                            @Nullable Map<String, Object> oldRow,
                            ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        invokeTableScript(table, c, "afterDelete", errors, extraContext, oldRow);
    }


    protected void invokeTableScript(TableInfo table, Container c, String methodName, BatchValidationException errors, Map<String, Object> extraContext, Object... args)
    {
        Object[] allArgs = Arrays.copyOf(args, args.length+1);
        allArgs[allArgs.length-1] = errors;
        Boolean success = _invokeTableScript(c, Boolean.class, methodName, extraContext, allArgs);
        if (success != null && !success)
            errors.addRowError(new ValidationException(methodName + " validation failed"));
        if (isConnectionClosed(table.getSchema().getScope()))
            errors.addRowError(new ValidationException("script error: " + methodName + " trigger closed the connection, possibly due to constraint violation"));
    }


    protected void invokeTableScript(TableInfo table, Container c, String methodName, ValidationException errors, Map<String, Object> extraContext, Object... args)
    {
        Object[] allArgs = Arrays.copyOf(args, args.length+1);
        allArgs[allArgs.length-1] = errors;
        Boolean success = _invokeTableScript(c, Boolean.class, methodName, extraContext, allArgs);
        if (success != null && !success)
            errors.addGlobalError(methodName + " validation failed");
        if (isConnectionClosed(table.getSchema().getScope()))
            errors.addGlobalError("script error: " + methodName + " trigger closed the connection, possibly due to constraint violation");
    }


    private <T> T _invokeTableScript(Container c, Class<T> resultType, String methodName, Map<String, Object> extraContext, Object... args)
    {
        try
        {
            if (!_script.evaluated())
            {
                Map<String, Object> bindings = new HashMap<>();
                if (extraContext == null)
                    extraContext = new HashMap<>();
                bindings.put("extraContext", extraContext);
                bindings.put("schemaName", _table.getPublicSchemaName());
                bindings.put("tableName", _table.getPublicName());

                _script.eval(bindings);
            }

            if (_script.hasFn(methodName))
            {
                return _script.invokeFn(resultType, methodName, args);
            }
        }
        catch (NoSuchMethodException | ScriptException e)
        {
            throw new UnexpectedException(e);
        }

        return null;
    }


    private boolean isConnectionClosed(DbScope scope)
    {
        DbScope.Transaction tx = scope.getCurrentTransaction();
        if (null == tx)
            return false;
        try
        {
            return tx.getConnection().isClosed();
        }
        catch (SQLException x)
        {
            return true;
        }
    }
}
