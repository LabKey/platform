/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
import org.labkey.api.data.PHI;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.script.ScriptReference;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.script.ScriptException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Implements a trigger for table operations backed by JavaScript code.
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
    public String getName()
    {
        return _script.getPath().toString();
    }

    @Override
    public String getModuleName()
    {
        return _script.getModuleName();
    }

    @Override
    public String getSource()
    {
        return _script.getPath().toString();
    }

    @Override
    public List<TableInfo.TriggerMethod> getEvents()
    {
        User user = _table.getUserSchema() != null ? _table.getUserSchema().getUser() : null;

        List<TableInfo.TriggerMethod> events = new ArrayList<>(8);

        for (TableInfo.TriggerMethod m : TableInfo.TriggerMethod.values())
        {
            if (_hasFn(_container, user, m.name()))
                events.add(m);
        }

        return events;
    }

    @Override
    public boolean canStream()
    {
        // TODO: introspect the script to see if we can stream
        return false;
    }

    /**
     * To avoid leaking PHI through log files, avoid including the full row info in the error detail when any of the
     * columns in the target table is considered PHI
     */
    private Supplier<String> filterErrorDetailByPhi(TableInfo table, Supplier<String> errorDetail)
    {
        if (table.getMaxPhiLevel() != PHI.NotPHI)
        {
            return () -> null;
        }
        return errorDetail;
    }

    @Override
    public void init(TableInfo table, Container c, User user, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table, c, user, "init", errors, extraContext, () -> null, event.name().toLowerCase());
    }

    @Override
    public void complete(TableInfo table, Container c, User user, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table, c, user, "complete", errors, extraContext, () -> null, event.name().toLowerCase());
    }


    @Override
    public void beforeInsert(TableInfo table, Container c,
                             User user, @Nullable Map<String, Object> newRow,
                             ValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table,
                c,
                user,
                "beforeInsert",
                errors,
                extraContext,
                filterErrorDetailByPhi(table, () -> "New row data: " + newRow),
                newRow);
    }

    @Override
    public void beforeUpdate(TableInfo table, Container c,
                             User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                             ValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table, c, user, "beforeUpdate", errors, extraContext, filterErrorDetailByPhi(table, () -> "New row: " + newRow + ". Old row: "  + oldRow), newRow, oldRow);
    }

    @Override
    public void beforeDelete(TableInfo table, Container c,
                             User user, @Nullable Map<String, Object> oldRow,
                             ValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table, c, user, "beforeDelete", errors, extraContext,  filterErrorDetailByPhi(table, () -> "Old row: "  + oldRow), oldRow);
    }

    @Override
    public void afterInsert(TableInfo table, Container c,
                            User user, @Nullable Map<String, Object> newRow,
                            ValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table, c, user, "afterInsert", errors, extraContext,  filterErrorDetailByPhi(table, () -> "New row: " + newRow), newRow);
    }

    @Override
    public void afterUpdate(TableInfo table, Container c,
                            User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                            ValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table, c, user, "afterUpdate", errors, extraContext, filterErrorDetailByPhi(table, () -> "New row: " + newRow + ". Old row: "  + oldRow), newRow, oldRow);
    }

    @Override
    public void afterDelete(TableInfo table, Container c,
                            User user, @Nullable Map<String, Object> oldRow,
                            ValidationException errors, Map<String, Object> extraContext)
    {
        invokeTableScript(table, c, user, "afterDelete", errors, extraContext, filterErrorDetailByPhi(table, () -> "Old row: "  + oldRow), oldRow);
    }


    protected void invokeTableScript(TableInfo table, Container c, User user, String methodName, BatchValidationException errors, Map<String, Object> extraContext, Supplier<String> errorDetail, Object... args)
    {
        Object[] allArgs = Arrays.copyOf(args, args.length+1);
        allArgs[allArgs.length-1] = errors;
        Boolean success = _invokeTableScript(c, user, Boolean.class, methodName, extraContext, errorDetail, allArgs);
        if (success != null && !success)
            errors.addRowError(new ValidationException(methodName + " validation failed"));
        if (isConnectionClosed(table.getSchema().getScope()))
            errors.addRowError(new ValidationException("script error: " + methodName + " trigger closed the connection, possibly due to constraint violation"));
    }


    protected void invokeTableScript(TableInfo table, Container c, User user, String methodName, ValidationException errors, Map<String, Object> extraContext, Supplier<String> errorDetail, Object... args)
    {
        Object[] allArgs = Arrays.copyOf(args, args.length+1);
        allArgs[allArgs.length-1] = errors;
        Boolean success = _invokeTableScript(c, user, Boolean.class, methodName, extraContext, errorDetail, allArgs);
        if (success != null && !success)
            errors.addGlobalError(methodName + " validation failed");
        if (isConnectionClosed(table.getSchema().getScope()))
            errors.addGlobalError("script error: " + methodName + " trigger closed the connection, possibly due to constraint violation");
    }


    private boolean _hasFn(Container c, User user, String methodName)
    {
        return _try(c, user, null, (script) -> _script.hasFn(methodName));
    }

    private <T> T _invokeTableScript(Container c, User user, Class<T> resultType, String methodName, Map<String, Object> extraContext, Supplier<String> errorDetail, Object... args)
    {
        return _try(c, user, extraContext, (script) -> {
            if (_script.hasFn(methodName))
            {
                return _script.invokeFn(resultType, methodName, args);
            }

            return null;
        });
    }

    private <T> T _try(Container c, User user, Map<String, Object> extraContext, ScriptFn<T> fn)
    {
        try
        {
            ViewContext.StackResetter viewContextResetter = null;
            if (!HttpView.hasCurrentView())
            {
                // Push a view context if we don't already have one available. It will be pulled if labkey.js
                // is required by the trigger script being invoked, via the call to PageFlowUtil.jsInitObject() in
                // server/modules/core/resources/scripts/labkey/init.js
                viewContextResetter = ViewContext.pushMockViewContext(user, c, new ActionURL("dummy", "dummy", c));
            }
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

                return fn.apply(_script);
            }
            finally
            {
                if (viewContextResetter != null)
                {
                    viewContextResetter.close();
                }
            }
        }
        catch (NoSuchMethodException | ScriptException e)
        {
            String extraErrorMessage = errorDetail.get();
            throw UnexpectedException.wrap(e, "Script execution failed for " + methodName + "()" + (extraErrorMessage == null ? "" : " " + extraErrorMessage));
        }
    }

    interface ScriptFn<R>
    {
        R apply(ScriptReference scriptReference) throws NoSuchMethodException, ScriptException;
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

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptTrigger that = (ScriptTrigger) o;
        return Objects.equals(_container, that._container) &&
                Objects.equals(_table, that._table) &&
                Objects.equals(_script, that._script);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(_container, _table, _script);
    }
}
