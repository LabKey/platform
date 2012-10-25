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

package org.labkey.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Filter;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.MetadataException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.UniqueID;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.query.audit.QueryAuditViewFactory;
import org.labkey.query.audit.QueryUpdateAuditViewFactory;
import org.labkey.query.controllers.QueryController;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.QuerySnapshotDef;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.QueryTableInfo;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class QueryServiceImpl extends QueryService
{
    private static final Cache<String, Object> MODULE_RESOURCES_CACHE = CacheManager.getCache(10000, CacheManager.DAY, "Module resources cache");
    private static final String QUERYDEF_SET_CACHE_ENTRY = "QUERYDEFS:";
    private static final String QUERYDEF_METADATA_SET_CACHE_ENTRY = "QUERYDEFSMETADATA:";
    private static final String CUSTOMVIEW_SET_CACHE_ENTRY = "CUSTOMVIEW:";
    private static final Logger _log = Logger.getLogger(QueryServiceImpl.class);

    static public QueryServiceImpl get()
    {
        return (QueryServiceImpl)QueryService.get();
    }

    public UserSchema getUserSchema(User user, Container container, String schemaPath)
    {
        QuerySchema schema = DefaultSchema.get(user, container, schemaPath);
        if (schema instanceof UserSchema && schema.getName() != null)
            return (UserSchema)schema;

        return null;
    }

    public UserSchema getUserSchema(User user, Container container, SchemaKey schemaPath)
    {
        QuerySchema schema = DefaultSchema.get(user, container, schemaPath);
        if (schema instanceof UserSchema && schema.getName() != null)
            return (UserSchema)schema;

        return null;
    }

    public QueryDefinition createQueryDef(User user, Container container, String schema, String name)
    {
        return new CustomQueryDefinitionImpl(user, container, schema, name);
    }

    public QueryDefinition createQueryDef(User user, Container container, UserSchema schema, String name)
    {
        return new CustomQueryDefinitionImpl(user, container, schema, name);
    }

    public ActionURL urlQueryDesigner(User user, Container container, String schema)
    {
        return urlFor(user, container, QueryAction.begin, schema, null);
    }

    public ActionURL urlFor(User user, Container container, QueryAction action, @Nullable String schema, @Nullable String query)
    {
        ActionURL ret = null;

        if (schema != null && query != null)
        {
            UserSchema userschema = QueryService.get().getUserSchema(user, container, schema);
            if (userschema != null)
            {
                QueryDefinition queryDef = QueryService.get().getQueryDef(user, container, schema, query);
                if (queryDef == null)
                    queryDef = userschema.getQueryDefForTable(query);
                if (queryDef != null)
                    ret = userschema.urlFor(action, queryDef);
            }
        }

        // old behavior for backwards compatibility
        if (ret == null)
            ret = urlDefault(container, action, schema, query);

        return ret;
    }

    public ActionURL urlDefault(Container container, QueryAction action, @Nullable String schema, @Nullable String query)
    {
        if (action == QueryAction.schemaBrowser)
            action = QueryAction.begin;

        ActionURL ret = new ActionURL("query", action.toString(), container);
        if (schema != null)
            ret.addParameter(QueryParam.schemaName.toString(), schema);
        if (query != null)
            ret.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, query);
        return ret;
    }

    public DetailsURL urlDefault(Container container, QueryAction action, String schema, String query, Map<String, ?> params)
    {
        ActionURL url = urlDefault(container, action, schema, query);
        return new DetailsURL(url, params);
    }

    public DetailsURL urlDefault(Container container, QueryAction action, TableInfo table)
    {
        Map<String, FieldKey> params = new LinkedHashMap<String, FieldKey>();
        for (ColumnInfo pkCol : table.getPkColumns())
            params.put(pkCol.getColumnName(), pkCol.getFieldKey());

        return urlDefault(container, action, table.getPublicSchemaName(), table.getPublicName(), params);
    }

    public QueryDefinition createQueryDefForTable(UserSchema schema, String tableName)
    {
        return new TableQueryDefinition(schema, tableName);
    }

    public Map<String, QueryDefinition> getQueryDefs(User user, Container container, String schemaName)
    {
        Map<String, QueryDefinition> ret = new LinkedHashMap<String,QueryDefinition>();

        for (QueryDefinition queryDef : getAllQueryDefs(user, container, schemaName, true, false).values())
            ret.put(queryDef.getName(), queryDef);

        return ret;
    }

    public List<QueryDefinition> getQueryDefs(User user, Container container)
    {
        return new ArrayList<QueryDefinition>(getAllQueryDefs(user, container, null, true, false).values());
    }

    private Map<Map.Entry<String, String>, QueryDefinition> getAllQueryDefs(User user, Container container, @Nullable String schemaName, boolean inheritable, boolean includeSnapshots)
    {
        Map<Map.Entry<String, String>, QueryDefinition> ret = new LinkedHashMap<Map.Entry<String, String>, QueryDefinition>();

        // session queries have highest priority
        HttpServletRequest request = HttpView.currentRequest();
        if (request != null && schemaName != null)
        {
            for (QueryDefinition qdef : getAllSessionQueries(request, user, container, schemaName))
            {
                Map.Entry<String, String> key = new Pair<String,String>(schemaName, qdef.getName());
                ret.put(key, qdef);
            }
        }

        // look in all the active modules in this container to see if they contain any query definitions
        if (null != schemaName)
        {
            Path path = new Path(MODULE_QUERIES_DIRECTORY, schemaName);
            for (QueryDefinition queryDef : getFileBasedQueryDefs(user, container, schemaName, path))
            {
                Map.Entry<String, String> key = new Pair<String, String>(schemaName, queryDef.getName());
                if (!ret.containsKey(key))
                    ret.put(key, queryDef);
            }
        }

        // look in the database for query definitions
        for (QueryDef queryDef : QueryManager.get().getQueryDefs(container, schemaName, false, includeSnapshots, true))
        {
            Map.Entry<String, String> key = new Pair<String,String>(queryDef.getSchema(), queryDef.getName());
            if (!ret.containsKey(key))
                ret.put(key, new CustomQueryDefinitionImpl(user, queryDef));
        }

        if (!inheritable)
            return ret;

        Container containerCur = container;

        // look up the container hierarchy
        while (!containerCur.isRoot())
        {
            containerCur = containerCur.getParent();

            for (QueryDef queryDef : QueryManager.get().getQueryDefs(containerCur, schemaName, true, includeSnapshots, true))
            {
                Map.Entry<String, String> key = new Pair<String,String>(queryDef.getSchema(), queryDef.getName());

                if (!ret.containsKey(key))
                    ret.put(key, new CustomQueryDefinitionImpl(user, queryDef));
            }
        }

        // look in the Shared project
        for (QueryDef queryDef : QueryManager.get().getQueryDefs(ContainerManager.getSharedContainer(), schemaName, true, includeSnapshots, true))
        {
            Map.Entry<String, String> key = new Pair<String,String>(queryDef.getSchema(), queryDef.getName());

            if (!ret.containsKey(key))
                ret.put(key, new CustomQueryDefinitionImpl(user, queryDef));
        }

        return ret;
    }

    public List<QueryDefinition> getFileBasedQueryDefs(User user, Container container, String schemaName, Path path)
    {
        Collection<Module> modules = container.getActiveModules();
        List<QueryDefinition> ret = new ArrayList<QueryDefinition>();
        for (Module module : modules)
        {
            Collection<? extends Resource> queries;

            //always scan the file system in dev mode
            if (AppProps.getInstance().isDevMode())
            {
                Resource schemaDir = module.getModuleResolver().lookup(path);
                queries = getModuleQueries(schemaDir, ModuleQueryDef.FILE_EXTENSION);
            }
            else
            {
                //in production, cache the set of query defs for each module on first request
                String fileSetCacheKey = QUERYDEF_SET_CACHE_ENTRY + module.toString() + "." + schemaName + "." + path;
                //noinspection unchecked
                queries = (Collection<? extends Resource>) MODULE_RESOURCES_CACHE.get(fileSetCacheKey);

                if (null == queries)
                {
                    Resource schemaDir = module.getModuleResolver().lookup(path);
                    queries = getModuleQueries(schemaDir, ModuleQueryDef.FILE_EXTENSION);
                    MODULE_RESOURCES_CACHE.put(fileSetCacheKey, queries);
                }
            }

            if (null != queries)
            {
                for (Resource query : queries)
                {
                    String cacheKey = query.getPath().toString();
                    ModuleQueryDef moduleQueryDef = (ModuleQueryDef) MODULE_RESOURCES_CACHE.get(cacheKey);
                    if (null == moduleQueryDef || moduleQueryDef.isStale())
                    {
                        moduleQueryDef = new ModuleQueryDef(query, schemaName, module.getName());
                        MODULE_RESOURCES_CACHE.put(cacheKey, moduleQueryDef);
                    }

                    ret.add(new ModuleCustomQueryDefinition(moduleQueryDef, user, container));
                }
            }
        }
        return ret;
    }

    private Collection<? extends Resource> getModuleQueries(Resource schemaDir, String fileExtension)
    {
        if (schemaDir == null)
            return Collections.emptyList();

        Collection<? extends Resource> queries = schemaDir.list();
        List<Resource> result = new ArrayList<Resource>(queries.size());
        for (Resource query : queries)
            if (query.getName().endsWith(fileExtension))
                result.add(query);

        return result;
    }

    public QueryDefinition getQueryDef(User user, Container container, String schema, String name)
    {
        Map<String, QueryDefinition> ret = new CaseInsensitiveHashMap<QueryDefinition>();

        for (QueryDefinition queryDef : getAllQueryDefs(user, container, schema, true, true).values())
            ret.put(queryDef.getName(), queryDef);

        return ret.get(name);
    }

    private Map<String, CustomView> getCustomViewMap(User user, Container container, String schema, String query)
    {
        // Check for a custom query that matches
        Map<Map.Entry<String, String>, QueryDefinition> queryDefs = getAllQueryDefs(user, container, schema, false, true);
        QueryDefinition qd = queryDefs.get(new Pair<String, String>(schema, query));
        if (qd == null)
        {
            UserSchema userSchema = QueryService.get().getUserSchema(user, container, schema);
            if (userSchema != null)
            {
                // Get the built-in query from the schema
                qd = userSchema.getQueryDefForTable(query);
            }
        }

        if (qd != null)
        {
            return getCustomViewMap(user, container, qd, false);
        }
        return Collections.emptyMap();
    }

    protected Map<String, CustomView> getCustomViewMap(@Nullable User user, Container container, QueryDefinition qd, boolean inheritable)
    {
        Map<String, CustomView> views = new HashMap<String, CustomView>();

        // module query views have lower precedence, so add them first
        for (CustomView view : qd.getSchema().getModuleCustomViews(container, qd))
        {
            views.put(view.getName(), view);
        }

        // custom views in the database get highest precedence, so let them overwrite the module-defined views in the map
        for (CstmView cstmView : QueryManager.get().getAllCstmViews(container, qd.getSchema().getSchemaPath().toString(), qd.getName(), user, inheritable))
            views.put(cstmView.getName(), new CustomViewImpl(qd, cstmView));

        return views;
    }

    public CustomView getCustomView(User user, Container container, String schema, String query, String name)
    {
        Map<String, CustomView> views = getCustomViewMap(user, container, schema, query);
        return views.get(name);
    }

    public List<CustomView> getCustomViews(User user, Container container, @Nullable String schemaName, @Nullable String queryName)
    {
        if (schemaName == null || queryName == null)
        {
            // TODO - include module-based custom views (.qview.xml) in this list. Currently it only finds views
            // stored in the database
            List<CustomView> result = new ArrayList<CustomView>();
            Map<String, UserSchema> schemas = new HashMap<String, UserSchema>();
            Map<Pair<String, String>, QueryDefinition> queryDefs = new HashMap<Pair<String, String>, QueryDefinition>();
            for (CstmView cstmView : QueryManager.get().getAllCstmViews(container, schemaName, queryName, user, true))
            {
                Pair<String, String> key = new Pair<String, String>(cstmView.getSchema(), cstmView.getQueryName());
                QueryDefinition queryDef = queryDefs.get(key);
                if (queryDef == null)
                {
                    UserSchema schema = schemas.get(schemaName);
                    if (schema == null)
                    {
                        schema = getUserSchema(user, container, cstmView.getSchema());
                        schemas.put(cstmView.getSchema(), schema);
                    }
                    if (schema != null)
                    {
                        queryDef = schema.getQueryDefForTable(cstmView.getQueryName());
                        queryDefs.put(key, queryDef);
                    }
                }

                if (queryDef != null)
                {
                    result.add(new CustomViewImpl(queryDef, cstmView));
                }
            }
            return result;
        }

        return new ArrayList<CustomView>(getCustomViewMap(user, container, schemaName, queryName).values());
    }

    public List<CustomView> getFileBasedCustomViews(Container container, QueryDefinition qd, Path path)
    {
        List<CustomView> customViews = new ArrayList<CustomView>();

        String schema = qd.getSchema().getSchemaName();
        String query = qd.getName();

        for (Module module : container.getActiveModules())
        {
            Collection<? extends Resource> views;

            //always scan the file system in dev mode
            if (AppProps.getInstance().isDevMode())
            {
                Resource queryDir = module.getModuleResource(path);
                views = getModuleCustomViews(queryDir);
            }
            else
            {
                //in production, cache the set of custom view defs for each module on first request
                String fileSetCacheKey = CUSTOMVIEW_SET_CACHE_ENTRY + module.toString() + "." + schema + "." + query;
                views = (Collection<? extends Resource>)MODULE_RESOURCES_CACHE.get(fileSetCacheKey);

                if (null == views)
                {
                    Resource queryDir = module.getModuleResource(path);
                    views = getModuleCustomViews(queryDir);
                    MODULE_RESOURCES_CACHE.put(fileSetCacheKey, views);
                }
            }

            if (null != views)
            {
                for (Resource view : views)
                {
                    String cacheKey = view.getPath().toString();
                    ModuleCustomViewDef moduleCustomViewDef = (ModuleCustomViewDef)MODULE_RESOURCES_CACHE.get(cacheKey);
                    if (null == moduleCustomViewDef || moduleCustomViewDef.isStale())
                    {
                        try
                        {
                            moduleCustomViewDef = new ModuleCustomViewDef(view, schema, query);
                            MODULE_RESOURCES_CACHE.put(cacheKey, moduleCustomViewDef);
                        }
                        catch (UnexpectedException ex)
                        {
                            // XXX: log or throw?
                            Logger.getLogger(QueryServiceImpl.class).warn("Failed to load module custom view " + view, ex);
                        }
                    }

                    if (moduleCustomViewDef != null)
                        customViews.add(new ModuleCustomView(qd, moduleCustomViewDef));
                }
            }
        }

        return customViews;
    }

    /** Find any .qview.xml files under the given queryDir Resource. */
    private Collection<? extends Resource> getModuleCustomViews(Resource queryDir)
    {
        if (queryDir == null || !queryDir.isCollection())
            return Collections.emptyList();

        List<Resource> ret = new ArrayList<Resource>();
        for (String name : queryDir.listNames())
        {
            if (name.toLowerCase().endsWith(CustomViewXmlReader.XML_FILE_EXTENSION))
            {
                Resource queryViewXml = queryDir.find(name);
                if (queryViewXml != null)
                    ret.add(queryViewXml);
            }
        }
        return ret;
    }

    public int importCustomViews(User user, Container container, VirtualFile viewDir) throws XmlValidationException, IOException
    {
        QueryManager mgr = QueryManager.get();
        HttpServletRequest request = new MockHttpServletRequest();

        int count = 0;
        String[] viewXmlFileNames = viewDir.list();
        for (String viewFileName : viewXmlFileNames)
        {
            // skip over any files that don't end with the expected extension
            if (!viewFileName.endsWith(CustomViewXmlReader.XML_FILE_EXTENSION))
                continue;

            CustomViewXmlReader reader = CustomViewXmlReader.loadDefinition(viewDir.getInputStream(viewFileName), viewDir.getRelativePath(viewFileName));

            QueryDefinition qd = QueryService.get().createQueryDef(user, container, reader.getSchema(), reader.getQuery());
            String viewName = reader.getName();

            if (null == viewName)
                throw new IllegalStateException(viewFileName + ": Must specify a view name");

            try
            {
                // Get all shared views on this query with the same name
                CstmView[] views = mgr.getCstmViews(container, qd.getSchemaName(), qd.getName(), viewName, null, false);

                // Delete them
                for (CstmView view : views)
                    mgr.delete(null, view);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            // owner == null since we're exporting/importing only shared views
            CustomView cv = qd.createCustomView(null, reader.getName());
            cv.setColumnProperties(reader.getColList());
            cv.setFilterAndSort(reader.getFilterAndSortString());
            cv.setIsHidden(reader.isHidden());
            cv.save(user, request);

            count++;
        }

        return count;
    }


    public void updateCustomViewsAfterRename(@NotNull Container c, @NotNull String schema,
            @NotNull String oldQueryName, @NotNull String newQueryName)
    {
        QueryManager.get().updateViewsAfterRename(c,schema,oldQueryName,newQueryName);
    }

    public Map<String, Object> getCustomViewProperties(@Nullable CustomView view, @Nullable User currentUser)
    {
        return getCustomViewProperties(view, currentUser, true);
    }

    private Map<String, Object> getCustomViewProperties(@Nullable CustomView view, @Nullable User currentUser, boolean includeShadowed)
    {
        if (view == null)
            return null;

        Map<String, Object> ret = new LinkedHashMap<String, Object>();
        ret.put("name", view.getName() == null ? "" : view.getName());
        ret.put("default", view.getName() == null);
        if (null != view.getOwner())
            ret.put("owner", view.getOwner().getDisplayName(currentUser));
        ret.put("shared", view.isShared());
        ret.put("inherit", view.canInherit());
        ret.put("session", view.isSession());
        ret.put("editable", view.isEditable());
        ret.put("hidden", view.isHidden());
        // XXX: This is a query property and not a custom view property!
        ret.put("savable", !view.getQueryDefinition().isTemporary());
        // module custom views have no container
        ret.put("containerPath", view.getContainer() != null ? view.getContainer().getPath() : "");

        // Include view information about shadowed view
        if (includeShadowed && view.isSession())
        {
            CustomView shadowedView = view.getQueryDefinition().getCustomView(currentUser, null, view.getName());
            ret.put("shadowed", getCustomViewProperties(shadowedView, currentUser, false));
        }

        return ret;
    }


    private Map<String, QuerySnapshotDefinition> getAllQuerySnapshotDefs(Container container, String schemaName)
    {
        Map<String, QuerySnapshotDefinition> ret = new LinkedHashMap<String, QuerySnapshotDefinition>();

        for (QuerySnapshotDef queryDef : QueryManager.get().getQuerySnapshots(container, schemaName))
            ret.put(queryDef.getName(), new QuerySnapshotDefImpl(queryDef));

        return ret;
    }

    public QuerySnapshotDefinition getSnapshotDef(Container container, String schema, String name)
    {
        return getAllQuerySnapshotDefs(container, schema).get(name);
    }

    public boolean isQuerySnapshot(Container container, String schema, String name)
    {
        return QueryService.get().getSnapshotDef(container, schema, name) != null;
    }

    public List<QuerySnapshotDefinition> getQuerySnapshotDefs(Container container, String schema)
    {
        List<QuerySnapshotDefinition> ret = new ArrayList<QuerySnapshotDefinition>();

        for (QuerySnapshotDef queryDef : QueryManager.get().getQuerySnapshots(container, schema))
            ret.add(new QuerySnapshotDefImpl(queryDef));

        return ret;
    }

    private static class ContainerSchemaKey implements Serializable
    {
        private Container _container;
        private String _schema;

        public ContainerSchemaKey(Container container, String schema)
        {
            _container = container;
            _schema = schema;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ContainerSchemaKey that = (ContainerSchemaKey) o;

            if (!_container.equals(that._container)) return false;
            if (!_schema.equals(that._schema)) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _container.hashCode();
            result = 31 * result + _schema.hashCode();
            return result;
        }
    }

    public QueryDefinition saveSessionQuery(ViewContext context, Container container, String schemaName, String sql, String metadataXml)
    {
        Map<String, SessionQuery> queries = getSessionQueryMap(context.getRequest(), container, schemaName, true);
        String queryName = null;
        SessionQuery sq = new SessionQuery(sql, metadataXml);
        for (Map.Entry<String, SessionQuery> query : queries.entrySet())
        {
            if (query.getValue().equals(sq))
            {
                queryName = query.getKey();
                break;
            }
        }
        if (queryName == null)
        {
            queryName = schemaName + "-temp-" + UniqueID.getServerSessionScopedUID();
            queries.put(queryName, sq);
        }
        return getSessionQuery(context, container, schemaName, queryName);
    }

    @Override
    public QueryDefinition saveSessionQuery(ViewContext context, Container container, String schemaName, String sql)
    {
        return saveSessionQuery(context, container, schemaName, sql, null);
    }

    private static final String PERSISTED_TEMP_QUERIES_KEY = "LABKEY.PERSISTED_TEMP_QUERIES";
    private static class SessionQuery implements Serializable
    {
        String sql;
        String metadata;

        public SessionQuery(String sql, String metadata)
        {
            this.sql = sql;
            this.metadata = metadata;
        }

        @Override
        public int hashCode()
        {
            int result = sql.hashCode();
            if (metadata != null)
                result = 31 * result + metadata.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof SessionQuery)
            {
                SessionQuery sq = (SessionQuery)obj;
                if (!sql.equals(sq.sql))
                    return false;
                if (metadata == null && sq.metadata != null)
                    return false;
                if (metadata != null && !metadata.equals(sq.metadata))
                    return false;

                return true;
            }
            return false;
        }
    }

    private Map<String, SessionQuery> getSessionQueryMap(HttpServletRequest request, Container container, String schemaName, boolean create)
    {
        HttpSession session = request.getSession(create);
        if (session == null)
            return Collections.emptyMap();
        Map<ContainerSchemaKey, Map<String, SessionQuery>> containerQueries = (Map<ContainerSchemaKey, Map<String, SessionQuery>>) session.getAttribute(PERSISTED_TEMP_QUERIES_KEY);
        if (containerQueries == null)
        {
            containerQueries = new ConcurrentHashMap<ContainerSchemaKey, Map<String, SessionQuery>>();
            session.setAttribute(PERSISTED_TEMP_QUERIES_KEY, containerQueries);
        }
        ContainerSchemaKey key = new ContainerSchemaKey(container, schemaName);
        Map<String, SessionQuery> queries = containerQueries.get(key);
        if (queries == null)
        {
            queries = new ConcurrentHashMap<String, SessionQuery>();
            containerQueries.put(key, queries);
        }
        return queries;
    }

    private List<QueryDefinition> getAllSessionQueries(HttpServletRequest request, User user, Container container, String schemaName)
    {
        Map<String, SessionQuery> sessionQueries = getSessionQueryMap(request, container, schemaName, false);
        List<QueryDefinition> ret = new ArrayList<QueryDefinition>();
        for (Map.Entry<String, SessionQuery> entry : sessionQueries.entrySet())
            ret.add(createTempQueryDefinition(user, container, schemaName, entry.getKey(), entry.getValue()));
        return ret;
    }

    public QueryDefinition getSessionQuery(ViewContext context, Container container, String schemaName, String queryName)
    {
        SessionQuery query = getSessionQueryMap(context.getRequest(), container, schemaName, false).get(queryName);
        return createTempQueryDefinition(context.getUser(), container, schemaName, queryName, query);
    }

    private QueryDefinition createTempQueryDefinition(User user, Container container, String schemaName, String queryName, SessionQuery query)
    {
        QueryDefinition qdef = QueryService.get().createQueryDef(user, container, schemaName, queryName);
        qdef.setSql(query.sql);
        if (query.metadata != null)
            qdef.setMetadataXml(query.metadata);
        qdef.setIsTemporary(true);
        qdef.setIsHidden(true);
        return qdef;
    }

    public QuerySnapshotDefinition createQuerySnapshotDef(Container container, QueryDefinition queryDef, String name)
    {
        return new QuerySnapshotDefImpl(queryDef, container, name);
    }

    public QuerySnapshotDefinition createQuerySnapshotDef(QueryDefinition queryDef, String name)
    {
        return createQuerySnapshotDef(queryDef.getContainer(), queryDef, name);
    }

    private ColumnInfo getColumn(AliasManager manager, TableInfo table, Map<FieldKey, ColumnInfo> columnMap, FieldKey key)
    {
        if (key != null && key.getTable() == null)
        {
            String name = key.getName();
            ColumnInfo ret = table.getColumn(name);

            if (ret != null && key.getName().equals(table.getTitleColumn()) && ret.getEffectiveURL() == null)
            {
                List<ColumnInfo> pkColumns = table.getPkColumns();
                Set<FieldKey> pkColumnMap = new HashSet<FieldKey>();
                ContainerContext cc = table.getContainerContext();
                if (cc instanceof ContainerContext.FieldKeyContext)
                {
                    ContainerContext.FieldKeyContext fko = (ContainerContext.FieldKeyContext) cc;
                    pkColumnMap.add(fko.getFieldKey());
                }

                for (ColumnInfo column : pkColumns)
                    pkColumnMap.add(column.getFieldKey());

                StringExpression url = table.getDetailsURL(pkColumnMap, null);

                if (url != null)
                    ret.setURL(url);
            }

            if (ret != null && !AliasManager.isLegalName(ret.getName()) && !ret.isAliasSet())
                ret = new QAliasedColumn(ret.getName(), manager.decideAlias(key.toString()), ret);

            return ret;
        }

        if (columnMap.containsKey(key))
            return columnMap.get(key);

        if (key == null)
            return null;

        ColumnInfo parent = getColumn(manager, table, columnMap, key.getParent());

        if (parent == null)
            return null;

        ColumnInfo lookup = table.getLookupColumn(parent, StringUtils.trimToNull(key.getName()));

        if (lookup == null)
            return null;

        // When determining the alias, use the field key with the canonical casing for this column name, not the one
        // that was passed in. This makes sure that we generate the exact same JOIN SQL.
        AliasedColumn ret = new QAliasedColumn(key, manager.decideAlias(lookup.getFieldKey().toString()), lookup, true);
        columnMap.put(key, ret);

        return ret;
    }

    @NotNull
    public Map<FieldKey, ColumnInfo> getColumns(@NotNull TableInfo table, Collection<FieldKey> fields)
    {
        return getColumns(table, fields, Collections.<ColumnInfo>emptySet());
    }


    @NotNull
    public LinkedHashMap<FieldKey, ColumnInfo> getColumns(@NotNull TableInfo table, @NotNull Collection<FieldKey> fields, @NotNull Collection<ColumnInfo> existingColumns)
    {
        assert null != (existingColumns = Collections.unmodifiableCollection(existingColumns));
        assert Table.checkAllColumns(table, existingColumns, "QueryServiceImpl.getColumns() existingColums", false);

        AliasManager manager = new AliasManager(table, existingColumns);
        LinkedHashMap<FieldKey, ColumnInfo> ret = new LinkedHashMap<FieldKey,ColumnInfo>();
        Map<FieldKey, ColumnInfo> columnMap = new HashMap<FieldKey,ColumnInfo>();

        for (ColumnInfo existingColumn : existingColumns)
        {
            columnMap.put(existingColumn.getFieldKey(), existingColumn);
            ret.put(existingColumn.getFieldKey(), existingColumn);
        }

        for (FieldKey field : fields)
        {
            if (!ret.containsKey(field))
            {
                ColumnInfo column = getColumn(manager, table, columnMap, field);
                if (column != null)
                    ret.put(field, column);
            }
        }

        assert Table.checkAllColumns(table, ret.values(), "QueryServiceImpl.getColumns() ret", true);

        return ret;
    }


    public List<DisplayColumn> getDisplayColumns(TableInfo table, Collection<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> fields)
    {
        List<DisplayColumn> ret = new ArrayList<DisplayColumn>();
        Set<FieldKey> fieldKeys = new HashSet<FieldKey>();

        for (Map.Entry<FieldKey, ?> entry : fields)
            fieldKeys.add(entry.getKey());

        Map<FieldKey, ColumnInfo> columns = getColumns(table, fieldKeys);

        for (Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>> entry : fields)
        {
            ColumnInfo column = columns.get(entry.getKey());

            if (column == null)
                continue;

            DisplayColumn displayColumn = column.getRenderer();
            String caption = entry.getValue().get(CustomViewInfo.ColumnProperty.columnTitle);

            if (caption != null)
                displayColumn.setCaption(caption);

            ret.add(displayColumn);
        }

        return ret;
    }


    public Collection<ColumnInfo> ensureRequiredColumns(@NotNull TableInfo table, @NotNull Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, @Nullable Set<FieldKey> unresolvedColumns)
    {
        AliasManager manager = new AliasManager(table, columns);
        Set<FieldKey> selectedColumns = new HashSet<FieldKey>();
        Map<FieldKey, ColumnInfo> columnMap = new HashMap<FieldKey,ColumnInfo>();

        for (ColumnInfo column : columns)
        {
            FieldKey key = column.getFieldKey();
            selectedColumns.add(key);
            columnMap.put(key, column);
        }

        Set<FieldKey> fieldKeys = new HashSet<FieldKey>();

        if (filter != null)
            fieldKeys.addAll(filter.getWhereParamFieldKeys());

        if (sort != null)
            for (Sort.SortField field : sort.getSortList())
                fieldKeys.add(field.getFieldKey());

        ArrayList<ColumnInfo> ret = null;

        for (FieldKey field : fieldKeys)
        {
            if (field == null)
                continue;

            if (selectedColumns.contains(field))
                continue;

            ColumnInfo column = getColumn(manager, table, columnMap, field);

            if (column != null)
            {
                assert Table.checkColumn(table, column, "ensureRequiredColumns():");
                assert field.getTable() == null || columnMap.containsKey(field);

                if (null == ret)
                    ret = new ArrayList<ColumnInfo>(columns);

                ret.add(column);
            }
            else
            {
                if (unresolvedColumns != null)
                    unresolvedColumns.add(field);
            }
        }

        if (unresolvedColumns != null)
        {
            for (FieldKey field : unresolvedColumns)
            {
                if (filter instanceof SimpleFilter)
                {
                    SimpleFilter simpleFilter = (SimpleFilter) filter;
                    simpleFilter.deleteConditions(field);
                }

                if (sort != null)
                    sort.deleteSortColumn(field);
            }
        }
        assert null == ret || ret.size() > 0;
        return null == ret ? columns : ret;
    }


    public Map<String, UserSchema> getExternalSchemas(DefaultSchema folderSchema)
    {
        Map<String, UserSchema> ret = new HashMap<String, UserSchema>();
        ExternalSchemaDef[] defs = QueryManager.get().getExternalSchemaDefs(folderSchema.getContainer());

        for (ExternalSchemaDef def : defs)
        {
            try
            {
                UserSchema schema = ExternalSchema.get(folderSchema.getUser(), folderSchema.getContainer(), def);
                ret.put(def.getUserSchemaName(), schema);
            }
            catch (Exception e)
            {
                Logger.getLogger(QueryServiceImpl.class).warn("Could not load schema " + def.getDbSchemaName() + " from " + def.getDataSource(), e);
            }
        }

        return ret;
    }

    @Override
    public UserSchema getExternalSchema(DefaultSchema folderSchema, String name)
    {
        ExternalSchemaDef def = QueryManager.get().getExternalSchemaDef(folderSchema.getContainer(), name);

        if (null != def)
        {
            try
            {
                return ExternalSchema.get(folderSchema.getUser(), folderSchema.getContainer(), def);
            }
            catch (Exception e)
            {
                Logger.getLogger(QueryServiceImpl.class).warn("Could not load schema " + def.getDbSchemaName() + " from " + def.getDataSource(), e);
            }
        }

        return null;
    }

    @Override
    public UserSchema createSimpleUserSchema(String name, @Nullable String description, User user, Container container, DbSchema schema)
    {
        return new SimpleUserSchema(name, description, user, container, schema);
    }

    public List<ColumnInfo> getDefaultVisibleColumnInfos(List<ColumnInfo> columns)
    {
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>(columns.size());

        for (ColumnInfo column : columns)
        {
            if (column.isHidden())
                continue;

            if (column.isUnselectable())
                continue;

            if (column.isMvIndicatorColumn())
                continue;

            ret.add(column);
        }

        return ret;
    }

    public List<FieldKey> getDefaultVisibleColumns(List<ColumnInfo> columns)
    {
        List<FieldKey> ret = new ArrayList<FieldKey>();

        for (ColumnInfo column : getDefaultVisibleColumnInfos(columns))
        {
            ret.add(FieldKey.fromParts(column.getName()));
        }

        return ret;
    }

    public TableType findMetadataOverride(UserSchema schema, String tableName, boolean customQuery, boolean allModules, Collection<QueryException> errors, Path dir)
    {
        QueryDef queryDef = findMetadataOverrideImpl(schema, tableName, customQuery, allModules, dir);
        if (queryDef == null)
            return null;

        return parseMetadata(queryDef.getMetaData(), errors);
    }

    public TableType parseMetadata(String metadataXML, Collection<QueryException> errors)
    {
        if (metadataXML == null || StringUtils.isBlank(metadataXML))
            return null;

        XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
        List<XmlError> xmlErrors = new ArrayList<XmlError>();
        options.setErrorListener(xmlErrors);
        try
        {
            TablesDocument doc = TablesDocument.Factory.parse(metadataXML, options);
            TablesType tables = doc.getTables();
            if (tables != null && tables.sizeOfTableArray() > 0)
                return tables.getTableArray(0);
        }
        catch (XmlException e)
        {
            errors.add(new MetadataException(XmlBeansUtil.getErrorMessage(e)));
        }
        for (XmlError xmle : xmlErrors)
        {
            errors.add(new MetadataException(XmlBeansUtil.getErrorMessage(xmle)));
        }

        return null;
    }


    // BUGBUG: Should we look in the session queries for metadata overrides?
    public QueryDef findMetadataOverrideImpl(UserSchema schema, String tableName, boolean customQuery, boolean allModules, Path dir)
    {
        if (dir == null)
        {
            List<String> subDirs = new ArrayList<String>(schema.getSchemaPath().getParts().size() + 1);
            subDirs.add(QueryService.MODULE_QUERIES_DIRECTORY);
            subDirs.addAll(schema.getSchemaPath().getParts());
            dir = new Path(subDirs.toArray(new String[subDirs.size()]));
        }

        String schemaName = schema.getSchemaPath().toString();
        Container container = schema.getContainer();
        QueryDef queryDef;
        do
        {
            // Look up the folder hierarchy to try to find an override
            queryDef = QueryManager.get().getQueryDef(container, schemaName, tableName, customQuery);
            if (queryDef != null && (customQuery || queryDef.getMetaData() != null))
            {
                return queryDef;
            }
            container = container.getParent();
        }
        while (null != container && !container.isRoot());

        // Try the shared container too
        queryDef = QueryManager.get().getQueryDef(ContainerManager.getSharedContainer(), schemaName, tableName, customQuery);
        if (queryDef != null && queryDef.getMetaData() != null)
        {
            return queryDef;
        }

        // Finally, look for file-based definitions in modules
        Collection<Module> modules = allModules ? ModuleLoader.getInstance().getModules() : schema.getContainer().getActiveModules();
        for (Module module : modules)
        {
            Collection<? extends Resource> queryMetadatas;

            //always scan the file system in dev mode
            if (AppProps.getInstance().isDevMode())
            {
                Resource schemaDir = module.getModuleResolver().lookup(dir);
                queryMetadatas = getModuleQueries(schemaDir, ModuleQueryDef.META_FILE_EXTENSION);
            }
            else
            {
                //in production, cache the set of query defs for each module on first request
                String fileSetCacheKey = QUERYDEF_METADATA_SET_CACHE_ENTRY + module.toString() + "." + dir.toString();
                //noinspection unchecked
                queryMetadatas = (Collection<? extends Resource>) MODULE_RESOURCES_CACHE.get(fileSetCacheKey);

                if (null == queryMetadatas)
                {
                    Resource schemaDir = module.getModuleResolver().lookup(dir);
                    queryMetadatas = getModuleQueries(schemaDir, ModuleQueryDef.META_FILE_EXTENSION);
                    MODULE_RESOURCES_CACHE.put(fileSetCacheKey, queryMetadatas);
                }
            }

            if (null != queryMetadatas)
            {
                for (Resource query : queryMetadatas)
                {
                    String cacheKey = query.getPath().toString();
                    ModuleQueryMetadataDef metadataDef = (ModuleQueryMetadataDef) MODULE_RESOURCES_CACHE.get(cacheKey);
                    if (null == metadataDef || metadataDef.isStale())
                    {
                        metadataDef = new ModuleQueryMetadataDef(query);
                        MODULE_RESOURCES_CACHE.put(cacheKey, metadataDef);
                    }

                    if (metadataDef.getName().equalsIgnoreCase(tableName))
                    {
                        QueryDef result = metadataDef.toQueryDef(container);
                        result.setSchema(schemaName);
                        return result;
                    }
                }
            }
        }

        return null;
    }


    public ResultSet select(@NotNull QuerySchema schema, String sql, boolean strictColumnList) throws SQLException
	{
		Query q = new Query(schema);
        q.setStrictColumnList(strictColumnList);
		q.parse(sql);

		if (q.getParseErrors().size() > 0)
			throw q.getParseErrors().get(0);

		TableInfo table = q.getTableInfo();

		if (q.getParseErrors().size() > 0)
			throw q.getParseErrors().get(0);

        SQLFragment sqlf = getSelectSQL(table, null, null, null, Table.ALL_ROWS, Table.NO_OFFSET, false);

		return new SqlSelector(table.getSchema(), sqlf).getResultSet();
	}


    public void bindNamedParameters(SQLFragment frag, @Nullable Map<String,Object> in)
    {
        Map<String, Object> params = null == in ? Collections.<String, Object>emptyMap() :
                in instanceof CaseInsensitiveHashMap ? in :
                new CaseInsensitiveHashMap<Object>(in);

        List<Object> list = frag.getParams();
        for (int i=0 ; i<list.size() ; i++)
        {
            Object o = list.get(i);
            if (!(o instanceof ParameterDecl))
                continue;

            ParameterDecl p = (ParameterDecl)o;
            String name = p.getName();
            Object value = p.getDefault();
            boolean required = p.isRequired();
            boolean provided = null != value;

            if (params.containsKey(name))
            {
                value = params.get(p.getName());
                if (value instanceof String && ((String)value).isEmpty())
                    value = null;
                provided = true;
            }

            if (required && !provided)
            {
                continue; // maybe someone else will bind it....
            }

            Object converted = p.getType().convert(value);
            list.set(i, new Parameter.TypedValue(converted, p.getType()));
        }
    }


    // verify that named parameters have been bound
    public void validateNamedParameters(SQLFragment frag)
    {
        for (Object o : frag.getParams())
        {
            if (!(o instanceof ParameterDecl))
                continue;
            ParameterDecl p = (ParameterDecl)o;
            throw new NamedParameterNotProvided(p.getName());
        }
    }


    public Results select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, Map<String, Object> parameters) throws SQLException
    {
        SQLFragment sql = getSelectSQL(table, columns, filter, sort, Table.ALL_ROWS, Table.NO_OFFSET, false);
        bindNamedParameters(sql, parameters);
        validateNamedParameters(sql);
		ResultSet rs = Table.executeQuery(table.getSchema(), sql);
        return new ResultsImpl(rs, columns);
    }


	public SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> selectColumns, @Nullable Filter filter, @Nullable Sort sort,
                                    int maxRows, long offset, boolean forceSort)
	{
        assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for rowCount; should be positive, Table.ALL_ROWS or Table.NO_ROWS";

        if (null == selectColumns)
            selectColumns = table.getColumns();

        // Check incoming columns to ensure they come from table
        assert Table.checkAllColumns(table, selectColumns, "getSelectSQL() selectColumns", true);

        SqlDialect dialect = table.getSqlDialect();
        Map<String, SQLFragment> joins = new LinkedHashMap<String, SQLFragment>();
        List<ColumnInfo> allColumns = new ArrayList<ColumnInfo>(selectColumns);
        allColumns = (List<ColumnInfo>)ensureRequiredColumns(table, allColumns, filter, sort, null);

        // Check columns again: ensureRequiredColumns() may have added new columns
        assert Table.checkAllColumns(table, allColumns, "getSelectSQL() results of ensureRequiredColumns()", true);

        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(table, allColumns);
        boolean requiresExtraColumns = allColumns.size() > selectColumns.size();
        SQLFragment outerSelect = new SQLFragment("SELECT *");
        SQLFragment selectFrag = new SQLFragment("SELECT");
        String strComma = "\n";
        String tableName = table.getName();

        if (tableName == null)
        {
            // This shouldn't happen, but if it's null we'll blow up later without enough context to give a good error
            // message
            throw new NullPointerException("Null table name from " + table);
        }

        String tableAlias = AliasManager.makeLegalName(tableName, table.getSchema().getSqlDialect());

        if (allColumns.isEmpty())
        {
            selectFrag.append(" * ");
        }
        else
        {
            CaseInsensitiveHashMap<ColumnInfo> aliases = new CaseInsensitiveHashMap<ColumnInfo>();
            ColumnInfo prev;
            for (ColumnInfo column : allColumns)
            {
                if (null != (prev = aliases.put(column.getAlias(),column)))
                {
                    if (prev != column)
                        ExceptionUtil.logExceptionToMothership(null, new Exception("Duplicate alias in column list: " + table.getSchema() + "." + table.getName() + "." + column.getFieldKey().toSQLString() + " as " + column.getAlias()));
                    continue;
                }
                column.declareJoins(tableAlias, joins);
                selectFrag.append(strComma);
                selectFrag.append(column.getValueSql(tableAlias));
                selectFrag.append(" AS " );
                selectFrag.append(dialect.makeLegalIdentifier(column.getAlias()));
                strComma = ",\n";
            }
        }

        if (requiresExtraColumns)
        {
            outerSelect = new SQLFragment("SELECT ");
            strComma = "";

            for (ColumnInfo column : selectColumns)
            {
                outerSelect.append(strComma);
                outerSelect.append(dialect.getColumnSelectName(column.getAlias()));
                strComma = ", ";
            }
        }

		SQLFragment fromFrag = new SQLFragment("FROM ");
        fromFrag.append(table.getFromSQL(tableAlias));
        fromFrag.append(" ");

		for (Map.Entry<String, SQLFragment> entry : joins.entrySet())
		{
			fromFrag.append("\n").append(entry.getValue());
		}

		SQLFragment filterFrag = null;

		if (filter != null)
		{
			filterFrag = filter.getSQLFragment(dialect, columnMap);
		}

		if ((sort == null || sort.getSortList().size() == 0) &&
            (maxRows > 0 || offset > 0 || Table.NO_ROWS == maxRows || forceSort) &&
            // Don't add a sort if we're running a custom query and it has its own ORDER BY clause
            (!(table instanceof QueryTableInfo) || !((QueryTableInfo)table).hasSort()))
		{
			sort = createDefaultSort(selectColumns);
		}

        String orderBy = null;

		if (sort != null)
		{
			orderBy = sort.getOrderByClause(dialect, columnMap);
		}

		if ((filterFrag == null || filterFrag.getSQL().length()==0) && sort == null && Table.ALL_ROWS == maxRows && offset == 0)
		{
			selectFrag.append("\n").append(fromFrag);
			return selectFrag;
		}

		SQLFragment nestedFrom = new SQLFragment();
		nestedFrom.append("FROM (\n").append(selectFrag).append("\n").append(fromFrag).append(") x\n");
		SQLFragment ret = dialect.limitRows(outerSelect, nestedFrom, filterFrag, orderBy, null, maxRows, offset);

        if (AppProps.getInstance().isDevMode())
        {
            SQLFragment t = new SQLFragment();
            t.appendComment("<QueryServiceImpl.getSelectSQL(" + AliasManager.makeLegalName(table.getName(), dialect) + ")>", dialect);
            t.append(ret);
            t.appendComment("</QueryServiceImpl.getSelectSQL()>", dialect);
            String s = _prettyPrint(t.getSQL());
            ret = new SQLFragment(s, ret.getParams());
        }

	    return ret;
    }


    private static Sort createDefaultSort(Collection<ColumnInfo> columns)
	{
		Sort sort = new Sort();
		addSortableColumns(sort, columns, true);

		if (sort.getSortList().size() == 0)
		{
			addSortableColumns(sort, columns, false);
		}

		return sort;
	}


	private static void addSortableColumns(Sort sort, Collection<ColumnInfo> columns, boolean usePrimaryKey)
	{
		for (ColumnInfo column : columns)
		{
			if (usePrimaryKey && !column.isKeyField())
				continue;
			ColumnInfo sortField = column.getSortField();
			if (sortField != null)
			{
				sort.getSortList().add(sort.new SortField(sortField.getFieldKey(), column.getSortDirection()));
				return;
			}
		}
	}

    public void addQueryListener(QueryListener listener)
    {
        QueryManager.get().addQueryListener(listener);
    }

    private String _prettyPrint(String s)
    {
        StringBuilder sb = new StringBuilder(s.length() + 200);
        String[] lines = StringUtils.split(s, '\n');
        int indent = 0;

        for (String line : lines)
        {
            String t = line.trim();

            if (t.length() == 0)
                continue;

            if (t.startsWith("-- </"))
                indent = Math.max(0,indent-1);

            for (int i=0 ; i<indent ; i++)
                sb.append('\t');

            sb.append(line);
            sb.append('\n');

            if (t.startsWith("-- <") && !t.startsWith("-- </"))
                indent++;
        }

        return sb.toString();
    }


    private static class QAliasedColumn extends AliasedColumn
    {
        public QAliasedColumn(FieldKey key, String alias, ColumnInfo column, boolean forceKeepLabel)
        {
            super(column.getParentTable(), key, column, forceKeepLabel);
            setAlias(alias);
        }

        public QAliasedColumn(String name, String alias, ColumnInfo column)
        {
            super(column.getParentTable(), new FieldKey(null, name), column, true);
            setAlias(alias);
        }
    }


    private static ThreadLocal<HashMap<Environment,Object>> environments = new ThreadLocal<HashMap<Environment, Object>>()
    {
        @Override
        protected HashMap<Environment, Object> initialValue()
        {
            return new HashMap<Environment, Object>();
        }
    };


    @Override
    public void setEnvironment(QueryService.Environment e, Object value)
    {
        HashMap<Environment,Object> env = environments.get();
        env.put(e,e.type.convert(value));
    }

    public Object getEnvironment(QueryService.Environment e)
    {
        HashMap<Environment,Object> env = environments.get();
        return env.get(e);
    }

    @Override
    public Object cloneEnvironment()
    {
        HashMap<Environment,Object> env = environments.get();
        return new HashMap<Environment,Object>(env);
    }

    @Override
    public void copyEnvironment(Object o)
    {
        HashMap<Environment,Object> env = environments.get();
        env.clear();
        env.putAll((HashMap<Environment,Object>)o);
    }


    @Override
    public void clearEnvironment()
    {
        environments.get().clear();
    }

    @Override
    public void addAuditEvent(QueryView queryView, String comment)
    {
        QueryDefinition query = queryView.getQueryDef();
        if (query == null)
            return;

        String schemaName = query.getSchemaName();
        String queryName = query.getName();
        ActionURL sortFilter = queryView.getSettings().getSortFilterURL();
        addAuditEvent(queryView.getUser(), queryView.getContainer(), schemaName, queryName, sortFilter, comment);
    }

    @Override
    public void addAuditEvent(User user, Container c, String schemaName, String queryName, ActionURL sortFilter, String comment)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(user);
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        event.setContainerId(c.getId());
        event.setEventType(QueryAuditViewFactory.QUERY_AUDIT_EVENT);
        event.setComment(comment);
        event.setKey1(schemaName);
        event.setKey2(queryName);

        ActionURL url = sortFilter.clone();
        url.deleteParameter(ActionURL.Param.cancelUrl);
        url.deleteParameter(ActionURL.Param.redirectUrl);
        url.deleteParameter(ActionURL.Param.returnUrl);
        DetailsURL detailsURL = new DetailsURL(url);
        event.setKey3(detailsURL.toString());

        AuditLogService.get().addEvent(event);
    }

    @Override
    public void addAuditEvent(User user, Container c, TableInfo table, AuditAction action, List<Map<String, Object>> ... params)
    {
        AuditBehaviorType auditType = table.getAuditBehavior();

        switch (auditType)
        {
            case NONE:
                return;

            case SUMMARY:
            {
                assert (params.length > 0);

                List<Map<String, Object>> rows = params[0];
                String comment = String.format(action.getCommentSummary(), rows.size());
                AuditLogEvent event = _createAuditRecord(user, c, table, comment, rows.get(0));

                AuditLogService.get().addEvent(event);
                break;
            }
            case DETAILED:
            {
                assert (params.length > 0);

                List<Map<String, Object>> rows = params[0];
                for (int i=0; i < rows.size(); i++)
                {
                    Map<String,Object> dataMap = new HashMap<String,Object>();
                    Map<String, Object> row = rows.get(i);
                    String comment = String.format(action.getCommentDetailed(), row.size());

                    AuditLogEvent event = _createAuditRecord(user, c, table, comment, row);
                    String oldRecord = QueryAuditViewFactory.encodeForDataMap(row);

                    switch (action)
                    {
                        case INSERT:
                            if (oldRecord != null)
                                dataMap.put(QueryAuditViewFactory.NEW_RECORD_PROP_NAME, oldRecord);
                            break;

                        case DELETE:
                            if (oldRecord != null)
                                dataMap.put(QueryAuditViewFactory.OLD_RECORD_PROP_NAME, oldRecord);
                            break;

                        case UPDATE:
                        {
                            assert (params.length >= 2);

                            if (oldRecord != null)
                                dataMap.put(QueryAuditViewFactory.OLD_RECORD_PROP_NAME, oldRecord);

                            List<Map<String, Object>> updatedRows = params[1];
                            Map<String, Object> updatedRow = updatedRows.get(i);

                            String newRecord = QueryAuditViewFactory.encodeForDataMap(updatedRow);
                            if (newRecord != null)
                                dataMap.put(QueryAuditViewFactory.NEW_RECORD_PROP_NAME, newRecord);
                            break;
                        }
                    }
                    AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(QueryUpdateAuditViewFactory.QUERY_UPDATE_AUDIT_EVENT));
                }
                break;
            }
        }
    }

    private static AuditLogEvent _createAuditRecord(User user, Container c, TableInfo tinfo, String comment, @Nullable Map<String, Object> row)
    {
        AuditLogEvent event = new AuditLogEvent();

        event.setCreatedBy(user);
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        event.setContainerId(c.getId());
        event.setEventType(QueryUpdateAuditViewFactory.QUERY_UPDATE_AUDIT_EVENT);
        event.setComment(comment);
        event.setKey2(tinfo.getPublicSchemaName());
        event.setKey3(tinfo.getPublicName());

        FieldKey rowPk = tinfo.getAuditRowPk();
        if (rowPk != null && row != null)
        {
            if (row.containsKey(rowPk.toString()))
            {
                Object pk = row.get(rowPk.toString());
                event.setKey1(String.valueOf(pk));
            }
        }
        return event;
    }

    @Override
    public @Nullable ActionURL getAuditHistoryURL(User user, Container c, TableInfo table)
    {
        AuditBehaviorType auditBehavior = table.getAuditBehavior();

        if (auditBehavior != null && auditBehavior != AuditBehaviorType.NONE)
        {
            return new ActionURL(QueryController.AuditHistoryAction.class, c).
                    addParameter(QueryParam.schemaName, table.getPublicSchemaName()).
                    addParameter(QueryParam.queryName, table.getPublicName());
        }

        return null;
    }

    @Override
    public DetailsURL getAuditDetailsURL(User user, Container c, TableInfo table)
    {
        FieldKey rowPk = table.getAuditRowPk();

        if (rowPk != null)
        {
            ActionURL url = new ActionURL(QueryController.AuditDetailsAction.class, c).
                    addParameter(QueryParam.schemaName, table.getPublicSchemaName()).
                    addParameter(QueryParam.queryName, table.getName());

            return new DetailsURL(url, Collections.singletonMap("keyValue", rowPk));
        }
        return null;
    }

    public static class TestCase extends Assert
    {
        ResultSet rs = null;

	    void _close()
	    {
		    rs = ResultSetUtil.close(rs);
	    }

        @Test
        public void testSelect() throws SQLException
        {
            QueryService qs = ServiceRegistry.get().getService(QueryService.class);
            assertNotNull(qs);
            assertEquals(qs, QueryService.get());
            TableInfo issues = DbSchema.get("issues").getTable("issues");
            assertNotNull(issues);

            {
				List<ColumnInfo> l = Arrays.asList(
					issues.getColumn("issueid"),
					issues.getColumn("title"),
					issues.getColumn("status"));
				rs = qs.select(issues, l, null, null);
				assertEquals(rs.getMetaData().getColumnCount(),3);
				_close();
            }


	        {
				List<ColumnInfo> l = Arrays.asList(
					issues.getColumn("issueid"),
					issues.getColumn("title"),
					issues.getColumn("status"));
		        Sort sort = new Sort("+milestone");
				rs = qs.select(issues, l, null, sort);
				assertEquals(rs.getMetaData().getColumnCount(),3);
		        _close();
	        }

	        {
				List<ColumnInfo> l = Arrays.asList(
					issues.getColumn("issueid"),
					issues.getColumn("title"),
					issues.getColumn("status"));
				Filter f = new SimpleFilter("assignedto",1001);
				rs = qs.select(issues, l, f, null);
				assertEquals(rs.getMetaData().getColumnCount(),3);
		        _close();
	        }

	        {
		        Map<FieldKey,ColumnInfo> map = qs.getColumns(issues, Arrays.asList(
				        new FieldKey(null, "issueid"),
				        new FieldKey(null, "title"),
				        new FieldKey(null, "status"),
				        new FieldKey(new FieldKey(null, "createdby"), "email")));
		        Sort sort = new Sort("+milestone");
				Filter f = new SimpleFilter("assignedto",1001);
				rs = qs.select(issues, map.values(), f, sort);
				assertEquals(rs.getMetaData().getColumnCount(),4);
		        _close();
	        }
        }

	    @After
	    public void tearDown() throws Exception
	    {
		    _close();
	    }
    }
}
