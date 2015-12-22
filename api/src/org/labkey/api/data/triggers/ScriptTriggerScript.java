package org.labkey.api.data.triggers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.script.ScriptReference;
import org.labkey.api.util.UnexpectedException;

import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/21/15
 */
/* package */ class ScriptTriggerScript implements TriggerScript
{
    @NotNull protected final Container _container;
    @NotNull protected final TableInfo _table;
    @NotNull protected final ScriptReference _script;

    protected ScriptTriggerScript(@NotNull Container c, @NotNull TableInfo table, @NotNull ScriptReference script)
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
    public Boolean init(TableInfo table, Container c, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
        return invokeTableScript(c, Boolean.class, "init", extraContext, event.name().toLowerCase(), errors);
    }

    @Override
    public Boolean complete(TableInfo table, Container c, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
        return invokeTableScript(c, Boolean.class, "complete", extraContext, event.name().toLowerCase(), errors);
    }


    @Override
    public Boolean beforeInsert(TableInfo table, Container c,
                                @Nullable Map<String, Object> newRow,
                                ValidationException errors, Map<String, Object> extraContext)
    {
        return invokeTableScript(c, Boolean.class, "beforeInsert", extraContext, newRow, errors);
    }

    @Override
    public Boolean beforeUpdate(TableInfo table, Container c,
                                @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                                ValidationException errors, Map<String, Object> extraContext)
    {
        return invokeTableScript(c, Boolean.class, "beforeUpdate", extraContext, newRow, oldRow, errors);
    }

    @Override
    public Boolean beforeDelete(TableInfo table, Container c,
                                @Nullable Map<String, Object> oldRow,
                                ValidationException errors, Map<String, Object> extraContext)
    {
        return invokeTableScript(c, Boolean.class, "beforeDelete", extraContext, oldRow, errors);
    }

    @Override
    public Boolean afterInsert(TableInfo table, Container c,
                               @Nullable Map<String, Object> newRow,
                               ValidationException errors, Map<String, Object> extraContext)
    {
        return invokeTableScript(c, Boolean.class, "afterInsert", extraContext, newRow, errors);
    }

    @Override
    public Boolean afterUpdate(TableInfo table, Container c,
                               @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                               ValidationException errors, Map<String, Object> extraContext)
    {
        return invokeTableScript(c, Boolean.class, "afterUpdate", extraContext, newRow, oldRow, errors);
    }

    @Override
    public Boolean afterDelete(TableInfo table, Container c,
                               @Nullable Map<String, Object> oldRow,
                               ValidationException errors, Map<String, Object> extraContext)
    {
        return invokeTableScript(c, Boolean.class, "afterDelete", extraContext, oldRow, errors);
    }

    protected <T> T invokeTableScript(Container c, Class<T> resultType, String methodName, Map<String, Object> extraContext, Object... args)
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

}
