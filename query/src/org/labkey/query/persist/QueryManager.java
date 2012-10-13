/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

package org.labkey.query.persist;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.query.ExternalSchema;
import org.labkey.query.ExternalSchemaDocumentProvider;

import java.beans.PropertyChangeEvent;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;


public class QueryManager
{
    private static final Logger _log = Logger.getLogger(QueryManager.class);
    private static final QueryManager instance = new QueryManager();
    private static final String SCHEMA_NAME = "query";
    private static final List<QueryService.QueryListener> _listeners = new CopyOnWriteArrayList<QueryService.QueryListener>();

    public static final int FLAG_INHERITABLE = 0x01;
    public static final int FLAG_HIDDEN = 0x02;
    public static final int FLAG_SNAPSHOT = 0x04;

    public static QueryManager get()
    {
        return instance;
    }

    /**
     * @param customQuery whether to look for custom queries or modified metadata on built-in tables
     */
    public QueryDef getQueryDef(Container container, String schema, String name, boolean customQuery)
    {
        // Metadata for built-in tables is stored with a NULL value for the SQL
        QueryDef.Key key = new QueryDef.Key(container, customQuery);
        key.setSchema(schema);
        key.setQueryName(name);

        return key.selectObject();
    }

    /**
     * @param customQuery whether to look for custom queries or modified metadata on built-in tables
     */
    public QueryDef[] getQueryDefs(Container container, String schema, boolean inheritableOnly, boolean includeSnapshots, boolean customQuery)
    {
        // Metadata for built-in tables is stored with a NULL value for the SQL
        QueryDef.Key key = new QueryDef.Key(container, customQuery);
        if (schema != null)
        {
            key.setSchema(schema);
        }

        int mask = 0;
        int value = 0;

        if (inheritableOnly)
        {
            mask |= FLAG_INHERITABLE;
            value |= FLAG_INHERITABLE;
        }

        if (!includeSnapshots)
            mask |= FLAG_SNAPSHOT;

        if (mask != 0 || value != 0)
            key.setFlagMask(mask, value);

        return key.select();
    }

    public QuerySnapshotDef[] getQuerySnapshots(Container container, String schema)
    {
        QuerySnapshotDef.Key key = new QuerySnapshotDef.Key(container);
        if (schema != null)
        {
            key.setSchema(schema);
        }
        return key.select();
    }

    public QueryDef insert(User user, QueryDef queryDef) throws SQLException
    {
        return Table.insert(user, getTableInfoQueryDef(), queryDef);
    }

    public QueryDef update(User user, QueryDef queryDef) throws SQLException
    {
        return Table.update(user, getTableInfoQueryDef(), queryDef, queryDef.getQueryDefId());
    }

    public void delete(User user, QueryDef queryDef) throws SQLException
    {
        Table.delete(getTableInfoQueryDef(), queryDef.getQueryDefId());
    }

    public void delete(User user, QuerySnapshotDef querySnapshotDef) throws SQLException
    {
        Table.delete(getTableInfoQuerySnapshotDef(), querySnapshotDef.getRowId());
        if (querySnapshotDef.getQueryDefId() != null)
            Table.delete(getTableInfoQueryDef(), querySnapshotDef.getQueryDefId());
    }

    public QuerySnapshotDef insert(User user, QueryDef queryDef, QuerySnapshotDef snapshotDef) throws SQLException
    {
        if (queryDef != null && snapshotDef.getQueryTableName() == null)
        {
            QueryDef def = insert(user, queryDef);
            snapshotDef.setQueryDefId(def.getQueryDefId());
        }
        return Table.insert(user, getTableInfoQuerySnapshotDef(), snapshotDef);
    }

    public QuerySnapshotDef update(User user, QueryDef queryDef, QuerySnapshotDef snapshotDef) throws SQLException
    {
        if (queryDef != null && snapshotDef.getQueryTableName() == null)
            update(user, queryDef);
        return Table.update(user, getTableInfoQuerySnapshotDef(), snapshotDef, snapshotDef.getRowId());
    }

    public QuerySnapshotDef getQuerySnapshotDef(int id)
    {
        return Table.selectObject(getTableInfoQuerySnapshotDef(), id, QuerySnapshotDef.class);
    }

    public CstmView getCustomView(int id) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(getTableInfoCustomView().getColumn("CustomViewId"), id);
        CstmView view = Table.selectObject(getTableInfoCustomView(), id, CstmView.class);
        _log.debug(view);
        return view;
    }

    public List<CstmView> getAllCstmViews(Container container, String schemaName, String queryName, @Nullable User user, boolean inheritable)
    {
        List<CstmView> views = new ArrayList<CstmView>();

        views.addAll(Arrays.asList(getCstmViews(container, schemaName, queryName, null, user, false)));
        if (user != null)
            views.addAll(Arrays.asList(getCstmViews(container, schemaName, queryName, null, null, false)));

        if (!inheritable)
            return views;

        Container containerCur = container == null ? null : container.getParent();
        while (containerCur != null && !containerCur.isRoot())
        {
            views.addAll(Arrays.asList(getCstmViews(containerCur, schemaName, queryName, null, user, true)));
            if (user != null)
                views.addAll(Arrays.asList(getCstmViews(containerCur, schemaName, queryName, null, null, true)));
            containerCur = containerCur.getParent();
        }

        // look in the shared project
        views.addAll(Arrays.asList(getCstmViews(ContainerManager.getSharedContainer(), schemaName, queryName, null, user, true)));
        if (user != null)
            views.addAll(Arrays.asList(getCstmViews(ContainerManager.getSharedContainer(), schemaName, queryName, null, null, true)));

        return views;
    }

    public CstmView[] getCstmViews(Container container, @Nullable String schemaName, @Nullable String queryName, @Nullable String viewName, @Nullable User user, boolean inheritableOnly)
    {
        CstmView.Key key = new CstmView.Key(container);
        if (schemaName != null)
            key.setSchema(schemaName);
        if (queryName != null)
            key.setQueryName(queryName);
        if (viewName != null)
            key.setName(viewName);
        if (inheritableOnly)
        {
            key.setFlagMask(FLAG_INHERITABLE, FLAG_INHERITABLE);
        }

        key.setUser(user);
        return key.select();
    }

    public CstmView update(User user, CstmView view) throws SQLException
    {
        return Table.update(user, getTableInfoCustomView(), view, view.getCustomViewId());
    }

    public CstmView insert(User user, CstmView view) throws SQLException
    {
        return Table.insert(user, getTableInfoCustomView(), view);
    }

    public void delete(@Nullable User user, CstmView view) throws SQLException
    {
        Table.delete(getTableInfoCustomView(), view.getCustomViewId());
    }


    public void updateViewsAfterRename(@NotNull Container c, @NotNull String schema,
            @NotNull String oldQueryName, @NotNull String newQueryName)
    {
        try
        {
            Table.execute(getDbSchema(), "UPDATE " + getTableInfoCustomView() + " SET queryname=? WHERE container=? AND \"schema\"=? AND queryname=?",
                    newQueryName, c, schema, oldQueryName);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    
    public boolean canInherit(int flag)
    {
        return (flag & FLAG_INHERITABLE) != 0;
    }

    public ExternalSchemaDef getExternalSchemaDef(int id)
    {
        return Table.selectObject(getTableInfoExternalSchema(), id, ExternalSchemaDef.class);
    }

    public ExternalSchemaDef[] getExternalSchemaDefs(@Nullable Container container)
    {
        ExternalSchemaDef.Key key = new ExternalSchemaDef.Key(container);
        return key.select();
    }

    public ExternalSchemaDef getExternalSchemaDef(Container container, String userSchemaName)
    {
        if (userSchemaName == null)
            return null;

        ExternalSchemaDef.Key key = new ExternalSchemaDef.Key(container);
        key.setUserSchemaName(userSchemaName);
        return key.selectObject();
    }

    public ExternalSchemaDef insert(User user, ExternalSchemaDef def) throws Exception
    {
        ExternalSchemaDef ret = Table.insert(user, getTableInfoExternalSchema(), def);
        ExternalSchemaDocumentProvider.getInstance().enumerateDocuments(null, def.lookupContainer(), null);
        return ret;
    }

    public ExternalSchemaDef update(User user, ExternalSchemaDef def) throws Exception
    {
        ExternalSchemaDef ret = Table.update(user, getTableInfoExternalSchema(), def, def.getExternalSchemaId());
        ExternalSchemaDocumentProvider.getInstance().enumerateDocuments(null, def.lookupContainer(), null);
        return ret;
    }

    public void delete(User user, ExternalSchemaDef def) throws Exception
    {
        Table.delete(getTableInfoExternalSchema(), def.getExternalSchemaId());
        ExternalSchemaDocumentProvider.getInstance().enumerateDocuments(null, def.lookupContainer(), null);
    }


    public void reloadAllExternalSchemas(Container c)
    {
        ExternalSchemaDef[] defs = getExternalSchemaDefs(c);

        for (ExternalSchemaDef def : defs)
            reloadExternalSchema(def);
    }


    public void reloadExternalSchema(ExternalSchemaDef def)
    {
        ExternalSchema.uncache(def);
    }


    public int setCanInherit(int flag, boolean f)
    {
        if (f)
        {
            return flag | FLAG_INHERITABLE;
        }
        else
        {
            return flag & ~FLAG_INHERITABLE;
        }
    }

    public boolean isHidden(int flag)
    {
        return (flag & FLAG_HIDDEN) != 0;
    }

    public int setIsHidden(int flag, boolean f)
    {
        if (f)
        {
            return flag | FLAG_HIDDEN;
        }
        else
        {
            return flag & ~FLAG_HIDDEN;
        }
    }

    public boolean isSnapshot(int flag)
    {
        return (flag & FLAG_SNAPSHOT) != 0;
    }

    public int setIsSnapshot(int flag, boolean f)
    {
        if (f)
        {
            return flag | FLAG_SNAPSHOT;
        }
        else
        {
            return flag & ~FLAG_SNAPSHOT;
        }
    }
    
    public String getDbSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getDbSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public TableInfo getTableInfoQueryDef()
    {
        return getDbSchema().getTable("QueryDef");
    }

    public TableInfo getTableInfoQuerySnapshotDef()
    {
        return getDbSchema().getTable("QuerySnapshotDef");
    }

    public TableInfo getTableInfoCustomView()
    {
        return getDbSchema().getTable("CustomView");
    }

    public TableInfo getTableInfoExternalSchema()
    {
        return getDbSchema().getTable("ExternalSchema");
    }

    public void containerDeleted(Container c) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Container", c.getId());
        Table.delete(getTableInfoQuerySnapshotDef(), filter);
        Table.delete(getTableInfoCustomView(), filter);
        Table.delete(getTableInfoQueryDef(), filter);
        Table.delete(getTableInfoExternalSchema(), filter);
    }

    public void addQueryListener(QueryService.QueryListener listener)
    {
        _listeners.add(listener);
    }

    public void fireViewChanged(CustomView view)
    {
        for (QueryService.QueryListener l : _listeners)
            l.viewChanged(view);
    }

    public void fireViewDeleted(CustomView view)
    {
        for (QueryService.QueryListener l : _listeners)
            l.viewDeleted(view);
    }

    static public final ContainerManager.ContainerListener CONTAINER_LISTENER = new ContainerManager.ContainerListener()
    {
        public void containerCreated(Container c, User user)
        {

        }

        @Override
        public void containerMoved(Container c, Container oldParent, User user)
        {
            
        }

        public void containerDeleted(Container c, User user)
        {
            try
            {
                QueryManager.get().containerDeleted(c);
            }
            catch (SQLException e)
            {
                _log.error("Error", e);
            }
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
        }

    };

    public void validateQuery(SchemaKey schemaPath, String queryName, User user, Container container) throws SQLException, QueryParseException
    {
        validateQuery(schemaPath, queryName, user, container, true);
    }

    public void validateQuery(SchemaKey schemaPath, String queryName, User user, Container container, boolean testAllColumns) throws SQLException, QueryParseException
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, schemaPath);
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + schemaPath.toDisplayString() + "'!");

        TableInfo table = schema.getTable(queryName);
        if (null == table)
            throw new IllegalArgumentException("The query '" + queryName + "' was not found in the schema '" + schemaPath.toDisplayString() + "'!");

        validateQuery(table, testAllColumns);
    }

    public void validateQuery(TableInfo table, boolean testAllColumns) throws SQLException, QueryParseException
    {
        Collection<QueryService.ParameterDecl> params = table.getNamedParameters();
        Map<String,Object> parameters = new HashMap<String,Object>();
        for (QueryService.ParameterDecl p : params)
        {
            if (!p.isRequired())
                continue;
            parameters.put(p.getName(), null);
        }

        //get the set of columns
        List<ColumnInfo> cols = null;
        if (testAllColumns)
        {
            List<FieldKey> defVisCols = table.getDefaultVisibleColumns();
            Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(table, defVisCols);
            cols = new ArrayList<ColumnInfo>(colMap.values());
        }

        //try to execute it with a rowcount of 0 (will throw SQLException to client if it fails
        Table.TableResultSet results = null;
        try
        {
            //use selectForDisplay to mimic the behavior one would get in the UI
            if (testAllColumns)
                results = Table.selectForDisplay(table, Table.ALL_COLUMNS, parameters, null, null, Table.NO_ROWS, Table.NO_OFFSET);
            else
                results = Table.selectForDisplay(table, cols, parameters, null, null, Table.NO_ROWS, Table.NO_OFFSET);
        }
        finally
        {
            ResultSetUtil.close(results);
        }
    }

    /**
     * Experimental.  The goal is to provide a more thorough validation of query metadata, including warnings of potentially
     * invalid conditions, like autoincrement columns set userEditable=true.
     */
    public Set<String> validateQueryMetadata(String schemaName, String queryName, User user, Container container) throws SQLException, QueryParseException
    {
        UserSchema schema = (UserSchema) DefaultSchema.get(user, container).getSchema(schemaName);
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + schemaName + "'!");

        TableInfo table = schema.getTable(queryName);
        if (null == table)
            throw new IllegalArgumentException("The query '" + queryName + "' was not found in the schema '" + schemaName + "'!");

        //validate foreign keys and other metadata warnings
        Set<String> queryErrors = new HashSet<String>();
        Set<ColumnInfo> columns = new HashSet<ColumnInfo>();
        columns.addAll(table.getColumns());
        columns.addAll(QueryService.get().getColumns(table, table.getDefaultVisibleColumns()).values());

        for (ColumnInfo col : columns)
        {
            queryErrors.addAll(validateColumn(col, user, container, table));
        }

        return queryErrors;
    }

    /**
     * Experimental.  See validateQueryMetadata()
     */
    public Set<String> validateColumn(ColumnInfo col, User user, Container container, @Nullable TableInfo parentTable)
    {
        Set<String> queryErrors = new HashSet<String>();
        if(parentTable == null)
            parentTable = col.getParentTable();

        String publicSchema = col.getParentTable().getPublicSchemaName() != null ? col.getParentTable().getPublicSchemaName() : col.getParentTable().getSchema().toString();
        String publicQuery = col.getParentTable().getPublicName() != null ? col.getParentTable().getPublicName() : col.getParentTable().getName();
        String errorBase = "for column '" + col.getFieldKey() + "' in " + publicSchema + "." + publicQuery + ": ";

        queryErrors.addAll(validateFk(col, user, container, parentTable));

        List<String> specialCols = new ArrayList<String>();
        specialCols.add("container");
        specialCols.add("created");
        specialCols.add("createdby");
        specialCols.add("modified");
        specialCols.add("modifiedby");

        if(specialCols.contains(col.getName()))
        {
            if(col.isUserEditable())
                queryErrors.add("INFO: " + errorBase + " column is user editable, which is not expected based on its name");
            if(col.isShownInInsertView())
                queryErrors.add("INFO: " + errorBase + " column has shownInInsertView set to true, which is not expected based on its name");
            if(col.isShownInUpdateView())
                queryErrors.add("INFO: " + errorBase + " column has shownInUpdateView set to true, which is not expected based on its name");
        }

        if(col.isAutoIncrement() && col.isUserEditable())
            queryErrors.add("ERROR: " + errorBase + " column is autoIncrement, but has userEditable set to true");
        if(col.isAutoIncrement() && col.isShownInInsertView())
            queryErrors.add("WARNING: " + errorBase + " column is autoIncrement, but has shownInInsertView set to true");
        if(col.isAutoIncrement() && col.isShownInUpdateView())
            queryErrors.add("WARNING: " + errorBase + " column is autoIncrement, but has shownInUpdateView set to true");

//        if(col.isShownInInsertView() && !col.isUserEditable())
//            queryErrors.add("INFO: " + errorBase + " has shownInInsertView=true, but it is not userEditable");
//        if(col.isShownInUpdateView() && !col.isUserEditable())
//            queryErrors.add("INFO: " + errorBase + " has shownInUpdateView=true, but it is not userEditable");

        if(col.isShownInInsertView() && col.isHidden())
            queryErrors.add("INFO: " + errorBase + " has shownInInsertView=true, but it is hidden");
        if(col.isShownInUpdateView() && col.isHidden())
            queryErrors.add("INFO: " + errorBase + " has shownInUpdateView=true, but it is hidden");

        try
        {
            if(col.getDisplayWidth() != null && Integer.parseInt(col.getDisplayWidth()) > 200 && !"textarea".equalsIgnoreCase(col.getInputType()))
            {
                if (col.isUserEditable() && col.getJdbcType() != null && col.getJdbcType().getJavaClass() == String.class)
                    queryErrors.add("INFO: " + errorBase + " column has a displayWidth > 200, but does not use a textarea as the inputType");
            }
        }
        catch (NumberFormatException e)
        {
            queryErrors.add("INFO: " + errorBase + " column has a blank value for displayWidth: '" + col.getDisplayWidth() + "'");
        }
        return queryErrors;
    }

    /**
     * Experimental.  See validateQueryMetadata()
     */
    public Set<String> validateFk(ColumnInfo col, User user, Container container, TableInfo parentTable)
    {
        Set<String> queryErrors = new HashSet<String>();

        //NOTE: this is the same code that writes JSON to the client
        JSONObject o = JsonWriter.getLookupInfo(col, false);
        if (o == null)
            return queryErrors;

        boolean isPublic = o.getBoolean("isPublic");
        SchemaKey schemaPath = SchemaKey.fromString(o.getString("schemaName"));
        String queryName = o.getString("queryName");
        String displayColumn = o.getString("displayColumn");
        String keyColumn = o.getString("keyColumn");
        String containerPath = o.getString("containerPath");
        String errorBase = "Column '" + col.getName() + "' in " + schemaPath.toDisplayString() + "." + queryName + ": ";

        Container lookupContainer = containerPath == null ? container : ContainerManager.getForPath(containerPath);
        if (lookupContainer == null)
        {
            queryErrors.add("ERROR: " + errorBase + " Unable to find container" + containerPath);
        }


        //String publicSchema = col.getParentTable().getPublicSchemaName() != null ? col.getParentTable().getPublicSchemaName() : col.getParentTable().getSchema().toString();
        //String publicQuery = col.getParentTable().getPublicName() != null ? col.getParentTable().getPublicName() : col.getParentTable().getName();
        if (o == null || col.getFk() == null)
            return queryErrors;

        if (!isPublic)
        {
            queryErrors.add("INFO: " + errorBase + " has a lookup to a non-public table: " + schemaPath.toDisplayString() + "." + queryName);
            return queryErrors;
        }

        UserSchema userSchema = QueryService.get().getUserSchema(user, lookupContainer, schemaPath);
        if (userSchema == null)
        {
            queryErrors.add("ERROR: " + errorBase + " unable to find the user schema: " + schemaPath.toDisplayString());
            return queryErrors;
        }

        TableInfo fkTable = userSchema.getTable(queryName);
        if(fkTable == null)
        {
            queryErrors.add("ERROR: " + errorBase + " has a lookup to a table that does not exist: " + schemaPath.toDisplayString() + "." + queryName);
            return queryErrors;
        }

        //a FK can have a table non-visible to the client, so long as public is set to false
        if (fkTable.isPublic()){
            String fkt = schemaPath.toDisplayString() + "." + queryName;

            try
            {
                QueryManager.get().validateQuery(schemaPath, queryName, user, lookupContainer);
            }
            catch (Exception e){
                queryErrors.add("ERROR: " + errorBase + " has a foreign key to a table that fails query validation: " + fkt + ". The error was: " + e.getMessage());
            }

            if (displayColumn != null)
            {
                FieldKey displayFieldKey = FieldKey.fromString(displayColumn);
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(fkTable, Collections.singleton(displayFieldKey));
                if (cols == null || !cols.containsKey(displayFieldKey))
                {
                    queryErrors.add("ERROR: " + errorBase + " reports a foreign key with displayColumn of " + displayColumn + " in the table " + schemaPath.toDisplayString() + "." + queryName + ", but the column does not exist");
                }
                else
                {
                    ColumnInfo ci = cols.get(displayFieldKey);
                    if (!displayColumn.equals(ci.getFieldKey().toString()))
                    {
                        queryErrors.add("WARNING: " + errorBase + ", the lookup to " + schemaPath.toDisplayString() + "." + queryName + "' did not match the expected case, which was '" + ci.getFieldKey().toString()  + "'. Actual: '" + displayColumn + "'");
                    }
                }
            }

            if (keyColumn != null)
            {
                FieldKey keyFieldKey = FieldKey.fromString(keyColumn);
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(fkTable, Collections.singleton(keyFieldKey));
                if (cols == null || !cols.containsKey(keyFieldKey))
                {
                    queryErrors.add("ERROR: " + errorBase + " reports a foreign key with keyColumn of " + keyColumn + " in the table " + schemaPath.toDisplayString() + "." + queryName + ", but the column does not exist");
                }
                else
                {
                    ColumnInfo ci = cols.get(keyFieldKey);
                    if (!keyColumn.equals(ci.getFieldKey().toString()))
                    {
                        queryErrors.add("WARNING: " + errorBase + ", the lookup to " + schemaPath.toDisplayString() + "." + queryName + "' did not match the expected case, which was '" + ci.getFieldKey().toString()  + "'. Actual: '" + keyColumn + "'");
                    }
                }
            }
            else
            {
                queryErrors.add("INFO: " + errorBase + ", there is a lookup where the keyColumn is blank");
            }
        }

        return queryErrors;
    }

    /**
     * Experimental.  The goal is to provide a more thorough validation of saved views, including errors like invalid
     * column names or case errors (which cause problems for case-sensitive js)
     */
    public Set<String> validateQueryViews(String schemaName, String queryName, User user, Container container) throws SQLException, QueryParseException
    {
        UserSchema schema = (UserSchema) DefaultSchema.get(user, container).getSchema(schemaName);
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + schemaName + "'!");

        TableInfo table = schema.getTable(queryName);
        if (null == table)
            throw new IllegalArgumentException("The query '" + queryName + "' was not found in the schema '" + schemaName + "'!");

        //validate views
        Set<String> queryErrors = new HashSet<String>();
        List<CustomView> views = QueryService.get().getCustomViews(user, container, schemaName, queryName);
        for (CustomView v : views)
        {
            validateViewColumns(user, container, v, "columns", v.getColumns(), queryErrors, table);

            if (!StringUtils.isEmpty(v.getFilterAndSort()))
                try
                {
                    CustomViewInfo.FilterAndSort fs = CustomViewInfo.FilterAndSort.fromString(v.getFilterAndSort());
                    List<FieldKey> filterCols = new ArrayList<FieldKey>();
                    for (FilterInfo f : fs.getFilter())
                    {
                        filterCols.add(f.getField());
                    }
                    validateViewColumns(user, container, v, "filter", filterCols, queryErrors, table);

                    List<FieldKey> sortCols = new ArrayList<FieldKey>();
                    for (Sort.SortField f : fs.getSort())
                    {
                        sortCols.add(f.getFieldKey());
                    }
                    validateViewColumns(user, container, v, "sort", sortCols, queryErrors, table);

                }
                catch (URISyntaxException e)
                {
                    queryErrors.add("ERROR: unable to process the filter/sort section of view: " + v.getName());
                }

        }

        return queryErrors;
    }

    private void validateViewColumns(User user, Container container, CustomView v, String identifier, List<FieldKey> viewCols, Set<String> queryErrors, TableInfo sourceTable)
    {
        //verify columns match, accounting for case
        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(sourceTable, viewCols);

        for (FieldKey f : viewCols)
        {
            boolean found = false;
            boolean matchCase = false;
            FieldKey fk = null;
            ColumnInfo c = colMap.get(f);
            if(c != null)
            {
                found = true;
                fk = c.getFieldKey();
                if(c instanceof AliasedColumn)
                    fk = ((AliasedColumn)c).getColumn().getFieldKey();

                if(fk.toString().equals(f.toString()))
                {
                    matchCase = true;
                }
            }

            if (!found){
                queryErrors.add("ERROR: In the saved view '" + (v.getName() == null ? "default" : v.getName()) + "', in the " + identifier + " section, the column '" + f.toString() + "' in " + v.getSchemaName() + "." + v.getQueryName() + " could not be matched to a column");
                continue;
            }

            if (!matchCase){
                queryErrors.add("WARNING: In the saved view '" + (v.getName() == null ? "default" : v.getName()) + "', in the " + identifier + " section, the column '" + f.toString() + "' in " + v.getSchemaName() + "." + v.getQueryName() + "' did not match the expected case, which was '" + fk + "'");
            }

            //queryErrors.addAll(validateColumn(c, user, container));
        }
    }
}
