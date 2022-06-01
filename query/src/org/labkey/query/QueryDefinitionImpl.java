/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveLinkedHashMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.MetadataParseException;
import org.labkey.api.query.MetadataParseWarning;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryChangeListener.QueryProperty;
import org.labkey.api.query.QueryChangeListener.QueryPropertyChange;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseWarning;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ViewOptions;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.queryCustomView.NamedFiltersType;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.QueryTableInfo;
import org.labkey.query.view.CustomViewSetKey;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings({"ThrowableInstanceNeverThrown"})
public abstract class QueryDefinitionImpl implements QueryDefinition
{
    private static final QueryManager mgr = QueryManager.get();
    private static final Logger log = LogManager.getLogger(QueryDefinitionImpl.class);

    protected final User _user;
    protected final Container _container;

    protected UserSchema _schema = null;
    protected QueryDef _queryDef;
    protected List<QueryPropertyChange> _changes = null;

    protected boolean _dirty;
    private ContainerFilter _containerFilter;
    private boolean _temporary = false;
    private boolean _includedForLookups = true;

    // todo: spec 25628 making _cache static prevents the entire map of all tableInfos from being reloaded each time GetQueryViewsAction instantiates a new copy of QueryDefintionImpl
    // but may make _cache susceptible to concurrency conflicts or security problems -- more investigation is needed
    // private static Map<Pair<String, Boolean>, TableInfo> _cache = new HashMap<>();
    private final Map<Pair<String, Boolean>, TableInfo> _cache = new HashMap<>();

    private Map<String, TableType> _metadataTableMap = null;

    public QueryDefinitionImpl(User user, Container container, QueryDef queryDef)
    {
        _user = user;
        _container = container;
        _queryDef = queryDef;
        _dirty = queryDef.getQueryDefId() == 0;
        if (_dirty)
            _changes = new ArrayList<>();
    }

    public QueryDefinitionImpl(User user, Container container, UserSchema schema, String name)
    {
        this(user, container, schema.getSchemaPath(), name);
        _schema = schema;
    }

    public QueryDefinitionImpl(User user, Container container, SchemaKey schema, String name)
    {
        _user = user;
        _container = container;
        _queryDef = new QueryDef();
        _queryDef.setName(name);
        _queryDef.setSchemaPath(schema);
        _queryDef.setContainer(container.getId());
        _dirty = true;
        _changes = new ArrayList<>();
    }

    @Override
    public boolean canInherit()
    {
        return (_queryDef.getFlags() & QueryManager.FLAG_INHERITABLE) != 0;
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        String name="anonymous";
        if (null != _queryDef && null != _queryDef.getName())
            name = _queryDef.getName();
        ContainerFilter.logSetContainerFilter(containerFilter, getClass().getSimpleName(), name);
        if (containerFilter != _containerFilter)
            _cache.clear();
        _containerFilter = containerFilter;
    }

    @Override
    public ContainerFilter getContainerFilter()
    {
        return _containerFilter;
    }

    @Override
    public void delete(User user)
    {
        delete(user, true);
    }

    @Override
    public void delete(User user, boolean fireChangeEvent)
    {
        if (!canDelete(user))
        {
            throw new IllegalArgumentException("Access denied");
        }
        QueryManager.get().delete(_queryDef);
        if (fireChangeEvent)
            QueryService.get().fireQueryDeleted(user, getContainer(), null, getSchemaPath(), Collections.singleton(getName()));
        _queryDef = null;
    }

    protected boolean isNew()
    {
        return _queryDef.getQueryDefId() == 0;
    }

    @Override
    public boolean canEdit(User user)
    {
        if (!getDefinitionContainer().equals(getContainer()))
            return false;
        return getDefinitionContainer().hasPermissions(user, Set.of(EditQueriesPermission.class, UpdatePermission.class));
    }

    @Override
    public boolean canEditMetadata(User user)
    {
        return canEdit(user);
    }

    @Override
    public boolean canDelete(User user)
    {
        return canEdit(user);
    }

    @Override
    public CustomView getCustomView(@NotNull User owner, @Nullable HttpServletRequest request, String name)
    {
        CustomView result = getCustomViews(owner, request, true, false).get(name);
        if (result == null && name != null)
        {
            String extra = "";

            if (null != request)
            {
                String referrer = request.getHeader("Referer");
                extra = " [url=" + request.getRequestURI() + (null != referrer ? ", referrer=" + referrer : "") + "]";
            }
            log.info("Could not find the requested custom view named '" + name + "'" + " in " + getSchemaPath() + "." + getQueryDef().getName() + " in the container " + _container.getPath() + " for user " + owner + extra);
        }
        return result;
    }

    @Override
    public CustomView getSharedCustomView(String name)
    {
        return getCustomViews(null, null, true, true).get(name);
    }


    @Override
    public CustomView createCustomView()
    {
        return new CustomViewImpl(this, null, null);
    }

    @Override
    public CustomView createCustomView(@NotNull User owner, String name)
    {
        return new CustomViewImpl(this, owner, name);
    }

    @Override
    public CustomView createSharedCustomView(String name)
    {
        return new CustomViewImpl(this, null, name);
    }


    Map<String, CustomView> _customViewMap = null;

    @Override
    public Map<String, CustomView> getCustomViews(@Nullable User owner, @Nullable HttpServletRequest request, boolean includeHidden, boolean sharedOnly)
    {
        Map<String, CustomView> ret = new CaseInsensitiveLinkedHashMap<>();

        if (includeHidden)
        {
            AutoGeneratedCustomView insertView = new AutoGeneratedInsertCustomView(this);
            ret.put(insertView.getName(), insertView);
            AutoGeneratedCustomView detailsView = new AutoGeneratedDetailsCustomView(this);
            ret.put(detailsView.getName(), detailsView);
            AutoGeneratedCustomView updateView = new AutoGeneratedUpdateCustomView(this);
            ret.put(updateView.getName(), updateView);
        }

        // Database custom view and module custom views.
        Map<String, CustomView> map;
        if (null != _customViewMap && !sharedOnly)
        {
            map = _customViewMap;
        }
        else
        {
            map = QueryServiceImpl.get().getCustomViewMap(getUser(), getContainer(), owner, this, true, sharedOnly);
            if (!sharedOnly)
                _customViewMap = map;
        }
        ret.putAll(map);

        // Session views have highest precedence.
        if (owner != null && request != null)
        {
            for (CstmView view : CustomViewSetKey.getCustomViewsFromSession(request, this).values())
            {
                CustomViewImpl v = new CustomViewImpl(this, view);
                v.isSession(true);
                CustomView existing = ret.get(view.getName());
                if (existing instanceof ModuleCustomView)
                    v.setOverridesModuleView(true);
                else if (existing instanceof  CustomViewInfoImpl)
                    v.setOverridesModuleView(((CustomViewInfoImpl) existing).isOverridesModuleView());

                ret.put(view.getName(), v);
            }
        }

        if (!includeHidden)
        {
            ret.entrySet().removeIf(stringCustomViewEntry -> stringCustomViewEntry.getValue().isHidden());
        }

        return ret;
    }

    @Override
    public User getUser()
    {
        return _user;
    }

    @Override
    public Container getContainer()
    {
        return _container;
    }

    @Override
    public Container getDefinitionContainer()
    {
        return ContainerManager.getForId(_queryDef.getContainerId());
    }

    @Override
    public String getName()
    {
        return _queryDef.getName();
    }

    @Override
    public void setName(String name)
    {
        if (getName().equals(name))
            return;
        String oldName = getName();
        edit().setName(name);
        _changes.add(new QueryPropertyChange<>(this, QueryProperty.Name, oldName, name));
    }

    @Override
    public String getTitle()
    {
        return getName();
    }

    @Override
    public String getModuleName()
    {
        // TODO: In the future this could use the TableInfo if that ever has access to module information
        // or possibly lookup via ModuleLoader.
        return "";
    }



    @Override
    public List<QueryParseException> getParseErrors(QuerySchema schema)
    {
        ArrayList<QueryParseException> ret = new ArrayList<>();
        validateQuery(schema, ret, null);
        return ret;
    }


    @Override
    public boolean validateQuery(QuerySchema schema, @NotNull List<QueryParseException> errors, @Nullable List<QueryParseException> warnings)
    {
        String metadata = StringUtils.trimToNull(getMetadataXml());
        if (metadata != null)
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            List<XmlError> xmlErrors = new ArrayList<>();
            options.setErrorListener(xmlErrors);
            try
            {
                TablesDocument table = TablesDocument.Factory.parse(metadata, options);
                table.validate(options);
            }
            catch (XmlException xmle)
            {
                XmlError error = xmle.getError();
                errors.add(new MetadataParseException(XmlBeansUtil.getErrorMessage(xmle), null, error == null ? 0 : error.getLine(), error == null ? 0 : error.getColumn()));
            }
            for (XmlError xmle : xmlErrors)
            {
                errors.add(new MetadataParseException(xmle));
            }
        }

        Query q = getQuery(schema);
        if (q.getParseErrors().isEmpty())
        {
            try
            {
                q.getTableInfo();
            }
            catch (QueryService.NamedParameterNotProvided x)
            {
                /* ignore */
            }
            catch (Query.QueryInternalException e)
            {
                errors.add(wrapParseException(e.getCause(), false));
            }
            catch (Exception x)
            {
                log.error("Unexpected error",  x);
                errors.add(wrapParseException(x, false));
            }
        }
        for (QueryException e : q.getParseErrors())
            errors.add(wrapParseException(e, true));
        if (errors.isEmpty() && null != warnings)
            warnings.addAll(q.getParseWarnings());

        if (errors.isEmpty())
        {
            List<QueryException> queryExceptions = new ArrayList<>();
            getTable(queryExceptions, true);
            for (QueryException e : queryExceptions)
            {
                if (!(e instanceof MetadataParseWarning))
                    errors.add(wrapParseException(e, true));
                else if  (null != warnings)
                    warnings.add((MetadataParseWarning) e);
            }
        }
        return errors.isEmpty();
    }



    public static QueryParseException wrapParseException(Throwable e, boolean metadataExists)
    {
        if (e instanceof MetadataParseException)
        {
            return new QueryParseException(metadataExists ? "Error with dependent query XML: " + e.getMessage() : e.getMessage(), e, 0, 0);
        }
        if (e instanceof QueryParseException)
        {
            return (QueryParseException) e;
        }
        if (e instanceof QueryException)
        {
            return new QueryParseException(e.getMessage(), e.getCause(), 0, 0);
        }
        if (e instanceof BadSqlGrammarException || e instanceof DataIntegrityViolationException)
        {
            return new QueryParseException(e.getMessage(), e, 0, 0);
        }
        return new QueryParseException("Unexpected exception", e, 0, 0);
    }

    public final Query getQuery(@NotNull QuerySchema schema)
    {
        return getQuery(schema, null, null, false);
    }

    public final Query getQuery(@NotNull QuerySchema schema, List<QueryException> errors, Query parent, boolean includeMetadata)
    {
        return getQuery(schema, errors, parent, includeMetadata, false);
    }

    /*
     * I find it very strange that only the xml errors get added to the "errors" list, while
     * the parse errors remain in the getParseErrors() list
     */
    public Query getQuery(@NotNull QuerySchema schema, List<QueryException> errors, Query parent, boolean includeMetadata, boolean skipSuggestedColumns)
    {
        Query query = new Query(schema, getName(), parent);

        query.setDebugName(getSchemaName() + "." + getName());
        query.setContainerFilter(getContainerFilter());
        query.setMetadataTableMap(_metadataTableMap);
        String sql = getSql();
        if (sql != null)
        {
            log.debug("Parsing query " + schema.getSchemaName() + "." + getName());
            query.parse(sql, skipSuggestedColumns);
        }
        if (includeMetadata)
        {
            query.setTablesDocument(_queryDef.getParsedMetadata().getTablesDocument(errors));
        }
        return query;
    }

    @Override
    public void setSchema(@NotNull UserSchema schema)
    {
        _schema = schema;
    }

    @Override
    @NotNull
    public UserSchema getSchema()
    {
//        assert _schema != null : "Schema not set post-construction";
        if (null == _schema)
            _schema = QueryService.get().getUserSchema(getUser(), getContainer(), getSchemaPath());
        if (_schema == null)
        {
            throw new NotFoundException("Could not find schema " + getSchemaPath() + " in " + getContainer().getPath() + " for user " + getUser() + " with query " + getName() + " - is its owning module enabled?");
        }
        assert _schema.getSchemaPath().equals(getSchemaPath()) : "Paths were not equal: " + _schema.getSchemaPath() + " vs " + getSchemaPath();
        return _schema;
    }


    @Override
    @Deprecated // Use .getSchemaPath()
    public String getSchemaName()
    {
        return _queryDef.getSchemaPath().toString();
    }

    @Override
    public SchemaKey getSchemaPath()
    {
        return _queryDef.getSchemaPath();
    }


    @Override
    @Nullable
    public TableInfo getTable(@Nullable List<QueryException> errors, boolean includeMetadata)
    {
        return getTable(getSchema(), errors, includeMetadata);
    }

    @Nullable
    @Override
    public TableInfo getTable(@NotNull UserSchema schema, @Nullable List<QueryException> errors, boolean includeMetadata)
    {
        return getTable(schema, errors, includeMetadata, false);
    }

    @Nullable
    @Override
    public TableInfo getTable(@NotNull UserSchema schema, @Nullable List<QueryException> errors, boolean includeMetadata, boolean skipSuggestedColumns)
    {
        // CONSIDER: define UserSchema.equals() ?
        if (schema.getSchemaPath().equals(getSchema().getSchemaPath()) &&
            schema.getContainer().equals(getSchema().getContainer()) &&
            Objects.equals(schema.getUser(), getSchema().getUser()))
        {
            // Stash the schema because it's a match with the one we'd made for ourself
            if (_schema == null)
            {
                _schema = schema;
            }
            Pair<String,Boolean> key = new Pair<>(getName().toLowerCase(), includeMetadata);
            TableInfo table = _cache.get(key);
            if (table == null)
            {
                // Occasionally called with a get, but simple table creation is not a serious vector for CSRF attacks
                try (var ignored = SpringActionController.ignoreSqlUpdates())
                {
                    table = createTable(schema, errors, includeMetadata, null, skipSuggestedColumns);
                }

                if (null == table)
                    return null;

                log.debug("Caching table " + schema.getName() + "." + table.getName());
                _cache.put(key, table);
            }
            else
            {
                log.debug("Returning cached table '" + getName() + "', " + (includeMetadata ? "with" : "without") + " metadata");
            }

            return table;
        }
        log.debug("!! Not using cached table: schemas not equal");

        return createTable(schema, errors, includeMetadata, null);
    }

    @Nullable
    public TableInfo createTable(@NotNull UserSchema schema, @Nullable List<QueryException> errors, boolean includeMetadata, @Nullable Query query)
    {
        return createTable(schema, errors, includeMetadata, query, false);
    }

    /**
     * @param query a Query object to reuse, if available. Otherwise, a new one will be created behind the scenes
     */
    @Nullable
    public TableInfo createTable(@NotNull UserSchema schema, @Nullable List<QueryException> errors, boolean includeMetadata, @Nullable Query query, boolean skipSuggestedColumns)
    {
        if (errors == null)
        {
            errors = new ArrayList<>();
        }
        if (query == null)
        {
            query = getQuery(schema, errors, null, includeMetadata, skipSuggestedColumns);
        }
        TableInfo ret = query.getTableInfo();
        if (null != ret)
        {
            QueryTableInfo queryTable = (QueryTableInfo)ret;
            queryTable.setDescription(getDescription());
            queryTable.setName(getName());

            if (includeMetadata)
            {
                List<QueryException> metadataErrors = new ArrayList<>();
                ret = applyQueryMetadata(schema, metadataErrors, query, (AbstractTableInfo) ret);
                for (QueryException qe : metadataErrors)
                {
                    if (!(qe instanceof QueryParseWarning))
                        errors.add(qe);
                }
            }
        }

        if (!query.getParseErrors().isEmpty())
        {
            String resolveURL = null;
            ActionURL sourceURL = urlFor(QueryAction.sourceQuery);
            if (sourceURL != null)
                resolveURL = sourceURL.getLocalURIString(false);

            for (QueryException qe : query.getParseErrors())
            {
                if (ExceptionUtil.decorateException(qe, ExceptionUtil.ExceptionInfo.ResolveURL, resolveURL, false))
                    ExceptionUtil.decorateException(qe, ExceptionUtil.ExceptionInfo.ResolveText, "edit " + getName(), true);
                errors.add(qe);
            }
        }

        if (ret != null)
        {
            // Apply ContainerContext to any URLs added in metadata override.
            ((AbstractTableInfo)ret).afterConstruct();
        }

        return ret;
    }

    /**
     * Apply the metadata attached to the Query to the AbstractTableInfo
     */
    protected TableInfo applyQueryMetadata(UserSchema schema, List<QueryException> errors, Query query, AbstractTableInfo ret)
    {
        // First, apply metadata associated with the query (e.g., .query.xml files)
        TableType xmlTable = query.getTablesDocument() == null ? null : query.getTablesDocument().getTables().getTableArray(0);
        NamedFiltersType[] xmlFilters = query.getTablesDocument() == null ? null : query.getTablesDocument().getTables().getFiltersArray();

        Map<String, NamedFiltersType> namedFilters = new HashMap<>();
        if (xmlFilters != null)
            for (NamedFiltersType xmlFilter : xmlFilters)
                namedFilters.put(xmlFilter.getName(), xmlFilter);

        applyQueryMetadata(schema, errors, xmlTable, namedFilters, ret);

        // Finally, lookup any XML metadata that has been stored in the database, which won't have been applied
        // if this is a file-based custom query
        ret.overlayMetadata(getName(), schema, errors);

        return ret;
    }

    protected void applyQueryMetadata(UserSchema schema, List<QueryException> errors, TableType xmlTable, Map<String, NamedFiltersType> namedFilters, AbstractTableInfo ret)
    {
        ret.loadFromXML(schema, Collections.singleton(xmlTable), errors);
    }

    @Override
    public String getMetadataXml()
    {
        return _queryDef.getMetaData();
    }

    @Override
    public void setDefinitionContainer(Container container)
    {
        if (container.equals(getDefinitionContainer()))
            return;
        Container oldContainer = getDefinitionContainer();
        edit().setContainer(container.getId());
        _changes.add(new QueryPropertyChange<>(this, QueryProperty.Container, oldContainer, container));
    }

    @Override
    public Collection<QueryPropertyChange> save(User user, Container container)
    {
        return save(user, container, true);
    }

    @Override
    public Collection<QueryPropertyChange> save(User user, Container container, boolean fireChangeEvent)
    {
        setDefinitionContainer(container);
        if (!_dirty)
            return null;

        if (isNew())
        {
            _queryDef = QueryManager.get().insert(user, _queryDef);

            if (fireChangeEvent)
                QueryService.get().fireQueryCreated(user, container, null, _queryDef.getSchemaPath(), Collections.singleton(_queryDef.getName()));
        }
        else
        {
            _queryDef = QueryManager.get().update(user, _queryDef);

            if (fireChangeEvent)
            {
                // Fire change event for each property change.
                for (QueryPropertyChange change : _changes)
                {
                    QueryService.get().fireQueryChanged(user, container, null, _queryDef.getSchemaPath(), change.getProperty(), Collections.singleton(change));
                }
            }
        }

        Collection<QueryPropertyChange> changes = _changes;
        _changes = null;
        _dirty = false;
        return changes;
    }

    @Override
    public void setCanInherit(boolean f)
    {
        if (canInherit() == f)
            return;
        boolean oldValue = canInherit();
        edit().setFlags(mgr.setCanInherit(_queryDef.getFlags(), f));
        _changes.add(new QueryPropertyChange<>(this, QueryProperty.Inherit, oldValue, f));
    }

    @Override
    public boolean isHidden()
    {
        if (mgr.isHidden(_queryDef.getFlags()))
            return true;

        if (_queryDef.getParsedMetadata() != null)
        {
            List<QueryException> errors = new ArrayList<>();
            TablesDocument xDoc = _queryDef.getParsedMetadata().getTablesDocument(errors);
            if (errors.isEmpty() && xDoc != null)
            {
                TableType[] xTables = xDoc.getTables().getTableArray();
                if (xTables != null && xTables.length > 0)
                {
                    if (xTables[0].isSetHidden())
                        return xTables[0].getHidden();
                }
            }
        }

        return false;
    }

    @Override
    public void setIsHidden(boolean f)
    {
        if (isHidden() == f)
            return;
        boolean oldValue = isHidden();
        edit().setFlags(mgr.setIsHidden(_queryDef.getFlags(), f));
        _changes.add(new QueryPropertyChange<>(this, QueryProperty.Hidden, oldValue, f));
    }

    @Override
    public boolean isSnapshot()
    {
        return mgr.isSnapshot(_queryDef.getFlags());
    }

    @Override
    public void setIsTemporary(boolean temporary)
    {
        _temporary = temporary;
    }

    @Override
    public boolean isTemporary()
    {
        return _temporary;
    }

    public void setIsSnapshot(boolean f)
    {
        if (isSnapshot() == f)
            return;
        edit().setFlags(mgr.setIsSnapshot(_queryDef.getFlags(), f));
    }

    @Override
    public boolean isIncludedForLookups()
    {
        return _includedForLookups;
    }

    @Override
    public void setIsIncludedForLookups(boolean included)
    {
        _includedForLookups = included;
    }

    @Override
    public void setMetadataXml(String xml)
    {
        edit().setMetaData(StringUtils.trimToNull(xml));
        // CONSIDER: Add metadata QueryPropertyChange to _changes
    }

    @Override
    public ActionURL urlFor(QueryAction action)
    {
        return urlFor(action, getContainer());
    }

    @Override
    public ActionURL urlFor(QueryAction action, Container container)
    {
        ActionURL url = null;
        if (action == QueryAction.insertQueryRow || action == QueryAction.deleteQueryRows || action == QueryAction.executeQuery || action == QueryAction.importData)
        {
            TableInfo table = getTable(null, true);
            if (table != null)
            {
                switch (action)
                {
                    case insertQueryRow:
                        url = table.getInsertURL(container);
                        break;
                    case deleteQueryRows:
                        url = table.getDeleteURL(container);
                        break;
                    case executeQuery:
                        url = table.getGridURL(container);
                        break;
                    case importData:
                        url = table.getImportDataURL(container);
                        break;
                }
            }

            if (url == AbstractTableInfo.LINK_DISABLER_ACTION_URL)
                return null;
        }

        return url != null ? url : QueryService.get().urlDefault(container, action, getSchemaName(), getName());
    }

    @Override
    public ActionURL urlFor(QueryAction action, Container container, Map<String, Object> pks)
    {
        ActionURL url = urlFor(action, container);
        if (url == null)
            return null;

        for (Map.Entry<String, Object> pk : pks.entrySet())
        {
            if (pk.getValue() != null)
                url.addParameter(pk.getKey(), pk.getValue().toString());
        }
        return url;
    }

    @Override
    public StringExpression urlExpr(QueryAction action, Container container)
    {
        StringExpression expr = null;
        TableInfo table = null;
        if (action == QueryAction.detailsQueryRow || action == QueryAction.updateQueryRow || action == QueryAction.updateQueryRows)
        {
            table = getTable(null, true);
            if (table != null)
            {
                switch (action)
                {
                    case detailsQueryRow:
                        expr = table.getDetailsURL(null, container);
                        break;

                    case updateQueryRow:
                    case updateQueryRows:
                        expr = table.getUpdateURL(null, container);
                        break;
                }

                if (expr == AbstractTableInfo.LINK_DISABLER)
                    return null;
            }
        }

        if (expr == null)
        {
            ActionURL url = urlFor(action, container);
            if (url != null)
            {
                // Query's pk columns may not correspond to the main table's pk columns.
                // Adding the pk URL parameters will probably only work for simple queries.
                if (table == null)
                    table = getTable(null, true);
                if (table != null)
                {
                    List<ColumnInfo> pkColumns = table.getPkColumns();
                    if (pkColumns.size() > 0)
                    {
                        Map<String, String> params = new HashMap<>();
                        for (ColumnInfo column : pkColumns)
                        {
                            params.put(column.getName(), column.getAlias());
                        }
                        DetailsURL detailsURL = new DetailsURL(url, params);

                        // Details and update url expressions on tables usually have their ContainerContext set in AbstractTableInfo.afterConstruct(),
                        // but since we're creating a generic query URL expression we need to set the ContainerContext now before rendering.
                        ContainerContext cc = table.getContainerContext();
                        if (cc != null)
                            detailsURL.setContainerContext(cc, false);

                        expr = detailsURL;
                    }
                }
                else
                {
                    expr = StringExpressionFactory.create(url.getLocalURIString());
                }
            }
        }

        return expr;
    }

    @Override
    public String getDescription()
    {
        return _queryDef.getDescription();
    }

    @Override
    public void setDescription(String description)
    {
        if (StringUtils.equals(getDescription(), description))
            return;
        String oldDescription = getDescription();
        edit().setDescription(description);
        _changes.add(new QueryPropertyChange<>(this, QueryProperty.Description, oldDescription, description));
    }

    public QueryDef getQueryDef()
    {
        return _queryDef;
    }

    @Override
    public List<ColumnInfo> getColumns(CustomView view, TableInfo table)
    {
        if (table == null)
            throw new NullPointerException();

        if (view != null)
        {
            Map<FieldKey, ColumnInfo> map = QueryService.get().getColumns(table, view.getColumns());

            if (!map.isEmpty())
            {
                return new ArrayList<>(map.values());
            }
        }

        return new ArrayList<>(QueryService.get().getColumns(table, table.getDefaultVisibleColumns()).values());
    }

    @Override
    public List<DisplayColumn> getDisplayColumns(CustomView view, TableInfo table)
    {
        if (table == null)
            throw new NullPointerException();
        List<DisplayColumn> ret;
        if (view != null)
        {
            ret = QueryService.get().getDisplayColumns(table, view.getColumnProperties());
            if (!ret.isEmpty())
            {
                return ret;
            }

            if (view.getName() != null)
            {
                // Try and grab the columns from the default view
                CustomView defaultView = QueryService.get().getCustomView(getUser(), getContainer(), getUser(), getSchemaName(), getQueryDef().getName(), null);
                if (defaultView != null)
                {
                    ret = QueryService.get().getDisplayColumns(table, defaultView.getColumnProperties());
                    if (!ret.isEmpty())
                    {
                        return ret;
                    }
                }
            }
        }
        ret = new ArrayList<>();
        // Fall back on the table's default set of columns
        for (ColumnInfo column : QueryService.get().getColumns(table, table.getDefaultVisibleColumns()).values())
        {
            ret.add(column.getRenderer());
        }
        return ret;
    }

    protected QueryDef edit()
    {
        if (_dirty)
            return _queryDef;
        _queryDef = _queryDef.clone();
        _changes = new ArrayList<>();
        _dirty = true;
        return _queryDef;
    }

    @Override
    public Collection<String> getDependents(User user)
    {
        return QueryManager.get().getQueryDependents(user, getContainer(), null, getSchemaPath(), Collections.singleton(getName()));
    }

    @Override
    public boolean isSqlEditable()
    {
        return true;
    }

    @Override
    public boolean isMetadataEditable()
    {
        return true;
    }

    @Override
    public void setMetadataTableMap(Map<String, TableType> metadataTableMap)
    {
        _metadataTableMap = metadataTableMap;
    }

    @Override
    public String getQueryKey()
    {
        return getSchemaPath().toString() + "||" + getName();
    }

    @Override
    public ViewOptions getViewOptions()
    {
        return new ViewOptionsImpl(getMetadataXml());
    }

    private class ViewOptionsImpl implements ViewOptions
    {
        private TablesDocument _document;

        public ViewOptionsImpl(String metadataXml)
        {
            if (!StringUtils.isBlank(metadataXml))
            {
                try
                {
                    _document = TablesDocument.Factory.parse(metadataXml);
                }
                catch (XmlException e)
                {
                    // Don't completely die if someone specified invalid metadata XML. Log a warning
                    // and render without the custom metadata.
                    log.warn("Unable to parse metadata XML for " + getSchemaName() + "." + getName() + " in " + getContainer(), e);
                }
            }
            if (_document == null)
            {
                _document = TablesDocument.Factory.newInstance();
                TableType table = _document.addNewTables().addNewTable();

                table.setTableName(getName());
                table.setTableDbType("NOT_IN_DB");
            }
        }

        @Override
        public List<ViewFilterItem> getViewFilterItems()
        {
            List<ViewFilterItem> items = new ArrayList<>();

            org.labkey.data.xml.ViewOptions options = _document.getTables().getTableArray()[0].getViewOptions();
            if (options == null)
                options = _document.getTables().getTableArray()[0].addNewViewOptions();

            for (org.labkey.data.xml.ViewOptions.ViewFilterItem item : options.getViewFilterItemArray())
            {
                items.add(new ViewFilterItemImpl(item.getType(), item.getEnabled()));
            }
            return items;
        }

        @Override
        public void setViewFilterItems(List<ViewFilterItem> items)
        {
            List<org.labkey.data.xml.ViewOptions.ViewFilterItem> filterItems = new ArrayList<>();

            for (ViewFilterItem item : items)
            {
                org.labkey.data.xml.ViewOptions.ViewFilterItem vfi = org.labkey.data.xml.ViewOptions.ViewFilterItem.Factory.newInstance();
                vfi.setType(item.getViewType());
                vfi.setEnabled(item.isEnabled());

                filterItems.add(vfi);
            }
            org.labkey.data.xml.ViewOptions options = _document.getTables().getTableArray()[0].getViewOptions();
            if (options == null)
                options = _document.getTables().getTableArray()[0].addNewViewOptions();

            options.setViewFilterItemArray(filterItems.toArray(new org.labkey.data.xml.ViewOptions.ViewFilterItem[0]));
        }

        @Override
        public void save(User user)
        {
            setMetadataXml(_document.toString());
            QueryDefinitionImpl.this.save(user, getDefinitionContainer());
        }

        @Override
        public void delete(User user)
        {
            _document.getTables().getTableArray()[0].unsetViewOptions();
            save(user);
        }
    }
}
