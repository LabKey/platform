/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.security.User;
import org.labkey.api.data.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartView;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;

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
    abstract public QuerySnapshotDefinition getSnapshotDef(Container container, String schema, String name);
    abstract public QuerySnapshotDefinition createQuerySnapshotDef(QueryDefinition queryDef, String name);
    abstract public boolean isQuerySnapshot(Container container, String schema, String name);
    abstract public List<QuerySnapshotDefinition> getQuerySnapshotDefs(Container container, String schema);

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
    abstract public ResultSet select(TableInfo table, List<ColumnInfo> columns, Filter filter, Sort sort) throws SQLException;

    abstract public String[] getAvailableWebPartNames(UserSchema schema);
    abstract public WebPartView[] getWebParts(UserSchema schema, String location); 

    abstract public Map<String, UserSchema> getDbUserSchemas(DefaultSchema folderSchema);

    abstract public List<FieldKey> getDefaultVisibleColumns(List<ColumnInfo> columns);
}
