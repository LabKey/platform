/*
 * Copyright (c) 2006-2018 LabKey Corporation
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
import org.labkey.api.collections.BoundMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.CrosstabTableInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UserSchemaCustomizer;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MemTrackable;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.VisualizationProvider;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;


abstract public class UserSchema extends AbstractSchema implements MemTrackable
{
    protected final String _name;
    protected final SchemaKey _path;
    protected final String _description;

    protected boolean _cacheTableInfos = false;
    protected boolean _restricted = false;      // restricted schemas will return null from getSchema()
    protected final Collection<UserSchemaCustomizer> _schemaCustomizers;
    private boolean hasRegisteredSchemaLinks = false;

    public UserSchema(@NotNull String name, @Nullable String description, User user, Container container, DbSchema dbSchema)
    {
        this(SchemaKey.fromParts(name), description, user, container, dbSchema, null);
    }

    public UserSchema(@NotNull SchemaKey path, @Nullable String description, User user, Container container, DbSchema dbSchema, Collection<UserSchemaCustomizer> schemaCustomizers)
    {
        super(dbSchema, user, container);
        _name = path.getName();
        _path = path;
        _description = description;
        _schemaCustomizers = schemaCustomizers;
        assert MemTracker.getInstance().put(this);
    }

    @Override
    public @NotNull String getName()
    {
        return _name;
    }

    public SchemaKey getPath()
    {
        return _path;
    }

    @Override
    @Nullable
    public String getDescription()
    {
        return _description;
    }


    public void setRestricted(boolean restricted)
    {
        _restricted = restricted;
    }

    protected boolean canReadSchema()
    {
        User user = getUser();
        if (user == null)
            return false;
        return getContainer().hasPermission(getName() + ".canReadSchema()", user, ReadPermission.class) ||
               getContainer().hasPermission(getName() + ".canReadSchema()", user, InsertPermission.class);
    }


    public void checkCanReadSchema() throws UnauthorizedException
    {
        if (!canReadSchema())
            throw new UnauthorizedException("User cannot read schema: " + getName());
    }


    /* does user have access to cubes associated with this schema */
    public void checkCanReadSchemaOlap() throws UnauthorizedException
    {
        checkCanReadSchema();
    }


    public void checkCanExecuteMDX() throws UnauthorizedException
    {
        // by default disallow MDX if there is a getOlapContainerFilter() is not null so user can't
        // avoid the filter
        if (null != getOlapContainerFilter() && !getUser().hasSiteAdminPermission())
            throw new UnauthorizedException("User cannot execute MDX against this schema: " + getName());
    }


    public ContainerFilter getOlapContainerFilter()
    {
        return null;
    }

    public @NotNull ContainerFilter getDefaultContainerFilter()
    {
        return ContainerFilter.CURRENT;
    }

    @Nullable
    final public TableInfo getTable(String name, boolean includeExtraMetadata)
    {
        return getTable(name, null, includeExtraMetadata, false);
    }

    @Override
    @Nullable
    final public TableInfo getTable(String name, @Nullable ContainerFilter cf)
    {
        return getTable(name, cf, true, false);
    }

    /**
     * @param cf null means to use the default for this schema/table (often schema.getDefaultContainerFilter()). It does not mean there is no ContainerFilter
     * @param forWrite true means do not return a cached version
     */
    @Nullable
    public TableInfo getTable(String name, @Nullable ContainerFilter cf, boolean includeExtraMetadata, boolean forWrite)
    {
        TableInfo table = null;
        String cacheKey = cacheKey(name,cf,includeExtraMetadata,forWrite);

        // NOTE: _getTableOrQuery() does not cache TableInfo for QueryDefinition, so check here before calling
        if (null != cacheKey)
            table = tableInfoCache.get(cacheKey);
        if (null != table)
            return table;

        ArrayList<QueryException> errors = new ArrayList<>();
        Object o = _getTableOrQuery(name, cf, includeExtraMetadata, forWrite, errors);
        if (o instanceof TableInfo)
        {
            table = (TableInfo)o;
        }
        else if (o instanceof QueryDefinition)
        {
            table = ((QueryDefinition)o).getTable(this, errors, true);
            // throw if there are any non-warning errors
            for (QueryException ex : errors)
            {
                if (ex instanceof QueryParseException && ((QueryParseException)ex).isWarning())
                    continue;
                throw ex;
            }
            if (null != table && !forWrite)
                table.setLocked(true);
            if (null != table && null != cacheKey)
                tableInfoCache.put(cacheKey, table);
        }
        if (null == table)
            return null;

        assert validateTableInfo(table);
        assert forWrite || table.isLocked();
        assert null==cacheKey || table.isLocked();
        return table;
    }


    private boolean validateTableInfo(TableInfo t)
    {
        if (null == t)
            return true;
        // see https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=15584
        assert null != t.getSchema();
        assert null != t.getName();
        return true;
    }


    final String cacheKey(String name, ContainerFilter cf, boolean includeExtraMetadata, boolean forWrite)
    {
        if (includeExtraMetadata && !forWrite)
            return cacheKey(name, cf);
        return null;
    }


    // A schema can override this to make caching more efficient.  For instance, the schema implementation
    // may know which ContainerFilter is _actually_ going to be returned for a given requested ContainerFilter
    protected String cacheKey(String name, ContainerFilter cf)
    {
        String cfKey = null == cf ? "~defaultCF~" : cf.getCacheKey(getContainer());
        if (cfKey == null)
            return null;
        return getClass().getSimpleName() + "/" + name.toUpperCase() + "/" + cfKey;
    }


    /*
     * NOTE: there are two code paths that look somewhat redundant.  The Query parser uses this method, and still wants
     * to benefit from caching of calls to UserSchema.createTable().  Everyone else uses getTable() and want to benefit
     * from caching of UserSchema.createTable() AND QueryDefinition)o).getTable().  So both methods check the table cache
     */
    public Object _getTableOrQuery(String name, ContainerFilter cf, boolean includeExtraMetadata, boolean forWrite, Collection<QueryException> errors)
    {
        if (name == null)
            return null;

        if (!canReadSchema())
            return null; // See #21014

        TableInfo table = null;
        String cacheKey = cacheKey(name,cf,includeExtraMetadata,forWrite);
        if (null != cacheKey)
        {
            table = tableInfoCache.get(cacheKey);
            if (null != table)
                return table;
        }
        if (null == table)
            table = createTable(name, cf);
        Object torq;

        if (table != null)
        {
            assert !table.isLocked();
            if (includeExtraMetadata)
            {
                table.overlayMetadata(name, this, errors);
            }
            afterConstruct(table);
            if (!forWrite)
                table.setLocked(true);
            fireAfterConstruct(table);
            if (null != cacheKey)
                tableInfoCache.put(cacheKey, table);
            torq = table;
        }
        else
        {
            QueryDefinition def = getQueryDef(name);
            if (def == null)
                return null;
            if (!includeExtraMetadata && def.isMetadataEditable())
            {
                def.setMetadataXml(null);
            }
            if (null != cf)
                def.setContainerFilter(cf);

            fireAfterConstruct(def);
            torq = def;
        }

        MemTracker.getInstance().put(torq);
        return torq;
    }

    @Override
    @Nullable
    @Deprecated
    public final TableInfo getTable(String name)
    {
        return getTable(name, null, true, false);
    }


    @Deprecated // TODO ContainerFilter - remove. Schemas that still override or call this method have not been converted yet.
    public final @Nullable TableInfo createTable(String name)
    {
        return createTable(name, null);
    }
//
//
//    // TODO ContainerFilter - make abstract. Schemas that do not override this method have not been converted yet.
//    public @Nullable TableInfo createTable(String name, ContainerFilter cf)
//    {
//        return createTable(name);
//    }
//
//
    public abstract @Nullable TableInfo createTable(String name, ContainerFilter cf);


    @Override
    abstract public Set<String> getTableNames();

    public Set<String> getVisibleTableNames()
    {
        return getTableNames();
    }

    /**
     * Get a topologically sorted list of TableInfos within this schema.
     * Not all existing schemas are supported yet since their FKs don't expose the query tableName they join to or they contain loops.
     * 
     * @throws IllegalStateException if a loop is detected.
     */
    public List<TableInfo> getSortedTables()
    {
        return TableSorter.sort(this);
    }

    @Override
    public Container getContainer()
    {
        return _container;
    }

    /** Returns a SchemaKey encoded name for this schema. */
    @Override
    public @NotNull String getSchemaName()
    {
        return _path.toString();
    }

    public SchemaKey getSchemaPath()
    {
        return _path;
    }

    @Override
    public Set<String> getSchemaNames()
    {
        Set<String> ret = new HashSet<>(super.getSchemaNames());
        ret.add("Folder");
        return ret;
    }

    @Override
    public QuerySchema getSchema(String name)
    {
        if (_restricted)
            return null;
        return getDefaultSchema().getSchema(name);
    }

    public boolean canCreate()
    {
        return getContainer().hasPermission(getUser(), UpdatePermission.class);
    }

    @Nullable @Override
    public VisualizationProvider createVisualizationProvider()
    {
        return null;
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL ret;
        ret = new ActionURL("query", action.toString(), getContainer());
        ret.addParameter(QueryParam.schemaName.toString(), getSchemaName());
        return ret;
    }

    @Nullable
    public ActionURL urlFor(QueryAction action, @NotNull QueryDefinition queryDef)
    {
        return queryDef.urlFor(action, getContainer());
    }

    // Bit of a hack... by default, we include a list of all tables/queries in the current schema on the "Query" button.
    // This method lets a schema override this behavior.  In particular, external schemas may have thousands of tables
    // and need to adjust the default behavior for performance and UI sanity
    public boolean shouldRenderTableList()
    {
        return true;
    }

    public QueryDefinition getQueryDefForTable(String name)
    {                                                
        return QueryService.get().createQueryDefForTable(this, name);
    }

    public ActionURL urlSchemaDesigner()
    {
        return PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(getContainer(), getSchemaName());
    }

    @Deprecated
    // Use createView(ViewContext, QuerySettings, BindException) instead.
    public final QueryView createView(ViewContext context, QuerySettings settings)
    {
        return createView(context, settings, null);
    }

    /** Override this method to return a schema specific QueryView for the given QuerySettings. */
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        QueryDefinition qdef = settings.getQueryDef(this);
        if (qdef != null)
        {
            TableInfo tableInfo = qdef.getTable(this, new ArrayList<>(), true);
            if (tableInfo instanceof CrosstabTableInfo)
                return new CrosstabView(this, settings, errors);
        }

        return new QueryView(this, settings, errors);
    }

    public final QueryView createView(QueryForm form, BindException errors)
    {
        return createView(form.getViewContext(), form.getQuerySettings(), errors);
    }

    public final QueryView createView(ViewContext context, String dataRegionName, String queryName, BindException errors)
    {
        QuerySettings settings = getSettings(context, dataRegionName, queryName);
        return createView(context, settings, errors);
    }

    /**
     * Returns a sorted list of names for both built-in tables and custom queries.
     * @param visibleOnly Only return the visible tables and queries.
     * @return The names of the tables and queries.
     */
    public List<String> getTableAndQueryNames(boolean visibleOnly)
    {
        return new ArrayList<>(_getQueries(visibleOnly, true).keySet());
    }

    public Map<String, String> getTableAndQueryNamesAndLabels(boolean visibleOnly, boolean includeTemporary)
    {
        Map<String, QueryDefinition> queries = _getQueries(visibleOnly, includeTemporary);
        TreeMap<String, String> namesAndLabels = new CaseInsensitiveTreeMap<>();

        populateQueryNameToLabelMap(queries, namesAndLabels);
        return namesAndLabels;
    }

    protected void populateQueryNameToLabelMap(Map<String, QueryDefinition> queries, TreeMap<String, String> namesAndLabels)
    {
        for (String queryName : queries.keySet())
        {
            namesAndLabels.put(queryName, queryName);
        }
    }

    /**
     * Returns a sorted list of QueryDefinitions for both built-in tables and custom queries.
     * @param visibleOnly Only return the visible tables and queries.
     * @return The QueryDefinitions.
     */
    public List<QueryDefinition> getTablesAndQueries(boolean visibleOnly)
    {
        return new ArrayList<>(_getQueries(visibleOnly, true).values());
    }

    protected Map<String, QueryDefinition> _getQueries(boolean visibleOnly, boolean includeTemporary)
    {
        TreeMap<String, QueryDefinition> set = new CaseInsensitiveTreeMap<>();

        for (String tableName : visibleOnly ? getVisibleTableNames() : getTableNames())
        {
            QueryDefinition qdef = QueryService.get().createQueryDefForTable(this, tableName);
            if (includeTemporary || !qdef.isTemporary())
                set.put(tableName, qdef);
        }

        Map<String, QueryDefinition> queryDefs = getQueryDefs();
        for (QueryDefinition query : queryDefs.values())
        {
            if ((!visibleOnly || !query.isHidden()) && (includeTemporary || !query.isTemporary()))
                set.put(query.getName(), query);
        }

        return set;
    }

    @NotNull
    public Map<String, QueryDefinition> getQueryDefs()
    {
        Map<String, QueryDefinition> queryDefs = QueryService.get().getQueryDefs(getUser(), getContainer(), getSchemaName());
        for (QueryDefinition value : queryDefs.values())
        {
            value.setSchema(this);
        }
        return new CaseInsensitiveHashMap<>(queryDefs);
    }

    @Nullable
    public QueryDefinition getQueryDef(@NotNull String queryName)
    {
        QueryDefinition queryDef = QueryService.get().getQueryDef(getUser(), getContainer(), getSchemaName(), queryName);
        if (queryDef != null)
        {
            queryDef.setSchema(this);
        }
        return queryDef;
    }

    /** override this method to return schema specific QuerySettings object */
    protected QuerySettings createQuerySettings(String dataRegionName, String queryName, String viewName)
    {
        return new QuerySettings(dataRegionName);
    }

    public final QuerySettings getSettings(Portal.WebPart webPart, ViewContext context)
    {
        String dataRegionName = webPart.getPropertyMap().get(QueryParam.dataRegionName.name());

        if (null == dataRegionName)
            dataRegionName = "qwp" + webPart.getIndex();

        // Issue 17768: Default view not applied when viewing dataset from the 'Query' webpart
        // The Selector.js was initializing the view name of a portal QueryWebPart to "[default view]" rather than null and may be saved in the database.
        String viewName = webPart.getPropertyMap().get(QueryParam.viewName.name());
        if ("[default view]".equals(viewName))
            webPart.getPropertyMap().put(QueryParam.viewName.name(), null);

        QuerySettings settings = createQuerySettings(dataRegionName, null, null);
        (new BoundMap(settings)).putAll(webPart.getPropertyMap());
        settings.init(context);

        return settings;
    }

    public final QuerySettings getSettings(ViewContext context, String dataRegionName)
    {
        return getSettings(context, dataRegionName, null);
    }

    public final QuerySettings getSettings(ViewContext context, String dataRegionName, @Nullable String queryName)
    {
        return getSettings(context, dataRegionName, queryName, null);
    }

    public final QuerySettings getSettings(ViewContext context, String dataRegionName, @Nullable String queryName, @Nullable String viewName)
    {
        QuerySettings settings = createQuerySettings(dataRegionName, queryName, viewName);
        settings.init(context);
        settings.setSchemaName(getSchemaName());
        if (queryName != null)
        {
            settings.setQueryName(queryName);
        }
        if (viewName != null)
        {
            settings.setViewName(viewName);
        }
        return settings;
    }

    public final QuerySettings getSettings(PropertyValues pvs, String dataRegionName)
    {
        return getSettings(pvs, dataRegionName, null, null);
    }

    public final QuerySettings getSettings(String dataRegionName, @Nullable String queryName)
    {
        return getSettings(new MutablePropertyValues(), dataRegionName, queryName, null);
    }

    public final QuerySettings getSettings(PropertyValues pvs, String dataRegionName, @Nullable String queryName, @Nullable String viewName)
    {
        QuerySettings settings = createQuerySettings(dataRegionName, queryName, viewName);
        settings.init(pvs);
        settings.setSchemaName(getSchemaName());
        if (queryName != null)
        {
            settings.setQueryName(queryName);
        }
        if (viewName != null)
        {
            settings.setViewName(viewName);
        }
        return settings;
    }

    /**
     * Returns a schema suitable for use with ontology manager for the given query.
     * May return null if ontology manager is not supported for the query.
     * @param queryName The name of the query
     * @return A domain URI for ontology manager or null.
     */
    @Nullable
    public String getDomainURI(String queryName)
    {
        return null;
    }

    /**
     * Get a list of module custom view definitions for the schema/query from all modules or only the active modules in the container.
     * The list is cached in production mode.
     * @param container Used to determine active modules.
     * @param qd The query to which the views are attached
     */
    public List<CustomView> getModuleCustomViews(Container container, QueryDefinition qd)
    {
        // Look under <ROOT>/queries/<SCHEMA_NAME>/<QUERY_NAME> for custom views (.qview.xml) files
        // Also look under <ROOT>/queries/<SCHEMA_NAME>/<QUERY_LABEL>, if different

        List<String> parts = new ArrayList<>();

        String qdName = qd.getName();

        parts.add(QueryService.MODULE_QUERIES_DIRECTORY);
        for (String schemaPart : getSchemaPath().getParts())
        {
            String legalName = FileUtil.makeLegalName(schemaPart);
            parts.add(legalName);
        }
        parts.add(FileUtil.makeLegalName(qdName));

        return QueryService.get().getFileBasedCustomViews(container, qd, new Path(parts), qdName);
    }


    /**
     * Finds a TableInfo with the given domain URI.
     * This is expensive as each TableInfo and Domain in the schema is created just to ask for the Domain's URI.
     *
     * @param domainURI
     * @return The TableInfo if found.
     */
    @Nullable
    public TableInfo getTableForDomain(String domainURI)
    {
        Set<String> names = getTableNames();
        for (String name : names)
        {
            TableInfo table = getTable(name);
            if (table != null)
            {
                Domain domain = table.getDomain();
                if (domain != null && domainURI.equals(domain.getTypeURI()))
                    return table;
            }
        }

        return null;
    }

    @Override
    public <R, P> R accept(SchemaTreeVisitor<R, P> visitor, SchemaTreeVisitor.Path path, P param)
    {
        return visitor.visitUserSchema(this, path, param);
    }

    /** Gives the schema a chance to pick up reports associated with legacy report keys, like if the schema or query name has changed */
    public Collection<String> getReportKeys(String queryName)
    {
        return Collections.singleton(ReportUtil.getReportKey(getSchemaName(), queryName));
    }

    //
    // UserSchemaCustomizer methods
    //

    protected void fireAfterConstruct()
    {
        if (_schemaCustomizers != null)
            for (UserSchemaCustomizer customizer : _schemaCustomizers)
                customizer.afterConstruct(this);
    }

    protected void fireAfterConstruct(QueryDefinition def)
    {
        if (_schemaCustomizers != null)
            for (UserSchemaCustomizer customizer : _schemaCustomizers)
                customizer.afterConstruct(this, def);
    }

    protected void fireAfterConstruct(TableInfo table)
    {
        if (_schemaCustomizers != null)
            for (UserSchemaCustomizer customizer : _schemaCustomizers)
                customizer.afterConstruct(this, table);
    }

    @Override
    public String toMemTrackerString()
    {
        return "UserSchema: " + getSchemaPath() + " in " + getContainer().getPath();
    }

    // The schema for any table that has a Union version should override these
    public TableInfo getUnionTable(TableInfo tableInfo, Set<Container> containers)
    {
        return tableInfo;
    }

    @Override
    public NavTree getSchemaBrowserLinks(User user)
    {
        NavTree root = super.getSchemaBrowserLinks(user);
        if (hasRegisteredSchemaLinks())
        {
            Map<ActionURL, String> schemaLinks = QueryService.get().getSchemaLinks(getContainer());
            for (ActionURL actionURL : schemaLinks.keySet())
            {
                String actionLabel = schemaLinks.get(actionURL);
                actionURL.addParameter("schemaName", _name);
                root.addChild(actionLabel, actionURL);
            }
        }

        return root;
    }

    public void setHasRegisteredSchemaLinks(boolean hasRegisteredSchemaLinks)
    {
        this.hasRegisteredSchemaLinks = hasRegisteredSchemaLinks;
    }

    public boolean hasRegisteredSchemaLinks()
    {
        return this.hasRegisteredSchemaLinks;
    }

    /**
     * This provides a single method to translate a the value supplied on a row for container into a
     * proper Container object.  We accept: the containerId or a Container object.
     * If a value is supplied but cannot be resolved to a valid container then null is returned.
     */
    @Nullable
    public static Container translateRowSuppliedContainer(Object rowContainerVal, Container c, User u, TableInfo ti, Class<? extends Permission> clazz)
    {
        Container ret;
        if (rowContainerVal == null)
        {
            return null;
        }
        else if (rowContainerVal instanceof Container)
        {
            ret = (Container)rowContainerVal;
        }
        else
        {
            ret = ContainerManager.getForId(String.valueOf(rowContainerVal));
        }

        if (ret != null && !ret.equals(c))
        {
            verifyPermissionsForContainer(ret, c, u, ti, clazz);
        }

        return ret;
    }

    private static void verifyPermissionsForContainer(@NotNull Container rowContainer, Container originalContainer, User u, TableInfo ti, Class<? extends Permission> clazz)
    {
        Container permissionContainer = rowContainer.getContainerFor(ContainerType.DataType.permissions);

        // If a row supplies an alternate container, it is possible that permisions differ.  TableInfo can supply custom permissions.
        // If the effective permission container for the row-level container is actually the same as the original table (which is currently always true, such as Workbook->Parent,
        // then just defer to the original TableInfo.  If this is not the case, attempt to construct a new TableInfo and fail if we cannot do this.
        boolean hasPermission = false;
        if (originalContainer.equals(permissionContainer))
        {
            hasPermission = ti.hasPermission(u, clazz);
        }
        else if (ti.getUserSchema() != null)
        {
            UserSchema us = QueryService.get().getUserSchema(u, permissionContainer, ti.getUserSchema().getSchemaPath());
            if (us != null)
            {
                TableInfo rowTi = us.getTable(ti.getName());
                if (rowTi != null)
                {
                    hasPermission = rowTi.hasPermission(u, clazz);
                }
                else
                {
                    throw new UnauthorizedException("Unknown table: " + us.getSchemaName() + "." + ti.getName() + " in container: " + rowContainer.getPath());
                }
            }
            else
            {
                throw new UnauthorizedException("Unknown table: " + ti.getUserSchema().getSchemaName() + "." + ti.getName() + " in container: " + rowContainer.getPath());
            }
        }

        if (!hasPermission)
        {
            throw new UnauthorizedException("Insufficient permissions for folder: " + rowContainer.getPath());
        }
    }

    /**
     * UserSchema.getTable() implements support for caching TableInfos.
     * However, many ForeignKeys create tables w/o using getTable().  This method can be used to optimize
     * those scenario.  It is up to the caller to ensure proper caching when ContainerFilter may differ.  The
     * suggested way to do that is to call ContainerFilter.getCacheKey().
     *
     * Importantly, the key should reflect all the properties that affect how this table is created (e.g. additional filters, etc).
     */
    private Map<String,TableInfo> tableInfoCache = Collections.synchronizedMap(new CaseInsensitiveHashMap<TableInfo>());

    public TableInfo getCachedLookupTableInfo(String key, Callable<TableInfo> call)
    {
        TableInfo ret = null;
        if (null != key)
            ret = tableInfoCache.get(key);
        if (null == ret)
        {
            try
            {
                ret = call.call();
                if (null == ret)
                    return null;
            }
            catch (RuntimeException rex)
            {
                throw rex;
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
            assert ret.isLocked();
            ret.setLocked(true);
            tableInfoCache.put(key,ret);
        }
        return ret;
    }
}