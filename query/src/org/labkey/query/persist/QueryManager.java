/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewChangeListener;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseWarning;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;
import org.labkey.query.ExternalSchema;
import org.labkey.query.ExternalSchemaDocumentProvider;
import org.springframework.jdbc.BadSqlGrammarException;

import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
    private static final List<QueryChangeListener> QUERY_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<CustomViewChangeListener> VIEW_LISTENERS = new CopyOnWriteArrayList<>();

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
        return QueryDefCache.getQueryDef(container, schema, name, customQuery);
    }

    /**
     * @param customQuery whether to look for custom queries or modified metadata on built-in tables
     */
    public List<QueryDef> getQueryDefs(Container container, String schema, boolean inheritableOnly, boolean includeSnapshots, boolean customQuery)
    {
        return QueryDefCache.getQueryDefs(container, schema, inheritableOnly, includeSnapshots, customQuery);
    }

    public Collection<QuerySnapshotDef> getQuerySnapshots(@Nullable Container container, @Nullable String schemaName)
    {
        return QuerySnapshotCache.getQuerySnapshotDefs(container, schemaName);
    }

    public QuerySnapshotDef getQuerySnapshotDef(@NotNull Container container, @NotNull String schemaName, @NotNull String snapshotName)
    {
        return QuerySnapshotCache.getQuerySnapshotDef(container, schemaName, snapshotName);
    }

    public QueryDef insert(User user, QueryDef queryDef)
    {
        QueryDef def = Table.insert(user, getTableInfoQueryDef(), queryDef);
        QueryDefCache.uncache(ContainerManager.getForId(def.getContainerId()));
        return def;
    }

    public QueryDef update(User user, QueryDef queryDef)
    {
        QueryDef def = Table.update(user, getTableInfoQueryDef(), queryDef, queryDef.getQueryDefId());
        QueryDefCache.uncache(ContainerManager.getForId(def.getContainerId()));
        return def;
    }

    public void delete(QueryDef queryDef)
    {
        Table.delete(getTableInfoQueryDef(), queryDef.getQueryDefId());
        QueryDefCache.uncache(ContainerManager.getForId(queryDef.getContainerId()));
    }

    public void delete(QuerySnapshotDef querySnapshotDef)
    {
        Table.delete(getTableInfoQuerySnapshotDef(), querySnapshotDef.getRowId());
        QuerySnapshotCache.uncache(querySnapshotDef);
        if (querySnapshotDef.getQueryDefId() != null)
        {
            Table.delete(getTableInfoQueryDef(), querySnapshotDef.getQueryDefId());
            QueryDefCache.uncache(querySnapshotDef.lookupContainer());
        }
    }

    public QuerySnapshotDef insert(User user, QueryDef queryDef, QuerySnapshotDef snapshotDef)
    {
        if (queryDef != null && snapshotDef.getQueryTableName() == null)
        {
            QueryDef def = insert(user, queryDef);
            snapshotDef.setQueryDefId(def.getQueryDefId());
        }
        snapshotDef = Table.insert(user, getTableInfoQuerySnapshotDef(), snapshotDef);
        QuerySnapshotCache.uncache(snapshotDef);
        return snapshotDef;
    }

    public QuerySnapshotDef update(User user, QueryDef queryDef, QuerySnapshotDef snapshotDef) throws SQLException
    {
        if (queryDef != null && snapshotDef.getQueryTableName() == null)
            update(user, queryDef);
        snapshotDef = Table.update(user, getTableInfoQuerySnapshotDef(), snapshotDef, snapshotDef.getRowId());
        QuerySnapshotCache.uncache(snapshotDef);
        return snapshotDef;
    }

    // Does not use the cache... but only used at save time
    public QuerySnapshotDef getQuerySnapshotDef(int id)
    {
        return new TableSelector(getTableInfoQuerySnapshotDef()).getObject(id, QuerySnapshotDef.class);
    }

    public CstmView getCustomView(Container container, int id) throws SQLException
    {
        CstmView view = CustomViewCache.getCstmView(container, id);
        _log.debug(view);
        return view;
    }

    public CstmView getCustomView(Container container, String entityId) throws SQLException
    {
        CstmView view = CustomViewCache.getCstmViewByEntityId(container, entityId);
        _log.debug(view);
        return view;
    }

    /**
     * Get all shared custom views that are applicable.
     * If <code>inheritable</code> is true, custom views from parent and Shared container are included.
     *
     * @param container
     * @param schemaName
     * @param queryName
     * @param inheritable
     * @return
     */
    public List<CstmView> getAllSharedCstmViews(Container container, String schemaName, String queryName, boolean inheritable)
    {
        return getAllCstmViews(container, schemaName, queryName, null, inheritable, true);
    }

    /**
     * Get all custom views that are applicable for this user including shared custom views.
     * If <code>inheritable</code> is true, custom views from parent and Shared container are included.
     *
     * @param container The current container.
     * @param schemaName The schema name or null for all schemas.
     * @param queryName The query name or null for all queries in the schema.
     * @param owner The owner or null for all views (shared or owned by someone.)
     * @param inheritable If true, look up container hierarchy and in Shared project for custom views.
     * @param sharedOnly If true, ignore the <code>user</code> parameter and only include shared custom views.
     * @return List of custom views entities in priority order.
     */
    public List<CstmView> getAllCstmViews(Container container, String schemaName, String queryName, @Nullable User owner, boolean inheritable, boolean sharedOnly)
    {
        List<CstmView> views = new ArrayList<>();

        getCstmViewsInContainer(views, container, schemaName, queryName, owner, false, sharedOnly);
        if (container.isWorkbook())
        {
            getCstmViewsInContainer(views, container.getParent(), schemaName, queryName, owner, false, sharedOnly);
        }

        if (!inheritable)
            return views;

        Container containerCur = container.getParent();
        while (containerCur != null && !containerCur.isRoot())
        {
            getCstmViewsInContainer(views, containerCur, schemaName, queryName, owner, true, sharedOnly);
            containerCur = containerCur.getParent();
        }

        // look in the shared project
        getCstmViewsInContainer(views, ContainerManager.getSharedContainer(), schemaName, queryName, owner, true, sharedOnly);

        return views;
    }

    private void getCstmViewsInContainer(List<CstmView> views, Container container, String schemaName, String queryName, @Nullable User user, boolean inheritable, boolean sharedOnly)
    {
        if (sharedOnly)
        {
            // Get only shared custom views
            views.addAll(getCstmViews(container, schemaName, queryName, null, null, inheritable, true));
        }
        else
        {
            if (user != null)
            {
                // Custom views owned by the user first, then add shared custom views
                views.addAll(getCstmViews(container, schemaName, queryName, null, user, inheritable, false));
                views.addAll(getCstmViews(container, schemaName, queryName, null, null, inheritable, true));
            }
            else
            {
                // Get all custom views regardless of owner
                views.addAll(getCstmViews(container, schemaName, queryName, null, null, inheritable, false));
            }
        }
    }

    public List<CstmView> getCstmViews(Container container, @Nullable String schemaName, @Nullable String queryName, @Nullable String viewName, @Nullable User user, boolean inheritableOnly, boolean sharedOnly)
    {
        return CustomViewCache.getCstmViews(container, schemaName, queryName, viewName, user, inheritableOnly, sharedOnly);
    }

    public CstmView update(User user, CstmView view)
    {
        CstmView cstmView = Table.update(user, getTableInfoCustomView(), view, view.getCustomViewId());
        CustomViewCache.uncache(ContainerManager.getForId(cstmView.getContainerId()));

        return cstmView;
    }

    public CstmView insert(User user, CstmView view)
    {
        CstmView cstmView = Table.insert(user, getTableInfoCustomView(), view);
        CustomViewCache.uncache(ContainerManager.getForId(cstmView.getContainerId()));

        return cstmView;
    }

    public void delete(CstmView view)
    {
        Table.delete(getTableInfoCustomView(), view.getCustomViewId());
        CustomViewCache.uncache(ContainerManager.getForId(view.getContainerId()));
    }

    public ExternalSchemaDef getExternalSchemaDef(Container c, int rowId)
    {
        return ExternalSchemaDefCache.getSchemaDef(c, rowId, ExternalSchemaDef.class);
    }

    public List<ExternalSchemaDef> getExternalSchemaDefs(@Nullable Container container)
    {
        return ExternalSchemaDefCache.getSchemaDefs(container, ExternalSchemaDef.class);
    }

    public ExternalSchemaDef getExternalSchemaDef(Container container, @Nullable String userSchemaName)
    {
        return ExternalSchemaDefCache.getSchemaDef(container, userSchemaName, ExternalSchemaDef.class);
    }

    public LinkedSchemaDef getLinkedSchemaDef(Container c, int rowId)
    {
        return ExternalSchemaDefCache.getSchemaDef(c, rowId, LinkedSchemaDef.class);
    }

    public List<LinkedSchemaDef> getLinkedSchemaDefs(@Nullable Container c)
    {
        return ExternalSchemaDefCache.getSchemaDefs(c, LinkedSchemaDef.class);
    }

    public LinkedSchemaDef getLinkedSchemaDef(Container c, @Nullable String userSchemaName)
    {
        return ExternalSchemaDefCache.getSchemaDef(c, userSchemaName, LinkedSchemaDef.class);
    }

    public void delete(AbstractExternalSchemaDef def)
    {
        Table.delete(getTableInfoExternalSchema(), def.getExternalSchemaId());
        updateExternalSchemas(def.lookupContainer());
    }

    public LinkedSchemaDef insertLinkedSchema(User user, LinkedSchemaDef def)
    {
        LinkedSchemaDef newDef = Table.insert(user, getTableInfoExternalSchema(), def);
        updateExternalSchemas(def.lookupContainer());
        return newDef;
    }

    public void deleteLinkedSchema(Container container, String userSchemaName)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromString("UserSchemaName"), userSchemaName);
        filter.addCondition(FieldKey.fromString("SchemaType"), AbstractExternalSchemaDef.SchemaType.linked);
        Table.delete(getTableInfoExternalSchema(), filter);
        updateExternalSchemas(container);
    }

    // Uncaches and re-indexes all external schemas in a container. Called any time an external schema or linked schema
    // changes in any way (insert/update/delete).
    public void updateExternalSchemas(Container c)
    {
        if (null != c)
        {
            ExternalSchemaDefCache.uncache(c);
            ExternalSchemaDocumentProvider.getInstance().enumerateDocuments(null, c, null);
        }
    }

    public void reloadAllExternalSchemas(Container c)
    {
        getExternalSchemaDefs(c).forEach(this::reloadExternalSchema);
    }

    public void reloadExternalSchema(ExternalSchemaDef def)
    {
        ExternalSchema.uncache(def);
    }

    public boolean canInherit(int flag)
    {
        return (flag & FLAG_INHERITABLE) != 0;
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
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
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

    public TableInfo getTableInfoOlapDef()
    {
        return getDbSchema().getTable("OlapDef");
    }

    public void containerDeleted(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        Table.delete(getTableInfoQuerySnapshotDef(), filter);
        QuerySnapshotCache.uncache(c);
        Table.delete(getTableInfoCustomView(), filter);
        CustomViewCache.uncache(c);
        Table.delete(getTableInfoQueryDef(), filter);
        QueryDefCache.uncache(c);
        Table.delete(getTableInfoExternalSchema(), filter);
        ExternalSchemaDefCache.uncache(c);
        Table.delete(getTableInfoOlapDef(), filter);
    }

    public void addQueryListener(QueryChangeListener listener)
    {
        QUERY_LISTENERS.add(listener);
    }

    public void removeQueryListener(QueryChangeListener listener)
    {
        QUERY_LISTENERS.remove(listener);
    }

    public void fireQueryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        for (QueryChangeListener l : QUERY_LISTENERS)
            l.queryCreated(user, container, scope, schema, queries);
    }

    public void fireQueryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryChangeListener.QueryProperty property, @NotNull Collection<QueryChangeListener.QueryPropertyChange> changes)
    {
        assert checkChanges(property, changes);
        for (QueryChangeListener l : QUERY_LISTENERS)
            l.queryChanged(user, container, scope, schema, property, changes);
    }

    // Checks all changes have the correct property and type.
    private boolean checkChanges(QueryChangeListener.QueryProperty property, Collection<QueryChangeListener.QueryPropertyChange> changes)
    {
        if (property == null)
        {
            _log.error("Null property not allowed.");
            return false;
        }

        boolean valid = true;
        for (QueryChangeListener.QueryPropertyChange change : changes)
        {
            if (change.getProperty() != property)
            {
               _log.error(String.format("Property '%s' doesn't match change property '%s'", property, change.getProperty()));
                valid = false;
            }
            if (change.getOldValue() != null && !property.getPropertyClass().isInstance(change.getOldValue()))
            {
                _log.error(String.format("Old value '%s' isn't an instance of property '%s' class '%s'", change.getOldValue(), property, property.getPropertyClass()));
                valid = false;
            }
            if (change.getNewValue() != null && !property.getPropertyClass().isInstance(change.getNewValue()))
            {
                _log.error(String.format("New value '%s' isn't an instance of property '%s' class '%s'", change.getNewValue(), property, property.getPropertyClass()));
                valid = false;
            }
        }
        return valid;
    }

    public void fireQueryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        for (QueryChangeListener l : QUERY_LISTENERS)
            l.queryDeleted(user, container, scope, schema, queries);
    }

    public Collection<String> getQueryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        ArrayList<String> dependents = new ArrayList<>();
        for (QueryChangeListener l : QUERY_LISTENERS)
            dependents.addAll(l.queryDependents(user, container, scope, schema, queries));
        return dependents;
    }

    public void addCustomViewListener(CustomViewChangeListener listener)
    {
        VIEW_LISTENERS.add(listener);
    }

    public void removeCustomViewListener(CustomViewChangeListener listener)
    {
        VIEW_LISTENERS.remove(listener);
    }

    public void fireViewCreated(CustomView view)
    {
        for (CustomViewChangeListener l : VIEW_LISTENERS)
            l.viewCreated(view);
    }

    public void fireViewChanged(CustomView view)
    {
        for (CustomViewChangeListener l : VIEW_LISTENERS)
            l.viewChanged(view);
    }

    public void fireViewDeleted(CustomView view)
    {
        for (CustomViewChangeListener l : VIEW_LISTENERS)
            l.viewDeleted(view);
    }

    public Collection<String> getViewDepedents(CustomView view)
    {
        ArrayList<String> dependents = new ArrayList<>();
        for (CustomViewChangeListener l : VIEW_LISTENERS)
            dependents.addAll(l.viewDependents(view));
        return dependents;
    }

    static public final ContainerManager.ContainerListener CONTAINER_LISTENER = new ContainerManager.AbstractContainerListener()
    {
        public void containerDeleted(Container c, User user)
        {
            QueryManager.get().containerDeleted(c);
        }
    };

    public boolean validateQuery(SchemaKey schemaPath, String queryName, User user, Container container, @NotNull List<QueryParseException> errors,
                                 @NotNull List<QueryParseException> warnings)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, schemaPath);
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + schemaPath.toDisplayString() + "'!");

        TableInfo table = schema.getTable(queryName);
        if (null == table)
            throw new IllegalArgumentException("The query '" + queryName + "' was not found in the schema '" + schemaPath.toDisplayString() + "'!");

        return validateQuery(table, true, errors, warnings);
    }

    public boolean validateQuery(TableInfo table, boolean testAllColumns, @NotNull List<QueryParseException> errors,
                                 @NotNull List<QueryParseException> warnings)
    {
        Collection<QueryService.ParameterDecl> params = table.getNamedParameters();
        Map<String,Object> parameters = new HashMap<>();
        for (QueryService.ParameterDecl p : params)
        {
            if (!p.isRequired())
                continue;
            parameters.put(p.getName(), null);
        }

        TableSelector selector;

        // Note this check had been inverted for years, but was fixed in 14.1. Previously, testAllColumns == true meant
        // the default column list was computed but discarded, and testAllColumns == false was completely broken
        if (testAllColumns)
        {
            selector = new TableSelector(table);
        }
        else
        {
            List<FieldKey> defVisCols = table.getDefaultVisibleColumns();
            Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(table, defVisCols);
            List<ColumnInfo> cols = new ArrayList<>(colMap.values());

            selector = new TableSelector(table, cols, null, null);
        }

        // set forDisplay to mimic the behavior one would get in the UI
        // try to execute with a rowcount of 0 (will throw SQLException to client if it fails)
        selector.setForDisplay(true).setNamedParameters(parameters).setMaxRows(Table.NO_ROWS);

        //noinspection EmptyTryBlock,UnusedDeclaration
        try (ResultSet rs = selector.getResultSet())
        {
        }
        catch (SQLException e)
        {
            errors.add(new QueryParseException(e.getMessage(), e, 0, 0));
        }
        catch (BadSqlGrammarException e)
        {
            errors.add(new QueryParseException(e.getSQLException().getMessage(), e, 0, 0));
        }

        UserSchema schema = table.getUserSchema();
        if (schema != null)
        {
            QueryDefinition queryDef = schema.getQueryDef(table.getName());
            if (queryDef != null)
            {
                queryDef.validateQuery(schema, errors, warnings);
            }
        }
        return errors.isEmpty();
    }

    /**
     * Experimental.  The goal is to provide a more thorough validation of query metadata, including warnings of potentially
     * invalid conditions, like autoincrement columns set userEditable=true.
     */
    public boolean validateQueryMetadata(SchemaKey schemaPath, String queryName, User user, Container container,
                                             @NotNull List<QueryParseException> errors, @NotNull List<QueryParseException> warnings)
    {
        Set<ColumnInfo> columns = new HashSet<>();
        UserSchema schema = QueryService.get().getUserSchema(user, container, schemaPath);
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + schemaPath.getName() + "'!");

        TableInfo table = schema.getTable(queryName);
        if (null == table)
            throw new IllegalArgumentException("The query '" + queryName + "' was not found in the schema '" + schemaPath.getName() + "'!");

        try
        {
            //validate foreign keys and other metadata warnings
            columns.addAll(table.getColumns());
            columns.addAll(QueryService.get().getColumns(table, table.getDefaultVisibleColumns()).values());
        }
        catch(QueryParseException e)
        {
            errors.add(e);
        }

        for (ColumnInfo col : columns)
        {
            validateColumn(col, user, container, table, errors, warnings);
        }

        return errors.isEmpty();
    }

    /**
     * Experimental.  See validateQueryMetadata()
     */
    private boolean validateColumn(ColumnInfo col, User user, Container container, @Nullable TableInfo parentTable,
                                      @NotNull List<QueryParseException> errors, @NotNull List<QueryParseException> warnings)
    {
        if(parentTable == null)
            parentTable = col.getParentTable();

        String publicSchema = col.getParentTable().getPublicSchemaName() != null ? col.getParentTable().getPublicSchemaName() : col.getParentTable().getSchema().toString();
        String publicQuery = col.getParentTable().getPublicName() != null ? col.getParentTable().getPublicName() : col.getParentTable().getName();
        String errorBase = "(metadata) for column '" + col.getFieldKey() + "' in " + publicSchema + "." + publicQuery + ": ";

        validateFk(col, user, container, parentTable, errors, warnings, errorBase);

        List<String> specialCols = new ArrayList<>();
        specialCols.add("container");
        specialCols.add("created");
        specialCols.add("createdby");
        specialCols.add("modified");
        specialCols.add("modifiedby");

        if(specialCols.contains(col.getName()))
        {
            if(col.isUserEditable())
                warnings.add(new QueryParseWarning(errorBase + " column is user editable, which is not expected based on its name", null, 0,0));
            if(col.isShownInInsertView())
                warnings.add(new QueryParseWarning(errorBase + " column has shownInInsertView set to true, which is not expected based on its name", null, 0, 0));
            if(col.isShownInUpdateView())
                warnings.add(new QueryParseWarning(errorBase + " column has shownInUpdateView set to true, which is not expected based on its name", null, 0, 0));
        }

        if(col.isAutoIncrement() && col.isUserEditable())
            warnings.add(new QueryParseException(errorBase + " column is autoIncrement, but has userEditable set to true", null, 0, 0));
        if(col.isAutoIncrement() && col.isShownInInsertView())
            warnings.add(new QueryParseWarning(errorBase + " column is autoIncrement, but has shownInInsertView set to true", null, 0, 0));
        if(col.isAutoIncrement() && col.isShownInUpdateView())
            warnings.add(new QueryParseWarning(errorBase + " column is autoIncrement, but has shownInUpdateView set to true", null, 0, 0));

//        if(col.isShownInInsertView() && !col.isUserEditable())
//            queryErrors.add("INFO: " + errorBase + " has shownInInsertView=true, but it is not userEditable");
//        if(col.isShownInUpdateView() && !col.isUserEditable())
//            queryErrors.add("INFO: " + errorBase + " has shownInUpdateView=true, but it is not userEditable");

//        if(col.isShownInInsertView() && col.isHidden())
//            queryErrors.add("INFO: " + errorBase + " has shownInInsertView=true, but it is hidden");
//        if(col.isShownInUpdateView() && col.isHidden())
//            queryErrors.add("INFO: " + errorBase + " has shownInUpdateView=true, but it is hidden");

        try
        {
            if(col.getDisplayWidth() != null && Integer.parseInt(col.getDisplayWidth()) > 200 && !"textarea".equalsIgnoreCase(col.getInputType()))
            {
                if (col.isUserEditable() && col.getJdbcType() != null && col.getJdbcType().getJavaClass() == String.class)
                    warnings.add(new QueryParseWarning(errorBase + " column has a displayWidth > 200, but does not use a textarea as the inputType", null, 0, 0));
            }
        }
        catch (NumberFormatException e)
        {
            warnings.add(new QueryParseWarning(errorBase + " column has a blank value for displayWidth: '" + col.getDisplayWidth() + "'", null, 0, 0));
        }
        return errors.isEmpty();
    }

    /**
     * Experimental.  See validateQueryMetadata()
     */
    private boolean validateFk(ColumnInfo col, User user, Container container, TableInfo parentTable,
                              @NotNull List<QueryParseException> errors, @NotNull List<QueryParseException> warnings,
                              String errorBase)

    {
        //NOTE: this is the same code that writes JSON to the client
        JSONObject o = JsonWriter.getLookupInfo(col, false);
        if (o == null)
            return true;

        boolean isPublic = o.getBoolean("isPublic");
        SchemaKey schemaPath = SchemaKey.fromString(o.getString("schemaName"));
        String queryName = o.getString("queryName");
        String displayColumn = o.getString("displayColumn");
        String keyColumn = o.getString("keyColumn");
        String containerPath = o.getString("containerPath");

        Container lookupContainer = containerPath == null ? container : ContainerManager.getForPath(containerPath);
        if (lookupContainer == null)
        {
            warnings.add(new QueryParseWarning(errorBase + " Unable to find container" + containerPath, null, 0, 0));
        }

        //String publicSchema = col.getParentTable().getPublicSchemaName() != null ? col.getParentTable().getPublicSchemaName() : col.getParentTable().getSchema().toString();
        //String publicQuery = col.getParentTable().getPublicName() != null ? col.getParentTable().getPublicName() : col.getParentTable().getName();
        if (col.getFk() == null)
            return errors.isEmpty();

        if (!isPublic)
        {
            warnings.add(new QueryParseWarning(errorBase + " has a lookup to a non-public table: " + schemaPath.toDisplayString() + "." + queryName, null, 0, 0));
            return errors.isEmpty();
        }

        UserSchema userSchema = QueryService.get().getUserSchema(user, lookupContainer, schemaPath);
        if (userSchema == null)
        {
            warnings.add(new QueryParseWarning(errorBase + " unable to find the user schema: " + schemaPath.toDisplayString(), null, 0, 0));
            return errors.isEmpty();
        }

        TableInfo fkTable = userSchema.getTable(queryName);
        if(fkTable == null)
        {
            warnings.add(new QueryParseWarning(errorBase + " has a lookup to a table that does not exist: " + schemaPath.toDisplayString() + "." + queryName, null, 0, 0));
            return errors.isEmpty();
        }

        //a FK can have a table non-visible to the client, so long as public is set to false
        if (fkTable.isPublic()){
            String fkt = schemaPath.toDisplayString() + "." + queryName;

            QueryManager.get().validateQuery(schemaPath, queryName, user, lookupContainer, errors, warnings);
            if (displayColumn != null)
            {
                FieldKey displayFieldKey = FieldKey.fromString(displayColumn);
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(fkTable, Collections.singleton(displayFieldKey));
                if (!cols.containsKey(displayFieldKey))
                {
                    warnings.add(new QueryParseWarning(errorBase + " reports a foreign key with displayColumn of " + displayColumn + " in the table " + schemaPath.toDisplayString() + "." + queryName + ", but the column does not exist", null, 0, 0));
                }
                else
                {
                    ColumnInfo ci = cols.get(displayFieldKey);
                    if (!displayColumn.equals(ci.getFieldKey().toString()))
                    {
                        warnings.add(new QueryParseWarning(errorBase + ", the lookup to " + schemaPath.toDisplayString() + "." + queryName + "' did not match the expected case, which was '" + ci.getFieldKey().toString()  + "'. Actual: '" + displayColumn + "'", null, 0, 0));
                    }
                }
            }

            if (keyColumn != null)
            {
                FieldKey keyFieldKey = FieldKey.fromString(keyColumn);
                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(fkTable, Collections.singleton(keyFieldKey));
                if (!cols.containsKey(keyFieldKey))
                {
                    warnings.add(new QueryParseException(errorBase + " reports a foreign key with keyColumn of " + keyColumn + " in the table " + schemaPath.toDisplayString() + "." + queryName + ", but the column does not exist", null, 0, 0));
                }
                else
                {
                    ColumnInfo ci = cols.get(keyFieldKey);
                    if (!keyColumn.equals(ci.getFieldKey().toString()))
                    {
                        warnings.add(new QueryParseWarning(errorBase + ", the lookup to " + schemaPath.toDisplayString() + "." + queryName + "' did not match the expected case, which was '" + ci.getFieldKey().toString()  + "'. Actual: '" + keyColumn + "'", null, 0, 0));
                    }
                }
            }
            else
            {
                warnings.add(new QueryParseWarning(errorBase + ", there is a lookup where the keyColumn is blank", null, 0, 0));
            }
        }

        return errors.isEmpty();
    }

    /**
     * Experimental.  The goal is to provide a more thorough validation of saved views, including errors like invalid
     * column names or case errors (which cause problems for case-sensitive js)
     */
    public boolean validateQueryViews(SchemaKey schemaPath, String queryName, User user, Container container,
                                          @NotNull List<QueryParseException> errors, @NotNull List<QueryParseException> warnings) throws SQLException, QueryParseException
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, schemaPath);
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + schemaPath.getName() + "'!");

        TableInfo table = schema.getTable(queryName);
        if (null == table)
            throw new IllegalArgumentException("The query '" + queryName + "' was not found in the schema '" + schema.getSchemaName() + "'!");

        //validate views
        try
        {
            List<CustomView> views = QueryService.get().getCustomViews(user, container, null, schema.getSchemaName(), queryName, true);
            for (CustomView v : views)
            {
                validateViewColumns(user, container, v, "columns", v.getColumns(), table, errors, warnings);

                if (!StringUtils.isEmpty(v.getFilterAndSort()))
                {
                    try
                    {
                        CustomViewInfo.FilterAndSort fs = CustomViewInfo.FilterAndSort.fromString(v.getFilterAndSort());
                        List<FieldKey> filterCols = new ArrayList<>();
                        for (FilterInfo f : fs.getFilter())
                        {
                            filterCols.add(f.getField());
                        }
                        validateViewColumns(user, container, v, "filter", filterCols, table, errors, warnings);

                        List<FieldKey> sortCols = new ArrayList<>();
                        for (Sort.SortField f : fs.getSort())
                        {
                            sortCols.add(f.getFieldKey());
                        }
                        validateViewColumns(user, container, v, "sort", sortCols, table, errors, warnings);

                    }
                    catch (URISyntaxException e)
                    {
                        warnings.add(new QueryParseWarning("unable to process the filter/sort section of view: " + v.getName(), null, 0, 0));
                    }
                }
            }
        }
        catch (NotFoundException e)
        {
            errors.add(new QueryParseException("Cannot get views: ", e, 0, 0));
        }


        return errors.isEmpty();
    }

    private void validateViewColumns(User user, Container container, CustomView v, String identifier, List<FieldKey> viewCols, TableInfo sourceTable,
                                     @NotNull List<QueryParseException> errors, @NotNull List<QueryParseException> warnings) throws SQLException, QueryParseException
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
                warnings.add(new QueryParseWarning("In the saved view '" + (v.getName() == null ? "default" : v.getName()) + "', in the " + identifier + " section, the column '" + f.toString() + "' in " + v.getSchemaName() + "." + v.getQueryName() + " could not be matched to a column", null, 0, 0));
                continue;
            }

            if (!matchCase){
                warnings.add(new QueryParseWarning("In the saved view '" + (v.getName() == null ? "default" : v.getName()) + "', in the " + identifier + " section, the column '" + f.toString() + "' in " + v.getSchemaName() + "." + v.getQueryName() + "' did not match the expected case, which was '" + fk + "'", null, 0, 0));
            }

            //queryErrors.addAll(validateColumn(c, user, container));
        }
    }
}
