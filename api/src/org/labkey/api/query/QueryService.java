/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Filter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.TableType;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract public class QueryService
{
    static private QueryService instance;

    public static final String MODULE_QUERIES_DIRECTORY = "queries";
    public static final String MODULE_SCHEMAS_DIRECTORY = "schemas";

    public static final String SCHEMA_TEMPLATE_EXTENSION = ".template.xml";

    static public QueryService get()
    {
        return instance;
    }

    static public void set(QueryService impl)
    {
        instance = impl;
    }

    /**
     * Most usages should call UserSchema.getQueryDefs() instead, which allows the schema to include other queries that
     * may be stored in schema-specific places, such as queries associated with an assay provider.
     */
    abstract public Map<String, QueryDefinition> getQueryDefs(User user, Container container, String schema);
    abstract public List<QueryDefinition> getQueryDefs(User user, Container container);

    abstract public QueryDefinition getQueryDef(User user, Container container, String schema, String name);

    @Deprecated /** Use SchemaKey form instead. */
    abstract public QueryDefinition createQueryDef(User user, Container container, String schema, String name);
    abstract public QueryDefinition createQueryDef(User user, Container container, SchemaKey schema, String name);
    abstract public QueryDefinition createQueryDef(User user, Container container, UserSchema schema, String name);
    abstract public QueryDefinition createQueryDefForTable(UserSchema schema, String tableName);

    abstract public QuerySnapshotDefinition getSnapshotDef(Container container, String schema, String name);
    abstract public QuerySnapshotDefinition createQuerySnapshotDef(QueryDefinition queryDef, String name);
    abstract public QuerySnapshotDefinition createQuerySnapshotDef(Container container, QueryDefinition queryDef, String name);
    abstract public boolean isQuerySnapshot(Container container, String schema, String name);
    abstract public List<QuerySnapshotDefinition> getQuerySnapshotDefs(Container container, String schema);
    abstract public QueryDefinition saveSessionQuery(ViewContext context, Container container, String schema, String sql);
    abstract public QueryDefinition saveSessionQuery(ViewContext context, Container container, String schema, String sql, String metadataXml);
    abstract public QueryDefinition getSessionQuery(ViewContext context, Container container, String schema, String queryName);

    abstract public ActionURL urlQueryDesigner(User user, Container container, String schema);
    abstract public ActionURL urlFor(User user, Container container, QueryAction action, String schema, String queryName);
    /** Generate a generic query URL for the QueryAction. */
    abstract public ActionURL urlDefault(Container container, QueryAction action, @Nullable String schema, @Nullable String query);
    /** Generate a generic query URL for the QueryAction with a parameter for each primary key column. */
    abstract public DetailsURL urlDefault(Container container, QueryAction action, String schema, String query, Map<String, ?> params);
    /** Generate a generic query URL for the QueryAction with a parameter for each primary key column. */
    abstract public DetailsURL urlDefault(Container container, QueryAction action, TableInfo table);

    /** Get schema for SchemaKey encoded path. */
    abstract public UserSchema getUserSchema(User user, Container container, String schemaPath);
    /** Get schema for SchemaKey path. */
    abstract public UserSchema getUserSchema(User user, Container container, SchemaKey schemaPath);

    /** If schema, query, or user is null, return custom views for all schemas/queries/users.  To get only shared custom views, use {@link QueryService#getSharedCustomViews(Container, String, String, boolean)}. */
    abstract public List<CustomView> getCustomViews(@Nullable User user, Container container, @Nullable String schemaName, @Nullable String queryName, boolean includeInherited);
    abstract public CustomView getCustomView(@Nullable User user, Container container, String schema, String query, String name);

    /** If schema, query is null, return custom views for all schemas/queries */
    abstract public List<CustomView> getSharedCustomViews(Container container, @Nullable String schemaName, @Nullable String queryName, boolean includeInherited);
    abstract public CustomView getSharedCustomView(Container container, String schema, String query, String name);

    abstract public int importCustomViews(User user, Container container, VirtualFile viewDir) throws XmlValidationException, IOException;

    /**
     * Get CustomView properties as a JSON map.
     * @param view The view to be serialized or null.
     * @param user The current user or null. For user display name rendering.
     * @return null if view is null otherwise a map of the CustomView properties.
     */
    abstract public Map<String, Object> getCustomViewProperties(@Nullable CustomView view, @Nullable User user);

    /**
     * Loops through the field keys and turns them into ColumnInfos based on the base table
     */
    @NotNull
    abstract public Map<FieldKey, ColumnInfo> getColumns(@NotNull TableInfo table, Collection<FieldKey> fields);
    @NotNull
    abstract public LinkedHashMap<FieldKey, ColumnInfo> getColumns(@NotNull TableInfo table, @NotNull Collection<FieldKey> fields, @NotNull Collection<ColumnInfo> existingColumns);

    abstract public List<DisplayColumn> getDisplayColumns(@NotNull TableInfo table, Collection<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> fields);

    /**
     * Ensure that <code>columns</code> contains all of the columns necessary for <code>filter</code> and <code>sort</code>.
     * If <code>unresolvedColumns</code> is not null, then the Filter and Sort will be modified to remove any clauses that
     * involve unresolved columns, and <code>unresolvedColumns</code> will contain the names of the unresolved columns.
     *
     * NOTE: shouldn't need to call this anymore unless you really care about the unresolvedColumns
     */
    abstract public Collection<ColumnInfo> ensureRequiredColumns(@NotNull TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, Set<FieldKey> unresolvedColumns);

    abstract public UserSchema createSimpleUserSchema(String name, @Nullable String description, User user, Container container, DbSchema schema);

    abstract public List<ColumnInfo> getDefaultVisibleColumnInfos(List<ColumnInfo> columns);
    abstract public List<FieldKey> getDefaultVisibleColumns(List<ColumnInfo> columns);

    /**
     * Find a metadata override for the given schema and table by looking in the current folder,
     * parent folders up to and including the project, the shared container, and finally in
     * each module active in the current container for
     * "<code>queries/&lt;schemaName&gt;/&lt;tableName&gt;.qview.xml</code>" metadata files.
     *
     * @param schema The schema.
     * @param tableName The table.
     * @param customQuery whether to look for custom queries or modified metadata on built-in tables
     * @param allModules True to search all modules; false to search active modules in the schema's container.
     * @param errors A collection of errors generated while parsing the metadata xml.
     * @param dir An alternate location to search for file-based query metadata (defaults to "<code>queries/&lt;schemaName&gt;</code>").  Be careful to only use valid file names.
     * @return The metadata xml.
     */
    abstract public TableType findMetadataOverride(UserSchema schema, String tableName, boolean customQuery, boolean allModules, @NotNull Collection<QueryException> errors, @Nullable Path dir);

    abstract public TableType parseMetadata(String metadataXML, Collection<QueryException> errors);

	public ResultSet select(QuerySchema schema, String sql) throws SQLException
    {
        return select(schema, sql, false);
    }

    /* strictColumnList requires that query not add any addition columns to the query result */
    abstract public ResultSet select(QuerySchema schema, String sql, boolean strictColumnList) throws SQLException;

    public Results select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort) throws SQLException
    {
        return select(table, columns, filter, sort, Collections.<String, Object>emptyMap());
    }
	abstract public Results select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, Map<String, Object> parameters) throws SQLException;

    /**
     * @param forceSort always add a sort, even if the Sort parameter is null or empty. Do not pass true if the SQL will
     * be used as a subselect, as some databases don't allow you to do ORDER BY on a subselect if there is no LIMIT/TOP
     * clause 
     */
    abstract public SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, int maxRows, long offset, boolean forceSort);

    /**
     * Gets all of the custom views from the given relative path defined in the set of active modules for the
     * given container.
     * @param container Container to use to figure out the set of active modules
     * @param qd the query for which views should be fetched
     * @param path the relative path within the module to check for custom views
     */
    public abstract List<CustomView> getFileBasedCustomViews(Container container, QueryDefinition qd, Path path);

    public abstract List<QueryDefinition> getFileBasedQueryDefs(User user, Container container, String schemaName, Path path);

    abstract public void addQueryListener(QueryChangeListener listener);
    abstract public void removeQueryListener(QueryChangeListener listener);

    abstract public void addCustomViewListener(CustomViewChangeListener listener);
    abstract public void removeCustomViewListener(CustomViewChangeListener listener);

    //
    // Thread local environment for executing a query
    //
    // currently only supports USERID for implementing the USERID() method
    //
    public enum Environment
    {
        USERID(JdbcType.INTEGER);

        public JdbcType type;

        Environment(JdbcType type)
        {
            this.type = type;
        }
    }

    abstract public void setEnvironment(Environment e, Object value);
    abstract public void clearEnvironment();
    abstract public Object cloneEnvironment();
    abstract public void copyEnvironment(Object o);


    public interface ParameterDecl extends ParameterDescription
    {
        Object getDefault();
        boolean isRequired();
    }


    public static class NamedParameterNotProvided extends RuntimeException
    {
        public NamedParameterNotProvided(String name)
        {
            super("Parameter not provided: " + name);
        }
    }

    abstract public void bindNamedParameters(SQLFragment frag, Map<String, Object> in);
    abstract public void validateNamedParameters(SQLFragment frag);

    public enum AuditAction
    {
        INSERT("A row was inserted.",
                "%s row(s) were inserted."),
        UPDATE("Row was updated.",
                "%s row(s) were updated."),
        DELETE("Row was deleted.",
                "%s row(s) were deleted.");

        String _commentDetailed;
        String _commentSummary;

        AuditAction(String commentDetailed, String commentSummary)
        {
            _commentDetailed = commentDetailed;
            _commentSummary = commentSummary;
        }

        public String getCommentDetailed()
        {
            return _commentDetailed;
        }

        public String getCommentSummary()
        {
            return _commentSummary;
        }
    }

    /**
     * Add an audit log entry for this QueryView. The
     * schemaName, queryName, and sortFilters are logged along with a comment message.
     *
     * @param comment Comment to log.
     */
    abstract public void addAuditEvent(QueryView queryView, String comment, @Nullable Integer dataRowCount);
    abstract public void addAuditEvent(User user, Container c, String schemaName, String queryName, ActionURL sortFilter, String comment, @Nullable Integer dataRowCount);
    abstract public void addAuditEvent(User user, Container c, TableInfo table, AuditAction action, List<Map<String, Object>> ... params);

    /**
     * Returns a URL for the audit history for the table.
     */
    abstract public @Nullable ActionURL getAuditHistoryURL(User user, Container c, TableInfo table);

    /**
     * Returns a DetailsURL that can be used for the row audit history for the table.
     */
    abstract public @Nullable DetailsURL getAuditDetailsURL(User user, Container c, TableInfo table);

    abstract public void fireQueryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries);
    abstract public void fireQueryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, QueryChangeListener.QueryProperty property, Collection<QueryChangeListener.QueryPropertyChange> changes);
    abstract public void fireQueryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries);
}
