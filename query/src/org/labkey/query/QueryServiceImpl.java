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

package org.labkey.query;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.MultiValuedMapCollectors;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.query.*;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UniqueID;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.data.xml.externalSchema.TemplateSchemaDocument;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.data.xml.queryCustomView.OperatorType;
import org.labkey.query.audit.QueryExportAuditProvider;
import org.labkey.query.audit.QueryUpdateAuditProvider;
import org.labkey.query.controllers.QueryController;
import org.labkey.query.olap.ServerManager;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.LinkedSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.QuerySnapshotDef;
import org.labkey.query.sql.Method;
import org.labkey.query.sql.QExpr;
import org.labkey.query.sql.QField;
import org.labkey.query.sql.QIfDefined;
import org.labkey.query.sql.QInternalExpr;
import org.labkey.query.sql.QMethodCall;
import org.labkey.query.sql.QNode;
import org.labkey.query.sql.QQuery;
import org.labkey.query.sql.QRowStar;
import org.labkey.query.sql.QUnion;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.QueryTableInfo;
import org.labkey.query.sql.SqlBuilder;
import org.labkey.query.sql.SqlParser;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.gwt.client.AuditBehaviorType.SUMMARY;


public class QueryServiceImpl implements QueryService
{
    private static final Logger LOG = Logger.getLogger(QueryServiceImpl.class);
    private static final ResourceRootProvider QUERY_AND_ASSAY_PROVIDER = new ResourceRootProvider()
    {
        private ResourceRootProvider ASSAY_QUERY = ResourceRootProvider.chain(ResourceRootProvider.getAssayProviders(Path.rootPath), ResourceRootProvider.QUERY);

        @Override
        public void fillResourceRoots(@NotNull Resource topRoot, @NotNull Collection<Resource> roots)
        {
            ResourceRootProvider.QUERY.fillResourceRoots(topRoot, roots);
            ASSAY_QUERY.fillResourceRoots(topRoot, roots);
        }
    };

    private static final ModuleResourceCache<MultiValuedMap<Path, ModuleQueryDef>> MODULE_QUERY_DEF_CACHE = ModuleResourceCaches.create("Module query definitions cache", new QueryDefResourceCacheHandler(), QUERY_AND_ASSAY_PROVIDER);
    private static final ModuleResourceCache<MultiValuedMap<Path, ModuleQueryMetadataDef>> MODULE_QUERY_METADATA_DEF_CACHE = ModuleResourceCaches.create("Module query meta data cache", new QueryMetaDataDefResourceCacheHandler(), QUERY_AND_ASSAY_PROVIDER);
    private static final ModuleResourceCache<MultiValuedMap<Path, ModuleCustomViewDef>> MODULE_CUSTOM_VIEW_CACHE = ModuleResourceCaches.create("Module custom view definitions cache", new CustomViewResourceCacheHandler(), QUERY_AND_ASSAY_PROVIDER);

    private static final Cache<String, List<String>> NAMED_SET_CACHE = CacheManager.getCache(100, CacheManager.DAY, "Named sets for IN clause cache");
    private static final String NAMED_SET_CACHE_ENTRY = "NAMEDSETS:";

    private final ConcurrentMap<Class<? extends Controller>, Pair<Module,String>> _schemaLinkActions = new ConcurrentHashMap<>();

    private final List<CompareType> COMPARE_TYPES = new CopyOnWriteArrayList<>(Arrays.asList(
            CompareType.EQUAL,
            CompareType.DATE_EQUAL,
            CompareType.NEQ,
            CompareType.DATE_NOT_EQUAL,
            CompareType.NEQ_OR_NULL,
            CompareType.GT,
            CompareType.DATE_GT,
            CompareType.LT,
            CompareType.DATE_LT,
            CompareType.GTE,
            CompareType.DATE_GTE,
            CompareType.LTE,
            CompareType.DATE_LTE,
            CompareType.STARTS_WITH,
            CompareType.DOES_NOT_START_WITH,
            CompareType.CONTAINS,
            CompareType.DOES_NOT_CONTAIN,
            CompareType.CONTAINS_ONE_OF,
            CompareType.CONTAINS_NONE_OF,
            CompareType.IN,
            CompareType.NOT_IN,
            CompareType.IN_NS,
            CompareType.NOT_IN_NS,
            CompareType.BETWEEN,
            CompareType.NOT_BETWEEN,
            CompareType.MEMBER_OF,
            CompareType.ISBLANK,
            CompareType.NONBLANK,
            CompareType.MV_INDICATOR,
            CompareType.NO_MV_INDICATOR,
            CompareType.Q,
            WHERE
    ));

    public static final CompareType WHERE = new CompareType("WHERE", "where", "WHERE", true /* dataValueRequired */, "sql", OperatorType.WHERE)
    {
        @Override
        protected WhereClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new WhereClause((String) value);
        }
    };

    // This is defined here due to the usage of QExpr
    private static class WhereClause extends CompareType.CompareClause
    {
        final QExpr expr;

        WhereClause(String value)
        {
            super(new FieldKey(null, "*"), WHERE, value);
            _displayFilterText = true;

            String expression = (String)getParamVals()[0];
            List<QueryParseException> errors = new ArrayList<>();
            QExpr parseResult;
            if (StringUtils.isBlank(value))
                parseResult = null;
            else
                parseResult = new SqlParser(null, null).parseExpr(expression, errors);
            expr = parseResult;
            if (!errors.isEmpty())
                throw new ConversionException(errors.get(0));
        }

        @Override
        protected void appendFilterText(StringBuilder sb, SimpleFilter.ColumnNameFormatter formatter)
        {
            sb.append(getParamVals()[0]);
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            return (String)getParamVals()[0];
        }

        @Override
        public List<String> getColumnNames()
        {
            return getFieldKeys().stream()
                .filter(f -> null==f.getParent())
                .map(f -> f.getName())
                .collect(Collectors.toList());
        }

        @Override
        public List<FieldKey> getFieldKeys()
        {
            if (null == expr)
                return Collections.emptyList();
            Set<FieldKey> set = new HashSet<>();
            collectKeys(expr, set);
            return new ArrayList<>(set);
        }

        private void collectKeys(QExpr expr, Set<FieldKey> set)
        {
            if (expr instanceof QQuery || expr instanceof QUnion || expr instanceof QRowStar || expr instanceof QIfDefined)
                return;
            FieldKey key = expr.getFieldKey();
            if (key != null)
                set.add(key);

            QExpr methodName = null;
            if (expr instanceof QMethodCall)
            {
                methodName = (QExpr)expr.childList().get(0);
                if (null == methodName.getFieldKey())
                    methodName = null;
            }

            for (QNode child : expr.children())
            {
                // skip identifier that is actually a method
                if (child != methodName)
                    collectKeys((QExpr)child, set);
            }
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            String expression = (String)getParamVals()[0];
            if (StringUtils.isBlank(expression))
                return new SQLFragment("1=1");
            // reparse because we have a dialect now
            List<QueryParseException> errors = new ArrayList<>();
            QExpr expr = new SqlParser(dialect, null).parseExpr(expression, errors);
            if (null == expr || !errors.isEmpty())
                return new SQLFragment("0=1"); // UNDONE: error reporting
            QExpr bound = bind(expr, columnMap);
            return bound.getSqlFragment(dialect, null);
        }

        private QExpr bind(QExpr expr, Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            if (expr instanceof QQuery || expr instanceof QUnion || expr instanceof QRowStar || expr instanceof QIfDefined)
            {
                throw new UnsupportedOperationException("unsupported expression");
            }

            FieldKey key = expr.getFieldKey();
            if (key != null)
            {
                ColumnInfo c = columnMap.get(key);
                if (null == c)
                    throw new UnsupportedOperationException("column not found: " + key.toString());
                return new QColumnInfo(c);
            }

            QExpr methodName = null;
            if (expr instanceof QMethodCall)
            {
                methodName = (QExpr)expr.childList().get(0);
                if (null == methodName.getFieldKey())
                    methodName = null;
            }

            QExpr ret = (QExpr) expr.clone();
            for (QNode child : expr.children())
            {
                if (child == methodName)
                    ret.appendChild(new QField(null, ((QExpr)child).getFieldKey().getName(), child));
                else
                    ret.appendChild(bind((QExpr)child, columnMap));
            }
            return ret;
        }
    }

    public static class QColumnInfo extends QInternalExpr
    {
        private final ColumnInfo _col;

        QColumnInfo(ColumnInfo col)
        {
            _col = col;
        }

        @Override
        public void appendSql(SqlBuilder builder, Query query)
        {
            builder.append(_col.getAlias());
        }

        @Override
        public boolean isConstant()
        {
            return false;
        }
    }




    static public QueryServiceImpl get()
    {
        return (QueryServiceImpl)QueryService.get();
    }

    public UserSchema getUserSchema(User user, Container container, String schemaPath)
    {
        QuerySchema schema = DefaultSchema.get(user, container, schemaPath);
        if (schema instanceof UserSchema && !((UserSchema)schema).isFolder())
            return (UserSchema)schema;

        return null;
    }

    public UserSchema getUserSchema(User user, Container container, SchemaKey schemaPath)
    {
        QuerySchema schema = DefaultSchema.get(user, container, schemaPath);
        if (schema instanceof UserSchema && !((UserSchema)schema).isFolder())
            return (UserSchema)schema;

        return null;
    }

    @Deprecated /* Use SchemaKey form instead. */
    public QueryDefinition createQueryDef(User user, Container container, String schema, String name)
    {
        return new CustomQueryDefinitionImpl(user, container, SchemaKey.fromString(schema), name);
    }

    public QueryDefinition createQueryDef(User user, Container container, SchemaKey schema, String name)
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
        Map<String, FieldKey> params = new LinkedHashMap<>();
        for (ColumnInfo pkCol : table.getPkColumns())
            params.put(pkCol.getColumnName(), pkCol.getFieldKey());

        return urlDefault(container, action, table.getPublicSchemaName(), table.getPublicName(), params);
    }

    public QueryDefinition createQueryDefForTable(UserSchema schema, String tableName)
    {
        return new TableQueryDefinition(schema, tableName);
    }

    public Map<String, QueryDefinition> getQueryDefs(User user, @NotNull Container container, String schemaName)
    {
        Map<String, QueryDefinition> ret = new LinkedHashMap<>();

        for (QueryDefinition queryDef : getAllQueryDefs(user, container, schemaName, true, false).values())
            ret.put(queryDef.getName(), queryDef);

        return ret;
    }

    public List<QueryDefinition> getQueryDefs(User user, @NotNull Container container)
    {
        return new ArrayList<>(getAllQueryDefs(user, container, null, true, false).values());
    }


    private Map<Entry<String, String>, QueryDefinition> getAllQueryDefs(User user, @NotNull Container container, @Nullable String schemaName, boolean inheritable, boolean includeSnapshots)
    {
        Map<Entry<String, String>, QueryDefinition> ret = new LinkedHashMap<>();

        // session queries have highest priority
        HttpServletRequest request = HttpView.currentRequest();
        if (request != null && schemaName != null)
        {
            for (QueryDefinition qdef : getAllSessionQueries(request, user, container, schemaName))
            {
                Entry<String, String> key = new Pair<>(schemaName, qdef.getName());
                ret.put(key, qdef);
            }
        }

        // look in all the active modules in this container to see if they contain any query definitions
        if (null != schemaName)
        {
            Path path = createSchemaPath(SchemaKey.fromString(schemaName));
            for (QueryDefinition queryDef : getFileBasedQueryDefs(user, container, schemaName, path))
            {
                Entry<String, String> key = new Pair<>(schemaName, queryDef.getName());
                if (!ret.containsKey(key))
                    ret.put(key, queryDef);
            }
        }

        // look in the database for query definitions
        for (QueryDef queryDef : QueryManager.get().getQueryDefs(container, schemaName, false, includeSnapshots, true))
        {
            Entry<String, String> key = new Pair<>(queryDef.getSchema(), queryDef.getName());
            if (!ret.containsKey(key))
                ret.put(key, new CustomQueryDefinitionImpl(user, container, queryDef));
        }

        if (!inheritable)
            return ret;

        Container containerCur = container;

        // look up the container hierarchy
        while (!containerCur.isRoot())
        {
            containerCur = containerCur.getParent();
            if (null == containerCur)
            {
                assert false : "Unexpected null parent container encountered while resolving this container path: " + container.getPath();
                break;
            }

            for (QueryDef queryDef : QueryManager.get().getQueryDefs(containerCur, schemaName, true, includeSnapshots, true))
            {
                Entry<String, String> key = new Pair<>(queryDef.getSchema(), queryDef.getName());

                if (!ret.containsKey(key))
                    ret.put(key, new CustomQueryDefinitionImpl(user, container, queryDef));
            }
        }

        // look in the Shared project
        for (QueryDef queryDef : QueryManager.get().getQueryDefs(ContainerManager.getSharedContainer(), schemaName, true, includeSnapshots, true))
        {
            Entry<String, String> key = new Pair<>(queryDef.getSchema(), queryDef.getName());

            if (!ret.containsKey(key))
                ret.put(key, new CustomQueryDefinitionImpl(user, container, queryDef));
        }

        return ret;
    }

    public List<QueryDefinition> getFileBasedQueryDefs(User user, Container container, String schemaName, Path path, Module... extraModules)
    {
        Collection<Module> modules = new HashSet<>(container.getActiveModules(user));
        modules.addAll(Arrays.asList(extraModules));
        modules = ModuleLoader.getInstance().orderModules(modules);

        return MODULE_QUERY_DEF_CACHE.streamResourceMaps(modules)
            .map(mmap -> mmap.get(path))
            .flatMap(Collection::stream)
            .map(queryDef -> new ModuleCustomQueryDefinition(queryDef, SchemaKey.fromString(schemaName), user, container))
            .collect(Collectors.toList());
    }

    private static class QueryDefResourceCacheHandler implements ModuleResourceCacheHandler<MultiValuedMap<Path, ModuleQueryDef>>
    {
        @Override
        public MultiValuedMap<Path, ModuleQueryDef> load(Stream<? extends Resource> resources, Module module)
        {
            return unmodifiable(resources
                .filter(getFilter(ModuleQueryDef.FILE_EXTENSION))
                .map(resource -> new ModuleQueryDef(module, resource))
                .collect(MultiValuedMapCollectors.of(def -> def.getPath().getParent(), def -> def)));
        }
    }

    public QueryDefinition getQueryDef(User user, @NotNull Container container, String schema, String name)
    {
        Map<String, QueryDefinition> ret = new CaseInsensitiveHashMap<>();

        for (QueryDefinition queryDef : getAllQueryDefs(user, container, schema, true, true).values())
            ret.put(queryDef.getName(), queryDef);

        return ret.get(name);
    }

    private @NotNull Map<String, CustomView> getCustomViewMap(@NotNull User user, @NotNull Container container, @Nullable User owner, String schema, String query,
            boolean includeInherited, boolean sharedOnly)
    {
        // Check for a custom query that matches
        Map<Entry<String, String>, QueryDefinition> queryDefs = getAllQueryDefs(user, container, schema, false, true);
        QueryDefinition qd = queryDefs.get(new Pair<>(schema, query));
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
            return getCustomViewMap(user, container, owner, qd, includeInherited, sharedOnly);
        }
        return Collections.emptyMap();
    }

    protected Map<String, CustomView> getCustomViewMap(@NotNull User user, Container container, @Nullable User owner, QueryDefinition qd,
           boolean inheritable, boolean sharedOnly)
    {
        Map<String, CustomView> views = new CaseInsensitiveHashMap<>();

        // module query views have lower precedence, so add them first
        for (CustomView view : qd.getSchema().getModuleCustomViews(container, qd))
        {
            // there could be more than one view of the same name from different modules
            // reorderModules() orders higher precedence modules first
            if (!views.containsKey(view.getName()))
                views.put(view.getName(), view);
        }

        // custom views in the database get highest precedence, so let them overwrite the module-defined views in the map
        for (CstmView cstmView : QueryManager.get().getAllCstmViews(container, qd.getSchema().getSchemaPath().toString(), qd.getName(), owner, inheritable, sharedOnly))
        {
            // The database custom views are in priority order so check if the view has already been added
            if (!views.containsKey(cstmView.getName()))
                views.put(cstmView.getName(), new CustomViewImpl(qd, cstmView));
            //if the module-based view has set overridable=true, we allow the DB view to override it
            else if (views.get(cstmView.getName()) instanceof ModuleCustomView && views.get(cstmView.getName()).isOverridable())
            {
                CustomViewImpl view = new CustomViewImpl(qd, cstmView);
                view.setOverridesModuleView(true);
                views.put(cstmView.getName(), view);
            }
        }

        return views;
    }

    /**
     * Return all custom views for the specified container. This is an experimental optimized version of the current implementation which uses the
     * SchemaTreeWalker. We try to minimize the highly iterative nature of the STW which often times does many queries and tableinfo creations to yield
     * a very small number of custom views. Probably the most effective optimization would be to make tableinfos immutable and then begin to cache them
     * but that is a much larger chunk of work.
     */
    @NotNull
    private Collection<CustomView> getCustomViewsForContainer(@NotNull User user, Container container, @Nullable User owner, boolean inheritable, boolean sharedOnly)
    {
        // module query views have lower precedence, so add them first
        Set<String> moduleViewSchemas = new LinkedHashSet<>();

        // find out if there are any module custom views to deal with
        MODULE_CUSTOM_VIEW_CACHE.streamResourceMaps(container)
            .flatMap(mmap -> mmap.keys().stream())
            .forEach(path -> {
                if (AssayService.ASSAY_DIR_NAME.equals(path.get(0)) || AssayService.ASSAY_DIR_NAME.equals(path.get(1)))
                {
                    moduleViewSchemas.add(AssayService.ASSAY_DIR_NAME);
                }
                else if (MODULE_QUERIES_DIRECTORY.equals(path.get(0)))
                {
                    // after the queries directory, paths should contain schema and query name information
                    if (path.size() >= 3)
                    {
                        String[] parts = new String[path.size() - 2];
                        for (int i=0; i < path.size()-2; i++)
                            parts[i] = path.get(i+1);

                        SchemaKey schemaKey = SchemaKey.fromParts(parts);
                        moduleViewSchemas.add(schemaKey.toString());
                    }
                }
            });

        Map<Path, CustomView> views = new HashMap<>();

        for (String schemaName : moduleViewSchemas)
        {
            UserSchema defaultSchema = DefaultSchema.get(user, container).getUserSchema(schemaName);
            if (defaultSchema != null)
            {
                // if there are any nested schemas, pull those in as well
                Set<UserSchema> allSchemas = new HashSet<>();
                allSchemas.add(defaultSchema);
                getNestedSchemas(defaultSchema, allSchemas);

                for (UserSchema schema : allSchemas)
                {
                    Map<String, QueryDefinition> queryDefMap = schema.getQueryDefs();
                    for (String name : schema.getTableAndQueryNames(false))
                    {
                        QueryDefinition qd = queryDefMap.get(name);
                        if (qd == null)
                            qd = schema.getQueryDefForTable(name);

                        for (CustomView view : qd.getSchema().getModuleCustomViews(container, qd))
                        {
                            Path key = new Path(schema.getPath().toString(), view.getQueryDefinition().getName(), StringUtils.defaultString(view.getName(),""));
                            if (!views.containsKey(key))
                                views.put(key, view);
                        }
                    }
                }
            }
        }

        // custom views in the database get highest precedence, so let them overwrite the module-defined views in the map
        for (CstmView cstmView : QueryManager.get().getAllCstmViews(container, null, null, owner, inheritable, sharedOnly))
        {
            Path key = new Path(cstmView.getSchema(), cstmView.getQueryName(), StringUtils.defaultString(cstmView.getName(),""));
            // The database custom views are in priority order so check if the view has already been added
            if (!views.containsKey(key))
            {
                views.put(key, new CustomViewImpl(getQueryDefinition(user, container, cstmView), cstmView));
            }
            else if (views.get(key) instanceof ModuleCustomView && views.get(key).isOverridable())
            {
                //if the module-based view has set overridable=true, we allow the DB view to override it
                CustomViewImpl view = new CustomViewImpl(getQueryDefinition(user, container, cstmView), cstmView);
                view.setOverridesModuleView(true);
                views.put(key, view);
            }
        }

        return views.values();
    }

    private void getNestedSchemas(UserSchema schema, Set<UserSchema> schemas)
    {
        for (QuerySchema querySchema : schema.getSchemas(false))
        {
            if (querySchema instanceof UserSchema && !((UserSchema)querySchema).isFolder())
            {
                UserSchema userSchema = schema.getUserSchema(querySchema.getName());
                if (userSchema != null)
                {
                    schemas.add(userSchema);
                    getNestedSchemas(userSchema, schemas);
                }
            }
        }
    }

    private @Nullable QueryDefinition getQueryDefinition(User user, Container container, CstmView cstmView)
    {
        return getQueryDefinition(user, container, cstmView.getSchema(), cstmView.getQueryName());
    }

    /**
     * Returns either database (or session) query definitions as well as table-based query definitions
     */
    private @Nullable QueryDefinition getQueryDefinition(User user, Container container, String schemaName, String queryName)
    {
        QueryDefinition qd = getQueryDef(user, container, schemaName, queryName);
        if (qd == null)
        {
            // look for a table based query definition
            UserSchema schema = DefaultSchema.get(user, container).getUserSchema(schemaName);
            if (schema == null)
                return null;

            Set<String> tableNames = new HashSet<>();
            tableNames.addAll(schema.getTableAndQueryNames(true));

            if (tableNames.contains(queryName))
            {
                qd = schema.getQueryDefForTable(queryName);
            }
            else
            {
                Map<String, QueryDefinition> queryDefinitionMap = schema.getQueryDefs();
                // look for the table title name match
                for (String tableName : tableNames)
                {
                    if (!queryDefinitionMap.containsKey(tableName))
                    {
                        QueryDefinition def = schema.getQueryDefForTable(tableName);
                        if (def.getTitle().equals(queryName))
                        {
                            return def;
                        }
                    }
                }
            }
        }
        return qd;
    }

    public CustomView getCustomView(@NotNull User user, Container container, @Nullable User owner, String schema, String query, String name)
    {
        Map<String, CustomView> views = getCustomViewMap(user, container, owner, schema, query, false, false);
        return views.get(name);
    }

    public List<CustomView> getCustomViews(@NotNull User user, Container container, @Nullable User owner, @Nullable String schemaName, @Nullable String queryName, boolean includeInherited)
    {
        return _getCustomViews(user, container, owner, schemaName, queryName, includeInherited, false);
    }

    // TODO: Unused... delete?
    public CustomView getSharedCustomView(@NotNull User user, Container container, String schema, String query, String name)
    {
        Map<String, CustomView> views = getCustomViewMap(user, container, null, schema, query, false, true);
        return views.get(name);
    }

    public List<CustomView> getSharedCustomViews(@NotNull User user, Container container, @Nullable String schemaName, @Nullable String queryName, boolean includeInherited)
    {
        return _getCustomViews(user, container, null, schemaName, queryName, includeInherited, true);
    }

    private List<CustomView> _getCustomViews(final @NotNull User user, final Container container, final @Nullable User owner, final @Nullable String schemaName, final @Nullable String queryName,
            final boolean includeInherited,
            final boolean sharedOnly)
    {
        final Collection<CustomView> views;

        if (schemaName == null || queryName == null)
        {
            views = getCustomViewsForContainer(user, container, owner, includeInherited, sharedOnly);
        }
        else
        {
            views = getCustomViewMap(user, container, owner, schemaName, queryName, includeInherited, sharedOnly).values();
        }

        if (views.isEmpty())
            return Collections.emptyList();

        return new ArrayList<>(views);
    }

    public List<CustomView> getDatabaseCustomViews(@NotNull User user, Container container, @Nullable User owner, @Nullable String schemaName, @Nullable String queryName, boolean includeInherited, boolean sharedOnly)
    {
        if (schemaName == null || queryName == null)
        {
            List<CustomView> result = new ArrayList<>();
            Map<String, UserSchema> schemas = new HashMap<>();
            Map<Pair<String, String>, QueryDefinition> queryDefs = new HashMap<>();

            for (CstmView cstmView : QueryManager.get().getAllCstmViews(container, schemaName, queryName, owner, includeInherited, sharedOnly))
            {
                Pair<String, String> key = new Pair<>(cstmView.getSchema(), cstmView.getQueryName());
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

        return new ArrayList<>(getCustomViewMap(user, container, owner, schemaName, queryName, includeInherited, sharedOnly).values());
    }

    public List<CustomView> getFileBasedCustomViews(Container container, QueryDefinition qd, Path path, String query, Module... extraModules)
    {
        Collection<Module> currentModules = new HashSet<>(container.getActiveModules());
        currentModules.addAll(Arrays.asList(extraModules));

        currentModules = ModuleLoader.getInstance().orderModules(currentModules);

        // TODO: Instead of caching a separate list for each module + query combination, we could cache the full list of views defined on this
        // query in ALL modules... and then filter for active at this level.

        return MODULE_CUSTOM_VIEW_CACHE.streamResourceMaps(currentModules)
            .map(mmap -> mmap.get(path))
            .flatMap(Collection::stream)
            .map(view -> new ModuleCustomView(qd, view))
            .collect(Collectors.toList());
    }

    @Override
    public String getCustomViewNameFromEntityId(Container container, String entityId) throws SQLException
    {
        CstmView view = QueryManager.get().getCustomView(container, entityId);
        return view != null ? view.getName() : null;
    }

    private static class CustomViewResourceCacheHandler implements ModuleResourceCacheHandler<MultiValuedMap<Path, ModuleCustomViewDef>>
    {
        @Override
        public MultiValuedMap<Path, ModuleCustomViewDef> load(Stream<? extends Resource> resources, Module module)
        {
            // Note: Can't use standard filter (getFilter(suffix)) below since we must allow ".qview.xml"
            return unmodifiable(resources
                .filter(resource -> StringUtils.endsWithIgnoreCase(resource.getName(), CustomViewXmlReader.XML_FILE_EXTENSION))
                .map(ModuleCustomViewDef::new)
                .collect(MultiValuedMapCollectors.of(def -> def.getPath().getParent(), def -> def)));
        }
    }

    public void writeTables(Container c, User user, VirtualFile dir, Map<String, List<Map<String, Object>>> schemas, ColumnHeaderType header) throws IOException
    {
        TableWriter writer = new TableWriter();
        writer.write(c, user, dir, schemas, header);
    }

    public int importCustomViews(User user, Container container, VirtualFile viewDir) throws IOException
    {
        QueryManager mgr = QueryManager.get();
        HttpServletRequest request = new MockHttpServletRequest();

        int count = 0;

        for (String viewFileName : viewDir.list())
        {
            // skip over any files that don't end with the expected extension
            if (!viewFileName.endsWith(CustomViewXmlReader.XML_FILE_EXTENSION))
                continue;

            CustomViewXmlReader reader = CustomViewXmlReader.loadDefinition(viewDir.getInputStream(viewFileName), viewDir.getRelativePath(viewFileName));

            QueryDefinition qd = QueryService.get().createQueryDef(user, container, reader.getSchema(), reader.getQuery());
            String viewName = reader.getName();

            if (null == viewName)
                throw new IllegalStateException(viewFileName + ": Must specify a view name");

            // Get all shared views on this query with the same name
            List<CstmView> views = mgr.getCstmViews(container, qd.getSchemaName(), qd.getName(), viewName, null, false, true);

            // Delete them
            for (CstmView view : views)
                mgr.delete(view);

            // owner == null since we're exporting/importing only shared views
            CustomView cv = qd.createSharedCustomView(reader.getName());
            cv.setColumnProperties(reader.getColList());
            cv.setFilterAndSort(reader.getFilterAndSortString());
            cv.setIsHidden(reader.isHidden());
            cv.setCanInherit(reader.canInherit());
            cv.save(user, request);

            count++;
        }

        return count;
    }


    public Map<String, Object> getCustomViewProperties(@Nullable CustomView view, @NotNull User currentUser)
    {
        return getCustomViewProperties(view, currentUser, true);
    }

    private Map<String, Object> getCustomViewProperties(@Nullable CustomView view, @NotNull User currentUser, boolean includeShadowed)
    {
        if (view == null)
            return null;

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("name", view.getName() == null ? "" : view.getName());
        ret.put("label", view.getLabel());
        ret.put("default", view.getName() == null);
        if (null != view.getOwner())
            ret.put("owner", view.getOwner().getDisplayName(currentUser));
        ret.put("shared", view.isShared());
        ret.put("inherit", view.canInherit());
        ret.put("session", view.isSession());
        ret.put("editable", view.isEditable());
        ret.put("deletable", view.isDeletable());
        ret.put("revertable", view.isRevertable());
        ret.put("hidden", view.isHidden());
        // XXX: This is a query property and not a custom view property!
        ret.put("savable", !view.getQueryDefinition().isTemporary());
        // module custom views have no container
        ret.put("containerPath", view.getContainer() != null ? view.getContainer().getPath() : "");
        ret.put("containerFilter", view.getContainerFilterName());

        // Include view information about shadowed view
        if (includeShadowed && view.isSession())
        {
            User owner = view.getOwner();
            if (owner == null)
                owner = currentUser;
            CustomView shadowedView = view.getQueryDefinition().getCustomView(owner, null, view.getName());

            // Don't include shadowed custom view if it is owned by someone else.
            if (shadowedView == null || shadowedView.isShared() || shadowedView.getOwner() == owner)
                ret.put("shadowed", getCustomViewProperties(shadowedView, currentUser, false));
        }

        return ret;
    }

    public @Nullable QuerySnapshotDefinition getSnapshotDef(Container container, String schema, String snapshotName)
    {
        QuerySnapshotDef def = QueryManager.get().getQuerySnapshotDef(container, schema, snapshotName);

        return null != def ? new QuerySnapshotDefImpl(def) : null;
    }

    public boolean isQuerySnapshot(Container container, String schema, String name)
    {
        return getSnapshotDef(container, schema, name) != null;
    }

    public List<QuerySnapshotDefinition> getQuerySnapshotDefs(Container container, String schemaName)
    {
        return QueryManager.get().getQuerySnapshots(container, schemaName)
            .stream()
            .map(QuerySnapshotDefImpl::new)
            .collect(Collectors.toList());
    }

    private static class ContainerSchemaKey implements Serializable
    {
        private final Container _container;
        private final String _schema;

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
        return saveSessionQuery(context.getRequest().getSession(true), container, context.getUser(), schemaName, sql, metadataXml);
    }


    @Override
    public QueryDefinition saveSessionQuery(@NotNull HttpSession session, Container container, User user, String schemaName, String sql, String metadataXml)
    {
        if (session == null)
            throw new IllegalStateException();
        Map<String, SessionQuery> queries = getSessionQueryMap(session, container, schemaName);
        String queryName = null;
        SessionQuery sq = new SessionQuery(sql, metadataXml);
        for (Entry<String, SessionQuery> query : queries.entrySet())
        {
            if (query.getValue().equals(sq))
            {
                queryName = query.getKey();
                break;
            }
        }
        if (queryName == null)
        {
            queryName = schemaName + "_temp_" + UniqueID.getServerSessionScopedUID();
            queries.put(queryName, sq);
        }
        return getSessionQuery(session, container, user, schemaName, queryName);
    }


    @Override
    public QueryDefinition saveSessionQuery(ViewContext context, Container container, String schemaName, String sql)
    {
        return saveSessionQuery(context, container, schemaName, sql, null);
    }


    private static final String PERSISTED_TEMP_QUERIES_KEY = "LABKEY.PERSISTED_TEMP_QUERIES";

    private static class SessionQuery implements Serializable
    {
        private final String _sql;
        private final String _metadata;

        public SessionQuery(String sql, String metadata)
        {
            _sql = sql;
            _metadata = metadata;
        }

        @Override
        public int hashCode()
        {
            int result = _sql.hashCode();
            if (_metadata != null)
                result = 31 * result + _metadata.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof SessionQuery)
            {
                SessionQuery sq = (SessionQuery)obj;
                if (!_sql.equals(sq._sql))
                    return false;
                if (_metadata == null && sq._metadata != null)
                    return false;
                if (_metadata != null && !_metadata.equals(sq._metadata))
                    return false;

                return true;
            }
            return false;
        }
    }

    private Map<String, SessionQuery> getSessionQueryMap(@NotNull HttpSession session, Container container, String schemaName)
    {
        if (session == null)
            throw new IllegalStateException("No HTTP session");
        Map<ContainerSchemaKey, Map<String, SessionQuery>> containerQueries = (Map<ContainerSchemaKey, Map<String, SessionQuery>>) session.getAttribute(PERSISTED_TEMP_QUERIES_KEY);
        if (containerQueries == null)
        {
            containerQueries = new ConcurrentHashMap<>();
            session.setAttribute(PERSISTED_TEMP_QUERIES_KEY, containerQueries);
        }
        ContainerSchemaKey key = new ContainerSchemaKey(container, schemaName);
        Map<String, SessionQuery> queries = containerQueries.get(key);
        if (queries == null)
        {
            queries = new ConcurrentHashMap<>();
            containerQueries.put(key, queries);
        }
        return queries;
    }

    private List<QueryDefinition> getAllSessionQueries(HttpServletRequest request, User user, Container container, String schemaName)
    {
        List<QueryDefinition> ret = new ArrayList<>();
        HttpSession session = request.getSession(true);

        if (session != null)
        {
            Map<String, SessionQuery> sessionQueries = getSessionQueryMap(session, container, schemaName);
            for (Entry<String, SessionQuery> entry : sessionQueries.entrySet())
                ret.add(createTempQueryDefinition(user, container, schemaName, entry.getKey(), entry.getValue()));
        }

        return ret;
    }

    public QueryDefinition getSessionQuery(ViewContext context, Container container, String schemaName, String queryName)
    {
        return getSessionQuery(context.getSession(), container, context.getUser(), schemaName,queryName);
    }

    public QueryDefinition getSessionQuery(HttpSession session, Container container, User user, String schemaName, String queryName)
    {
        SessionQuery query = getSessionQueryMap(session, container, schemaName).get(queryName);
        return createTempQueryDefinition(user, container, schemaName, queryName, query);
    }


    private QueryDefinition createTempQueryDefinition(User user, Container container, String schemaName, String queryName, @NotNull SessionQuery query)
    {
        QueryDefinition qdef = QueryService.get().createQueryDef(user, container, schemaName, queryName);
        if (null == query || null == qdef)
            throw new IllegalStateException("Expected a QueryDefinition object.");

        qdef.setSql(query._sql);
        if (query._metadata != null)
            qdef.setMetadataXml(query._metadata);
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
                Set<FieldKey> pkColumnMap = new HashSet<>();
                ContainerContext cc = table.getContainerContext();
                if (cc instanceof ContainerContext.FieldKeyContext)
                {
                    ContainerContext.FieldKeyContext fko = (ContainerContext.FieldKeyContext) cc;
                    pkColumnMap.add(fko.getFieldKey());
                }

                for (ColumnInfo column : pkColumns)
                    pkColumnMap.add(new FieldKey(null, column.getAlias()));

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
    public Map<FieldKey, ColumnInfo> getColumns(@NotNull TableInfo table, @NotNull Collection<FieldKey> fields)
    {
        return getColumns(table, fields, Collections.emptySet());
    }


    @NotNull
    public LinkedHashMap<FieldKey, ColumnInfo> getColumns(@NotNull TableInfo table, @NotNull Collection<FieldKey> fields, @NotNull Collection<ColumnInfo> existingColumns)
    {
        assert null != (existingColumns = Collections.unmodifiableCollection(existingColumns));
        assert Table.checkAllColumns(table, existingColumns, "QueryServiceImpl.getColumns() existingColumns", false);

        AliasManager manager = new AliasManager(table, existingColumns);
        LinkedHashMap<FieldKey, ColumnInfo> ret = new LinkedHashMap<>();
        Map<FieldKey, ColumnInfo> columnMap = new HashMap<>();

        for (ColumnInfo existingColumn : existingColumns)
        {
            columnMap.put(existingColumn.getFieldKey(), existingColumn);
            ret.put(existingColumn.getFieldKey(), existingColumn);
        }

        // Consider that the fieldKey may have come from a URL field that is a colunm's alias
        Map<String, ColumnInfo> mapAlias = new HashMap<>();
        table.getColumns().forEach(col -> mapAlias.put(col.getAlias().toLowerCase(), col));

        for (FieldKey field : fields)
        {
            if (null != field && !ret.containsKey(field))
            {
                ColumnInfo column = getColumn(manager, table, columnMap, field);
                if (column != null)
                    ret.put(field, column);
                else if (null == field.getParent())
                {
                    column = mapAlias.get(field.getName().toLowerCase());
                    if (column != null)
                        ret.put(field, column);
                }
            }
        }

        assert Table.checkAllColumns(table, ret.values(), "QueryServiceImpl.getColumns() ret", true);

        return ret;
    }


    public List<DisplayColumn> getDisplayColumns(@NotNull TableInfo table, Collection<Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> fields)
    {
        List<DisplayColumn> ret = new ArrayList<>();
        Set<FieldKey> fieldKeys = new HashSet<>();

        for (Entry<FieldKey, ?> entry : fields)
            fieldKeys.add(entry.getKey());

        Map<FieldKey, ColumnInfo> columns = getColumns(table, fieldKeys);

        for (Entry<FieldKey, Map<CustomView.ColumnProperty, String>> entry : fields)
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


    public Collection<ColumnInfo> ensureRequiredColumns(@NotNull TableInfo table, @NotNull Collection<ColumnInfo> columns,
                                                        @Nullable Filter filter, @Nullable Sort sort, @Nullable Set<FieldKey> unresolvedColumns)
    {
        HashMap<FieldKey,ColumnInfo> hm = new HashMap<>();
        Set<ColumnInfo> involvedColumns = new HashSet<>();
        return ensureRequiredColumns(table, columns, filter, sort, unresolvedColumns, hm, involvedColumns);
    }


    // mapping may include multiple fieldkeys pointing at same columninfo (see ColumnInfo.resolveColumn());
    private List<ColumnInfo> ensureRequiredColumns(@NotNull TableInfo table, @NotNull Collection<ColumnInfo> columns, @Nullable Filter filter,
                                                         @Nullable Sort sort, @Nullable Set<FieldKey> unresolvedColumns,
                                                         Map<FieldKey,ColumnInfo> columnMap /* IN/OUT */, Set<ColumnInfo> allInvolvedColumns /* IN/OUT */)
    {
        AliasManager manager = new AliasManager(table, columns);

        for (ColumnInfo column : columns)
            columnMap.put(column.getFieldKey(), column);

        ArrayList<ColumnInfo> ret = new ArrayList<>(columns);

        // Add container column if needed
        ColumnInfo containerColumn = table.getColumn("Container");
        if (null != containerColumn && !columnMap.containsKey(containerColumn.getFieldKey()) && containerColumn.isRequired())
        {
            ret.add(containerColumn);
            columnMap.put(FieldKey.fromString("Container"), containerColumn);
        }

        // foreign keys
        // pass one - collect columns with foreign keys
        Map<FieldKey,ColumnInfo> columnsWithLookups = new TreeMap<>();
        for (ColumnInfo column : columns)
        {
            FieldKey fkOuter = column.getFieldKey();
            while (null != (fkOuter=fkOuter.getParent()))
            {
                if (columnsWithLookups.containsKey(fkOuter))
                    break;
                ColumnInfo colOuter = columnMap.get(fkOuter);
                if (null != colOuter && null != colOuter.getFk())
                    columnsWithLookups.put(fkOuter, colOuter);
            }
        }
        // pass two - get suggested columns
        for (ColumnInfo column : columnsWithLookups.values())
        {
            ForeignKey foreignKey = column.getFk();
            Set<FieldKey> set = foreignKey.getSuggestedColumns();
            if (null == set)
                continue;
            for (FieldKey fieldKey : set)
            {
                ColumnInfo col = resolveFieldKey(fieldKey, table, columnMap, unresolvedColumns, manager);
                if (col != null)
                {
                    ret.add(col);
                    allInvolvedColumns.add(col);
                }
            }
        }


        if (filter != null)
        {
            for (FieldKey fieldKey : filter.getWhereParamFieldKeys())
            {
                ColumnInfo col = resolveFieldKey(fieldKey, table, columnMap, unresolvedColumns, manager);
                if (col != null)
                {
                    ret.add(col);
                    allInvolvedColumns.add(col);
                }
            }
        }

        if (sort != null)
        {
            for (Sort.SortField field : sort.getSortList())
            {
                ColumnInfo col = resolveFieldKey(field.getFieldKey(), table, columnMap, unresolvedColumns, manager);
                if (col != null)
                {
                    ret.add(col);
                    resolveSortColumns(col, columnMap, manager, ret, allInvolvedColumns, false);
                }
                //the column might be displayed, but also used as a sort.  if so, we need to ensure we include sortFieldKeys
                else if (columnMap.containsKey(field.getFieldKey()))
                {
                    resolveSortColumns(columnMap.get(field.getFieldKey()), columnMap, manager, ret, allInvolvedColumns, true);
                }
            }
        }

        if (unresolvedColumns != null && !unresolvedColumns.isEmpty())
        {
            LOG.debug("Unable to resolve the following columns on table " + table.getName() + ": " + unresolvedColumns.toString());

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
        return ret;
    }


    private ArrayList<ColumnInfo> resolveSortColumns(ColumnInfo col, Map<FieldKey,ColumnInfo> columnMap, AliasManager manager,
                                                     ArrayList<ColumnInfo> ret, Set<ColumnInfo> allInvolvedColumns, boolean addSortKeysOnly)
    {
        if (col.getSortFieldKeys() != null || null != col.getMvColumnName())
        {
            List<ColumnInfo> toAdd = new ArrayList<>();
            List<FieldKey> sortFieldKeys = new ArrayList<>();
            if (null != col.getSortFieldKeys())
                sortFieldKeys.addAll(col.getSortFieldKeys());
            if (null != col.getMvColumnName())
                sortFieldKeys.add(col.getMvColumnName());

            for (FieldKey key : sortFieldKeys)
            {
                ColumnInfo sortCol = resolveFieldKey(key, col.getParentTable(), columnMap, null, manager);
                if (sortCol != null)
                {
                    toAdd.add(sortCol);
                }
                else
                {
                    // if we cannot find the sortCols, which could occur if we have a Query over a raw table,
                    // default to the raw value
                    toAdd.clear();
                    if (!columnMap.containsKey(col.getFieldKey()))
                        toAdd.add(col);

                    break;
                }
            }

            ret.addAll(toAdd);
            allInvolvedColumns.addAll(toAdd);
        }
        else
        {
            if (!addSortKeysOnly)
            {
                ret.add(col);
                allInvolvedColumns.add(col);
            }
        }

        return ret;
    }


    private ColumnInfo resolveFieldKey(FieldKey fieldKey, TableInfo table, Map<FieldKey,ColumnInfo> columnMap, Set<FieldKey> unresolvedColumns, AliasManager manager)
    {
        if (fieldKey == null)
            return null;

        if (columnMap.containsKey(fieldKey))
            return null;

        ColumnInfo column = getColumn(manager, table, columnMap, fieldKey);

        if (column != null)
        {
            assert Table.checkColumn(table, column, "ensureRequiredColumns():");
            assert fieldKey.getTable() == null || columnMap.containsKey(fieldKey);

            // getColumn() might return a column with a different field key than we asked for!
            if (!column.getFieldKey().equals(fieldKey))
            {
                if (columnMap.containsKey(column.getFieldKey()))
                {
                    columnMap.put(fieldKey, columnMap.get(column.getFieldKey()));
                    return null;
                }
            }

            return column;
        }
        else
        {
            if (unresolvedColumns != null)
                unresolvedColumns.add(fieldKey);
        }

        return null;
    }


    public Map<String, UserSchema> getExternalSchemas(User user, Container c)
    {
        Map<String, UserSchema> ret = new HashMap<>();
        List<ExternalSchemaDef> defs = QueryManager.get().getExternalSchemaDefs(c);

        for (ExternalSchemaDef def : defs)
        {
            try
            {
                UserSchema schema = ExternalSchema.get(user, c, def);
                ret.put(def.getUserSchemaName(), schema);
            }
            catch (Exception e)
            {
                Logger.getLogger(QueryServiceImpl.class).warn("Could not load schema " + def.getSourceSchemaName() + " from " + def.getDataSource(), e);
            }
        }

        return ret;
    }

    public UserSchema getExternalSchema(User user, Container c, String name)
    {
        ExternalSchemaDef def = QueryManager.get().getExternalSchemaDef(c, name);

        if (null != def)
        {
            try
            {
                return ExternalSchema.get(user, c, def);
            }
            catch (Exception e)
            {
                Logger.getLogger(QueryServiceImpl.class).warn("Could not load schema " + def.getSourceSchemaName() + " from " + def.getDataSource(), e);
            }
        }

        return null;
    }

    public Map<String, UserSchema> getLinkedSchemas(User user, Container c)
    {
        Map<String, UserSchema> ret = new HashMap<>();
        List<LinkedSchemaDef> defs = QueryManager.get().getLinkedSchemaDefs(c);

        for (LinkedSchemaDef def : defs)
        {
            try
            {
                UserSchema schema = LinkedSchema.get(user, c, def);
                if (schema != null)
                    ret.put(def.getUserSchemaName(), schema);
            }
            catch (Exception e)
            {
                Logger.getLogger(QueryServiceImpl.class).error("Error creating linked schema " + def.getUserSchemaName(), e);
            }
        }

        return ret;
    }

    public UserSchema getLinkedSchema(User user, Container c, String name)
    {
        LinkedSchemaDef def = QueryManager.get().getLinkedSchemaDef(c, name);

        if (null != def)
        {
            try
            {
                return LinkedSchema.get(user, c, def);
            }
            catch (Exception e)
            {
                Logger.getLogger(QueryServiceImpl.class).error("Error creating linked schema " + def.getUserSchemaName(), e);
            }
        }

        return null;
    }

    public UserSchema createLinkedSchema(User user, Container c, String name, String sourceContainerId, String sourceSchemaName,
                                         String metadata, String tables, String template)
    {
        LinkedSchemaDef def = new LinkedSchemaDef();
        def.setContainer(c.getId());
        def.setUserSchemaName(name);
        def.setDataSource(sourceContainerId);
        def.setSourceSchemaName(sourceSchemaName);
        def.setMetaData(metadata);
        def.setTables(tables);
        def.setSchemaTemplate(template);
        LinkedSchemaDef newDef = QueryManager.get().insertLinkedSchema(user, def);
        return LinkedSchema.get(user, c, newDef);
    }

    public void deleteLinkedSchema(User user, Container c, String name)
    {
        try
        {
            QueryManager.get().deleteLinkedSchema(c, name);
        }
        catch (Exception e)
        {
            Logger.getLogger(QueryServiceImpl.class).error("Error deleting linked schema " + name, e);
        }
    }

    public TemplateSchemaType getSchemaTemplate(Container c, String templateName)
    {
        if (templateName == null)
            return null;

        for (Module module : c.getActiveModules())
        {
            Resource schemasDir = module.getModuleResource(QueryService.MODULE_SCHEMAS_PATH);
            if (schemasDir != null && schemasDir.isCollection())
            {
                for (Resource resource : schemasDir.list())
                {
                    String name = resource.getName();
                    if (name.endsWith(QueryService.SCHEMA_TEMPLATE_EXTENSION) && templateName.equalsIgnoreCase(name.substring(0, name.length() - SCHEMA_TEMPLATE_EXTENSION.length())))
                    {
                        try (InputStream is = resource.getInputStream())
                        {
                            TemplateSchemaDocument doc = TemplateSchemaDocument.Factory.parse(is);
                            XmlBeansUtil.validateXmlDocument(doc, resource.getName());
                            return doc.getTemplateSchema();
                        }
                        catch (XmlException | XmlValidationException | IOException e)
                        {
                            LOG.error("Skipping '" + name + "' schema template file: " + e.getMessage());
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get list of available schema template XML files for the Container's active modules.
     * @returns Map of template name -> (template name, template xml)
     */
    public Map<String, TemplateSchemaType> getSchemaTemplates(Container c)
    {
        Map<String, TemplateSchemaType> ret = new HashMap<>();
        for (Module module : c.getActiveModules())
        {
            Resource schemasDir = module.getModuleResource(QueryService.MODULE_SCHEMAS_PATH);
            if (schemasDir != null && schemasDir.isCollection())
            {
                for (Resource resource : schemasDir.list())
                {
                    String name = resource.getName();
                    if (name.endsWith(QueryService.SCHEMA_TEMPLATE_EXTENSION))
                    {
                        name = name.substring(0, name.length() - QueryService.SCHEMA_TEMPLATE_EXTENSION.length());
                        try (InputStream is = resource.getInputStream())
                        {
                            TemplateSchemaDocument doc = TemplateSchemaDocument.Factory.parse(is);
                            XmlBeansUtil.validateXmlDocument(doc, resource.getName());
                            TemplateSchemaType template = doc.getTemplateSchema();
                            ret.put(name, template);
                        }
                        catch (XmlException e)
                        {
                            LOG.error("Skipping '" + name + "' schema template file: " + XmlBeansUtil.getErrorMessage(e));
                        }
                        catch (XmlValidationException e)
                        {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Skipping '").append(name).append("' schema template file:\n");
                            for (XmlError err : e.getErrorList())
                                sb.append("  ").append(XmlBeansUtil.getErrorMessage(err)).append("\n");
                            LOG.error(sb.toString());
                        }
                        catch (IOException e)
                        {
                            LOG.error("Skipping '" + name + "' schema template file: " + e.getMessage());
                        }
                    }
                }
            }
        }

        return ret;
    }

    public JSONObject schemaTemplateJson(String name, TemplateSchemaType template)
    {
        JSONObject templateJson = new JSONObject();
        templateJson.put("name", name);
        templateJson.put("sourceSchemaName", template.getSourceSchemaName());

        String[] tables = new String[0];
        if (template.isSetTables())
            tables = template.getTables().getTableNameArray();
        templateJson.put("tables", tables);
        templateJson.put("metadata", template.getMetadata());
        return templateJson;
    }

    @Override
    public UserSchema createSimpleUserSchema(String name, @Nullable String description, User user, Container container, DbSchema schema)
    {
        if (null == schema)
            throw new NotFoundException("DB Schema does not exist.");

        if (description == null)
        {
            description = schema.getDescription();
        }

        return new SimpleUserSchema(name, description, user, container, schema);
    }

    @Override
    public List<ColumnInfo> getDefaultVisibleColumnInfos(List<ColumnInfo> columns)
    {
        List<ColumnInfo> ret = new ArrayList<>(columns.size());

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

    @Override
    public List<FieldKey> getDefaultVisibleColumns(List<ColumnInfo> columns)
    {
        List<FieldKey> ret = new ArrayList<>();

        for (ColumnInfo column : getDefaultVisibleColumnInfos(columns))
        {
            ret.add(FieldKey.fromParts(column.getName()));
        }

        return ret;
    }

    @Override
    public Collection<TableType> findMetadataOverride(UserSchema schema, String tableName, boolean customQuery, boolean allModules, @NotNull Collection<QueryException> errors, Path dir)
    {
        Collection<QueryDef> queryDefs = findMetadataOverrideImpl(schema, tableName, customQuery, allModules, dir);
        if (queryDefs == null)
            return null;

        Collection<TableType> tableTypes = new ArrayList<>();
        for (QueryDef queryDef : queryDefs)
            tableTypes.add(parseMetadata(queryDef.getMetaData(), errors));

        return tableTypes;
    }

    public TableType parseMetadata(String metadataXML, Collection<QueryException> errors)
    {
        if (metadataXML == null || StringUtils.isBlank(metadataXML))
            return null;

        XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
        List<XmlError> xmlErrors = new ArrayList<>();
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
            errors.add(new MetadataParseException(XmlBeansUtil.getErrorMessage(e)));
        }
        for (XmlError xmle : xmlErrors)
        {
            errors.add(new MetadataParseException(XmlBeansUtil.getErrorMessage(xmle)));
        }

        return null;
    }

    // Use a WeakHashMap to cache QueryDefs. This means that the cache entries will only be associated directly
    // with the exact same UserSchema instance, regardless of whatever UserSchema.equals() returns. This means
    // that the scope of the cache is very limited, and this is a very conservative cache.
    private Map<ObjectIdentityCacheKey, WeakReference<Map<String, List<QueryDef>>>> _metadataCache = Collections.synchronizedMap(new WeakHashMap<>());

    /** Hides whatever the underlying key might do for .equals() and .hashCode() and instead relies on pointer equality */
    private static class ObjectIdentityCacheKey
    {
        private final Object _object;
        private final boolean _customQuery;
        private final boolean _allModules;
        private final Path _dir;

        private ObjectIdentityCacheKey(UserSchema object, boolean customQuery, boolean allModules, @Nullable Path dir)
        {
            _object = object;
            _customQuery = customQuery;
            _allModules = allModules;
            _dir = dir;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ObjectIdentityCacheKey that = (ObjectIdentityCacheKey) o;

            if (_allModules != that._allModules) return false;
            if (_customQuery != that._customQuery) return false;
            if (_dir != null ? !_dir.equals(that._dir) : that._dir != null) return false;
            return that._object == _object;
        }

        @Override
        public int hashCode()
        {
            int result = System.identityHashCode(_object);
            result = 31 * result + (_customQuery ? 1 : 0);
            result = 31 * result + (_allModules ? 1 : 0);
            result = 31 * result + (_dir != null ? _dir.hashCode() : 0);
            return result;
        }
    }

    /**
     * Finds metadata overrides for the given schema and table and returns them in application order.
     * For now, a maximum of two metadata xml overrides will be returned:
     *
     * 1) The first metadata "<code>queries/&lt;schemaName&gt;/&lt;tableName&gt;.qview.xml</code>" file found from the set of active (or all) modules.
     * 2) The first metadata xml found in the database searching up the container hierarchy plus shared.
     */
    // BUGBUG: Should we look in the session queries for metadata overrides?
    @Nullable
    public List<QueryDef> findMetadataOverrideImpl(UserSchema schema, String tableName, boolean customQuery, boolean allModules, @Nullable Path dir)
    {
        ObjectIdentityCacheKey schemaCacheKey = new ObjectIdentityCacheKey(schema, customQuery, allModules, dir);
        WeakReference<Map<String, List<QueryDef>>> ref = _metadataCache.get(schemaCacheKey);
        Map<String, List<QueryDef>> queryDefs = ref == null ? null : ref.get();
        if (queryDefs == null)
        {
            queryDefs = new CaseInsensitiveHashMap<>();
            ref = new WeakReference<>(queryDefs);
            _metadataCache.put(schemaCacheKey, ref);
        }

        // Check if we've already cached the QueryDef for this specific UserSchema object
        if (queryDefs.containsKey(tableName))
        {
            return queryDefs.get(tableName);
        }

        // Collect metadata xml in application order
        List<QueryDef> defs = new ArrayList<>();

        // 1) Module query metadata
        QueryDef moduleQueryDef = findMetadataOverrideInModules(schema, tableName, allModules, dir);
        if (moduleQueryDef != null)
            defs.add(moduleQueryDef);

        // 2) User created query metadata
        QueryDef databaseQueryDef = findMetadataOverrideInDatabase(schema, tableName, customQuery);
        if (databaseQueryDef != null)
            defs.add(databaseQueryDef);

        if (defs.isEmpty())
        {
            queryDefs.put(tableName, null);
            return null;
        }
        else
        {
            List<QueryDef> ret = Collections.unmodifiableList(defs);
            queryDefs.put(tableName, ret);
            return ret;
        }
    }

    private QueryDef findMetadataOverrideInDatabase(UserSchema schema, String tableName, boolean customQuery)
    {
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

        return null;
    }

    private Path createSchemaPath(SchemaKey schemaKey)
    {
        if (schemaKey == null)
        {
            return QueryService.MODULE_QUERIES_PATH;
        }
        List<String> subDirs = new ArrayList<>(schemaKey.getParts().size() + 1);
        subDirs.add(QueryService.MODULE_QUERIES_DIRECTORY);
        subDirs.addAll(schemaKey.getParts());
        return new Path(subDirs);
    }

    private @Nullable QueryDef findMetadataOverrideInModules(UserSchema schema, String tableName, boolean allModules, @Nullable Path dir)
    {
        String schemaName = schema.getSchemaPath().toString();

        if (dir == null)
        {
            dir = createSchemaPath(schema.getSchemaPath());
        }

        // Look for file-based definitions in modules
        Collection<Module> modules = allModules ? ModuleLoader.getInstance().getModules() : schema.getContainer().getActiveModules(schema.getUser());

        for (Module module : modules)
        {
            Collection<ModuleQueryMetadataDef> metadataDefs = MODULE_QUERY_METADATA_DEF_CACHE.getResourceMap(module).get(dir);

            for (ModuleQueryMetadataDef metadataDef : metadataDefs)
            {
                if (metadataDef.getName().equalsIgnoreCase(tableName))
                {
                    QueryDef result = metadataDef.toQueryDef(schema.getContainer());
                    result.setSchema(schemaName);
                    return result;
                }
            }
        }

        return null;
    }


    private static class QueryMetaDataDefResourceCacheHandler implements ModuleResourceCacheHandler<MultiValuedMap<Path, ModuleQueryMetadataDef>>
    {
        @Override
        public MultiValuedMap<Path, ModuleQueryMetadataDef> load(Stream<? extends Resource> resources, Module module)
        {
            return unmodifiable(resources
                .filter(getFilter(ModuleQueryDef.META_FILE_EXTENSION))
                .map(ModuleQueryMetadataDef::new)
                .collect(MultiValuedMapCollectors.of(def -> def.getPath().getParent(), def -> def)));
        }
    }

    @Override
    public void saveNamedSet(String setName, List<String> setList)
    {
        NAMED_SET_CACHE.put(NAMED_SET_CACHE_ENTRY + setName, setList);
    }

    @Override
    public void deleteNamedSet(String setName)
    {
        NAMED_SET_CACHE.remove(NAMED_SET_CACHE_ENTRY + setName);
    }

    @Override
    public List<String> getNamedSet(String setName)
    {
        List<String> namedSet = NAMED_SET_CACHE.get(NAMED_SET_CACHE_ENTRY + setName);
        if (namedSet == null)
            throw new InvalidNamedSetException("Named set not found in cache: " + setName);

        return Collections.unmodifiableList(namedSet);
    }

    @Override
    public void registerPassthroughMethod(String name, JdbcType returnType, int minArguments, int maxArguments)
    {
        registerPassthroughMethod(name, returnType, minArguments, maxArguments, QueryManager.get().getDbSchema().getSqlDialect());
    }

    @Override
    public void registerPassthroughMethod(String name, JdbcType returnType, int minArguments, int maxArguments, SqlDialect dialect)
    {
        Method.addPassthroughMethod(name, returnType, minArguments, maxArguments, dialect);
    }

    @Override
    @NotNull
    public TableSelector selector(@NotNull QuerySchema schema, @NotNull String sql)
    {
        return new LabKeyQuerySelector(schema, sql);
    }

    @Override
    @NotNull
    public TableSelector selector(@NotNull QuerySchema schema, @NotNull String sql, Set<String> columnNames, @Nullable Filter filter, @Nullable Sort sort)
    {
        return new LabKeyQuerySelector(schema, sql, columnNames, filter, sort);
    }

    private static class LabKeyQuerySelector extends TableSelector
    {
        public LabKeyQuerySelector(@NotNull QuerySchema schema, @NotNull String sql)
        {
            super(QueryServiceImpl.get().createTable(schema, sql));
        }

        public LabKeyQuerySelector(@NotNull QuerySchema schema, @NotNull String sql, Set<String> columnNames, @Nullable Filter filter, @Nullable Sort sort)
        {
            super(QueryServiceImpl.get().createTable(schema, sql), columnNames, filter, sort);
        }
    }

    private TableInfo createTable(QuerySchema schema, String sql)
    {
        return createTable(schema, sql, null, false);
    }

    private TableInfo createTable(QuerySchema schema, String sql, @Nullable Map<String, TableInfo> tableMap, boolean strictColumnList)
    {
        Query q = new Query(schema);
        q.setStrictColumnList(strictColumnList);
        q.setTableMap(tableMap);
        q.parse(sql);

        if (q.getParseErrors().size() > 0)
            throw q.getParseErrors().get(0);

        TableInfo table = q.getTableInfo();

        if (q.getParseErrors().size() > 0)
            throw q.getParseErrors().get(0);

        return table;
    }


    @Override
    public ResultSet select(@NotNull QuerySchema schema, String sql, @Nullable Map<String, TableInfo> tableMap, boolean strictColumnList, boolean cached)
	{
        TableInfo table = createTable(schema, sql, tableMap, strictColumnList);

        QueryLogging queryLogging = new QueryLogging();
        SQLFragment sqlf = getSelectSQL(table, null, null, null, Table.ALL_ROWS, Table.NO_OFFSET, false, queryLogging);

		return new SqlSelector(table.getSchema().getScope(), sqlf, queryLogging).getResultSet(cached);
	}


    @Override
    public Results selectResults(@NotNull QuerySchema schema, String sql, @Nullable Map<String, TableInfo> tableMap, Map<String, Object> parameters, boolean strictColumnList, boolean cached) throws SQLException
    {
        Query q = new Query(schema);
        q.setStrictColumnList(strictColumnList);
        q.setTableMap(tableMap);
        q.parse(sql);

        if (q.getParseErrors().size() > 0)
            throw q.getParseErrors().get(0);

        TableInfo table = q.getTableInfo();

        return select(table, table.getColumns(), null, null, parameters, cached);
    }


    @Override
    public void bindNamedParameters(SQLFragment frag, @Nullable Map<String, Object> in)
    {
        Map<String, Object> params = null == in ? Collections.emptyMap() :
                in instanceof CaseInsensitiveHashMap ? in :
                new CaseInsensitiveHashMap<>(in);

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

            try
            {
                Object converted = p.getJdbcType().convert(value);
                frag.set(i, new Parameter.TypedValue(converted, p.getJdbcType()));
            }
            catch (ConversionException e)
            {
                throw new RuntimeSQLException(new SQLGenerationException("Could not convert '" + value + "' to a " + p.getJdbcType() + " for column '" + p.getName() + "'"));
            }
        }
    }


    // verify that named parameters have been bound
    @Override
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


    @Override
    public Results select(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort, Map<String, Object> parameters, boolean cache)
    {
        QueryLogging queryLogging = new QueryLogging();
        SQLFragment sql = getSelectSQL(table, columns, filter, sort, Table.ALL_ROWS, Table.NO_OFFSET, false, queryLogging);
        bindNamedParameters(sql, parameters);
        validateNamedParameters(sql);
		ResultSet rs = new SqlSelector(table.getSchema().getScope(), sql, queryLogging).getResultSet(cache, cache);

        // Keep track of whether we've successfully created the ResultSetImpl to return. If not, we should
        // close the underlying ResultSet before returning since it won't be accessible anywhere else
        boolean success = false;
        try
        {
            ResultsImpl result = new ResultsImpl(rs, columns);
            success = true;
            return result;
        }
        finally
        {
            if (!success)
            {
                ResultSetUtil.close(rs);
            }
        }
    }

    @Override
    public SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> selectColumns, @Nullable Filter filter, @Nullable Sort sort,
                                    int maxRows, long offset, boolean forceSort)
    {
        return getSelectSQL(table, selectColumns, filter, sort, maxRows, offset, forceSort, QueryLogging.emptyQueryLogging());
    }

	public SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> selectColumns, @Nullable Filter filter, @Nullable Sort sort,
                                    int maxRows, long offset, boolean forceSort, @NotNull QueryLogging queryLogging)
	{
        assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for rowCount; should be positive, Table.ALL_ROWS or Table.NO_ROWS";

        QueryProfiler.getInstance().ensureListenerEnvironment();

        if (null == selectColumns)
            selectColumns = table.getColumns();
        Set<ColumnInfo> allInvolvedColumns = table.getAllInvolvedColumns(selectColumns);

        // Check incoming columns to ensure they come from table
        assert Table.checkAllColumns(table, selectColumns, "getSelectSQL() selectColumns", true);

        // Create a default sort before ensuring required columns
        // Don't add a sort if we're running a custom query and it has its own ORDER BY clause
        boolean viewHasSort = sort != null && !sort.getSortList().isEmpty();
        boolean viewHasLimit = maxRows > 0 || offset > 0 || Table.NO_ROWS == maxRows;
        boolean queryHasSort = table instanceof QueryTableInfo && ((QueryTableInfo)table).hasSort();
        SqlDialect dialect = table.getSqlDialect();
        
        if (!viewHasSort)
        {
            if ((viewHasLimit || forceSort) && (!queryHasSort || dialect.isSqlServer()))
            {
                sort = createDefaultSort(selectColumns);
            }
        }

        Map<String, SQLFragment> joins = new LinkedHashMap<>();
        List<ColumnInfo> allColumns = new ArrayList<>(selectColumns);
        Map<FieldKey, ColumnInfo> columnMap = new HashMap<>();
        allColumns = ensureRequiredColumns(table, allColumns, filter, sort, null, columnMap, allInvolvedColumns);

        // Check allInvolved columns for which need to be logged
        // Logged columns may also require data logging (e.g. a patientId)
        // If a data logging column cannot be found, we will only disallow this query (throw an exception)
        //      if the logged column that requires data logging is in allColumns (which is selected columns plus ones needed for sort/filter)
        Map<ColumnInfo, Set<FieldKey>> shouldLogNameToDataLoggingMap = new HashMap<>();
        Set<ColumnLogging> shouldLogNameLoggings = new HashSet<>();
        String columnLoggingComment = null;
        SelectQueryAuditProvider selectQueryAuditProvider = null;
        for (ColumnInfo column : allInvolvedColumns)
        {
            if (!(column instanceof LookupColumn))
            {
                ColumnLogging columnLogging = column.getColumnLogging();
                if (columnLogging.shouldLogName())
                {
                    shouldLogNameLoggings.add(columnLogging);
                    if (!shouldLogNameToDataLoggingMap.containsKey(column))
                        shouldLogNameToDataLoggingMap.put(column, new HashSet<>());
                    shouldLogNameToDataLoggingMap.get(column).addAll(columnLogging.getDataLoggingColumns());
                    if (null == columnLoggingComment)
                        columnLoggingComment = columnLogging.getLoggingComment();
                    if (null == selectQueryAuditProvider)
                        selectQueryAuditProvider = columnLogging.getSelectQueryAuditProvider();
                }
            }
        }

        Set<ColumnInfo> dataLoggingColumns = new HashSet<>();
        Set<ColumnInfo> extraSelectDataLoggingColumns = new HashSet<>();
        for (Entry<ColumnInfo, Set<FieldKey>> shouldLogNameToDataLoggingMapEntry : shouldLogNameToDataLoggingMap.entrySet())
        {
            for (FieldKey fieldKey : shouldLogNameToDataLoggingMapEntry.getValue())
            {
                ColumnInfo loggingColumn = getColumnForDataLogging(table, fieldKey);
                if (null != loggingColumn)
                {
                    // For the case where we had to add the MRN column in Visualization, that column is in the table.columnMap, but not the local columnMap.
                    // This is because it's marked as hidden in the queryDef, so not to display, but isn't a normal "extra" column that DataRegion deems to add.
                    if (!allColumns.contains(loggingColumn))
                    {
                        allColumns.add(loggingColumn);
                        extraSelectDataLoggingColumns.add(loggingColumn);
                    }
                    dataLoggingColumns.add(loggingColumn);
                }
                else
                {
                    // Looking for matching column in allColumns; must match by ColumnLogging object, which gets propagated up sql parse tree
                    for (ColumnInfo column : allColumns)
                        if (shouldLogNameToDataLoggingMapEntry.getKey().getColumnLogging().equals(column.getColumnLogging()))
                            throw new UnauthorizedException("Unable to locate required logging column '" + fieldKey.toString() + "'.");
                }
            }
        }

        if (null != table.getUserSchema() && !queryLogging.isReadOnly())
            queryLogging.setQueryLogging(table.getUserSchema().getUser(), table.getUserSchema().getContainer(), columnLoggingComment,
                                         shouldLogNameLoggings, dataLoggingColumns, selectQueryAuditProvider);
        else if (!shouldLogNameLoggings.isEmpty())
            throw new UnauthorizedException("Column logging is required but cannot set query logging object.");

        // Check columns again: ensureRequiredColumns() may have added new columns
        assert Table.checkAllColumns(table, allColumns, "getSelectSQL() results of ensureRequiredColumns()", true);

        // I think this is for some custom filter/sorts that do not declare fields, but assume all table fields are available
        for (ColumnInfo c : table.getColumns())
        {
            if (!columnMap.containsKey(c.getFieldKey()))
                columnMap.put(c.getFieldKey(), c);
        }

        boolean requiresExtraColumns = allColumns.size() > selectColumns.size();
        SQLFragment outerSelect = new SQLFragment("SELECT *");
        SQLFragment selectFrag = new SQLFragment("SELECT ");   // SAS/SHARE JDBC driver requires "SELECT " ("SELECT\n" is not allowed), #17168
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
            selectFrag.append("* ");
        }
        else
        {
            CaseInsensitiveHashMap<ColumnInfo> aliases = new CaseInsensitiveHashMap<>();
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
            for (ColumnInfo column : extraSelectDataLoggingColumns)
            {
                outerSelect.append(strComma);
                outerSelect.append(dialect.getColumnSelectName(column.getAlias()));
                strComma = ", ";
            }
        }

		SQLFragment fromFrag = new SQLFragment("FROM ");
        Set<FieldKey> fieldKeySet = allColumns.stream()
                .map(ColumnInfo::getFieldKey)
                .collect(Collectors.toSet());
        SQLFragment getfromsql = table.getFromSQL(tableAlias, fieldKeySet);
        fromFrag.append(getfromsql);
        fromFrag.append(" ");

		for (Entry<String, SQLFragment> entry : joins.entrySet())
		{
			fromFrag.append("\n").append(entry.getValue());
		}

		SQLFragment filterFrag = null;

		if (filter != null)
		{
			filterFrag = filter.getSQLFragment(dialect, columnMap);
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
		nestedFrom.append("FROM (\n").append(selectFrag).append("\n").append(fromFrag).append(") x");
		SQLFragment ret = dialect.limitRows(outerSelect, nestedFrom, filterFrag, orderBy, null, maxRows, offset);

        if (AppProps.getInstance().isDevMode())
        {
            SQLFragment t = new SQLFragment();
            t.appendComment("<QueryServiceImpl.getSelectSQL(" + AliasManager.makeLegalName(table.getName(), dialect) + ")>", dialect);
            t.append(ret);
            t.appendComment("</QueryServiceImpl.getSelectSQL()>", dialect);
            ret = SQLFragment.prettyPrint(t);
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
			List<ColumnInfo> sortFields = column.getSortFields();
			if (sortFields != null)
			{
                for (ColumnInfo sortField : sortFields)
                {
                    sort.appendSortColumn(sortField.getFieldKey(), column.getSortDirection(), false);
                }
				return;
			}
		}
	}

    @Nullable
    private static ColumnInfo getColumnForDataLogging(TableInfo table, FieldKey fieldKey)
    {
        ColumnInfo loggingColumn = table.getColumn(fieldKey);
        if (null != loggingColumn)
            return loggingColumn;

        // Column names may be mangled by visualization; lookup by original column name
        for (ColumnInfo column : table.getColumns())
        {
            if (column.getColumnLogging().getOriginalColumnFieldKey().equals(fieldKey))
                return column;
        }
        return null;
    }

    @Override
    public void addCompareType(CompareType type)
    {
        COMPARE_TYPES.forEach(ct -> {
            if (type.name().equalsIgnoreCase(ct.name()))
                throw new IllegalArgumentException("CompareType with name '" + ct.name() + "' already registered");

            if (type.getScriptName().equalsIgnoreCase(ct.getScriptName()))
                throw new IllegalArgumentException("CompareType with script name '" + ct.getScriptName() + "' already registered");

            if (type.getPreferredUrlKey().equalsIgnoreCase(ct.getPreferredUrlKey()))
                throw new IllegalArgumentException("CompareType with URL key '" + ct.getPreferredUrlKey() + "' already registered");

            if (type.getDisplayValue().equalsIgnoreCase(ct.getDisplayValue()))
                throw new IllegalArgumentException("CompareType with display value '" + ct.getDisplayValue() + "' already registered");
        });
        COMPARE_TYPES.add(type);
    }

    @Override
    public Collection<CompareType> getCompareTypes()
    {
        return Collections.unmodifiableCollection(COMPARE_TYPES);
    }

    @Override
    public void addQueryListener(QueryChangeListener listener)
    {
        QueryManager.get().addQueryListener(listener);
    }

    @Override
    public void removeQueryListener(QueryChangeListener listener)
    {
        QueryManager.get().removeQueryListener(listener);
    }

    @Override
    public void addCustomViewListener(CustomViewChangeListener listener)
    {
        QueryManager.get().addCustomViewListener(listener);
    }

    @Override
    public void removeCustomViewListener(CustomViewChangeListener listener)
    {
        QueryManager.get().removeCustomViewListener(listener);
    }

    @Override
    public void registerSchemaLinkAction(@NotNull Class<? extends Controller> actionClass, @NotNull Module module, @NotNull String linkLabel)
    {
        synchronized(_schemaLinkActions)
        {
            if (_schemaLinkActions.keySet().contains(actionClass))
                throw new IllegalStateException("Schema link action : " + actionClass.getName() + " has previously been registered.");

            _schemaLinkActions.put(actionClass, new Pair<>(module, linkLabel));
        }
    }

    @Override
    public Map<ActionURL, String> getSchemaLinks(@NotNull Container c)
    {
        Set<String> activeModuleNames = c.getActiveModules().stream().map(Module::getName).collect(Collectors.toSet());

        Map<ActionURL, String> schemaLinks = new HashMap<>();
        for (Class actionClass : _schemaLinkActions.keySet())
        {
            Pair<Module, String> actionInfo = _schemaLinkActions.get(actionClass);
            if (!activeModuleNames.contains(actionInfo.first.getName()))
                continue;
            schemaLinks.put(new ActionURL(actionClass, c), actionInfo.second);
        }
        return schemaLinks;
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


    private static ThreadLocal<HashMap<Environment, Object>> environments = ThreadLocal.withInitial(HashMap::new);


    @Override
    public void setEnvironment(QueryService.Environment e, Object value)
    {
        HashMap<Environment, Object> env = environments.get();
        env.put(e, e.type.convert(value));
    }

    @Override
    public Object getEnvironment(QueryService.Environment e)
    {
        HashMap<Environment, Object> env = environments.get();
        return env.get(e);
    }

    @Override
    public Object cloneEnvironment()
    {
        HashMap<Environment, Object> env = environments.get();
        return new HashMap<>(env);
    }

    @Override
    public void copyEnvironment(Object o)
    {
        HashMap<Environment, Object> env = environments.get();
        env.clear();
        env.putAll((HashMap<Environment, Object>)o);
    }


    @Override
    public void clearEnvironment()
    {
        environments.get().clear();
    }

    @Override
    public void addAuditEvent(QueryView queryView, String comment, @Nullable Integer dataRowCount)
    {
        QueryDefinition query = queryView.getQueryDef();
        if (query == null)
            return;

        String schemaName = query.getSchemaName();
        String queryName = query.getName();
        ActionURL sortFilter = queryView.getSettings().getSortFilterURL();
        addAuditEvent(queryView.getUser(), queryView.getContainer(), schemaName, queryName, sortFilter, comment, dataRowCount);
    }

    @Override
    public void addAuditEvent(User user, Container c, String schemaName, String queryName, ActionURL sortFilter, String comment, @Nullable Integer dataRowCount)
    {
        QueryExportAuditProvider.QueryExportAuditEvent event = new QueryExportAuditProvider.QueryExportAuditEvent(c.getId(), comment);

        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        event.setSchemaName(schemaName);
        event.setQueryName(queryName);
        if (dataRowCount != null)
            event.setDataRowCount(dataRowCount);

        ActionURL url = sortFilter.clone();
        url.deleteParameter(ActionURL.Param.cancelUrl);
        url.deleteParameter(ActionURL.Param.redirectUrl);
        url.deleteParameter(ActionURL.Param.returnUrl);
        url.deleteParameter(CSRFUtil.csrfName);
        DetailsURL detailsURL = new DetailsURL(url);
        event.setDetailsUrl(detailsURL.toString());

        AuditLogService.get().addEvent(user, event);
    }


    @Override
    public void addSummaryAuditEvent(User user, Container c, TableInfo table, AuditAction action, Integer dataRowCount)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable)table;
            AuditBehaviorType auditType = auditConfigurable.getAuditBehavior();

            if (auditType == SUMMARY)
            {
                String comment = String.format(action.getCommentSummary(), dataRowCount);
                AuditTypeEvent event = _createAuditRecord(user, c, auditConfigurable, comment, null);

                AuditLogService.get().addEvent(user, event);
            }
        }
    }

    @Override
    public void addAuditEvent(User user, Container c, TableInfo table, AuditAction action, List<Map<String, Object>> ... params)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable)table;
            AuditBehaviorType auditType = auditConfigurable.getAuditBehavior();

            // Truncate audit event doesn't accept any params
            if (action == AuditAction.TRUNCATE)
            {
                assert params.length == 0;
                switch (auditType)
                {
                    case NONE:
                        return;

                    case SUMMARY:
                    case DETAILED:
                        String comment = AuditAction.TRUNCATE.getCommentSummary();
                        AuditTypeEvent event = _createAuditRecord(user, c, auditConfigurable, comment, null);
                        AuditLogService.get().addEvent(user, event);
                        return;
                }
            }

            switch (auditType)
            {
                case NONE:
                    return;

                case SUMMARY:
                {
                    assert (params.length > 0);

                    List<Map<String, Object>> rows = params[0];
                    String comment = String.format(action.getCommentSummary(), rows.size());
                    AuditTypeEvent event = _createAuditRecord(user, c, auditConfigurable, comment, rows.get(0));

                    AuditLogService.get().addEvent(user, event);
                    break;
                }
                case DETAILED:
                {
                    assert (params.length > 0);

                    List<Map<String, Object>> rows = params[0];
                    for (int i=0; i < rows.size(); i++)
                    {
                        Map<String, Object> row = rows.get(i);
                        String comment = String.format(action.getCommentDetailed(), row.size());

                        QueryUpdateAuditProvider.QueryUpdateAuditEvent event = _createAuditRecord(user, c, auditConfigurable, comment, row);

                        switch (action)
                        {
                            case INSERT:
                            {
                                String newRecord = QueryExportAuditProvider.encodeForDataMap(row);
                                if (newRecord != null)
                                    event.setNewRecordMap(newRecord);
                                break;
                            }
                            case DELETE:
                            {
                                String oldRecord = QueryExportAuditProvider.encodeForDataMap(row);
                                if (oldRecord != null)
                                    event.setOldRecordMap(oldRecord);
                                break;
                            }
                            case UPDATE:
                            {
                                assert (params.length >= 2);

                                List<Map<String, Object>> updatedRows = params[1];
                                Map<String, Object> updatedRow = updatedRows.get(i);

                                // only record modified fields
                                Map<String, Object> originalRow = new HashMap<>();
                                Map<String, Object> modifiedRow = new HashMap<>();

                                for (Entry<String, Object> entry : row.entrySet())
                                {
                                    if (updatedRow.containsKey(entry.getKey()))
                                    {
                                        Object newValue = updatedRow.get(entry.getKey());
                                        if (!Objects.equals(entry.getValue(), newValue))
                                        {
                                            originalRow.put(entry.getKey(), entry.getValue());
                                            modifiedRow.put(entry.getKey(), newValue);
                                        }
                                    }
                                }

                                String oldRecord = QueryExportAuditProvider.encodeForDataMap(originalRow);
                                if (oldRecord != null)
                                    event.setOldRecordMap(oldRecord);

                                String newRecord = QueryExportAuditProvider.encodeForDataMap(modifiedRow);
                                if (newRecord != null)
                                    event.setNewRecordMap(newRecord);
                                break;
                            }
                        }
                        AuditLogService.get().addEvent(user, event);
                    }
                    break;
                }
            }
        }
    }

    private static QueryUpdateAuditProvider.QueryUpdateAuditEvent _createAuditRecord(User user, Container c, AuditConfigurable tinfo, String comment, @Nullable Map<String, Object> row)
    {
        QueryUpdateAuditProvider.QueryUpdateAuditEvent event = new QueryUpdateAuditProvider.QueryUpdateAuditEvent(c.getId(), comment);

        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());
        event.setSchemaName(tinfo.getPublicSchemaName());
        event.setQueryName(tinfo.getPublicName());

        FieldKey rowPk = tinfo.getAuditRowPk();
        if (rowPk != null && row != null)
        {
            if (row.containsKey(rowPk.toString()))
            {
                Object pk = row.get(rowPk.toString());
                event.setRowPk(String.valueOf(pk));
            }
        }
        return event;
    }

    @Override
    public @Nullable ActionURL getAuditHistoryURL(User user, Container c, TableInfo table)
    {
        if (table.supportsAuditTracking())
        {
            AuditBehaviorType auditBehavior = ((AuditConfigurable)table).getAuditBehavior();

            if (auditBehavior != null && auditBehavior != AuditBehaviorType.NONE)
            {
                return new ActionURL(QueryController.AuditHistoryAction.class, c).
                        addParameter(QueryParam.schemaName, table.getPublicSchemaName()).
                        addParameter(QueryParam.queryName, table.getPublicName());
            }
        }
        return null;
    }

    @Override
    public DetailsURL getAuditDetailsURL(User user, Container c, TableInfo table)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable)table;
            if (auditConfigurable.getAuditBehavior() != AuditBehaviorType.NONE)
            {
                FieldKey rowPk = auditConfigurable.getAuditRowPk();

                if (rowPk != null)
                {
                    ActionURL url = new ActionURL(QueryController.AuditDetailsAction.class, c).
                            addParameter(QueryParam.schemaName, table.getPublicSchemaName()).
                            addParameter(QueryParam.queryName, table.getName());

                    return new DetailsURL(url, Collections.singletonMap("keyValue", rowPk));
                }
            }
        }
        return null;
    }

    @Override
    public Collection<String> getQueryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        return QueryManager.get().getQueryDependents(user, container, scope, schema, queries);
    }

    @Override
    public void fireQueryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        QueryManager.get().fireQueryCreated(user, container, scope, schema, queries);
    }

    @Override
    public void fireQueryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, QueryChangeListener.QueryProperty property, Collection<QueryChangeListener.QueryPropertyChange> changes)
    {
        QueryManager.get().fireQueryChanged(user, container, scope, schema, property, changes);
    }

    @Override
    public void fireQueryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        QueryManager.get().fireQueryDeleted(user, container, scope, schema, queries);
    }

    @Override
    public void cubeDataChanged(Container c)
    {
        ServerManager.cubeDataChanged(c);
    }

    @Override
    public String warmCube(User user, Container container, String schemaName, String configId, String cubeName)
    {
        return ServerManager.warmCube(user, container, schemaName, configId, cubeName);
    }

    @Override
    public void cubeDataChanged(Set<Container> containers)
    {
        for (Container c : containers)
            cubeDataChanged(c);
    }

    @Override
    public String warmCube(User user, Set<Container> containers, String schemaName, String configId, String cubeName)
    {
        StringBuilder result = new StringBuilder();
        for (Container c : containers)
            result.append(warmCube(user, c, schemaName, configId, cubeName)).append("\n");
        return result.toString();
    }

    @Override
    public String cubeDataChangedAndRewarmCube(User user, Set<Container> containers, String schemaName, String configId, String cubeName)
    {
        StringBuilder result = new StringBuilder();
        for (Container c : containers)
        {
            cubeDataChanged(c);
            result.append(warmCube(user, c, schemaName, configId, cubeName)).append("\n");
        }
        return result.toString();
    }


    /*
    public Set<ColumnInfo> getIncomingLookups(User user, Container c, TableInfo targetTable, Set<SchemaKey> schemaKeys)
    {
        Set<ColumnInfo> lookups = new HashSet<>();

        Set<UserSchema> schemas = schemaKeys.stream().map(key -> getUserSchema(user, c, key)).collect(Collectors.toSet());
        UserSchema schema = getUserSchema(user, c, schemaKey);
        SchemaTreeWalker walk = new SchemaTreeWalker<ColumnInfo, Void>(true) {
            @Override
            public ColumnInfo visitTable(TableInfo table, Path path, Void param)
            {
                return super.visitTable(table, path, param);
            }
        };
        Set<ColumnInfo> lookups = walk.visitTop(schemas, null);
    }
    */

    public static class TestCase extends Assert
    {
        @Test
        public void testSelect() throws SQLException
        {
            QueryService qs = ServiceRegistry.get().getService(QueryService.class);
            assertNotNull(qs);
            assertEquals(qs, QueryService.get());
            TableInfo roleAssignments = DbSchema.get("core", DbSchemaType.Module).getTable("roleassignments");
            assertNotNull(roleAssignments);

            {
				List<ColumnInfo> l = Arrays.asList(
					roleAssignments.getColumn("resourceid"),
					roleAssignments.getColumn("userid"),
					roleAssignments.getColumn("role"));

                try (ResultSet rs = qs.select(roleAssignments, l, null, null))
                {
                    assertEquals(rs.getMetaData().getColumnCount(), 3);
                }
            }

	        {
				List<ColumnInfo> l = Arrays.asList(
                        roleAssignments.getColumn("resourceid"),
                        roleAssignments.getColumn("userid"),
                        roleAssignments.getColumn("role"));
		        Sort sort = new Sort("+userid");

                try (ResultSet rs = qs.select(roleAssignments, l, null, sort))
                {
                    assertEquals(rs.getMetaData().getColumnCount(), 3);
                }
	        }

	        {
				List<ColumnInfo> l = Arrays.asList(
                        roleAssignments.getColumn("resourceid"),
                        roleAssignments.getColumn("userid"),
                        roleAssignments.getColumn("role"));
                Filter f = new SimpleFilter(FieldKey.fromParts("userid"), -3);

                try (ResultSet rs = qs.select(roleAssignments, l, f, null))
                {
                    assertEquals(rs.getMetaData().getColumnCount(), 3);
                }
	        }

	        {
		        Map<FieldKey,ColumnInfo> map = qs.getColumns(roleAssignments, Arrays.asList(
				        new FieldKey(null, "resourceid"),
				        new FieldKey(null, "userid"),
				        new FieldKey(null, "role"),
				        new FieldKey(new FieldKey(null, "userid"), "name")));
		        Sort sort = new Sort("+userid");
                Filter f = new SimpleFilter(FieldKey.fromParts("userid"), -3);

                try (ResultSet rs = qs.select(roleAssignments, map.values(), f, sort))
                {
                    assertEquals(rs.getMetaData().getColumnCount(), 4);
                }
	        }
        }


        @Test
        public void testParameters() throws SQLException
        {
/*            PARAMETERS(X INTEGER DEFAULT 5)
            SELECT *
                    FROM Table R
            WHERE R.X = X

            Supported data types for parameters are: BIGINT, BIT, CHAR, DECIMAL, DOUBLE, FLOAT, INTEGER, LONGVARCHAR, NUMERIC, REAL, SMALLINT, TIMESTAMP, TINYINT, VARCHAR
*/
            String sql = "PARAMETERS (" +
                    " bint BIGINT DEFAULT NULL," +
                    " bit BIT DEFAULT NULL," +
                    " char CHAR DEFAULT NULL," +
                    " dec DECIMAL DEFAULT NULL," +
                    " d DOUBLE DEFAULT NULL, " +
                    " f FLOAT DEFAULT NULL," +
                    " i INTEGER DEFAULT NULL," +
                    " text LONGVARCHAR DEFAULT NULL," +
                    " num NUMERIC DEFAULT NULL," +
                    " real REAL DEFAULT NULL," +
                    " sint SMALLINT DEFAULT NULL," +
                    " ts TIMESTAMP DEFAULT NULL," +
                    " ti TINYINT DEFAULT NULL," +
                    " s VARCHAR DEFAULT NULL)\n" +
                    "SELECT bint, bit, char, dec, d, i, text, num, real, sint, ts, ti, s FROM core.Users WHERE 0=1";
            QueryDef qd = new QueryDef();
            qd.setSchema("core");
            qd.setName("junit" + GUID.makeHash());
            qd.setContainer(JunitUtil.getTestContainer().getId());
            qd.setSql(sql);
            QueryDefinition qdef = new CustomQueryDefinitionImpl(TestContext.get().getUser(),JunitUtil.getTestContainer(),qd);
            List<QueryException> errors = new ArrayList<>();
            TableInfo t = qdef.getTable(errors, false);
            assertTrue(errors.isEmpty());
            assertEquals(JdbcType.BIGINT, t.getColumn("bint").getJdbcType());
            assertEquals(JdbcType.BOOLEAN, t.getColumn("bit").getJdbcType());
            assertEquals(JdbcType.CHAR, t.getColumn("char").getJdbcType());
            assertEquals(JdbcType.DECIMAL, t.getColumn("dec").getJdbcType());
            assertEquals(JdbcType.DOUBLE, t.getColumn("d").getJdbcType());
            assertEquals(JdbcType.INTEGER, t.getColumn("i").getJdbcType());
            assertEquals(JdbcType.LONGVARCHAR, t.getColumn("text").getJdbcType());
            assertEquals(JdbcType.DECIMAL, t.getColumn("num").getJdbcType());
            assertEquals(JdbcType.REAL, t.getColumn("real").getJdbcType());
            assertEquals(JdbcType.SMALLINT, t.getColumn("sint").getJdbcType());
            assertEquals(JdbcType.TIMESTAMP, t.getColumn("ts").getJdbcType());
            assertEquals(JdbcType.TINYINT, t.getColumn("ti").getJdbcType());
            assertEquals(JdbcType.VARCHAR, t.getColumn("s").getJdbcType());

            try (Results rs = new TableSelector(t).getResults())
            {
                assertEquals(JdbcType.BIGINT, rs.findColumnInfo(new FieldKey(null, "bint")).getJdbcType());
                assertEquals(JdbcType.BOOLEAN, rs.findColumnInfo(new FieldKey(null, "bit")).getJdbcType());
                assertEquals(JdbcType.CHAR, rs.findColumnInfo(new FieldKey(null, "char")).getJdbcType());
                assertEquals(JdbcType.DECIMAL, rs.findColumnInfo(new FieldKey(null, "dec")).getJdbcType());
                assertEquals(JdbcType.DOUBLE, rs.findColumnInfo(new FieldKey(null, "d")).getJdbcType());
                assertEquals(JdbcType.INTEGER, rs.findColumnInfo(new FieldKey(null, "i")).getJdbcType());
                assertEquals(JdbcType.LONGVARCHAR, rs.findColumnInfo(new FieldKey(null, "text")).getJdbcType());
                assertEquals(JdbcType.DECIMAL, rs.findColumnInfo(new FieldKey(null, "num")).getJdbcType());
                assertEquals(JdbcType.REAL, rs.findColumnInfo(new FieldKey(null, "real")).getJdbcType());
                assertEquals(JdbcType.SMALLINT, rs.findColumnInfo(new FieldKey(null, "sint")).getJdbcType());
                assertEquals(JdbcType.TIMESTAMP, rs.findColumnInfo(new FieldKey(null, "ts")).getJdbcType());
                assertEquals(JdbcType.TINYINT, rs.findColumnInfo(new FieldKey(null, "ti")).getJdbcType());
                assertEquals(JdbcType.VARCHAR, rs.findColumnInfo(new FieldKey(null, "s")).getJdbcType());

                ResultSetMetaData rsmd = rs.getMetaData();
                assertEquals(JdbcType.BIGINT.sqlType, rsmd.getColumnType(rs.findColumn("bint")));
                assertTrue(Types.BIT==rsmd.getColumnType(rs.findColumn("bit")) || Types.BOOLEAN==rsmd.getColumnType(rs.findColumn("bit")));
                assertTrue(Types.CHAR==rsmd.getColumnType(rs.findColumn("char")) || Types.NCHAR==rsmd.getColumnType(rs.findColumn("char")));
                assertTrue(Types.DECIMAL==rsmd.getColumnType(rs.findColumn("dec"))||Types.NUMERIC==rsmd.getColumnType(rs.findColumn("dec")));
                assertEquals(JdbcType.DOUBLE.sqlType, rsmd.getColumnType(rs.findColumn("d")));
                assertEquals(JdbcType.INTEGER.sqlType, rsmd.getColumnType(rs.findColumn("i")));
                int columntype = rsmd.getColumnType(rs.findColumn("text"));
                assertTrue(Types.LONGVARCHAR == columntype || Types.CLOB == columntype || Types.VARCHAR == columntype || Types.LONGNVARCHAR == columntype);
                assertTrue(Types.DECIMAL==rsmd.getColumnType(rs.findColumn("num"))||Types.NUMERIC==rsmd.getColumnType(rs.findColumn("num")));
                assertEquals(JdbcType.REAL.sqlType, rsmd.getColumnType(rs.findColumn("real")));
                assertEquals(JdbcType.SMALLINT.sqlType, rsmd.getColumnType(rs.findColumn("sint")));
                assertEquals(JdbcType.TIMESTAMP.sqlType, rsmd.getColumnType(rs.findColumn("ts")));
                assertEquals(JdbcType.TINYINT.sqlType, rsmd.getColumnType(rs.findColumn("ti")));
                assertTrue(JdbcType.VARCHAR.sqlType==rsmd.getColumnType(rs.findColumn("s")) || Types.NVARCHAR==rsmd.getColumnType(rs.findColumn("s")));
            }
        }

        @Test
        public void testModuleResources()
        {
            // Loads all custom views, queries, and query metadata overrides from all modules. This is a simple test of
            // the caching process that also ensures resources are valid.

            int moduleCustomViewCount = MODULE_CUSTOM_VIEW_CACHE.streamAllResourceMaps()
                .mapToInt(MultiValuedMap::size)
                .sum();

            LOG.info(moduleCustomViewCount + " custom views defined in all modules");

            int moduleQueryCount = MODULE_QUERY_DEF_CACHE.streamAllResourceMaps()
                .mapToInt(MultiValuedMap::size)
                .sum();

            LOG.info(moduleQueryCount + " module queries defined in all modules");

            int moduleQueryMetadataCount = MODULE_QUERY_METADATA_DEF_CACHE.streamAllResourceMaps()
                .mapToInt(MultiValuedMap::size)
                .sum();

            LOG.info(moduleQueryMetadataCount + " module query metadata overrides defined in all modules");

            // Make sure the cache retrieves the expected number of custom views, queries, and metadata overrides from the simpletest module, if present

            Module simpleTest = ModuleLoader.getInstance().getModule("simpletest");

            if (null != simpleTest)
            {
                assertEquals("Custom views from the simpletest module", 10, MODULE_CUSTOM_VIEW_CACHE.getResourceMap(simpleTest).size());
                assertEquals("Queries from the simpletest module", 4, MODULE_QUERY_DEF_CACHE.getResourceMap(simpleTest).size());
                assertEquals("Query metadata overrides from the simpletest module", 2, MODULE_QUERY_METADATA_DEF_CACHE.getResourceMap(simpleTest).size());
            }
        }
    }
}
