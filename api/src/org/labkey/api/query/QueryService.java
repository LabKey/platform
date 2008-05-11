package org.labkey.api.query;

import org.labkey.api.security.User;
import org.labkey.api.data.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartView;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Collection;
import java.sql.ResultSet;
import java.sql.SQLException;

abstract public class QueryService
{
    static private QueryService instance;
    static public QueryService get()
    {
        return instance;
    }
    static public void set(QueryService impl)
    {
        instance = impl;
    }

    abstract public Map<String, QueryDefinition> getQueryDefs(Container container, String schema);
    abstract public List<QueryDefinition> getQueryDefs(Container container);
    abstract public QueryDefinition createQueryDef(Container container, String schema, String name);
    abstract public QueryDefinition createQueryDefForTable(UserSchema schema, String tableName);
    abstract public QueryDefinition getQueryDef(Container container, String schema, String name);

    abstract public ActionURL urlQueryDesigner(Container container, String schema);
    abstract public ActionURL urlFor(Container container, QueryAction action, String schema, String queryName);
    abstract public UserSchema getUserSchema(User user, Container container, String schema);

    /**
     * Loops through the field keys and turns them into ColumnInfos based on the base table
     */
    abstract public Map<FieldKey, ColumnInfo> getColumns(TableInfo table, Collection<FieldKey> fields);

    abstract public List<DisplayColumn> getDisplayColumns(TableInfo table, Collection<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> fields);

    /**
     * Ensure that <code>columns</code> contains all of the columns necessary for <code>filter</code> and <code>sort</code>.
     * If <code>unresolvedColumns</code> is not null, then the Filter and Sort will be modified to remove any clauses that
     * involve unresolved columns, and <code>unresolvedColumns</code> will contain the names of the unresolved columns.
     */
    abstract public void ensureRequiredColumns(TableInfo table, List<ColumnInfo> columns, Filter filter, Sort sort, Set<String> unresolvedColumns);
    abstract public ResultSet select(TableInfo table, ColumnInfo[] columns, Filter filter, Sort sort) throws SQLException;

    abstract public String[] getAvailableWebPartNames(UserSchema schema);
    abstract public WebPartView[] getWebParts(UserSchema schema, String location); 

    abstract public Map<String, UserSchema> getDbUserSchemas(DefaultSchema folderSchema);

    abstract public List<FieldKey> getDefaultVisibleColumns(List<ColumnInfo> columns);
}
