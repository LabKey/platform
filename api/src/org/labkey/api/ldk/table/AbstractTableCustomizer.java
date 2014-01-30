package org.labkey.api.ldk.table;

import org.apache.log4j.Logger;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.DataSetTable;

import java.util.HashMap;
import java.util.Map;

/**
 * User: bimber
 * Date: 1/8/14
 * Time: 11:06 AM
 */
abstract public class AbstractTableCustomizer implements TableCustomizer
{
    protected static final Logger _log = Logger.getLogger(AbstractTableCustomizer.class);
    private Map<String, UserSchema> _userSchemas = new HashMap<>();
    private Map<String, TableInfo> _tableInfos = new HashMap<>();


    public UserSchema getUserSchema(AbstractTableInfo ti, String name)
    {
        Container targetContainer = ti.getUserSchema().getContainer();
        return getUserSchema(ti, name, targetContainer);
    }

    public UserSchema getUserSchema(AbstractTableInfo ti, String name, Container targetContainer)
    {
        assert targetContainer != null : "No container provided";

        String key = targetContainer.getEntityId() + "||" + name;
        if (_userSchemas.containsKey(key))
            return _userSchemas.get(key);

        UserSchema originalUserSchema = ti.getUserSchema();
        if (originalUserSchema != null)
        {
            //cache the original UserSchema anyway
            String origTableKey = originalUserSchema.getContainer().getEntityId() + "||" + originalUserSchema.getName();
            _userSchemas.put(origTableKey, originalUserSchema);

            if (origTableKey.equalsIgnoreCase(key))
                return originalUserSchema;

            //if container differs, look up the schema
            UserSchema us2 = QueryService.get().getUserSchema(originalUserSchema.getUser(), targetContainer, name);
            if (us2 != null)
                _userSchemas.put(key, us2);

            return us2;
        }

        return null;
    }

    public TableInfo getTableInfo(AbstractTableInfo ti, String schemaName, String queryName)
    {
        return getTableInfo(ti, schemaName, queryName, ti.getUserSchema().getContainer());
    }

    public TableInfo getTableInfo(AbstractTableInfo ti, String schemaName, String queryName, Container targetContainer)
    {
        assert targetContainer != null : "No container provided";

        String key = targetContainer.getEntityId() + "||" + schemaName + "||" + queryName;
        if (_tableInfos.containsKey(key))
            return _tableInfos.get(key);

        UserSchema us = getUserSchema(ti, schemaName, targetContainer);
        if (us == null)
            return null;

        TableInfo table = us.getTable(queryName);
        _tableInfos.put(key, table);

        return table;
    }

    protected String getChr(TableInfo ti)
    {
        return ti.getSqlDialect().isPostgreSQL() ? "chr" : "char";
    }

    protected boolean matches(TableInfo ti, String schema, String query)
    {
        if (ti instanceof DataSetTable)
            return ti.getSchema().getName().equalsIgnoreCase(schema) && (ti.getName().equalsIgnoreCase(query) || ti.getTitle().equalsIgnoreCase(query));
        else
            return ti.getSchema().getName().equalsIgnoreCase(schema) && ti.getName().equalsIgnoreCase(query);
    }
}
