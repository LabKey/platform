/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.query.sql;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryNotFoundException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseWarning;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySchemaWrapper;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.QueryDefinitionImpl;
import org.labkey.query.controllers.QueryController;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNullElse;
import static org.labkey.api.util.ExceptionUtil.ExceptionInfo.LabkeySQL;
import static org.labkey.api.util.ExceptionUtil.ExceptionInfo.QueryName;
import static org.labkey.api.util.ExceptionUtil.ExceptionInfo.QuerySchema;


/**
 * Query is the "interface" to the SQL->SQL transformation code
 *
 * This class manages the state transitions from
 *
 * parse, resolve, generateSQL
 *
 */

public class Query
{
    private final QuerySchema _schema;
    private final String _queryName;
    private final IdentityHashMap<QuerySchema, HashMap<FieldKey, Pair<QuerySchema, TableInfo>>> _resolveCache = new IdentityHashMap<>();
    private final Set<QueryTable.TableColumn> _involvedTableColumns = new HashSet<>();

    // TableInfos handed to Query that will be used if a table isn't found.
    private Map<String, TableInfo> _tableMap;

    private final ArrayList<QueryException> _parseErrors = new ArrayList<>();
    private final ArrayList<QueryParseException> _parseWarnings = new ArrayList<>();

    private TablesDocument _metadata = null;
    private ContainerFilter _containerFilter;
    private QueryRelation _queryRoot;
    private Query _parent; // only used to avoid recursion for now
    private int _aliasCounter = 0;

    boolean _strictColumnList = false;
    String _debugName = null;
	String _querySource;
    ArrayList<QParameter> _parameters = new ArrayList<>();
    private final Set<SchemaKey> _resolvedTables = new HashSet<>();

    // for displaying dependency graph in UI
    private final HashSetValuedHashMap<QueryService.DependencyObject, QueryService.DependencyObject> _dependencies = new HashSetValuedHashMap<>();

    final IdentityHashMap<QueryTable, Map<FieldKey, QueryRelation.RelationColumn>> qtableColumnMaps = new IdentityHashMap<>();

    final private Map<String, QueryRelation> _cteTables = new LinkedCaseInsensitiveMap<>();   // Queries in With stmt
    private boolean _hasRecursiveWith = false;
    private Map<String, TableType> _metadataTableMap = null;
    private boolean _parsingWith = false;
    private boolean _allowDuplicateColumns = true;

    public Query(@NotNull QuerySchema schema)
    {
        _schema = schema;
        _queryName = null;
        MemTracker.getInstance().put(this);
    }

    public Query(@NotNull QuerySchema schema, String queryName, Query parent)
    {
        _schema = schema;
        _queryName = queryName;
        _parent = parent;
        if (null != _parent)
            _depth = _parent._depth + 1;
        MemTracker.getInstance().put(this);
    }

    public Query(@NotNull QuerySchema schema, String queryName, String sql)
    {
        _schema = schema;
        _queryName = queryName;
        _querySource = sql;
        MemTracker.getInstance().put(this);
    }

    public void setStrictColumnList(boolean b)
    {
        _strictColumnList = b;
    }

    public void setTableMap(Map<String, TableInfo> tableMap)
    {
        _tableMap = tableMap;
    }

    /* for debugging */
    public void setDebugName(String debugName)
    {
        _debugName = debugName;
        if (null != _queryRoot)
            _queryRoot.setSavedName(debugName);
    }

	QuerySchema getSchema()
	{
		return _schema;
	}


    public final int incrementAliasCounter()
    {
        return ++_aliasCounter;
    }


    public void setTablesDocument(TablesDocument doc)
    {
        _metadata = doc;
    }

    public TablesDocument getTablesDocument()
    {
        return _metadata;
    }

    public void setContainerFilter(ContainerFilter containerFilter)
    {
        ContainerFilter.logSetContainerFilter(containerFilter, getClass().getSimpleName(), StringUtils.defaultString(_debugName, "anonymous"));
        if (_queryRoot != null)
        {
            throw new IllegalStateException("query is already parsed");
        }
        _containerFilter = containerFilter;
    }

    public ContainerFilter getContainerFilter()
    {
        return _containerFilter;
    }


    public void parseQuerySource(boolean skipSuggestedColumns)
    {
        if (null == _querySource)
            throw new IllegalStateException("SQL has not been specified");

        LogManager.getLogger(Query.class).debug("Query.parse()\n" + _querySource);
        _parse(_querySource, skipSuggestedColumns);
        
        for (QueryException e : _parseErrors)
        {
            decorateException(e);
        }
    }

    public void decorateException(Exception e)
    {
        ExceptionUtil.decorateException(e, LabkeySQL, _querySource, false);
        if (null != getSchema())
            ExceptionUtil.decorateException(e, QuerySchema, getSchema().getName(), false);
        if (null != _debugName)
            ExceptionUtil.decorateException(e, QueryName, _debugName, false);
    }

    public void parse(String queryText)
    {
        parse(queryText, false);
    }

    public void parse(String queryText, boolean skipSuggestedColumns)
    {
        _querySource = queryText;
        parseQuerySource(skipSuggestedColumns);
    }

	private void _parse(String queryText, boolean skipSuggestedColumns)
    {
		try
		{
            // see https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=15562
            if (null == getSchema())
                throw new NullPointerException("getSchema() returned null");
            if (null == getSchema().getDbSchema())
            {
                parseError(_parseErrors, "Schema is not available, check configuration: " + getSchema().getName(), null);
                return;
            }
            SqlParser parser = new SqlParser(getSchema().getDbSchema().getSqlDialect(), getSchema().getContainer());
            parser.parseQuery(queryText, _parseErrors, _parseWarnings);
            if (!_parseErrors.isEmpty())
                return;
            _parameters = parser.getParameters();

			QNode root = parser.getRoot();
            QueryRelation relation = createQueryRelation(this, root, false, skipSuggestedColumns);

            if (relation == null)
                return;

            _queryRoot = relation;

            if (_queryRoot._savedName == null && _debugName != null)
                _queryRoot.setSavedName(_debugName);

            if (_parseErrors.isEmpty() && null != _queryRoot)
                _queryRoot.declareFields();
		}
        catch (QueryParseException qpe)
        {
            _parseErrors.add(qpe);
        }
        catch (UnauthorizedException ex)
        {
            throw ex;
        }
		catch (RuntimeException ex)
		{
			throw wrapRuntimeException(ex, _querySource);
		}
    }

    public static QueryRelation createQueryRelation(Query query, QNode root, boolean inFromClause)
    {
        return createQueryRelation(query, root, inFromClause, false);
    }

    public static QueryRelation createQueryRelation(Query query, QNode root, boolean inFromClause, boolean skipSuggestedColumns)
    {
        CommonTableExpressions queryCTEs = null;
        if (root instanceof QWithQuery qwith)
        {
            // With statement precedes query
            queryCTEs = new CommonTableExpressions(query, qwith.getWith());
            root = qwith.getExpr();
        }

        QueryRelation relation = null;
        if (root instanceof QUnion)
        {
            relation = new QueryUnion(query, (QUnion) root);
        }
        else if (root instanceof QQuery qquery)
        {
            QPivot qPivot = qquery.getChildOfType(QPivot.class);

            if (null == qPivot)
            {
                QuerySelect select = new QuerySelect(query, qquery, inFromClause);
                select.setSkipSuggestedColumns(skipSuggestedColumns);

                relation = new QueryLookupWrapper(query, select, null);
            }
            else
            {
                var orderby = qquery.removeOrderBy();
                var limit = qquery.removeLimit();

                QuerySelect select = new QuerySelect(query, qquery, inFromClause);
                select.setSkipSuggestedColumns(skipSuggestedColumns);

                QueryPivot pivot = new QueryPivot(query, select, qquery);

                // Grammar was relaxed to allow HAVING without GROUP BY for #36276; need to enforce here that PIVOT requires GROUP BY
                if (null == qquery.getGroupBy())
                {
                    query.getParseErrors().add(new QueryParseException("PIVOT queries must include a GROUP BY clause", null, qPivot.getLine(), qPivot.getColumn()));
                    return select;
                }

                pivot.setAlias("_pivot");
                QueryLookupWrapper wrapper = new QueryLookupWrapper(query, pivot, null);

                if (null == limit && null == orderby)
                {
                    relation = wrapper;
                }
                else
                {
                    relation = new QuerySelect(wrapper, orderby, limit);
                }
            }
        }
        
        if (null != relation && null != queryCTEs)
        {
            relation.setCommonTableExpressions(queryCTEs);
        }
        return relation;
    }



    /**
     * When the user has chosen to create a new query based on a particular table, create a new QueryDef which
     * selects all of the non-hidden columns from that table.
     */
	public void setRootTable(FieldKey key)
	{
		SourceBuilder builder = new SourceBuilder();
		builder.append("SELECT ");
		builder.pushPrefix("");
		QueryRelation relation = resolveTable(getSchema(), null, key, key.getName(), null);
		if (relation == null)
		{
			builder.append("'Table not found' AS message");
		}
		else
		{
            TableInfo table = relation.getTableInfo();
            boolean foundColumn = false;
            if (null == table)
            {
                // can't generate good sql text if the source query can't be parsed
                // we should have an error to display if null == table

                // TODO caller should check and display the parse error
                // instead of returning message in the generated SQL
                assert !this.getParseErrors().isEmpty();
            }
            else
            {
                List<FieldKey> defaultVisibleColumns = table.getDefaultVisibleColumns();
                for (FieldKey field : defaultVisibleColumns)
                {
                    if (field.getParent() != null)
                        continue;
                    if (null == table.getColumn(field.getName()))
                        continue;
                    List<String> parts = new ArrayList<>();
                    parts.add(key.getName());
                    parts.addAll(field.getParts());
                    QFieldKey qfield = QFieldKey.of(FieldKey.fromParts(parts));
                    qfield.appendSource(builder);
                    builder.nextPrefix(",");
                    foundColumn = true;

                    // Check if there's a corresponding OORIndicator that's not part of the default set, and add it
                    FieldKey oorFieldKey = new FieldKey(field.getParent(), field.getName() + OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX);
                    if (table.getColumn(oorFieldKey.getName()) != null && !defaultVisibleColumns.contains(oorFieldKey))
                    {
                        List<String> oorParts = new ArrayList<>();
                        oorParts.add(key.getName());
                        oorParts.addAll(oorFieldKey.getParts());
                        QFieldKey oorQField = QFieldKey.of(FieldKey.fromParts(oorParts));
                        oorQField.appendSource(builder);
                        builder.nextPrefix(",");
                    }
                }
			}
            if (!foundColumn)
            {
                List<String> pkNames = null == table ? null : table.getPkColumnNames();
                if (null == pkNames || pkNames.isEmpty())
                {
                    builder.append("'No columns selected' AS message");
                }
                else
                {
                    for (String pkName : pkNames)
                    {
                        List<String> parts = new ArrayList<>();
                        parts.add(key.getName());
                        parts.add(pkName);
                        QFieldKey qfield = QFieldKey.of(FieldKey.fromParts(parts));
                        qfield.appendSource(builder);
                        builder.nextPrefix(",");
                    }
                }
            }
		}
		builder.popPrefix();
		builder.append("\nFROM ");
		QFieldKey.of(new FieldKey(key.getParent(), key.getName())).appendSource(builder);
		if (key.getParent() != null)
		{
			builder.append(" AS ");
			new QIdentifier(key.getName()).appendSource(builder);
		}
		parse(builder.getText());
	}



    public TableInfo getFromTable(FieldKey key)
    {
        QuerySelect qs = getQuerySelect();
        return qs != null ? qs.getFromTable(key) : null;
    }


    public String getQueryText()
    {
        if (_queryRoot != null)
            return _queryRoot.getQueryText();
        else
        {
            // TableQueryDefinition case, but why is _queryRoot null?
            return _querySource;
        }
    }


    public Set<FieldKey> getFromTables()
    {
        QuerySelect qs = getQuerySelect();
        if (null != qs)
            return qs.getFromTables();
        return null;
    }


    public List<QueryException> getParseErrors()
    {
        return _parseErrors;
    }


    public List<QueryParseException> getParseWarnings()
    {
        return _parseWarnings;
    }

    public void reportError(String error)
    {
        _parseErrors.add(new QueryParseException(error, null, 0, 0));
    }

    public void reportWarning(String warning)
    {
        _parseWarnings.add(new QueryParseWarning(warning, null, 0, 0));
    }

    public void reportWarning(String warning, int line, int col)
    {
        _parseWarnings.add(new QueryParseWarning(warning, null, line, col));
    }


    public boolean isAggregate()
    {
        QuerySelect select = getQuerySelect();
        return null != select && select.isAggregate();
    }


    public boolean isUnion()
    {
        return _queryRoot instanceof QueryUnion;
    }


    public QuerySelect getQuerySelect()
    {
        if (_queryRoot instanceof QuerySelect)
            return (QuerySelect)_queryRoot;
        if (_queryRoot instanceof QueryLookupWrapper)
        {
            if (((QueryLookupWrapper)_queryRoot)._source instanceof QuerySelect)
                return (QuerySelect)((QueryLookupWrapper)_queryRoot)._source;
        }
        return null;
    }



    public boolean hasSubSelect()
    {
        QuerySelect select = getQuerySelect();
        return null != select && select.hasSubSelect();
    }


    public boolean hasRecursiveWith()
    {
        return _hasRecursiveWith;
    }

    public void setHasRecursiveWith(boolean recursive)
    {
        _hasRecursiveWith = recursive;
    }


    private void assertParsed()
    {
        if (_parseErrors.size() == 0 && null == _queryRoot)
        {
            assert false : "call parse() first";
            // shouldn't get here, there should be a parse error if parse() failed
            parseError(getParseErrors(), "Error parsing query", null);
        }
    }


    public ArrayList<QueryService.ParameterDecl> getParameters()
    {
        assertParsed();
        if (_parseErrors.size() > 0)
            return null;
        // don't return hidden parameters
        ArrayList<QueryService.ParameterDecl> ret = new ArrayList<>(_parameters.size());
        for (QParameter p : _parameters)
        {
           if (!p.getName().startsWith("@@"))
               ret.add(p);
        }
        return ret;
    }


    QParameter resolveParameter(FieldKey key)
    {
        if (key.getParent() != null || _parameters == null)
            return null;
        String name = key.getName();
        for (QParameter param : _parameters)
        {
            if (param.getName().equalsIgnoreCase(name))
                return param;
        }
        return null;
    }


    public TableInfo getTableInfo()
    {
        try
        {
            assertParsed();
            if (_parseErrors.size() > 0)
                return null;

            QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, getSchema().getContainer());

            TableInfo tinfo = _queryRoot.getTableInfo();

            if (_parseErrors.size() > 0)
                return null;

            return tinfo;
        }
        catch (RuntimeException x)
        {
            LogManager.getLogger(Query.class).error("error", x);
            throw Query.wrapRuntimeException(x, _querySource);
        }
    }


    static RuntimeException wrapRuntimeException(RuntimeException ex, String sql)
    {
        if (ex instanceof ConfigurationException)
            return ex;
        if (ex instanceof QueryInternalException)
            return ex;
        return new QueryInternalException(ex, sql);
    }

    public void addInvolvedTableColumn(QueryTable.TableColumn column)
    {
        _involvedTableColumns.add(column);
    }

    public Set<QueryTable.TableColumn> getInvolvedTableColumns()
    {
        return _involvedTableColumns;
    }


    public boolean isParsingWith()
    {
        return _parsingWith;
    }

    public void setParsingWith(boolean parsingWith)
    {
        _parsingWith = parsingWith;
    }


    public static class QueryInternalException extends RuntimeException
    {
        QueryInternalException(RuntimeException cause, String sql)
        {
            super("Internal error while parsing \""+ sql + "\"", cause);
        }
    }


	//
	// Helpers
	//


	static void parseError(List<QueryException> errors, String message, @Nullable QNode node)
	{
		int line = 0;
		int column = 0;
		if (node != null)
		{
			line = node.getLine();
			column = node.getColumn();
		}
		//noinspection ThrowableInstanceNeverThrown
		errors.add(new QueryParseException(message, null, line, column));
	}


    // Query._depth handles most recursion, but there can be unexpected recursion caused by LinkedSchema for instance, or
    // other paths that cause a query to be compiled during resolveTable().  The thread local value makes sure this case
    // is handled as well.
    static private final ThreadLocal<AtomicInteger> resolveDepth = ThreadLocal.withInitial(() -> new AtomicInteger(0));

    static final int MAX_TABLES_IN_QUERY = 200;
    static final int MAX_RESOLVE_DEPTH = 20;

    int _countResolvedTables = 0;
    int _depth = 1;


    private int getTotalCountResolved()
    {
        return _countResolvedTables + (null == _parent ? 0 : _parent.getTotalCountResolved());
    }


	/**
	 * Resolve a particular table name.  The table name may have schema names (folder.schema.table etc.) prepended to it.
	 */
    QueryRelation resolveTable(QuerySchema currentSchema, QNode node, FieldKey key, String alias, @Nullable ContainerFilter.Type cfType) throws QueryNotFoundException
    {
        // to simplify the logic a bit, break out the error translation from the _resolveTable()
        List<QueryException> resolveExceptions = new ArrayList<>();
        QueryDefinition[] queryDefOUT = new QueryDefinition[1];
        QueryRelation ret;

        try
        {
            if (resolveDepth.get().incrementAndGet() > MAX_RESOLVE_DEPTH)
            {
                parseError(resolveExceptions, "Too many tables used in this query (recursive?)", node);
                return null;
            }

            ret = _resolveTable(currentSchema, node, key, alias, resolveExceptions, queryDefOUT, cfType);
            if ((ret != null) && (queryDefOUT[0] == null))
            {
                TableInfo tinfo = ret.getTableInfo();
                if (tinfo != null)
                    _resolvedTables.add(SchemaKey.fromParts(tinfo.getSchema().getName(), tinfo.getName()));
            }
        }
        catch (QueryNotFoundException qnfe)
        {
            _parseErrors.add(qnfe);
            return null;
        }
        finally
        {
            resolveDepth.get().decrementAndGet();
        }

        QueryDefinition def = queryDefOUT[0];
        ActionURL source = (null == def) ? null :  def.urlFor(QueryAction.sourceQuery);

        QueryException firstError = null;
        for (QueryException x : resolveExceptions)
        {
            if (!(x instanceof QueryParseException && ((QueryParseException)x).isWarning()))
            {
                firstError = x;
                break;
            }
        }
        if (null == firstError && !resolveExceptions.isEmpty())
            firstError = resolveExceptions.get(0);

        if (null != firstError)
        {
            QueryParseException qpe;
            if (firstError instanceof QueryParseException && ((QueryParseException)firstError).isWarning())
            {
                qpe = new QueryParseWarning("Query '" + key.getName() + "' has warnings", null, null == node ? 0 : node.getLine(), null == node ? 0 : node.getColumn());
                _parseWarnings.add(qpe);
            }
            else
            {
                qpe = new QueryParseException("Query '" + key.getName() + "' has errors", firstError, null == node ? 0 : node.getLine(), null == node ? 0 : node.getColumn());
                _parseErrors.add(qpe);
            }
            if (null != source)
            {
                ExceptionUtil.decorateException(qpe, ExceptionUtil.ExceptionInfo.ResolveURL, source.getLocalURIString(false), true);
                ExceptionUtil.decorateException(qpe, ExceptionUtil.ExceptionInfo.ResolveText, "edit " + def.getName(), true);
            }
        }

        assert ret != null || !_parseErrors.isEmpty();
        return ret;
    }


    void addDependency(QueryService.DependencyType type, Container container, SchemaKey containerRelativeSchemaKey, String name, ActionURL url)
    {
        ActionURL fromUrl = new QueryController.QueryUrlsImpl().urlSchemaBrowser(_schema.getContainer(), _schema.getName(), _queryName);
        QueryService.DependencyObject from = new QueryService.DependencyObject(QueryService.DependencyType.Query,
                _schema.getContainer(), ((UserSchema)_schema).getSchemaPath(), requireNonNullElse(_queryName, "~"), fromUrl);

        QueryService.DependencyObject dep = new QueryService.DependencyObject(type,
                container, containerRelativeSchemaKey, name, url);

        _dependencies.put(from, dep);
    }


    public @NotNull HashSetValuedHashMap<QueryService.DependencyObject, QueryService.DependencyObject> getDependencies()
    {
        return _dependencies;
    }


    /* TODO use _dependencies to implement this instead of _resolvedTables */
    public Set<SchemaKey> getResolvedTables()
    {
        return _resolvedTables;
    }


    private QueryRelation _resolveTable(
            QuerySchema currentSchema, QNode node, FieldKey key, String alias,
            // OUT parameters
            List<QueryException> resolveExceptions,
            QueryDefinition[] queryDefOUT,
            ContainerFilter.Type cfType)
        throws QueryNotFoundException
	{
	    FieldKey cacheKey = null==cfType ? key : key.append(" ~cf~ ", cfType.name());
        boolean trackDependency = true;

        ++_countResolvedTables;
        if (getTotalCountResolved() > MAX_TABLES_IN_QUERY || _depth > MAX_RESOLVE_DEPTH)
        {
            // recursive query?
            parseError(resolveExceptions, "Too many tables used in this query (recursive?)", node);
            return null;
        }

        // check the cache to see if we've resolved this exact same table already
        _resolveCache.computeIfAbsent(currentSchema, k -> new HashMap<>());
        Pair<QuerySchema, TableInfo> found = _resolveCache.get(currentSchema).get(cacheKey);
        if (null != found)
        {
            // ensure that the table has the same containerFilter that we asked for, otherwise don't return cached TableInfo
            TableInfo ti = found.second;
            ContainerFilter cachedTableCF = ti.getContainerFilter();
            if (null != cachedTableCF)
            {
                if (null != cfType)
                {
                    // explicit containerfilter e.g. due to SQL table annotation TABLE[ContainerFilter='Current']
                    if (cfType == cachedTableCF.getType())
                        return new QueryTable(this, found.first, found.second, alias);
                }
                // compare against default container filter for this query
                else if (cachedTableCF.equals(getContainerFilter()))
                {
                    return new QueryTable(this, found.first, found.second, alias);
                }
            }
        }

		List<String> parts = key.getParts();
		List<String> names = new ArrayList<>(parts.size());
		for (String part : parts)
			names.add(FieldKey.decodePart(part));

		ContainerFilter cf = null==cfType ? getContainerFilter() : null;

        QuerySchema resolvedSchema = currentSchema;
		for (int i = 0; i < parts.size() - 1; i ++)
		{
			String name = names.get(i);
            resolvedSchema = resolvedSchema.getSchema(name);
            if (resolvedSchema instanceof QuerySchema.ContainerSchema)
            {
                // If user explicitly specifies a different folder, don't propagate the default container filter.
                // Use the default container filter for that schema.
                cf = null;
            }
            else if (resolvedSchema == null && DbSchema.TEMP_SCHEMA_NAME.equalsIgnoreCase(name))
            {
                resolvedSchema = new QuerySchemaWrapper(DbSchema.getTemp());
                trackDependency = false;
            }

			if (null == resolvedSchema)
			{
                throw new QueryNotFoundException(StringUtils.join(names, "."), null == node ? 0 : node.getLine(), null == node ? 0 : node.getColumn());
			}
		}

        Object t;

        try
        {
            if (null == cf && null != cfType)
                cf = cfType.create(resolvedSchema);

            if (resolvedSchema instanceof UserSchema userSchema)
            {
                TableType tableType = lookupMetadataTable(key.getName());
                boolean forWrite = tableType != null;
                t = userSchema._getTableOrQuery(key.getName(), cf, true, forWrite, resolveExceptions);
            }
            else
            {
                t = resolvedSchema.getTable(key.getName(), cf);
            }
        }
        catch (QueryException ex)
        {
            resolveExceptions.add(ex);
            return null;
        }
        catch (UnauthorizedException ex)
        {
            parseError(resolveExceptions, "No permission to read table: " + key.getName(), node);
            return null;
        }

        // Last attempt: map of extra tables previously resolved and provided to Query.
        if (t == null && _tableMap != null)
        {
            t = _tableMap.get(key.getName());
            trackDependency = false;
        }

		if (t == null)
		{
            throw new QueryNotFoundException(StringUtils.join(names, "."), null == node ? 0 : node.getLine(), null == node ? 0 : node.getColumn());
		}

        if (t instanceof TableInfo tableInfo)
        {
            // I don't see why Query is being roped into helping with this??? Can't this be handled on the LinkedSchema side?
            TableType tableType = lookupMetadataTable(tableInfo.getName());
            if (null != tableType && tableInfo.isMetadataOverrideable() && resolvedSchema instanceof UserSchema userSchema)
                tableInfo.overlayMetadata(Collections.singletonList(tableType), userSchema, _parseErrors);
            _resolveCache.get(currentSchema).put(cacheKey, new Pair<>(resolvedSchema, tableInfo));

            String name = ((TableInfo) t).getName();

            if (trackDependency)
            {
                ActionURL url = new QueryController.QueryUrlsImpl().urlSchemaBrowser(resolvedSchema.getContainer(), resolvedSchema.getName(), name);
                addDependency(QueryService.DependencyType.Table, resolvedSchema.getContainer(), ((UserSchema)resolvedSchema).getSchemaPath(), name, url);
            }

            if (!tableInfo.hasPermission(getSchema().getUser(), ReadPermission.class))
                throw new UnauthorizedException(tableInfo.getPublicSchemaName() + "." + tableInfo.getName());

            return new QueryTable(this, resolvedSchema, tableInfo, alias);
        }

        if (t instanceof QueryDefinition)
        {
            QueryDefinitionImpl def = (QueryDefinitionImpl)t;
            queryDefOUT[0] = def;
            Query query = def.getQuery(resolvedSchema, resolveExceptions, this, true);

            if (query.getParseErrors().size() > 0)
            {
                resolveExceptions.addAll(query.getParseErrors());
                return null;
            }

            // merge parameter lists
            mergeParameters(query);

            // merge dependencies
            _dependencies.putAll(query._dependencies);

            // and add this dependency
            if (trackDependency)
            {
                String name = def.getName();
                ActionURL url = new QueryController.QueryUrlsImpl().urlSchemaBrowser(resolvedSchema.getContainer(), resolvedSchema.getName(), name);
                addDependency(QueryService.DependencyType.Query, resolvedSchema.getContainer(), ((UserSchema)resolvedSchema).getSchemaPath(), name, url);
            }

            // check for cases where we don't want to merge
            QuerySelect s = query.getQuerySelect();
            boolean simpleSelect = null != s && !s.isAggregate() && null == s._distinct;
            boolean hasMetadata = query.getTablesDocument() != null && query.getTablesDocument().getTables().getTableArray().length > 0;
            if (!simpleSelect || hasMetadata)
            {
                TableInfo ti;

                if (resolvedSchema instanceof UserSchema)
                {
                    ti = def.createTable((UserSchema)resolvedSchema, resolveExceptions, true, query);
                }
                else
                {
                    ti = def.getTable(resolveExceptions, true);
                }

                if (null == ti)
                {
                    if (resolveExceptions.isEmpty())
                        parseError(resolveExceptions, "Could not load table: " +  key.getName() + "' has errors", node);
                    return null;
                }
                return new QueryTable(this, resolvedSchema, ti, alias);
            }

            // move relation to new outer query
            // NOTE: setting _inFromClause == true enables some optimizations
            s._inFromClause = true;
            QueryRelation ret = query._queryRoot;
            ret.setQuery(this);
            resolveExceptions.addAll(query.getParseErrors());

            TableType tableType = null;
            if (query.getTablesDocument() != null && query.getTablesDocument().getTables().getTableArray().length > 0)
                tableType = query.getTablesDocument().getTables().getTableArray(0);

            if (null != tableType || !(query._queryRoot instanceof QueryLookupWrapper))
                ret = new QueryLookupWrapper(this, query._queryRoot, tableType);

            ret.setAlias(alias);
            return ret;
        }

		return null;
	}


    void mergeParameters(Query fromQuery)
    {
        CaseInsensitiveHashMap<QParameter> map = new CaseInsensitiveHashMap<>();
        if (null == _parameters)
            _parameters = new ArrayList<>();
        for (QParameter p : _parameters)
            map.put(p.getName(), p);
        if (null != fromQuery._parameters)
        {
            for (QParameter p : fromQuery._parameters)
            {
                QParameter to = map.get(p.getName());
                if (null == to)
                    _parameters.add(p);
                else if (to.getJdbcType() != p.getJdbcType())
                    parseError(_parseErrors, "Parameter is declared with different types: " + p.getName(), to);
            }
        }
    }

    public void putCteTable(String legalName, QueryRelation relation)
    {
        _cteTables.put(legalName, relation);
    }

    @Nullable
    public QueryRelation lookupCteTable(String legalName)
    {
        return _cteTables.get(legalName);
    }

    public void removeWithTable(String legalName)
    {
        _cteTables.remove(legalName);
    }

    public void setMetadataTableMap(Map<String, TableType> metadataTableMap)
    {
        _metadataTableMap = metadataTableMap;
    }

    public void setAllowDuplicateColumns(boolean allow)
    {
        _allowDuplicateColumns = allow;
    }

    public boolean isAllowDuplicateColumns()
    {
        return _allowDuplicateColumns;
    }

    @Nullable
    public TableType lookupMetadataTable(String tableName)
    {
        return null != _metadataTableMap ? _metadataTableMap.get(tableName) : null;
    }
}
