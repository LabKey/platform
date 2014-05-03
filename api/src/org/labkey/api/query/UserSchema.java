/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CrosstabTableInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UserSchemaCustomizer;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MemTrackable;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.Portal;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.VisualizationProvider;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


abstract public class UserSchema extends AbstractSchema implements MemTrackable
{
    protected String _name;
    protected SchemaKey _path;
    protected String _description;
    protected boolean _cacheTableInfos = false;
    protected boolean _restricted = false;      // restricted schemas will return null from getSchema()
    protected final Collection<UserSchemaCustomizer> _schemaCustomizers;

    public UserSchema(String name, @Nullable String description, User user, Container container, DbSchema dbSchema)
    {
        this(SchemaKey.fromParts(name), description, user, container, dbSchema, null);
    }

    public UserSchema(SchemaKey path, @Nullable String description, User user, Container container, DbSchema dbSchema, Collection<UserSchemaCustomizer> schemaCustomizers)
    {
        super(dbSchema, user, container);
        _name = path.getName();
        _path = path;
        _description = description;
        _schemaCustomizers = schemaCustomizers;
        assert MemTracker.getInstance().put(this);
    }

    public String getName()
    {
        return _name;
    }

    public SchemaKey getPath()
    {
        return _path;
    }

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
        return getContainer().hasPermission(getName() + ".canReadSchema()", user, ReadPermission.class);
    }

    @Nullable
    public TableInfo getTable(String name, boolean includeExtraMetadata)
    {
        return getTable(name, includeExtraMetadata, false);
    }


    /** if forWrite==true, do not return a cached version */
    public TableInfo getTable(String name, boolean includeExtraMetadata, boolean forWrite)
    {
        ArrayList<QueryException> errors = new ArrayList<>();
        Object o = _getTableOrQuery(name, includeExtraMetadata, forWrite, errors);
        if (o instanceof TableInfo)
        {
            assert validateTableInfo((TableInfo)o);
            return (TableInfo)o;
        }
        if (o instanceof QueryDefinition)
        {
            TableInfo t = ((QueryDefinition)o).getTable(this, errors, true);
            if (!errors.isEmpty())
                throw errors.get(0);
            assert validateTableInfo(t);
            return t;
        }
        return null;
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


    Map<Pair<String, Boolean>, Object> cache = new HashMap<>();

    public Object _getTableOrQuery(String name, boolean includeExtraMetadata, boolean forWrite, Collection<QueryException> errors)
    {
        if (name == null)
            return null;

        if (!canReadSchema())
            throw new UnauthorizedException("Cannot read query " + getSchemaName() + "." + name + " in " + getContainer().getPath());

        Pair<String,Boolean> key = new Pair<>(name.toLowerCase(), includeExtraMetadata);
        boolean useCache = _cacheTableInfos && !forWrite;
        Object torq;

        if (useCache)
        {
            torq = cache.get(key);
            if (null != torq)
                return torq;
        }

        TableInfo table = createTable(name);
        if (table != null)
        {
            if (includeExtraMetadata)
                table.overlayMetadata(name, this, errors);
            afterConstruct(table);
            // should just be !forWrite, but exp schema is still a problem
            if (useCache)
                table.setLocked(true);
            fireAfterConstruct(table);
            torq = table;
        }
        else
        {
            QueryDefinition def = getQueryDefs().get(name);
            if (def == null)
                return null;
            if (!includeExtraMetadata && def.isMetadataEditable())
            {
                def.setMetadataXml(null);
            }

            fireAfterConstruct(def);
            torq = def;
        }

        if (false && useCache)
           cache.put(key,torq);

        MemTracker.getInstance().put(torq);
        return torq;
    }

    @Nullable
    public final TableInfo getTable(String name)
    {
        return getTable(name, true);
    }

    protected abstract @Nullable TableInfo createTable(String name);

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

    public Container getContainer()
    {
        return _container;
    }

    /** Returns a SchemaKey encoded name for this schema. */
    public String getSchemaName()
    {
        return _path.toString();
    }

    public SchemaKey getSchemaPath()
    {
        return _path;
    }

    public Set<String> getSchemaNames()
    {
        Set<String> ret = new HashSet<>(super.getSchemaNames());
        ret.add("Folder");
        return ret;
    }

    public QuerySchema getSchema(String name)
    {
        if (_restricted)
            return null;
        return DefaultSchema.get(_user, _container).getSchema(name);
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
    public ActionURL urlFor(QueryAction action, QueryDefinition queryDef)
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
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
//        // HACK: until I figure out a better way to create QueryView subclasses based upon TableInfo type
        QueryDefinition qdef = settings.getQueryDef(this);
        if (qdef != null)
        {
            TableInfo tableInfo = qdef.getTable(this, new ArrayList<QueryException>(), true);
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
        TreeMap<String, String> namesAndLabels = new TreeMap<>(new Comparator<String>()
        {
            @Override
            public int compare(String o1, String o2)
            {
                return o1.compareToIgnoreCase(o2);
            }
        });

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
        TreeMap<String, QueryDefinition> set = new TreeMap<>(new Comparator<String>()
        {
            @Override
            public int compare(String o1, String o2)
            {
                return o1.compareToIgnoreCase(o2);
            }
        });

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
        return new CaseInsensitiveHashMap<>(QueryService.get().getQueryDefs(getUser(), getContainer(), getSchemaName()));
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
        QuerySettings settings = createQuerySettings(dataRegionName, null, null);
        settings.init(pvs);
        settings.setSchemaName(getSchemaName());

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
        List<String> partsLabel = null;
        if (!qd.getName().equals(qd.getTitle()))
        {
            partsLabel = new ArrayList<>();
        }

        List<String> parts = new ArrayList<>();
        parts.add(QueryService.MODULE_QUERIES_DIRECTORY);
        if (null != partsLabel)
            partsLabel.add(QueryService.MODULE_QUERIES_DIRECTORY);
        for (String schemaPart : getSchemaPath().getParts())
        {
            String legalName = FileUtil.makeLegalName(schemaPart);
            parts.add(legalName);
            if (null != partsLabel)
                partsLabel.add(legalName);
        }

        parts.add(FileUtil.makeLegalName(qd.getName()));
        if (null != partsLabel)
            partsLabel.add(FileUtil.makeLegalName(qd.getTitle()));

        List<CustomView> views = QueryService.get().getFileBasedCustomViews(container, qd, new Path(parts), qd.getName());
        if (null != partsLabel)
            views.addAll(QueryService.get().getFileBasedCustomViews(container, qd, new Path(partsLabel), qd.getTitle()));

        return views;
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

    public ContainerFilter getOlapContainerFilter(User user)
    {
        return null;
    }

    @Override
    public String toMemTrackerString()
    {
        return "UserSchema: " + getSchemaPath() + " in " + getContainer().getPath();
    }
}
