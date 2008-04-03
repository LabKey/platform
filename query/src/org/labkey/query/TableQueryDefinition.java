package org.labkey.query;

import org.labkey.api.query.*;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.query.data.Query;

import java.util.List;

public class TableQueryDefinition extends QueryDefinitionImpl
{
    UserSchema _schema;
    String _tableName;
    TableInfo _table;

    public TableQueryDefinition(UserSchema schema, String tableName, TableInfo table)
    {
        super(schema.getContainer(), schema.getSchemaName(), tableName);
        _schema = schema;
        _tableName = tableName;
        _table = table;
        Query query = new Query(schema);
        query.setRootTable(FieldKey.fromParts(tableName));
        setSql(query.getQueryText());
    }

    public TableInfo getTable(String name, QuerySchema schema, List<QueryException> errors)
    {
        if (_table != null && schema == _schema && name == null)
        {
            return _table;
        }
        return super.getTable(name, schema, errors);
    }

    public boolean canEdit(User user)
    {
        return false;
    }
}
