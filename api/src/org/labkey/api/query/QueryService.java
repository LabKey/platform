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

package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Filter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.data.ParameterDescriptionImpl;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.TableType;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface QueryService
{
    String MODULE_QUERIES_DIRECTORY = "queries";
    Path MODULE_QUERIES_PATH = Path.parse(MODULE_QUERIES_DIRECTORY);

    Path MODULE_SCHEMAS_PATH = Path.parse("schemas");

    String SCHEMA_TEMPLATE_EXTENSION = ".template.xml";

    static QueryService get()
    {
        return ServiceRegistry.get(QueryService.class);
    }

    static void set(QueryService impl)
    {
        ServiceRegistry.get().registerService(QueryService.class, impl);
    }

    /**
     * Most usages should call UserSchema.getQueryDefs() instead, which allows the schema to include other queries that
     * may be stored in schema-specific places, such as queries associated with an assay provider.
     */
    Map<String, QueryDefinition> getQueryDefs(User user, Container container, String schema);
    List<QueryDefinition> getQueryDefs(User user, Container container);

    QueryDefinition getQueryDef(User user, Container container, String schema, String name);

    @Deprecated /** Use SchemaKey form instead. */ QueryDefinition createQueryDef(User user, Container container, String schema, String name);
    QueryDefinition createQueryDef(User user, Container container, SchemaKey schema, String name);
    QueryDefinition createQueryDef(User user, Container container, UserSchema schema, String name);
    QueryDefinition createQueryDefForTable(UserSchema schema, String tableName);

    @Nullable QuerySnapshotDefinition getSnapshotDef(Container container, String schema, String snapshotName);
    QuerySnapshotDefinition createQuerySnapshotDef(QueryDefinition queryDef, String name);
    QuerySnapshotDefinition createQuerySnapshotDef(Container container, QueryDefinition queryDef, String name);
    boolean isQuerySnapshot(Container container, String schema, String name);
    List<QuerySnapshotDefinition> getQuerySnapshotDefs(@Nullable Container container, @Nullable String schema);
    QueryDefinition saveSessionQuery(ViewContext context, Container container, String schema, String sql);
    QueryDefinition saveSessionQuery(ViewContext context, Container container, String schema, String sql, String metadataXml);
    QueryDefinition saveSessionQuery(HttpSession session, Container container, User user, String schema, String sql, @Nullable String xml);
    QueryDefinition getSessionQuery(ViewContext context, Container container, String schema, String queryName);

    ActionURL urlQueryDesigner(User user, Container container, String schema);
    ActionURL urlFor(User user, Container container, QueryAction action, String schema, String queryName);
    /** Generate a generic query URL for the QueryAction. */
    ActionURL urlDefault(Container container, QueryAction action, @Nullable String schema, @Nullable String query);
    /** Generate a generic query URL for the QueryAction with a parameter for each primary key column. */
    DetailsURL urlDefault(Container container, QueryAction action, String schema, String query, Map<String, ?> params);
    /** Generate a generic query URL for the QueryAction with a parameter for each primary key column. */
    DetailsURL urlDefault(Container container, QueryAction action, TableInfo table);

    // TODO: These probably need to change to support data source qualified schema names

    /** Get schema for SchemaKey encoded path. */
    UserSchema getUserSchema(User user, Container container, String schemaPath);
    /** Get schema for SchemaKey path. */
    UserSchema getUserSchema(User user, Container container, SchemaKey schemaPath);

    UserSchema getLinkedSchema(User user, Container container, String name);
    UserSchema createLinkedSchema(User user, Container container, String name, String sourceContainerId, String sourceSchemaName,
                                  String metadata, String tables, String template);
    void deleteLinkedSchema(User user, Container container, String name);

    void writeTables(Container c, User user, VirtualFile dir, Map<String, List<Map<String, Object>>> schemas, ColumnHeaderType header) throws IOException;

    /**
     * Get the list of custom views.
     * If schema, query, or owner is null, return custom views for all schemas/queries/users.
     * To get only shared custom views, use {@link QueryService#getSharedCustomViews(User, Container, String, String, boolean)}.
     * NOTE: user is not the owner of the custom views, but is used for container and schema permission checks.
     */
    List<CustomView> getCustomViews(@NotNull User user, Container container, @Nullable User owner, @Nullable String schemaName, @Nullable String queryName, boolean includeInherited);
    CustomView getCustomView(@NotNull User user, Container container, @Nullable User owner, String schema, String query, String name);

    /**
     * Get the list of shared custom views.
     * If schema, query is null, return custom views for all schemas/queries.
     * NOTE: user is not the owner of the custom views, but is used for container and schema permission checks.
     */
    List<CustomView> getSharedCustomViews(@NotNull User user, Container container, @Nullable String schemaName, @Nullable String queryName, boolean includeInherited);
    CustomView getSharedCustomView(@NotNull User user, Container container, String schema, String query, String name);

    /**
     * Returns custom views stored in the database (not module custom views) that meet the criteria. This is not appropriate
     * for UI operations (see getCustomViews() for that), but it's important for query change listeners. See #21641 and #21862.
     */
    List<CustomView> getDatabaseCustomViews(@NotNull User user, Container container, @Nullable User owner, @Nullable String schemaName, @Nullable String queryName, boolean includeInherited, boolean sharedOnly);

    int importCustomViews(User user, Container container, VirtualFile viewDir) throws IOException;

    /**
     * Get CustomView properties as a JSON map.
     * @param view The view to be serialized or null.
     * @param user The current user or null. For user display name rendering.
     * @return null if view is null otherwise a map of the CustomView properties.
     */
    Map<String, Object> getCustomViewProperties(@Nullable CustomView view, @Nullable User user);

    String getCustomViewNameFromEntityId(Container container, String entityId) throws SQLException;

    /**
     * Loops through the field keys and turns them into ColumnInfos based on the base table
     */
    @NotNull
    Map<FieldKey, ColumnInfo> getColumns(@NotNull TableInfo table, @NotNull Collection<FieldKey> fields);
    @NotNull
    LinkedHashMap<FieldKey, ColumnInfo> getColumns(@NotNull TableInfo table, @NotNull Collection<FieldKey> fields, @NotNull Collection<ColumnInfo> existingColumns);

    List<DisplayColumn> getDisplayColumns(@NotNull TableInfo table, Collection<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> fields);

    /**
     * Ensure that <code>columns</code> contains all of the columns necessary for <code>filter</code> and <code>sort</code>.
     * If <code>unresolvedColumns</code> is not null, then the Filter and Sort will be modified to remove any clauses that
     * involve unresolved columns, and <code>unresolvedColumns</code> will contain the names of the unresolved columns.
     *
     * NOTE: shouldn't need to call this anymore unless you really care about the unresolvedColumns
     */
    Collection<ColumnInfo> ensureRequiredColumns(@NotNull TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, Set<FieldKey> unresolvedColumns);

    UserSchema createSimpleUserSchema(String name, @Nullable String description, User user, Container container, DbSchema schema);

    List<ColumnInfo> getDefaultVisibleColumnInfos(List<ColumnInfo> columns);
    List<FieldKey> getDefaultVisibleColumns(List<ColumnInfo> columns);

    /**
     * Finds metadata overrides for the given schema and table and returns them in application order.
     * For now, a maximum of two metadata xml overrides will be returned, in application order:
     *
     * 1) The first metadata "<code>queries/&lt;schemaName&gt;/&lt;tableName&gt;.qview.xml</code>" file found from the set of active (or all) modules.
     * 2) The first metadata xml found in the database searching up the container hierarchy plus shared.
     *
     * @param schema The schema.
     * @param tableName The table.
     * @param customQuery whether to look for custom queries or modified metadata on built-in tables
     * @param allModules True to search all modules; false to search active modules in the schema's container.
     * @param errors A collection of errors generated while parsing the metadata xml.
     * @param dir An alternate location to search for file-based query metadata (defaults to "<code>queries/&lt;schemaName&gt;</code>").  Be careful to only use valid file names.
     * @return A set of metadata xml in application order.
     */
    Collection<TableType> findMetadataOverride(UserSchema schema, String tableName, boolean customQuery, boolean allModules, @NotNull Collection<QueryException> errors, @Nullable Path dir);

    TableType parseMetadata(String metadataXML, Collection<QueryException> errors);

    /**
     * Create a TableSelector for a LabKey sql query string.
     * @param schema The query schema context used to parse the sql query in.
     * @param sql The LabKey query string.
     * @return a TableSelector
     */
    @NotNull
    TableSelector selector(@NotNull QuerySchema schema, @NotNull String sql);

    @NotNull
    TableSelector selector(@NotNull QuerySchema schema, @NotNull String sql, Set<String> columnNames, @Nullable Filter filter, @Nullable Sort sort);

	default ResultSet select(QuerySchema schema, String sql) throws SQLException
    {
        return select(schema, sql, null, false, true);
    }

    /* strictColumnList requires that query not add any addition columns to the query result */
    ResultSet select(QuerySchema schema, String sql, @Nullable Map<String, TableInfo> tableMap, boolean strictColumnList, boolean cached);

    Results selectResults(@NotNull QuerySchema schema, String sql, @Nullable Map<String, TableInfo> tableMap, Map<String, Object> parameters, boolean strictColumnList, boolean cached) throws SQLException;

    default Results select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort)
    {
        return select(table, columns, filter, sort, Collections.emptyMap(), true);
    }

    Results select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, Map<String, Object> parameters, boolean cached);

    /**
     * @param forceSort always add a sort, even if the Sort parameter is null or empty. Do not pass true if the SQL will
     * be used as a subselect, as some databases don't allow you to do ORDER BY on a subselect if there is no LIMIT/TOP
     * clause 
     */
    SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, int maxRows, long offset, boolean forceSort);
    SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, int maxRows, long offset, boolean forceSort, @NotNull QueryLogging queryLogging);

    void addCompareType(CompareType type);

    Collection<CompareType> getCompareTypes();

    /**
     * Gets all of the custom views from the given relative path defined in the set of active modules for the
     * given container.
     * @param container Container to use to figure out the set of active modules
     * @param qd the query for which views should be fetched
     * @param path the relative path within the module to check for custom views
     * @param extraModules any extra modules that may need to be searched for Custom Views but are not explicitly enabled
     */
    List<CustomView> getFileBasedCustomViews(Container container, QueryDefinition qd, Path path, String query, Module... extraModules);

    /*
     * Normally we look at all active modules within a container for query file paths, however sometimes
     * (i.e. in the Luminex module) the module is disabled but we still want to look in that directory for additional QueryDefs.
     * In such cases, one may pass additional modules as 'extraModules'.
     */
    List<QueryDefinition> getFileBasedQueryDefs(User user, Container container, String schemaName, Path path, Module... extraModules);

    void addQueryListener(QueryChangeListener listener);
    void removeQueryListener(QueryChangeListener listener);

    void addCustomViewListener(CustomViewChangeListener listener);
    void removeCustomViewListener(CustomViewChangeListener listener);

    /**
     * Register an action that can be used for generating links to be displayed in schema browser
     * @param actionClass Action class of the schema link to be registered
     * @param module Module in which the actionClass resides
     * @param linkLabel Label for displaying the action link
     */
    void registerSchemaLinkAction(@NotNull Class<? extends Controller> actionClass, @NotNull Module module, @NotNull String linkLabel);

    /**
     * Get the set of registered schema links for the active modules in the given container.
     * @param c container
     * @return a map of ActionURLs and their labels
     */
    Map<ActionURL, String> getSchemaLinks(@NotNull Container c);

    //
    // Thread local environment for executing a query
    //
    // currently supports:
    // - USER for implementing the USERID() and USERNAME() method
    // - CONTAINER for
    // - ACTION so query schemas can exclude certain types of actions (e.g., disallow export)
    //
    enum Environment
    {
        USER(JdbcType.OTHER),
        CONTAINER(JdbcType.OTHER),
        ACTION(JdbcType.OTHER),
        LISTENER_ENVIRONMENTS(JdbcType.OTHER);

        public JdbcType type;

        Environment(JdbcType type)
        {
            this.type = type;
        }
    }

    void setEnvironment(Environment e, Object value);
    void clearEnvironment();
    Object cloneEnvironment();
    void copyEnvironment(Object o);
    Object getEnvironment(QueryService.Environment e);


    interface ParameterDecl extends ParameterDescription
    {
        Object getDefault();
        boolean isRequired();
    }

    class ParameterDeclaration extends ParameterDescriptionImpl implements ParameterDecl
    {
        protected final Object _defaultValue;
        protected final boolean _required;

        public ParameterDeclaration(@NotNull String name, @NotNull JdbcType type)
        {
            this(name, type, null, null, false);
        }

        public ParameterDeclaration(@NotNull String name, @NotNull JdbcType type, @Nullable String uri)
        {
            this(name, type, uri, null, false);
        }

        public ParameterDeclaration(@NotNull String name, @NotNull JdbcType type, @Nullable String uri, @Nullable Object defaultValue, boolean required)
        {
            super(name, type, uri);
            _defaultValue = defaultValue;
            _required = required;
        }

        @Override
        public Object getDefault()
        {
            return _defaultValue;
        }

        @Override
        public boolean isRequired()
        {
            return _required;
        }
    }


    class NamedParameterNotProvided extends RuntimeException
    {
        public NamedParameterNotProvided(String name)
        {
            super("Parameter not provided: " + name);
        }
    }

    void bindNamedParameters(SQLFragment frag, Map<String, Object> in);
    void validateNamedParameters(SQLFragment frag);

    enum AuditAction
    {
        INSERT("A row was inserted.",
                "%s row(s) were inserted."),
        UPDATE("Row was updated.",
                "%s row(s) were updated."),
        DELETE("Row was deleted.",
                "%s row(s) were deleted."),
        TRUNCATE("Table was truncated.",
                "All rows were deleted.");

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
    void addAuditEvent(QueryView queryView, String comment, @Nullable Integer dataRowCount);
    void addAuditEvent(User user, Container c, String schemaName, String queryName, ActionURL sortFilter, String comment, @Nullable Integer dataRowCount);
    void addAuditEvent(User user, Container c, TableInfo table, AuditAction action, List<Map<String, Object>>... params);
    void addSummaryAuditEvent(User user, Container c, TableInfo table, AuditAction action, Integer dataRowCount);

    /**
     * Returns a URL for the audit history for the table.
     */
    @Nullable ActionURL getAuditHistoryURL(User user, Container c, TableInfo table);

    /**
     * Returns a DetailsURL that can be used for the row audit history for the table.
     */
    @Nullable DetailsURL getAuditDetailsURL(User user, Container c, TableInfo table);

    Collection<String> getQueryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries);
    void fireQueryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries);
    void fireQueryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, QueryChangeListener.QueryProperty property, Collection<QueryChangeListener.QueryPropertyChange> changes);
    void fireQueryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries);


    /** OLAP **/
    // could make this a separate service
    void cubeDataChanged(Container c);    // TODO could be more specific than "something in this container"
    String warmCube(User user, Container container, String schemaName, String configId, String cubeName);
    void cubeDataChanged(Set<Container> containers);
    String warmCube(User user, Set<Container> containers, String schemaName, String configId, String cubeName);
    String cubeDataChangedAndRewarmCube(User user, Set<Container> containers, String schemaName, String configId, String cubeName);

    void saveNamedSet(String setName, List<String> setList);
    void deleteNamedSet(String setName);
    List<String> getNamedSet(String setName);

    /**
     * Add a passthrough method to the whitelist for the primary LabKey database type. This enables modules to create
     * and enable custom database functions, for example.
     */
    void registerPassthroughMethod(String name, JdbcType returnType, int minArguments, int maxArguments);

    /**
     * Add a passthrough method to the whitelist for a particular database type. This enables modules to create
     * and enable custom database functions, for example.
     */
    void registerPassthroughMethod(String name, JdbcType returnType, int minArguments, int maxArguments, SqlDialect dialect);
}
