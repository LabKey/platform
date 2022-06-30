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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.CachedResultSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.PostgreSql91Dialect;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryNotFoundException;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseWarning;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySchemaWrapper;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewServlet;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.QueryDefinitionImpl;
import org.labkey.query.controllers.QueryController;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;
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

    final private Map<String, QueryRelation> _withTables = new LinkedCaseInsensitiveMap<>();   // Queries in With stmt
    private boolean _hasRecursiveWith = false;
    private Map<String, TableType> _metadataTableMap = null;
    private QueryRelation _withFirstTerm = null;
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
        QueryWith queryWith = null;
        if (root instanceof QWithQuery)
        {
            // With statement precedes query
            queryWith = new QueryWith(query, ((QWithQuery)root).getWith());
            root = ((QWithQuery) root).getExpr();
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
        
        if (null != relation && null != queryWith)
        {
            relation.setQueryWith(queryWith);
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

    public QueryRelation getWithFirstTerm()
    {
        return _withFirstTerm;
    }

    public void setWithFirstTerm(QueryRelation withFirstTerm)
    {
        _withFirstTerm = withFirstTerm;
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

    public void putWithTable(String legalName, QueryRelation relation)
    {
        _withTables.put(legalName, relation);
    }

    @Nullable
    public QueryRelation lookupWithTable(String legalName)
    {
        return _withTables.get(legalName);
    }

    public void removeWithTable(String legalName)
    {
        _withTables.remove(legalName);
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


    //
	// TESTING
	//
    private static class TestDataLoader extends DataLoader
    {
        private static final String[] COLUMNS = new String[] {"d", "seven", "twelve", "day", "month", "date", "duration", "guid"};
        private static final JdbcType[] TYPES = new JdbcType[] {JdbcType.DOUBLE, JdbcType.INTEGER, JdbcType.INTEGER, JdbcType.VARCHAR, JdbcType.VARCHAR, JdbcType.TIMESTAMP, JdbcType.VARCHAR, JdbcType.GUID};
        private static final String[] days = new String[] {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        private static final String[] months = new String[] {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

        private final String[][] data;
        private final RowMapFactory<Object> _rowMapFactory;

		// UNDONE: need some NULLS in here
        @SuppressWarnings({"UnusedAssignment"})
        TestDataLoader(String propertyPrefix, int len)
        {
            data = new String[len+1][];
            data[0] = COLUMNS;
            var m = new HashMap<String,Integer>();
            for (int i=0 ; i<COLUMNS.length ; i++)
                m.put(COLUMNS[i],i);
            _rowMapFactory = new RowMapFactory<>(new ArrayListMap.FindMap<>(m));

            for (int i=1 ; i<=len ; i++)
            {
                String[] row = data[i] = new String[COLUMNS.length];
                int c = 0;
                row[c++] = "" + Math.exp(i);
                row[c++] = "" + (i%7);
                row[c++] = "" + (i%12);
                row[c++] = days[i%7];
                row[c++] = months[i%12];
                row[c++] = DateUtil.toISO(DateUtil.parseISODateTime("2010-01-01") + ((long)i)*12*60*60*1000L);
                row[c++] = DateUtil.formatDuration(((long)i)*1000);
                row[c++] = GUID.makeGUID();
            }
            _columns = new ColumnDescriptor[COLUMNS.length];
            for (int i=0 ; i<_columns.length ; i++)
                _columns[i] = new ColumnDescriptor(COLUMNS[i], TYPES[i].getJavaClass());
            setScrollable(true);
        }

        @Override
        public String[][] getFirstNLines(int n)
        {
            return data;
        }

        private int i=1;

        @Override
        protected CloseableIterator<Map<String, Object>> _iterator(boolean includeRowHash)
        {
            return new _Iterator();
        }

        class _Iterator implements CloseableIterator<Map<String, Object>>
        {
            @Override
            public boolean hasNext()
            {
                return i < data.length;
            }

            @Override
            public Map<String, Object> next()
            {
                // Leave this in place: javac complains without this cast. IntelliJ disagrees.
                //noinspection RedundantCast
                return _rowMapFactory.getRowMap((Object[])data[i++]);
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close()
            {
            }
        }

        @Override
        public void close()
        {
        }
    }

/* This is what the data looks like if you want to create your own list for testing
d,seven,twelve,day,month,date,duration,guid
2.718281828459045,1,1,Monday,February,2010-01-01 12:00:00.000,1s,017fed24-8c54-103a-82d5-809624b24ac0
7.38905609893065,2,2,Tuesday,March,2010-01-02 00:00:00.000,2s,017fed25-8c54-103a-82d5-809624b24ac0
20.085536923187668,3,3,Wednesday,April,2010-01-02 12:00:00.000,3s,017fed26-8c54-103a-82d5-809624b24ac0
54.598150033144236,4,4,Thursday,May,2010-01-03 00:00:00.000,4s,017fed27-8c54-103a-82d5-809624b24ac0
148.4131591025766,5,5,Friday,June,2010-01-03 12:00:00.000,5s,017fed28-8c54-103a-82d5-809624b24ac0
403.4287934927351,6,6,Saturday,July,2010-01-04 00:00:00.000,6s,017fed29-8c54-103a-82d5-809624b24ac0
1096.6331584284585,0,7,Sunday,August,2010-01-04 12:00:00.000,7s,017fed2a-8c54-103a-82d5-809624b24ac0
2980.9579870417283,1,8,Monday,September,2010-01-05 00:00:00.000,8s,017fed2b-8c54-103a-82d5-809624b24ac0
8103.083927575384,2,9,Tuesday,October,2010-01-05 12:00:00.000,9s,017fed2c-8c54-103a-82d5-809624b24ac0
22026.465794806718,3,10,Wednesday,November,2010-01-06 00:00:00.000,10s,017fed2d-8c54-103a-82d5-809624b24ac0
59874.14171519782,4,11,Thursday,December,2010-01-06 12:00:00.000,11s,017fed2e-8c54-103a-82d5-809624b24ac0
162754.79141900392,5,0,Friday,January,2010-01-07 00:00:00.000,12s,017fed2f-8c54-103a-82d5-809624b24ac0
442413.3920089205,6,1,Saturday,February,2010-01-07 12:00:00.000,13s,017fed30-8c54-103a-82d5-809624b24ac0
1202604.2841647768,0,2,Sunday,March,2010-01-08 00:00:00.000,14s,017fed31-8c54-103a-82d5-809624b24ac0
3269017.3724721107,1,3,Monday,April,2010-01-08 12:00:00.000,15s,017fed32-8c54-103a-82d5-809624b24ac0
8886110.520507872,2,4,Tuesday,May,2010-01-09 00:00:00.000,16s,017fed33-8c54-103a-82d5-809624b24ac0
2.41549527535753E7,3,5,Wednesday,June,2010-01-09 12:00:00.000,17s,017fed34-8c54-103a-82d5-809624b24ac0
6.565996913733051E7,4,6,Thursday,July,2010-01-10 00:00:00.000,18s,017fed35-8c54-103a-82d5-809624b24ac0
1.7848230096318725E8,5,7,Friday,August,2010-01-10 12:00:00.000,19s,017fed36-8c54-103a-82d5-809624b24ac0
4.851651954097903E8,6,8,Saturday,September,2010-01-11 00:00:00.000,20s,017fed37-8c54-103a-82d5-809624b24ac0
1.3188157344832146E9,0,9,Sunday,October,2010-01-11 12:00:00.000,21s,017fed38-8c54-103a-82d5-809624b24ac0
3.584912846131592E9,1,10,Monday,November,2010-01-12 00:00:00.000,22s,017fed39-8c54-103a-82d5-809624b24ac0
9.744803446248903E9,2,11,Tuesday,December,2010-01-12 12:00:00.000,23s,017fed3a-8c54-103a-82d5-809624b24ac0
2.648912212984347E10,3,0,Wednesday,January,2010-01-13 00:00:00.000,24s,017fed3b-8c54-103a-82d5-809624b24ac0
7.200489933738588E10,4,1,Thursday,February,2010-01-13 12:00:00.000,25s,017fed3c-8c54-103a-82d5-809624b24ac0
1.9572960942883878E11,5,2,Friday,March,2010-01-14 00:00:00.000,26s,017fed3d-8c54-103a-82d5-809624b24ac0
5.3204824060179865E11,6,3,Saturday,April,2010-01-14 12:00:00.000,27s,017fed3e-8c54-103a-82d5-809624b24ac0
1.446257064291475E12,0,4,Sunday,May,2010-01-15 00:00:00.000,28s,017fed3f-8c54-103a-82d5-809624b24ac0
3.931334297144042E12,1,5,Monday,June,2010-01-15 12:00:00.000,29s,017fed40-8c54-103a-82d5-809624b24ac0
1.0686474581524463E13,2,6,Tuesday,July,2010-01-16 00:00:00.000,30s,017fed41-8c54-103a-82d5-809624b24ac0
2.9048849665247426E13,3,7,Wednesday,August,2010-01-16 12:00:00.000,31s,017fed42-8c54-103a-82d5-809624b24ac0
7.896296018268069E13,4,8,Thursday,September,2010-01-17 00:00:00.000,32s,017fed43-8c54-103a-82d5-809624b24ac0
2.1464357978591606E14,5,9,Friday,October,2010-01-17 12:00:00.000,33s,017fed44-8c54-103a-82d5-809624b24ac0
5.834617425274549E14,6,10,Saturday,November,2010-01-18 00:00:00.000,34s,017fed45-8c54-103a-82d5-809624b24ac0
1.5860134523134308E15,0,11,Sunday,December,2010-01-18 12:00:00.000,35s,017fed46-8c54-103a-82d5-809624b24ac0
4.311231547115195E15,1,0,Monday,January,2010-01-19 00:00:00.000,36s,017fed47-8c54-103a-82d5-809624b24ac0
1.1719142372802612E16,2,1,Tuesday,February,2010-01-19 12:00:00.000,37s,017fed48-8c54-103a-82d5-809624b24ac0
3.1855931757113756E16,3,2,Wednesday,March,2010-01-20 00:00:00.000,38s,017fed49-8c54-103a-82d5-809624b24ac0
8.6593400423993744E16,4,3,Thursday,April,2010-01-20 12:00:00.000,39s,017fed4a-8c54-103a-82d5-809624b24ac0
2.3538526683702E17,5,4,Friday,May,2010-01-21 00:00:00.000,40s,017fed4b-8c54-103a-82d5-809624b24ac0
6.3984349353005491E17,6,5,Saturday,June,2010-01-21 12:00:00.000,41s,017fed4c-8c54-103a-82d5-809624b24ac0
1.73927494152050099E18,0,6,Sunday,July,2010-01-22 00:00:00.000,42s,017fed4d-8c54-103a-82d5-809624b24ac0
4.7278394682293463E18,1,7,Monday,August,2010-01-22 12:00:00.000,43s,017fed4e-8c54-103a-82d5-809624b24ac0
1.2851600114359308E19,2,8,Tuesday,September,2010-01-23 00:00:00.000,44s,017fed4f-8c54-103a-82d5-809624b24ac0
3.4934271057485095E19,3,9,Wednesday,October,2010-01-23 12:00:00.000,45s,017fed50-8c54-103a-82d5-809624b24ac0
9.496119420602448E19,4,10,Thursday,November,2010-01-24 00:00:00.000,46s,017fed51-8c54-103a-82d5-809624b24ac0
2.5813128861900675E20,5,11,Friday,December,2010-01-24 12:00:00.000,47s,017fed52-8c54-103a-82d5-809624b24ac0
7.016735912097631E20,6,0,Saturday,January,2010-01-25 00:00:00.000,48s,017fed53-8c54-103a-82d5-809624b24ac0
1.9073465724950998E21,0,1,Sunday,February,2010-01-25 12:00:00.000,49s,017fed54-8c54-103a-82d5-809624b24ac0
5.184705528587072E21,1,2,Monday,March,2010-01-26 00:00:00.000,50s,017fed55-8c54-103a-82d5-809624b24ac0
1.4093490824269389E22,2,3,Tuesday,April,2010-01-26 12:00:00.000,51s,017fed56-8c54-103a-82d5-809624b24ac0
3.831008000716577E22,3,4,Wednesday,May,2010-01-27 00:00:00.000,52s,017fed57-8c54-103a-82d5-809624b24ac0
1.0413759433029089E23,4,5,Thursday,June,2010-01-27 12:00:00.000,53s,017fed58-8c54-103a-82d5-809624b24ac0
2.830753303274694E23,5,6,Friday,July,2010-01-28 00:00:00.000,54s,017fed59-8c54-103a-82d5-809624b24ac0
7.694785265142018E23,6,7,Saturday,August,2010-01-28 12:00:00.000,55s,017fed5a-8c54-103a-82d5-809624b24ac0
2.091659496012996E24,0,8,Sunday,September,2010-01-29 00:00:00.000,56s,017fed5b-8c54-103a-82d5-809624b24ac0
5.685719999335932E24,1,9,Monday,October,2010-01-29 12:00:00.000,57s,017fed5c-8c54-103a-82d5-809624b24ac0
1.545538935590104E25,2,10,Tuesday,November,2010-01-30 00:00:00.000,58s,017fed5d-8c54-103a-82d5-809624b24ac0
4.2012104037905144E25,3,11,Wednesday,December,2010-01-30 12:00:00.000,59s,017fed5e-8c54-103a-82d5-809624b24ac0
1.1420073898156842E26,4,0,Thursday,January,2010-01-31 00:00:00.000,1m,017fed5f-8c54-103a-82d5-809624b24ac0
3.10429793570192E26,5,1,Friday,February,2010-01-31 12:00:00.000,1m1s,017fed60-8c54-103a-82d5-809624b24ac0
8.438356668741454E26,6,2,Saturday,March,2010-02-01 00:00:00.000,1m2s,017fed61-8c54-103a-82d5-809624b24ac0
2.29378315946961E27,0,3,Sunday,April,2010-02-01 12:00:00.000,1m3s,017fed62-8c54-103a-82d5-809624b24ac0
6.235149080811617E27,1,4,Monday,May,2010-02-02 00:00:00.000,1m4s,017fed63-8c54-103a-82d5-809624b24ac0
1.6948892444103338E28,2,5,Tuesday,June,2010-02-02 12:00:00.000,1m5s,017fed64-8c54-103a-82d5-809624b24ac0
4.607186634331292E28,3,6,Wednesday,July,2010-02-03 00:00:00.000,1m6s,017fed65-8c54-103a-82d5-809624b24ac0
1.2523631708422137E29,4,7,Thursday,August,2010-02-03 12:00:00.000,1m7s,017fed66-8c54-103a-82d5-809624b24ac0
3.404276049931741E29,5,8,Friday,September,2010-02-04 00:00:00.000,1m8s,017fed67-8c54-103a-82d5-809624b24ac0
9.253781725587787E29,6,9,Saturday,October,2010-02-04 12:00:00.000,1m9s,017fed68-8c54-103a-82d5-809624b24ac0
2.515438670919167E30,0,10,Sunday,November,2010-02-05 00:00:00.000,1m10s,017fed69-8c54-103a-82d5-809624b24ac0
6.837671229762744E30,1,11,Monday,December,2010-02-05 12:00:00.000,1m11s,017fed6a-8c54-103a-82d5-809624b24ac0
1.8586717452841279E31,2,0,Tuesday,January,2010-02-06 00:00:00.000,1m12s,017fed6b-8c54-103a-82d5-809624b24ac0
5.052393630276104E31,3,1,Wednesday,February,2010-02-06 12:00:00.000,1m13s,017fed6c-8c54-103a-82d5-809624b24ac0
1.3733829795401761E32,4,2,Thursday,March,2010-02-07 00:00:00.000,1m14s,017fed6d-8c54-103a-82d5-809624b24ac0
3.7332419967990015E32,5,3,Friday,April,2010-02-07 12:00:00.000,1m15s,017fed6e-8c54-103a-82d5-809624b24ac0
1.0148003881138887E33,6,4,Saturday,May,2010-02-08 00:00:00.000,1m16s,017fed6f-8c54-103a-82d5-809624b24ac0
2.7585134545231703E33,0,5,Sunday,June,2010-02-08 12:00:00.000,1m17s,017fed70-8c54-103a-82d5-809624b24ac0
7.498416996990121E33,1,6,Monday,July,2010-02-09 00:00:00.000,1m18s,017fed71-8c54-103a-82d5-809624b24ac0
2.0382810665126688E34,2,7,Tuesday,August,2010-02-09 12:00:00.000,1m19s,017fed72-8c54-103a-82d5-809624b24ac0
5.54062238439351E34,3,8,Wednesday,September,2010-02-10 00:00:00.000,1m20s,017fed73-8c54-103a-82d5-809624b24ac0
1.5060973145850306E35,4,9,Thursday,October,2010-02-10 12:00:00.000,1m21s,017fed74-8c54-103a-82d5-809624b24ac0
4.0939969621274545E35,5,10,Friday,November,2010-02-11 00:00:00.000,1m22s,017fed75-8c54-103a-82d5-809624b24ac0
1.1128637547917594E36,6,11,Saturday,December,2010-02-11 12:00:00.000,1m23s,017fed76-8c54-103a-82d5-809624b24ac0
3.0250773222011426E36,0,0,Sunday,January,2010-02-12 00:00:00.000,1m24s,017fed77-8c54-103a-82d5-809624b24ac0
*/



    private static class SqlTest
    {
        public final String _sql;

		public String _name = null;
		public String _metadata = null;
        public int _countColumns = -1;
        public int _countRows = -1;

        SqlTest(String sql)
        {
            _sql = sql;
        }

        SqlTest(String sql, int cols, int rows)
        {
            this(sql);
            _countColumns = cols;
            _countRows = rows;
        }


		SqlTest(String name, String sql, String metadata, int cols, int rows)
		{
            this(sql);
			_name = name;
			_metadata = metadata;
			_countColumns = cols;
			_countRows = rows;
		}

        void validate(QueryTestCase test, @Nullable Container container)
        {
            if (null == container)
                container = JunitUtil.getTestContainer();

            try (CachedResultSet rs = test.resultset(_sql, container == JunitUtil.getTestContainer() ? null : container))
            {
                ResultSetMetaData md = rs.getMetaData();
                if (_countColumns >= 0)
                    QueryTestCase.assertEquals("Wrong number of columns. " + _sql, _countColumns, md.getColumnCount());
                if (_countRows >= 0)
                    QueryTestCase.assertEquals("Wrong number of rows. " + _sql, _countRows, rs.getSize());

                validateResults(rs);

                if (_name != null)
                {
                    User user = TestContext.get().getUser();
                    QueryDefinition existing = QueryService.get().getQueryDef(user, container, "lists", _name);
                    if (null != existing)
                        existing.delete(TestContext.get().getUser());
                    QueryDefinition q = QueryService.get().createQueryDef(user, container, SchemaKey.fromString("lists"), _name);
                    q.setSql(_sql);
                    if (null != _metadata)
                        q.setMetadataXml(_metadata);
                    q.setCanInherit(true);
                    q.save(TestContext.get().getUser(), container);
                }
            }
            catch (Exception x)
            {
                QueryTestCase.fail(x.toString() + "\n" + _sql);
            }
        }

        protected void validateResults(CachedResultSet rs) throws Exception
        {
        }
    }


    private static class MethodSqlTest extends SqlTest
    {
        private final JdbcType _type;
        private final Object _value;
        private final Callable<Object> _call;

        MethodSqlTest(String sql, JdbcType type, Object value)
        {
            super(sql, 1, 1);
            _type = type;
            _value = value;
            _call = null;
        }

        MethodSqlTest(String sql, JdbcType type, Callable<Object> call)
        {
            super(sql, 1, 1);
            _type = type;
            _value = null;
            _call = call;
        }

        @Override
        protected void validateResults(CachedResultSet rs) throws Exception
        {
            QueryTestCase.assertTrue("Expected one row: " + _sql, rs.next());
            Object o = rs.getObject(1);
            QueryTestCase.assertFalse("Expected one row: " + _sql, rs.next());
            Object value = null == _call ? _value : _call.call();
            assertSqlEquals(value, o);
        }

        private void assertSqlEquals(Object a, Object b)
        {
            if (null == a)
            {
                QueryTestCase.assertNull("Expected NULL value: + sql", b);
                return;
            }
            if (null == b)
            {
                QueryTestCase.fail("Did not expect null value: " + _sql);
                return;
            }
//            QueryTestCase.assertEquals(sql, _type.getJavaClass(), b.getClass());
            if (a instanceof Number && b instanceof Number)
            {
                if (((Number)a).doubleValue() == ((Number)b).doubleValue())
                    return;
            }
            if (a instanceof Character)
                a = a.toString();
            if (b instanceof Character)
                b = b.toString();
            if (_type == JdbcType.BOOLEAN)
            {
                a = a.equals(1) ? true : a.equals(0) ? false : a;
                b = b.equals(1) ? true : b.equals(0) ? false : b;
            }
            if (a.equals(b))
                return;
            QueryTestCase.assertEquals("expected:<" + a + "> but was:<" + b + "> " + _sql, a, b);
        }
    }


    static class FailTest extends SqlTest
    {
        private final boolean _onlyQueryParseExceptions;

        public FailTest(String sql, boolean onlyQueryParseExceptions)
        {
            super(sql);
            this._onlyQueryParseExceptions = onlyQueryParseExceptions;
        }

        FailTest(String sql)
        {
            this(sql, true);
        }

        @Override
        void validate(QueryTestCase test, @Nullable Container container)
        {
            try (CachedResultSet ignored = (CachedResultSet) QueryService.get().select(test.lists, _sql))
            {
                QueryTestCase.fail("should fail: " + _sql);
            }
            catch (QueryParseException x)
            {
                // should fail with SQLException not runtime exception
            }
            catch (Exception x)
            {
                if (!(x instanceof QueryException) || _onlyQueryParseExceptions)
                {
                    throw new AssertionError("unexpected exception: " + x.getMessage(), x);
                }
            }
        }
    }

    private static class InvolvedColumnsTest extends SqlTest
    {
        private final List<String> _expectedInvolvedColumns;

        private InvolvedColumnsTest(String sql, List<String> expectedInvolvedColumns)
        {
            super(sql);
            _expectedInvolvedColumns = expectedInvolvedColumns;
        }

        @Override
        public void validate(QueryTestCase test, @Nullable Container container)
        {
            if (null == container)
                container = JunitUtil.getTestContainer();

            try
            {
                test.validateInvolvedColumns(_sql, container == JunitUtil.getTestContainer() ? null : container, _expectedInvolvedColumns);
            }
            catch (Exception x)
            {
                QueryTestCase.fail(x.toString() + "\n" + _sql);
            }
        }
    }

    private static final int Rcolumns = TestDataLoader.COLUMNS.length + 9; // rowid, entityid, created, createdby, modified, modifiedby, lastindexed, container, diimporthash
	private static final int Rsize = 84;
	private static final int Ssize = 84;

    static SqlTest[] tests = new SqlTest[]
    {
        new SqlTest("SELECT R.seven FROM R UNION SELECT S.seven FROM Folder.qtest.lists.S S", 1, 7),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT S.seven FROM Folder.qtest.lists.S S", 1, Rsize*2),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S", 2, 14),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R", 2, 26),
        new SqlTest("(SELECT 'R' as x, R.seven FROM R) UNION (SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R)", 2, 26),
        new SqlTest("(SELECT x, y FROM (SELECT 'S' as x, S.seven as y FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve as y FROM R) UNION (SELECT 'R' as x, R.seven as y FROM R))", 2, 26),

        // mixed UNION, UNION ALL
        new SqlTest("SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("(SELECT R.seven FROM R UNION SELECT R.seven FROM R) UNION ALL SELECT R.twelve FROM R", 1, 7 + Rsize),
        new SqlTest("(SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R) UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R UNION ALL SELECT R.twelve FROM R", 1, 3*Rsize),
        new SqlTest("SELECT u.seven FROM (SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R) u WHERE u.seven > 5", 1, 6),

        new SqlTest("SELECT d, seven, twelve, (twelve/2) AS half FROM R", 4, Rsize), // Calculated column
        new SqlTest("SELECT d, seven, twelve, day, month, date, duration, guid FROM R", 8, Rsize),
        new SqlTest("SELECT d, seven, twelve, day, month, date, duration, guid FROM lists.R", 8, Rsize),
        new SqlTest("SELECT d, seven, twelve, day, month, date, duration, guid FROM Folder.qtest.lists.S", 8, Rsize),
        new SqlTest("SELECT Folder.qtest.lists.S.d, seven, Folder.qtest.lists.S.twelve, day, Folder.qtest.lists.S.month, date, duration, guid FROM Folder.qtest.lists.S", 8, Rsize),  // Folder+schema-qualified column names
        new SqlTest("SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid FROM R", 8, Rsize),
        new SqlTest("SELECT R.* FROM R", Rcolumns, Rsize),
        new SqlTest("SELECT * FROM R", Rcolumns, Rsize),
        new SqlTest("SELECT R.d, seven, R.twelve AS TWE, R.day DOM, LCASE(GUID) FROM lists.R", 5, Rsize),
        new SqlTest("SELECT lists.R.d, seven, lists.R.twelve AS TWE, R.day DOM, LCASE(GUID) FROM lists.R", 5, Rsize),  // Schema-qualified column names
        new SqlTest("SELECT true as T, false as F FROM R", 2, Rsize),
        new SqlTest("SELECT COUNT(*) AS _count FROM R", 1, 1),
        new SqlTest("SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid, R.created, R.createdby, R.createdby.displayname FROM R", 11, Rsize),
        new SqlTest("SELECT R.duration AS elapsed FROM R WHERE R.rowid=1", 1, 1),
		new SqlTest("SELECT R.rowid, R.seven, R.day FROM R WHERE R.day LIKE '%ues%'", 3, 12),
		new SqlTest("SELECT R.rowid, R.twelve, R.month FROM R WHERE R.month BETWEEN 'L' and 'O'", 3, 3*7), // March, May, Nov
        new SqlTest("SELECT R.rowid, R.twelve, (SELECT S.month FROM Folder.qtest.lists.S S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday'", 3, 12),
        new SqlTest("SELECT T.R, T.T, T.M FROM (SELECT R.rowid as R, R.twelve as T, (SELECT S.month FROM Folder.qtest.lists.S S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday') T", 3, 12),
		new SqlTest("SELECT R.rowid, R.twelve FROM R WHERE R.seven in (SELECT S.seven FROM Folder.qtest.lists.S S WHERE S.seven in (1, 4))", 2, Rsize*2/7),

		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S inner join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R AS S left join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S left outer join R AS T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S right join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S right outer join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S full join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S full outer join R T on S.rowid=T.rowid"),
        new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S cross join R T WHERE S.rowid=T.rowid"),
        new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S, R T WHERE S.rowid=T.rowid"),
        new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S LEFT JOIN (R T INNER JOIN R AS U ON T.rowid=U.rowid) ON S.rowid=t.rowid"),
        new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S, R AS T INNER JOIN R U ON T.rowid=U.rowid WHERE S.rowid=T.rowid"),

        new SqlTest("SELECT R.seven FROM R UNION SELECT S.seven FROM Folder.qtest.lists.S S", 1, 7),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT S.seven FROM Folder.qtest.lists.S S", 1, Rsize*2),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S", 2, 14),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R", 2, 26),
		new SqlTest("(SELECT 'R' as x, R.seven FROM R) UNION (SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R)", 2, 26),
		// mixed UNION, UNION ALL
        new SqlTest("SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("(SELECT R.seven FROM R UNION SELECT R.seven FROM R) UNION ALL SELECT R.twelve FROM R", 1, 7 + Rsize),
        new SqlTest("(SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R) UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R UNION ALL SELECT R.twelve FROM R", 1, 3*Rsize),
        new SqlTest("SELECT u.seven FROM (SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R) u WHERE u.seven > 5", 1, 6),
		// LIMIT
		new SqlTest("SELECT R.day, R.month, R.date FROM R LIMIT 5", 3, 5),
		new SqlTest("SELECT R.day, R.month, R.date FROM R ORDER BY R.date LIMIT 5", 3, 5),

        // quoted identifiers
        new SqlTest("SELECT T.\"count\", T.\"Opened By\", T.Seven, T.MonthName FROM (SELECT R.d as \"count\", R.seven as \"Seven\", R.twelve, R.day, R.month, R.date, R.duration, R.guid, R.created, R.createdby as \"Opened By\", R.month as MonthName FROM R) T", 4, Rsize),

        // PIVOT
        new SqlTest("SELECT seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven", 9, 12),
        new SqlTest("SELECT seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven IN (0 AS ZERO, 1 ONE, 2 AS TWO, 3 THREE, 4 FOUR, 5 FIVE, 6 SIX, NULL AS UNKNOWN)", 10, 12),
        new SqlTest("SELECT seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven IN (0, 1, 2, 3, 4, 5, 6) ORDER BY twelve LIMIT 4", 9, 4),
        new SqlTest("SELECT seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven IN (0, 1, 2, 3, 4, 5, 6) ORDER BY \"0::C\" LIMIT 12", 9, 12),
        new SqlTest("SELECT seven, month, count(*) C\n" +
                "FROM R\n" +
                "GROUP BY seven, month\n" +
                "PIVOT C BY month IN('January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December')"),
        // Regression tests for Issue 27910: pivot query summary columns not aggregated correctly
        new SqlTest("SELECT day, month, count(*) as total, " +
                "SUM(CASE WHEN month = 'April' THEN 1 ELSE 0 END) AS A, " +
                "SUM(CASE WHEN month = 'May' THEN 1 ELSE 0 END) AS M " +
                "FROM lists.R GROUP BY month, day " +
                "PIVOT A, M BY month IN ('January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December')", 28, 7),
        // Verify pivot on sub-select
        new SqlTest("SELECT A, B, count(*) As C " +
            "FROM (SELECT seven as A, twelve/1 AS B FROM lists.R) " +
            "GROUP BY A, B " +
            "PIVOT C BY B", 14, 7),

        // saved queries
        new SqlTest("Rquery",
                "SELECT R.rowid, R.rowid*2 as rowid2, R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid FROM R",
                "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
                "<table tableName=\"Rquery\" tableDbType=\"NOT_IN_DB\">\n" +
                "<columns>\n" +
                " <column columnName=\"rowid\">\n" +
                "  <fk>\n" +
                "  <fkTable>Squery</fkTable>\n" +
                "  <fkColumnName>rowid</fkColumnName>\n" +
                "  </fk>\n" +
                " </column>\n" +
                " <column columnName=\"rowid2\">\n" +
                "  <fk>\n" +
                "  <fkTable>Squery</fkTable>\n" +
                "  <fkColumnName>rowid</fkColumnName>\n" +
                "  </fk>\n" +
                " </column>\n" +
                "</columns>\n" +
                "</table>\n" +
                "</tables>",
                10, Rsize),
        new SqlTest("SELECT Rquery.d FROM Rquery", 1, Rsize),

        new SqlTest("Squery",
                "SELECT S.rowid, S.d, S.seven, S.twelve, S.day, S.month, S.date, S.duration, S.guid FROM Folder.qtest.lists.S S",
                null,
                9, Rsize),
        new SqlTest("SELECT S.rowid, S.d FROM Squery S", 2, Rsize),

        new SqlTest("SELECT Rquery.rowid, Rquery.rowid.duration FROM Rquery", 2, Rsize),
        new SqlTest("SELECT Rquery.rowid2, Rquery.rowid2.duration FROM Rquery", 2, Rsize),
        new SqlTest("SELECT Rquery.rowid, Rquery.rowid.date, Rquery.rowid2, Rquery.rowid2.duration FROM Rquery", 4, Rsize),

        // NOTE: DISTINCT means lookups can not be pushed down
        new SqlTest("Rdistinct", "SELECT DISTINCT R.twelve FROM R",
                "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
                "<table tableName=\"Rdistinct\" tableDbType=\"NOT_IN_DB\">\n" +
                "<columns>\n" +
                " <column columnName=\"twelve\">\n" +
                "  <fk>\n" +
                "  <fkTable>Squery</fkTable>\n" +
                "  <fkColumnName>rowid</fkColumnName>\n" +
                "  </fk>\n" +
                " </column>\n" +
                "</columns>\n" +
                "</table>\n" +
                "</tables>",
                1, 12),
        new SqlTest("SELECT Rdistinct.twelve, Rdistinct.twelve.duration from Rdistinct", 2, 12),

        // Test DATE vs. TIMESTAMP and display formats
        new SqlTest("DateFormatTest",
                "SELECT R.date, CAST(R.date AS DATE) AS D1, CAST(R.date AS TIMESTAMP) AS D2, CAST(R.date AS TIMESTAMP) AS T1, CAST(R.date AS DATE) AS T2 FROM R",
                "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
                "  <table tableName=\"DateFormatTest\" tableDbType=\"NOT_IN_DB\">\n" +
                "    <columns>\n" +
                "      <column columnName=\"D2\">\n" +
                "        <formatString>Date</formatString>\n" +
                "      </column>\n" +
                "      <column columnName=\"T2\">\n" +
                "        <formatString>DateTime</formatString>\n" +
                "      </column>\n" +
                "    </columns>\n" +
                "  </table>\n" +
                "</tables>",
                5, Rsize) {
            @Override
            void validate(QueryTestCase test, @Nullable Container container)
            {
                super.validate(test, container);

                QueryDefinition queryDef = QueryService.get().getQueryDef(TestContext.get().getUser(), JunitUtil.getTestContainer(), "lists", "DateFormatTest");
                List<QueryException> errors = new LinkedList<>();
                TableInfo table = queryDef.getTable(errors, true);

                QueryTestCase.assertNotNull(table);

                if (!errors.isEmpty())
                    throw new RuntimeException(errors.get(0));

                try (Results results = new TableSelector(table).getResults())
                {
                    verifyType(results.getColumn(1), JdbcType.TIMESTAMP, null);
                    verifyType(results.getColumn(2), JdbcType.DATE, null);
                    verifyType(results.getColumn(3), JdbcType.TIMESTAMP, "Date");
                    verifyType(results.getColumn(4), JdbcType.TIMESTAMP, null);
                    verifyType(results.getColumn(5), JdbcType.DATE, "DateTime");
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }

            private void verifyType(ColumnInfo column, JdbcType expectedType, @Nullable String expectedFormat)
            {
                QueryTestCase.assertEquals("Type discrepancy for " + column.getName(), column.getJdbcType(), expectedType);
                QueryTestCase.assertEquals("Format discrepancy for " + column.getName(), column.getFormat(), expectedFormat);
            }
        },

        // GROUPING
        new SqlTest("SELECT R.seven, MAX(R.twelve) AS _max FROM R GROUP BY R.seven", 2, 7),
        new SqlTest("SELECT COUNT(R.rowid) as _count FROM R", 1, 1),
        new SqlTest("SELECT seven, GROUP_CONCAT(twelve) as twelve FROM R GROUP BY seven", 2, 7),
        new SqlTest("SELECT R.seven, MAX(R.twelve) AS _max FROM R GROUP BY R.seven HAVING SUM(R.twelve) > 5", 2, 7),

        // Naked HAVING is allowed
        new SqlTest("SELECT MIN(R.seven), MAX(R.twelve) AS _max FROM R HAVING SUM(R.twelve) > 5", 2, 1),

        // METHODS
        new SqlTest("SELECT ROUND(R.d) AS _d, ROUND(R.d, 1) AS _rnd, ROUND(3.1415, 2) AS _pi, CONVERT(R.d, SQL_VARCHAR) AS _str FROM R", 4, Rsize),
        new MethodSqlTest("SELECT ABS(-1) FROM R WHERE rowid=1", JdbcType.INTEGER, 1),
            // TODO: acos
            // TODO: asin
            // TODO: atan
            // TODO: atan2
        new MethodSqlTest("SELECT CAST(AGE(CAST('02 Jan 2003' AS TIMESTAMP), CAST('02 Jan 2004' AS TIMESTAMP)) AS INTEGER)", JdbcType.INTEGER, 1),
        new MethodSqlTest("SELECT CAST(AGE(CAST('02 Jan 2004' AS TIMESTAMP), CAST('02 Jan 2003' AS TIMESTAMP)) AS INTEGER)", JdbcType.INTEGER, -1),
        new MethodSqlTest("SELECT CAST(AGE(CAST('02 Jan 2003' AS TIMESTAMP), CAST('03 Jan 2004' AS TIMESTAMP), SQL_TSI_YEAR) AS INTEGER)", JdbcType.INTEGER, 1),
        new MethodSqlTest("SELECT CAST(AGE(CAST('02 Jan 2003' AS TIMESTAMP), CAST('01 Feb 2004' AS TIMESTAMP), SQL_TSI_MONTH) AS INTEGER)", JdbcType.INTEGER, 12),
        new MethodSqlTest("SELECT CAST(AGE(CAST('02 Jan 2003' AS TIMESTAMP), CAST('02 Feb 2004' AS TIMESTAMP), SQL_TSI_MONTH) AS INTEGER)", JdbcType.INTEGER, 13),
        new MethodSqlTest("SELECT CAST('1' AS SQL_INTEGER) ", JdbcType.INTEGER, 1),
        new MethodSqlTest("SELECT CAST('1' AS INTEGER) ", JdbcType.INTEGER, 1),
        new MethodSqlTest("SELECT CAST('1.5' AS DOUBLE) ", JdbcType.DOUBLE, 1.5),
        new MethodSqlTest("SELECT CAST(1 AS VARCHAR) ", JdbcType.VARCHAR, '1'),
        new MethodSqlTest("SELECT CEILING(1.5) FROM R WHERE rowid=1", JdbcType.DECIMAL, 2),
        new MethodSqlTest("SELECT COALESCE(NULL, 'empty') FROM R WHERE rowid=1", JdbcType.VARCHAR, "empty"),
        new MethodSqlTest("SELECT concat('concat', concat('in', concat('the', 'hat'))) FROM R WHERE rowid=1", JdbcType.VARCHAR, "concatinthehat"),
        new MethodSqlTest("SELECT contextPath()", JdbcType.VARCHAR, () -> new ActionURL().getContextPath()),
        new MethodSqlTest("SELECT CONVERT(123, VARCHAR) FROM R WHERE rowid=1", JdbcType.VARCHAR, "123"),
            // TODO: cos
            // TODO: cot
            // TODO: curdate
        new MethodSqlTest("SELECT DAYOFMONTH(CAST('2/2/2001' AS TIMESTAMP)) FROM R WHERE rowid=1", JdbcType.INTEGER, 2),
        new MethodSqlTest("SELECT DAYOFWEEK(CAST('2/2/2001' AS TIMESTAMP)) FROM R WHERE rowid=1", JdbcType.INTEGER, 6),
        new MethodSqlTest("SELECT DAYOFYEAR(CAST('2/2/2001' AS TIMESTAMP)) FROM R WHERE rowid=1", JdbcType.INTEGER, 33),
            // TODO: degrees
            // TODO: exp
            // TODO: floor
        new MethodSqlTest("SELECT folderName()", JdbcType.VARCHAR, () -> JunitUtil.getTestContainer().getName()),
        new MethodSqlTest("SELECT folderPath()", JdbcType.VARCHAR, () -> JunitUtil.getTestContainer().getPath()),
        new MethodSqlTest("SELECT GREATEST(0, 2, 1)", JdbcType.INTEGER, 2),
            // TODO: hour
        new MethodSqlTest("SELECT IFNULL(NULL, 'empty') FROM R WHERE rowid=1", JdbcType.VARCHAR, "empty"),
        new MethodSqlTest("SELECT ISEQUAL(NULL, NULL) FROM R WHERE rowid=1", JdbcType.BOOLEAN, true),
        new MethodSqlTest("SELECT ISEQUAL(1, 1) FROM R WHERE rowid=1", JdbcType.BOOLEAN, true),
        new MethodSqlTest("SELECT ISEQUAL(1, 2) FROM R WHERE rowid=1", JdbcType.BOOLEAN, false),
            // javaConstant() always return VARCHAR currently, would like to fix
        new MethodSqlTest("SELECT javaConstant('java.lang.Integer.MAX_VALUE')", JdbcType.VARCHAR, String.valueOf(Integer.MAX_VALUE)),
        new MethodSqlTest("SELECT ISMEMBEROF(-1) FROM R WHERE rowid=1", JdbcType.BOOLEAN, true),   // admin is required for junit test
        new MethodSqlTest("SELECT LEAST(0, 2, 1)", JdbcType.INTEGER, 0),
        new MethodSqlTest("SELECT LCASE('FRED') FROM R WHERE rowid=1", JdbcType.VARCHAR, "fred"),
        new MethodSqlTest("SELECT LEFT('FRED', 2) FROM R WHERE rowid=1", JdbcType.VARCHAR, "FR"),
        new MethodSqlTest("SELECT lower('FRED') FROM R WHERE rowid=1", JdbcType.VARCHAR, "fred"),
            // TODO: ltrim
            // TODO: minute
            // TODO: mod
            // TODO: month
            // TODO: monthname
            // TODO: now
        new MethodSqlTest("SELECT NULLIF(1,1)", JdbcType.INTEGER, null),
        new MethodSqlTest("SELECT NULLIF('1','2')", JdbcType.VARCHAR, "1"),
        new MethodSqlTest("SELECT ROUND(PI()) FROM R WHERE rowid=1", JdbcType.DOUBLE, 3.0),
            // TODO: power
            // TODO: quarter
            // TODO: radians
            // TODO: rand
            // TODO: repeat
            // TODO: round
        new MethodSqlTest("SELECT RTRIM('FRED ')", JdbcType.VARCHAR, "FRED"),
            // TODO: second
            // TODO: sign
            // TODO: sin
            // TODO: sqrt
        new MethodSqlTest("SELECT STARTSWITH('FRED ', 'FR')", JdbcType.BOOLEAN, true),
        new MethodSqlTest("SELECT STARTSWITH('FRED ', 'Z')", JdbcType.BOOLEAN, false),
        new MethodSqlTest("SELECT SUBSTRING('FRED ', 2, 3)", JdbcType.VARCHAR, "RED"),
        new MethodSqlTest("SELECT SUBSTRING('FRED ', 2, 2)", JdbcType.VARCHAR, "RE"),
        new MethodSqlTest("SELECT SUBSTRING('FRED',3)", JdbcType.VARCHAR, "ED"),
            // TODO: tan
        new MethodSqlTest("SELECT TIMESTAMPADD(SQL_TSI_SECOND, 3, CAST('01 Jan 2003' AS TIMESTAMP))", JdbcType.TIMESTAMP, new Timestamp(DateUtil.parseISODateTime("2003-01-01 00:00:03"))),
        new MethodSqlTest("SELECT TIMESTAMPADD(SQL_TSI_MINUTE, 3, CAST('01 Jan 2003' AS TIMESTAMP))", JdbcType.TIMESTAMP, new Timestamp(DateUtil.parseISODateTime("2003-01-01 00:03"))),
        new MethodSqlTest("SELECT TIMESTAMPADD(SQL_TSI_HOUR, 3, CAST('01 Jan 2003' AS TIMESTAMP))", JdbcType.TIMESTAMP, new Timestamp(DateUtil.parseISODateTime("2003-01-01 03:00"))),
        new MethodSqlTest("SELECT TIMESTAMPADD(SQL_TSI_DAY, 3, CAST('01 Jan 2003' AS TIMESTAMP))", JdbcType.TIMESTAMP, new Timestamp(DateUtil.parseISODateTime("2003-01-04"))),
        new MethodSqlTest("SELECT TIMESTAMPADD(SQL_TSI_WEEK, 3, CAST('01 Jan 2003' AS TIMESTAMP))", JdbcType.TIMESTAMP, new Timestamp(DateUtil.parseISODateTime("2003-01-22"))),
        new MethodSqlTest("SELECT TIMESTAMPADD(SQL_TSI_MONTH, 3, CAST('01 Jan 2003' AS TIMESTAMP))", JdbcType.TIMESTAMP, new Timestamp(DateUtil.parseISODateTime("2003-04-01"))),
        new MethodSqlTest("SELECT TIMESTAMPADD(SQL_TSI_QUARTER, 3, CAST('01 Jan 2003' AS TIMESTAMP))", JdbcType.TIMESTAMP, new Timestamp(DateUtil.parseISODateTime("2003-10-01"))),
        new MethodSqlTest("SELECT TIMESTAMPADD(SQL_TSI_YEAR, 3, CAST('01 Jan 2003' AS TIMESTAMP))", JdbcType.TIMESTAMP, new Timestamp(DateUtil.parseISODateTime("2006-01-01"))),

        new MethodSqlTest("SELECT CAST(TIMESTAMPDIFF(SQL_TSI_SECOND, CAST('01 Jan 2004 5:00' AS TIMESTAMP), CAST('01 Jan 2004 6:00' AS TIMESTAMP)) AS INTEGER)", JdbcType.INTEGER, 3600),
        new MethodSqlTest("SELECT CAST(TIMESTAMPDIFF(SQL_TSI_MINUTE, CAST('01 Jan 2003' AS TIMESTAMP), CAST('01 Jan 2004' AS TIMESTAMP)) AS INTEGER)", JdbcType.INTEGER, 525600),
        new MethodSqlTest("SELECT CAST(TIMESTAMPDIFF(SQL_TSI_MINUTE, CAST('01 Jan 2004' AS TIMESTAMP), CAST('01 Jan 2005' AS TIMESTAMP)) AS INTEGER)", JdbcType.INTEGER, 527040), // leap year
        new MethodSqlTest("SELECT CAST(TIMESTAMPDIFF(SQL_TSI_HOUR, CAST('01 Jan 2003' AS TIMESTAMP), CAST('01 Jan 2004' AS TIMESTAMP)) AS INTEGER)", JdbcType.INTEGER, 8760),
        new MethodSqlTest("SELECT CAST(TIMESTAMPDIFF(SQL_TSI_HOUR, CAST('01 Jan 2004' AS TIMESTAMP), CAST('01 Jan 2005' AS TIMESTAMP)) AS INTEGER)", JdbcType.INTEGER, 8784), // leap year
        new MethodSqlTest("SELECT CAST(TIMESTAMPDIFF(SQL_TSI_DAY, CAST('01 Jan 2003' AS TIMESTAMP), CAST('31 Jan 2004' AS TIMESTAMP)) AS INTEGER)", JdbcType.INTEGER, 395),
        new MethodSqlTest("SELECT CAST(TIMESTAMPDIFF(SQL_TSI_DAY, CAST('31 Jan 2004' AS TIMESTAMP), CAST('01 Jan 2003' AS TIMESTAMP)) AS INTEGER)", JdbcType.INTEGER, -395),
        // NOTE: SQL_TSI_WEEK, SQL_TSI_MONTH, SQL_TSI_QUARTER, and SQL_TSI_YEAR are NYI in PostsgreSQL TIMESTAMPDIFF

        new MethodSqlTest("SELECT UCASE('Fred')", JdbcType.VARCHAR, "FRED"),
        new MethodSqlTest("SELECT UPPER('fred')", JdbcType.VARCHAR, "FRED"),
        new MethodSqlTest("SELECT USERID()", JdbcType.INTEGER, () -> TestContext.get().getUser().getUserId()),
        new MethodSqlTest("SELECT username()", JdbcType.INTEGER, () -> TestContext.get().getUser().getDisplayName(TestContext.get().getUser())),
            // TODO: week
            // TODO: year

        new SqlTest("SELECT stddev(d), day FROM R GROUP BY day", 2, 7),
        new SqlTest("SELECT stddev_pop(d), day FROM R GROUP BY day", 2, 7),
        new SqlTest("SELECT variance(d), day FROM R GROUP BY day", 2, 7),
        new SqlTest("SELECT var_pop(d), day FROM R GROUP BY day", 2, 7),
        new SqlTest("SELECT median(seven), day FROM R GROUP BY day", 2, 7),

        // Median on SQL server is tricky, so some  more tests...
        new SqlTest("SELECT avg(seven), median(seven), day FROM R GROUP BY day", 3, 7),   // with mixed aggregates
        new SqlTest("SELECT 1+median(seven), day FROM R GROUP BY day", 2, 7),             // not top-level expression
        new SqlTest("SELECT 1+median(seven), avg(seven), day FROM R GROUP BY day", 3, 7),             // not top-level expression
        new SqlTest("SELECT 1+median(seven)+avg(seven), avg(seven), day FROM R GROUP BY day", 3, 7),             // not top-level expression
        new SqlTest("SELECT median(d), median(seven), day FROM R GROUP BY day", 3, 7),
        new SqlTest("SELECT median(d), median(seven), day, length(day) FROM R GROUP BY day", 4, 7),
        new SqlTest("SELECT CASE day WHEN 'Monday' THEN median(d) ELSE median(seven) END, day, length(day) FROM R GROUP BY day", 3, 7),

        // LIMIT
        new SqlTest("SELECT R.day, R.month, R.date FROM R LIMIT 10", 3, 10),
        new SqlTest("SELECT R.day, R.month, R.date FROM R ORDER BY R.date LIMIT 10", 3, 10),
        new SqlTest("SELECT R.day, R.month, R.date FROM R UNION SELECT R.day, R.month, R.date FROM R LIMIT 5", 3, 5),
        new SqlTest("SELECT R.day, R.month, R.date FROM R UNION SELECT R.day, R.month, R.date FROM R ORDER BY date LIMIT 5", 3, 5),

        // misc regression related
            //17852
        new SqlTest("SELECT parent.name FROM (SELECT Parent FROM core.containers) AS X", 1, -1),
        new SqlTest("SELECT parent.name FROM (SELECT Parent FROM core.containers) AS X", 1, -1),
        new SqlTest("SELECT X.parent.name FROM (SELECT Parent FROM core.containers) AS X", 1, -1),
        new SqlTest("PARAMETERS(Y INTEGER DEFAULT 5) SELECT X.parent.name FROM (SELECT Parent FROM core.containers) AS X", 1, -1),

        // Issue 18257: postgres error executing query selecting empty string value
        new SqlTest("SELECT '' AS EmptyString"),

        // WITH
        new SqlTest("WITH peeps AS (SELECT * FROM R) SELECT * FROM peeps", -1, 84),
        new SqlTest("WITH peeps1 AS (SELECT * FROM R), peeps AS (SELECT * FROM peeps1 UNION ALL SELECT * FROM peeps WHERE (1=0)) SELECT * FROM peeps", -1, 84),
        new SqlTest("WITH peeps1 AS (SELECT * FROM R), peeps AS (SELECT * FROM peeps1 UNION ALL SELECT * FROM peeps WHERE (1=0)), peeps2 AS (SELECT * FROM peeps) SELECT * FROM peeps2", -1, 84),
        new SqlTest("WITH peeps1 AS (SELECT * FROM R), peeps AS (SELECT * FROM peeps1 UNION ALL SELECT * FROM peeps WHERE (1=0)) SELECT p.* FROM R JOIN peeps p ON p.rowId = R.rowId", -1, 84),
        new SqlTest("WITH peeps1 AS (SELECT * FROM R), peeps AS (SELECT * FROM peeps1 UNION ALL SELECT * FROM (SELECT * FROM peeps) q WHERE (1=0)) SELECT p.* FROM R JOIN peeps p ON p.rowId = R.rowId", -1, 84),
        new SqlTest("WITH \"P 1\" AS (SELECT * FROM R), \"P 2\" AS (SELECT seven, twelve, day, month, date, duration, guid FROM \"P 1\") SELECT * FROM \"P 2\"", 7, 84),
        new SqlTest("WITH \"P 1\" AS (SELECT * FROM Folder.qtest.lists.S), \"P 2\" AS (SELECT seven, twelve, day, month, date, duration, guid FROM \"P 1\") SELECT * FROM \"P 2\"", 7, 84),
        new SqlTest("WITH peeps1 AS (SELECT * FROM Folder.qtest.lists.S)," +
                "peeps AS (\n" +
                "   SELECT * FROM peeps1\n" +
                "   UNION ALL\n" +
                "   SELECT * FROM peeps WHERE (1=0)\n" +
                ")\n" +
                "SELECT date, month, MAX(seven) AS MaxDay \n" +
                "  FROM peeps\n" +
                "  GROUP BY date, month \n" +
                "  PIVOT MaxDay BY month", -1, 84),
        new SqlTest("PARAMETERS(Z INTEGER DEFAULT 2, A INTEGER DEFAULT 2, B INTEGER DEFAULT 2) WITH peeps AS (SELECT * FROM R WHERE (Z=2)) SELECT * FROM peeps WHERE (A=B)", -1, 84),
        new SqlTest("WITH folderTree AS (SELECT \n" +
                "                              cast('' as varchar) as ParentName, \n" +
                "                              cast('root' as varchar) as name, \n" +
                "                              c.entityId, \n" +
                "                              c.path, \n" +
                "                              0 as level \n" +
                "                            FROM core.Containers c \n" +
                "                            --where name is null \n" +
                "                            UNION ALL \n" +
                "                            SELECT \n" +
                "                              cast(p.Name as varchar) as ParentName, \n" +
                "                              cast(c.Name as varchar) as name, \n" +
                "                              c.entityId, \n" +
                "                              c.path, \n" +
                "                              level + 1 \n" +
                "                            FROM core.Containers c \n" +
                "                              INNER JOIN folderTree p ON c.parent = p.entityId \n" +
                "  ) \n" +
                "  SELECT * \n" +
                "  FROM folderTree", -1, -1),
        new SqlTest("WITH UserCTE AS (SELECT 1001 as UserId) \n" +
                "SELECT U1.UserId Expr1, U2.UserId Expr2\n" +
                "FROM UserCTE AS U1, UserCTE AS U2 \n" +
                "WHERE U1.UserId = U2.UserId", 2, 1),

        // 40830, this query caused a problem because it has no simple field references (only an expression) and so
        // getSuggestedColumns() is not called on the inner SELECT and therefore resolveFields() was not called before getKeyColumns()
        new SqlTest("SELECT Name || '-' || Label FROM (SELECT Name, Label FROM core.Modules) M"),

        // Allowed, but wrong syntax, trailing comma in select list and terminal semicolon
        new SqlTest("SELECT 1 AS ONE", 1, 1),
        new SqlTest("SELECT 1 AS ONE,", 1, 1),
        new SqlTest("SELECT 1 AS ONE;", 1, 1),
        new SqlTest("SELECT 1 AS ONE,;", 1, 1),

        // We allow duplicate column names, #42081
        new SqlTest("SELECT * FROM R r1 INNER JOIN R r2 ON r1.RowId = r2.RowId"),
        new SqlTest("SELECT r1.*, r2.* FROM R r1 INNER JOIN R r2 ON r1.RowId = r2.RowId"),
        new SqlTest("SELECT r1.guid, r1.month, r1.d, r1.seven, r1.date, r2.guid, r2.month, r2.d, r2.seven, r2.date  FROM R r1 INNER JOIN R r2 ON r1.RowId = r2.RowId"),
        new SqlTest("SELECT d, seven, d, seven FROM R"),
        new SqlTest("SELECT * FROM R A inner join R B ON 1=1"),
        new SqlTest("SELECT A.*, B.* FROM R A inner join R B on 1=1"),

        // VALUES tests
        new SqlTest("SELECT column1, column2 FROM (VALUES (CAST('1' as VARCHAR), CAST('1' as INTEGER)), ('two', 2)) as x", 2, 2),
        new SqlTest("SELECT column1, column2 FROM (VALUES (CAST('1' as VARCHAR), CAST('1' as INTEGER)), ('two', 2)) as x WHERE x.column1 = 'two'", 2, 1),
        new SqlTest("WITH v AS (SELECT column1, column2 FROM (VALUES (CAST('1' as VARCHAR), CAST('1' as INTEGER)), ('two', 2)) as v_) SELECT * FROM v", 2, 2),
        new SqlTest("WITH v AS (SELECT column1, column2 FROM (VALUES (CAST('1' as VARCHAR), CAST('1' as INTEGER)), ('two', 2)) as v_) SELECT column1 as txt, column2 as i FROM v WHERE column1 = 'two'", 2, 1),

        // regression test: field reference in sub-select (https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=43580)
        new SqlTest("SELECT (SELECT a.title), a.parent.rowid FROM core.containers a", 2, 1)
    };


	static SqlTest[] postgres = new SqlTest[]
	{
		// ORDER BY tests
		new SqlTest("SELECT R.day, R.month, R.date FROM R ORDER BY R.date", 3, Rsize),
        new SqlTest("SELECT R.day, R.month, R.date FROM R UNION SELECT R.day, R.month, R.date FROM R ORDER BY date"),
        new SqlTest("SELECT R.guid FROM R WHERE overlaps(CAST('2001-01-01' AS DATE), CAST('2001-01-10' AS DATE), CAST('2001-01-05' AS DATE), CAST('2001-01-15' AS DATE))", 1, Rsize),

        // regression test: field reference in sub-select (https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=43580)
        new SqlTest("SELECT (SELECT GROUP_CONCAT(b.displayname, ', ') FROM core.UsersAndGroups b WHERE b.email IN (SELECT UNNEST(STRING_TO_ARRAY(a.title, ',')))) AS procedurename, a.parent.rowid FROM core.containers a ", 2, 1),

        new MethodSqlTest("SELECT is_distinct_from(NULL,NULL)", JdbcType.BOOLEAN, false),
        new MethodSqlTest("SELECT is_not_distinct_from(NULL,NULL)", JdbcType.BOOLEAN, true),
        new MethodSqlTest("SELECT is_distinct_from(1,NULL)", JdbcType.BOOLEAN, true),
        new MethodSqlTest("SELECT is_not_distinct_from(1,NULL)", JdbcType.BOOLEAN, false),
        new MethodSqlTest("SELECT is_distinct_from(1,1)", JdbcType.BOOLEAN, false),
        new MethodSqlTest("SELECT is_not_distinct_from(1,1)", JdbcType.BOOLEAN, true),
        new MethodSqlTest("SELECT is_distinct_from(1,2)", JdbcType.BOOLEAN, true),
        new MethodSqlTest("SELECT is_not_distinct_from(1,2)", JdbcType.BOOLEAN, false)
    };


    static List<SqlTest> postgresOnlyFunctions()
    {
        int majorVersion = ((PostgreSql91Dialect)CoreSchema.getInstance().getSqlDialect()).getMajorVersion();

        List<SqlTest> result = new ArrayList<>(
            List.of(
                new SqlTest("SELECT stddev_samp(d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT var_samp(d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT bool_and((d < 0)), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT bool_or((d < 0)), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT every((d > 0)), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT bit_or(seven), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT bit_and(seven), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT mode(seven), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT corr(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT corr(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT covar_pop(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT covar_samp(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT regr_avgx(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT regr_avgy(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT regr_count(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT regr_intercept(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT regr_r2(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT regr_slope(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT regr_sxx(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT regr_sxy(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT regr_syy(seven, d), day FROM R GROUP BY day", 2, 7),
                new SqlTest("SELECT 'similar' WHERE similar_to('abc','abc')", 1, 1),
                new SqlTest("SELECT 'similar' WHERE similar_to('abc','a')", 1, 0),
                new SqlTest("SELECT 'similar' WHERE similar_to('abc','%(b|d)%')", 1, 1),
                new SqlTest("SELECT 'similar' WHERE similar_to('abc','(b|c)%')", 1, 0),
                new SqlTest("SELECT 'similar' WHERE similar_to('abc|','abc\\|', '\\')", 1, 1),

                // parse_json, parse_jsonb, and json_op
                new SqlTest("SELECT parse_jsonb('{\"a\":1, \"b\":null}')", 1, 1),
                new SqlTest("SELECT json_op(parse_jsonb('{\"a\":1, \"b\":null}'), '->', 'a')", 1, 1),
                // Postgres 9.6 doesn't support direct JSONB and JSON to INTEGER casting, so use VARCHAR for our simple purposes
                new SqlTest("SELECT f FROM (SELECT CAST(json_op(parse_jsonb('{\"a\":1, \"b\":null}'), '->', 'a') AS VARCHAR) AS f) X WHERE f != '1'", 1, 0),
                new SqlTest("SELECT f FROM (SELECT CAST(json_op(parse_jsonb('{\"a\":1, \"b\":null}'), '->', 'a') AS VARCHAR) AS f) X WHERE f = '1'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(json_op(parse_json('{\"a\":1, \"b\":null}'), '->', 'a') AS VARCHAR) AS f) X WHERE f != '1'", 1, 0),
                new SqlTest("SELECT f FROM (SELECT CAST(json_op(parse_json('{\"a\":1, \"b\":null}'), '->', 'a') AS VARCHAR) AS f) X WHERE f = '1'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(json_op(parse_json('{\"a\":1, \"b\":null}'), '->', 'a') AS VARCHAR) AS f) X WHERE f = '1'", 1, 1),

                // to_json and to_jsonb
                new SqlTest("SELECT f FROM (SELECT CAST(to_json(CAST('{\"a\":1, \"b\":null}' AS VARCHAR)) AS VARCHAR) AS f) X WHERE f = '\"{\\\"a\\\":1, \\\"b\\\":null}\"'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(to_jsonb(CAST('{\"a\":1, \"b\":null}' AS VARCHAR)) AS VARCHAR) AS f) X WHERE f = '\"{\\\"a\\\":1, \\\"b\\\":null}\"'", 1, 1),

                // array_to_json
                new SqlTest("SELECT f FROM (SELECT CAST(array_to_json(string_to_array('xx~^~yy~^~zz', '~^~', 'yy')) AS VARCHAR) AS f) X WHERE f = '[\"xx\",null,\"zz\"]'", 1, 1),

                // row_to_json
                new SqlTest("SELECT f FROM (SELECT CAST(row_to_json(row(1,'foo')) AS VARCHAR) AS f) X WHERE f = '{\"f1\":1,\"f2\":\"foo\"}'", 1, 1),

                // json_build_array and jsonb_build_array
                new SqlTest("SELECT f FROM (SELECT CAST(json_build_array(1, 2, 'foo', 4, 5) AS VARCHAR) AS f) X WHERE f = '[1, 2, \"foo\", 4, 5]'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_build_array(1, 2, 'foo', 4, 5) AS VARCHAR) AS f) X WHERE f = '[1, 2, \"foo\", 4, 5]'", 1, 1),

                // json_build_object and jsonb_build_object
                new SqlTest("SELECT f FROM (SELECT CAST(json_build_object('foo', 1, 2, row(3,'bar')) AS VARCHAR) AS f) X WHERE f = '{\"foo\" : 1, \"2\" : {\"f1\":3,\"f2\":\"bar\"}}'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_build_object('foo', 1, 2, row(3,'bar')) AS VARCHAR) AS f) X WHERE f = '{\"2\": {\"f1\": 3, \"f2\": \"bar\"}, \"foo\": 1}'", 1, 1),

                // json_object and jsonb_object
                new SqlTest("SELECT f FROM (SELECT CAST(json_object('{a, 1, b, \"def\", c, 3.5}') AS VARCHAR) AS f) X WHERE f = '{\"a\" : \"1\", \"b\" : \"def\", \"c\" : \"3.5\"}'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_object('{a, 1, b, \"def\", c, 3.5}') AS VARCHAR) AS f) X WHERE f = '{\"a\": \"1\", \"b\": \"def\", \"c\": \"3.5\"}'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(json_object('{a,b}', '{1,2}') AS VARCHAR) AS f) X WHERE f = '{\"a\" : \"1\", \"b\" : \"2\"}'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_object('{a,b}', '{1,2}') AS VARCHAR) AS f) X WHERE f = '{\"a\": \"1\", \"b\": \"2\"}'", 1, 1),

                // json_array_length and jsonb_array_length
                new SqlTest("SELECT f FROM (SELECT json_array_length(json_build_array(1, 2, 'foo', 4, 5)) AS f) X WHERE f = 5", 1, 1),
                new SqlTest("SELECT f FROM (SELECT jsonb_array_length(jsonb_build_array(1, 2, 'foo', 4, 5)) AS f) X WHERE f = 5", 1, 1),

                // json_each and jsonb_each, json_each_text and jsonb_each_text
                new SqlTest("SELECT json_each(parse_json('{\"a\":\"foo\", \"b\":\"bar\"}'))", 1, 2),
                new SqlTest("SELECT jsonb_each(parse_jsonb('{\"a\":\"foo\", \"b\":\"bar\"}'))", 1, 2),
                new SqlTest("SELECT json_each_text(parse_json('{\"a\":\"foo\", \"b\":\"bar\"}'))", 1, 2),
                new SqlTest("SELECT jsonb_each_text(parse_jsonb('{\"a\":\"foo\", \"b\":\"bar\"}'))", 1, 2),

                // json_extract_path and jsonb_extract_path, json_extract_path_text and jsonb_extract_path_text
                new SqlTest("SELECT f FROM (SELECT CAST(json_extract_path('{\"f2\":{\"f3\":1},\"f4\":{\"f5\":99,\"f6\":\"foo\"}}', 'f4', 'f6') AS VARCHAR) AS f) X WHERE f = '\"foo\"'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_extract_path('{\"f2\":{\"f3\":1},\"f4\":{\"f5\":99,\"f6\":\"foo\"}}', 'f4', 'f6') AS VARCHAR) AS f) X WHERE f = '\"foo\"'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(json_extract_path_text('{\"f2\":{\"f3\":1},\"f4\":{\"f5\":99,\"f6\":\"foo\"}}', 'f4', 'f6') AS VARCHAR) AS f) X WHERE f = 'foo'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_extract_path_text('{\"f2\":{\"f3\":1},\"f4\":{\"f5\":99,\"f6\":\"foo\"}}', 'f4', 'f6') AS VARCHAR) AS f) X WHERE f = 'foo'", 1, 1),

                // json_object_keys and jsonb_object_keys
                new SqlTest("SELECT f FROM (SELECT json_object_keys('{\"f1\":\"abc\",\"f2\":{\"f3\":\"a\", \"f4\":\"b\"}}') AS f) X WHERE f IN ('f1', 'f2')", 1, 2),
                new SqlTest("SELECT f FROM (SELECT jsonb_object_keys('{\"f1\":\"abc\",\"f2\":{\"f3\":\"a\", \"f4\":\"b\"}}') AS f) X WHERE f IN ('f1', 'f2')", 1, 2),

                // json_array_elements and jsonb_array_elements, json_array_elements_text and jsonb_array_elements_text
                new SqlTest("SELECT f FROM (SELECT CAST(json_array_elements('[1,true, [2,false]]') AS VARCHAR) AS f) X WHERE f IN ('1', 'true', '[2,false]')", 1, 3),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_array_elements('[1,true, [2,false]]') AS VARCHAR) AS f) X WHERE f IN ('1', 'true', '[2, false]')", 1, 3),
                new SqlTest("SELECT f FROM (SELECT CAST(json_array_elements_text('[1,true, [2,false]]') AS VARCHAR) AS f) X WHERE f IN ('1', 'true', '[2,false]')", 1, 3),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_array_elements_text('[1,true, [2,false]]') AS VARCHAR) AS f) X WHERE f IN ('1', 'true', '[2, false]')", 1, 3),

                // json_typeof and jsonb_typeof
                new SqlTest("SELECT f FROM (SELECT json_typeof('-123.4') AS f) X WHERE f IN ('number')", 1, 1),
                new SqlTest("SELECT f FROM (SELECT jsonb_typeof('-123.4') AS f) X WHERE f IN ('number')", 1, 1),

                // json_strip_nulls and jsonb_strip_nulls
                new SqlTest("SELECT f FROM (SELECT CAST(json_strip_nulls('[{\"f1\":1, \"f2\":null}, 2, null, 3]') AS VARCHAR) AS f) X WHERE f IN ('[{\"f1\":1},2,null,3]')", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_strip_nulls('[{\"f1\":1, \"f2\":null}, 2, null, 3]') AS VARCHAR) AS f) X WHERE f IN ('[{\"f1\": 1}, 2, null, 3]')", 1, 1),

                // jsonb_pretty
                new SqlTest("SELECT f FROM (SELECT jsonb_pretty('[{\"f1\":1,\"f2\":null}, 2]') AS f) X WHERE f LIKE '%        \"f2\": null%'", 1, 1),

                // jsonb_set
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_set('[{\"f1\":1,\"f2\":null},2,null,3]', '{0,f1}', '[2,3,4]', false) AS VARCHAR) AS f) X WHERE f = '[{\"f1\": [2, 3, 4], \"f2\": null}, 2, null, 3]'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_set('[{\"f1\":1,\"f2\":null},2,null,3]', '{0,f1}', '[2,3,4]') AS VARCHAR) AS f) X WHERE f = '[{\"f1\": [2, 3, 4], \"f2\": null}, 2, null, 3]'", 1, 1),

                // jsonb_insert
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_insert('{\"a\": [0,1,2]}', '{a, 1}', '\"new_value\"') AS VARCHAR) AS f) X WHERE f = '{\"a\": [0, \"new_value\", 1, 2]}'", 1, 1),
                new SqlTest("SELECT f FROM (SELECT CAST(jsonb_insert('{\"a\": [0,1,2]}', '{a, 1}', '\"new_value\"', true) AS VARCHAR) AS f) X WHERE f = '{\"a\": [0, 1, \"new_value\", 2]}'", 1, 1),

                // TEST CTE handling with undocumented test-only methods
                new SqlTest("SELECT __cte_two__() as two, __cte_three__() as three, __cte_two__() * __cte_three__() as six_simple, __cte_times__(__cte_two__(), __cte_three__()) as six_complex", 4, 1)
            ));

        if (majorVersion >= 12)
        {
            result.addAll(Arrays.asList(
                    // jsonb_path_exists
                    new SqlTest("SELECT f FROM (SELECT jsonb_path_exists('{\"a\":[1,2,3,4,5]}', '$.a[*] ? (@ >= $min && @ <= $max)', '{\"min\":2, \"max\":4}') AS f) X WHERE f = true", 1, 1),

                    // jsonb_path_match
                    new SqlTest("SELECT f FROM (SELECT jsonb_path_match('{\"a\":[1,2,3,4,5]}', 'exists($.a[*] ? (@ >= $min && @ <= $max))', '{\"min\":2, \"max\":4}') AS f) X WHERE f = true", 1, 1),

                    // jsonb_path_query
                    new SqlTest("SELECT f FROM (SELECT CAST(jsonb_path_query('{\"a\":[1,2,3,4,5]}', '$.a[*] ? (@ >= $min && @ <= $max)', '{\"min\":2, \"max\":4}') AS VARCHAR) AS f) X WHERE f IN ('2', '3', '4')", 1, 3),

                    // jsonb_path_query_array
                    new SqlTest("SELECT f FROM (SELECT CAST(jsonb_path_query_array('{\"a\":[1,2,3,4,5]}', '$.a[*] ? (@ >= $min && @ <= $max)', '{\"min\":2, \"max\":4}') AS VARCHAR) AS f) X WHERE f = '[2, 3, 4]'", 1, 1),

                    // jsonb_path_query_first
                    new SqlTest("SELECT f FROM (SELECT CAST(jsonb_path_query_first('{\"a\":[1,2,3,4,5]}', '$.a[*] ? (@ >= $min && @ <= $max)', '{\"min\":2, \"max\":4}') AS VARCHAR) AS f) X WHERE f = '2'", 1, 1)
            ));
        }

        if (majorVersion >= 13)
        {
            result.addAll(Arrays.asList(
                    // jsonb_set_lax
                    new SqlTest("SELECT f FROM (SELECT CAST(jsonb_set_lax('[{\"f1\":1,\"f2\":null},2,null,3]', '{0,f1}', null) AS VARCHAR) AS f) X WHERE f = '[{\"f1\": null, \"f2\": null}, 2, null, 3]'", 1, 1),

                    // jsonb_path_exists_tz, jsonb_path_match_tz, jsonb_path_query_tz, jsonb_path_query_array_tz, jsonb_path_query_first_tz
                    new SqlTest("SELECT f FROM (SELECT jsonb_path_exists_tz('[\"2015-08-01 12:00:00 -05\"]', '$[*] ? (@.datetime() < \"2015-08-02\".datetime())') AS f) X WHERE f = false", 1, 1),
                    new SqlTest("SELECT f FROM (SELECT jsonb_path_match_tz('[\"2015-08-01 12:00:00 -05\"]', 'exists($[*] ? (@.datetime() > \"2015-08-02\".datetime()))') AS f) X WHERE f = false", 1, 1),
                    new SqlTest("SELECT f FROM (SELECT CAST(jsonb_path_query_tz('[\"2015-08-01 12:00:00 -05\"]', '$[*] ? (@.datetime() < \"2016-08-02\".datetime())') AS VARCHAR) AS f) X", 1, 0),
                    new SqlTest("SELECT f FROM (SELECT CAST(jsonb_path_query_array_tz('[\"2015-08-01 12:00:00 -05\"]', '$[*] ? (@.datetime() < \"2016-08-02\".datetime())') AS VARCHAR) AS f) X WHERE f = '[]'", 1, 1),
                    new SqlTest("SELECT f FROM (SELECT CAST(jsonb_path_query_first_tz('[\"2015-08-01 12:00:00 -05\"]', '$[*] ? (@.datetime() < \"2016-08-02\".datetime())') AS VARCHAR) AS f) X WHERE f IS NULL", 1, 1)
            ));
        }
        return result;
    }


	static List<SqlTest> negative()
	{
        return List.of(
            new FailTest("SELECT lists.R.d, lists.R.seven FROM R"),  // Schema-qualified column names work only if FROM specifies schema
            new FailTest("SELECT S.d, S.seven FROM S"),
            new FailTest("SELECT S.d, S.seven FROM Folder.S"),
            new FailTest("SELECT S.d, S.seven FROM Folder.qtest.S"),
            new FailTest("SELECT S.d, S.seven FROM Folder.qtest.list.S"),
            new FailTest("SELECT SUM(*) FROM R"),
            new FailTest("SELECT d FROM R A inner join R B on 1=1"),            // ambiguous
            new FailTest("SELECT R.d, seven FROM lists.R A"),                    // R is hidden
            new FailTest("SELECT A.d, B.d FROM lists.R A INNER JOIN lists.R B"),     // ON expected
            new FailTest("SELECT A.d, B.d FROM lists.R A CROSS JOIN lists.R B ON A.d = B.d"),     // ON unexpected
            new FailTest("SELECT A.d FROM lists.R A WHERE A.StartsWith('x')"),     // bad method 17128
            new FailTest("SELECT A.d FROM lists.R A WHERE Z.StartsWith('x')"),     // bad method
            new FailTest("SELECT A.d FROM lists.R A WHERE A.d.StartsWith('x')"),     // bad method
            new FailTest("WITH peeps AS (SELECT * FROM R), peeps AS (SELECT * FROM peeps1 UNION ALL SELECT * FROM peeps WHERE (1=0)) SELECT * FROM peeps"),   // Duplicate CTE names
            new FailTest("WITH peeps AS (SELECT * FROM R), peeps1 AS (SELECT * FROM peeps1 UNION ALL SELECT * FROM peeps WHERE (1=0)) SELECT * FROM peeps"),  // CTE can't reference itself in first clause of UNION
            new FailTest("WITH peeps AS (SELECT * FROM peeps1), peeps1 AS (SELECT * FROM R) SELECT * FROM peeps"),    // Forward reference
            new FailTest("WITH peeps1 AS (SELECT * FROM R), peeps AS (SELECT * FROM peeps1 UNION ALL SELECT * FROM peeps WHERE (1=0) UNION ALL SELECT * FROM peeps WHERE (1=0)) SELECT * FROM peeps"),  // Can't have 2 recursive references
            new FailTest("WITH peeps AS (SELECT * FROM R), peeps2 AS (SELECT seven FROM peeps UNION ALL SELECT date FROM peeps) SELECT * FROM peeps2"),   // Column type mismatch
            new FailTest("WITH peeps2 AS (SELECT seven FROM R UNION SELECT seven FROM S WHERE S.seven IN (SELECT seven FROM peeps2) ) SELECT * FROM peeps2"),

            // UNDONE: should work since R.seven and seven are the same
            new FailTest("SELECT R.seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven IN (0, 1, 2, 3, 4, 5, 6)"),

            new FailTest("SELECT A.Name FROM core.Modules A FULL JOIN core.Modules B ON B.Name=C.Name FULL JOIN core.Modules C ON A.Name=C.Name"), // Missing from-clause entry

            // trailing semicolon in subselect
            new FailTest("SELECT Parent FROM (SELECT Parent FROM core.containers;) AS X"),

            // Regression test for Issue 40618: Generate better error message when PIVOT column list can't be computed due to bad sql
            new FailTest("SELECT A, B, count(*) As C " +
                "FROM (SELECT seven as A, twelve/0 AS B FROM lists.R) " +
                "GROUP BY A, B " +
                "PIVOT C BY B", false),

            // VALUES tests
            new FailTest("SELECT column1, column2 FROM (VALUES (CAST('1' as VARCHAR), CAST('1' as INTEGER)), ('two', 2))"), // require alias
            new FailTest("SELECT column1, column2 FROM (VALUES (a,b),(1,2)) as x") // can't use identifiers
        );
    };

    private static List<InvolvedColumnsTest> involvedColumnsTests()
    {
        return List.of(
            new InvolvedColumnsTest("SELECT R.seven FROM R UNION SELECT S.seven FROM Folder.qtest.lists.S S",
                                    Arrays.asList("R/seven", "S/seven")),
            new InvolvedColumnsTest("SELECT R.seven FROM R UNION ALL SELECT S.seven FROM Folder.qtest.lists.S S",
                                    Arrays.asList("R/seven", "S/seven")),
            new InvolvedColumnsTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S",
                    Arrays.asList("R/seven", "S/seven")),
            new InvolvedColumnsTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R",
                                    Arrays.asList("R/seven", "S/seven", "R/twelve")),
            new InvolvedColumnsTest("(SELECT 'R' as x, R.seven FROM R) UNION (SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R)",
                                    Arrays.asList("R/seven", "S/seven", "R/twelve")),
            new InvolvedColumnsTest("(SELECT x, y FROM (SELECT 'S' as x, S.seven as y FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve as y FROM R) UNION (SELECT 'R' as x, R.seven as y FROM R))",
                                    Arrays.asList("R/seven", "S/seven", "R/twelve")),

            new InvolvedColumnsTest("SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R",
                                    Arrays.asList("R/seven", "R/twelve")),
            new InvolvedColumnsTest("(SELECT R.seven FROM R UNION SELECT R.seven FROM R) UNION ALL SELECT R.twelve FROM R",
                                    Arrays.asList("R/seven", "R/twelve")),
            new InvolvedColumnsTest("(SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R) UNION SELECT R.twelve FROM R",
                                    Arrays.asList("R/seven", "R/twelve")),
            new InvolvedColumnsTest("SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R UNION ALL SELECT R.twelve FROM R",
                                    Arrays.asList("R/seven", "R/twelve")),
            new InvolvedColumnsTest("SELECT u.seven FROM (SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R) u WHERE u.seven > 5",
                                    Arrays.asList("R/seven", "R/twelve")),

            new InvolvedColumnsTest("SELECT R.seven FROM R ORDER BY twelve",
                                    Arrays.asList("R/seven", "R/twelve")),
            new InvolvedColumnsTest("SELECT seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven IN (0, 1, 2, 3, 4, 5, 6) ORDER BY twelve LIMIT 4",
                                    Arrays.asList("R/seven", "R/twelve")),
            new InvolvedColumnsTest("SELECT MAX(R.seven) FROM R GROUP BY twelve",
                                       Arrays.asList("R/seven", "R/twelve")),
            new InvolvedColumnsTest("SELECT MAX(seven) As MaxSeven, twelve FROM R GROUP BY twelve PIVOT MaxSeven BY twelve",
                                       Arrays.asList("R/seven", "R/twelve"))
        );
    };

    @TestWhen(TestWhen.When.BVT)
    public static class QueryTestCase extends Assert
    {
        private final String hash = GUID.makeHash();

        private QuerySchema lists;

        @Before
        public void setUp()
        {
            // if this fails, it probably means a previous test cleared them, which is unexpected
            assertNotNull(QueryService.get().getEnvironment(QueryService.Environment.USER));
            assertNotNull(QueryService.get().getEnvironment(QueryService.Environment.CONTAINER));
            Assume.assumeTrue(getClass().getSimpleName() + " requires list module", ListService.get() != null);
        }


        Container getSubfolder()
		{
            return ContainerManager.ensureContainer(JunitUtil.getTestContainer().getPath() + "/qtest");
		}


        private void addProperties(ListDefinition l)
        {
            Domain d = requireNonNull(l.getDomain());
            for (int i=0 ; i<TestDataLoader.COLUMNS.length ; i++)
            {
                DomainProperty p = d.addProperty();
                p.setPropertyURI(d.getName() + hash + "#" + TestDataLoader.COLUMNS[i]);
                p.setName(TestDataLoader.COLUMNS[i]);
                p.setRangeURI(getPropertyType(TestDataLoader.TYPES[i]).getTypeUri());
                if ("createdby".equals(TestDataLoader.COLUMNS[i]))
                {
                    p.setLookup(new Lookup(l.getContainer(), "core", "SiteUsers"));
                }
            }
        }

        private PropertyType getPropertyType(JdbcType jdbc)
        {
            switch (jdbc)
            {
                case VARCHAR : return PropertyType.STRING;
                case TIMESTAMP : return PropertyType.DATE_TIME;
                case INTEGER : return PropertyType.INTEGER;
                case DOUBLE : return PropertyType.DOUBLE;
                case GUID : return PropertyType.STRING;
                default: fail();
            }
            return PropertyType.STRING;
        }


        protected void _setUp() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
			Container qtest = getSubfolder();
            ListService listService = ListService.get();
            UserSchema lists = (UserSchema)DefaultSchema.get(user, c).getSchema("lists");
            assertNotNull(lists);

            ListDefinition R = listService.createList(c, "R", ListDefinition.KeyType.AutoIncrementInteger);
            R.setKeyName("rowid");
            addProperties(R);
            R.save(user);
            TableInfo rTableInfo = lists.getTable("R", null);
            assertNotNull(rTableInfo);
            DataIteratorContext context = new DataIteratorContext();
            rTableInfo.getUpdateService().importRows(user, c, new TestDataLoader(R.getName() + hash, Rsize), context.getErrors(), null, null);
            if (context.getErrors().hasErrors())
                fail(context.getErrors().getRowErrors().get(0).toString());

            ListDefinition S = listService.createList(qtest, "S", ListDefinition.KeyType.AutoIncrementInteger);
            S.setKeyName("rowid");
            addProperties(S);
            S.save(user);
            TableInfo sTableInfo = DefaultSchema.get(user, qtest).getSchema("lists").getTable("S", null);
            assertNotNull(sTableInfo);
            context = new DataIteratorContext();
            sTableInfo.getUpdateService().importRows(user, qtest, new TestDataLoader(S.getName() + hash, Rsize), context.getErrors(), null, null);
            if (context.getErrors().hasErrors())
                fail(context.getErrors().getRowErrors().get(0).toString());

//            if (0==1)
//            {
//                try
//                {
//                    ListDefinition RHOME = s.createList(ContainerManager.getForPath("/home"), "R");
//                    RHOME.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
//                    RHOME.setKeyName("rowid");
//                    addProperties(RHOME);
//                    RHOME.save(user);
//                    RHOME.insertListItems(user, new TestDataLoader(RHOME.getName() + hash, Rsize), null, null);
//                } catch (Exception x){};
//            }
        }


		@After
        public void tearDown()
        {
//            _tearDown();
        }


        protected void _tearDown() throws Exception
        {
            User user = TestContext.get().getUser();

            for (SqlTest test : tests)
            {
                if (test._name != null)
                {
                    QueryDefinition q = QueryService.get().getQueryDef(user, JunitUtil.getTestContainer(), "lists", test._name);
                    if (null != q)
                        q.delete(user);
                }
            }

			ListService s = ListService.get();

			Container c = JunitUtil.getTestContainer();
			{
				Map<String, ListDefinition> m = s.getLists(c);
				if (m.containsKey("R"))
					m.get("R").delete(user);
				if (m.containsKey("S"))
					m.get("S").delete(user);
			}

			Container qtest = getSubfolder();
			{
				Map<String, ListDefinition> m = s.getLists(qtest);
				if (m.containsKey("S"))
					m.get("S").delete(user);
			}
        }


        @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
        CachedResultSet resultset(String sql, @Nullable Container container)
        {
            QuerySchema schema = lists;
            if (null != container)
                schema = schema.getSchema("Folder").getSchema(container.getPath()).getSchema("lists");
            requireNonNull(schema);

			try
			{
				CachedResultSet rs = (CachedResultSet)QueryService.get().select(schema, sql, null, true, true);
				assertNotNull(sql, rs);
				return rs;
			}
			catch (QueryParseException x)
			{
				fail(x.getMessage() + "\n" + sql);
				return null;
			}
        }


        @Test
        public void testSQL() throws Exception
        {
            // note getPrimarySchema() will return NULL if there are no lists yet
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();

            lists = DefaultSchema.get(user, c).getSchema("lists");
            if (1==1 || null == lists)
            {
                _tearDown();
                _setUp();
                lists = DefaultSchema.get(user, c).getSchema("lists");
            }

            assertNotNull(lists);
            TableInfo Rinfo = lists.getTable("R");
            assertNotNull(Rinfo);
			TableInfo Sinfo = DefaultSchema.get(user, getSubfolder()).getSchema("lists").getTable("S");
            assertNotNull(Sinfo);

            // custom tests
            SqlDialect dialect = lists.getDbSchema().getSqlDialect();
            String sql = "SELECT d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.created, R.createdby FROM R";

            try (CachedResultSet rs = resultset(sql, null))
            {
                ResultSetMetaData md = rs.getMetaData();
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("d", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("seven", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("twelve", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("day", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("month", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("date", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("created", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("createdby", dialect)));
                assertEquals(sql, 9, md.getColumnCount());
                assertEquals(sql, Rsize, rs.getSize());
                rs.next();

                for (int col = 1; col <= md.getColumnCount(); col++)
                    assertNotNull(sql, rs.getObject(col));
            }

            // simple tests
            for (SqlTest test : tests)
            {
                test.validate(this, null);
            }

			if (dialect.isPostgreSQL() /* dialect.allowSortOnSubqueryWithoutLimit() is the preferred check, but SQL Server still has problems with these queries */)
			{
				for (SqlTest test : postgres)
                {
					test.validate(this, null);
                }
			}

			if (dialect.isPostgreSQL())
			{
				for (SqlTest test : postgresOnlyFunctions())
                {
					test.validate(this, null);
                }
			}

			for (SqlTest test : negative())
			{
				test.validate(this, null);
			}

            for (SqlTest test : tests)
            {
                if (test._name != null)
                {
                    QueryDefinition q = QueryService.get().getQueryDef(user, JunitUtil.getTestContainer(), "lists", test._name);
                    assertNotNull(q);
//                    q.delete(user);
                }
            }

            for (InvolvedColumnsTest test : involvedColumnsTests())
            {
                test.validate(this, null);
            }

            testDuplicateColumns(user, c);
        }

        // Duplicate column names are supported. Introduced as an option for #35424; made the default behavior for #42081.
        private void testDuplicateColumns(User user, Container c) throws SQLException
        {
            String sql = "SELECT d, seven, d, seven FROM R";
            QueryDefinition query = QueryService.get().createQueryDef(user, c, SchemaKey.fromParts("lists"), GUID.makeHash());
            query.setSql(sql);
            ArrayList<QueryException> qerrors = new ArrayList<>();
            TableInfo t = query.getTable(query.getSchema(), qerrors, false, true);

            if (null == t)
            {
                Assert.fail("Table not found");
            }
            else if (!qerrors.isEmpty())
            {
                throw qerrors.get(0);
            }
            else
            {
                try (Results rs = QueryService.get().select(t, t.getColumns(), null, null))
                {
                    assertNotNull(sql, rs);
                    QueryTestCase.assertEquals(sql, Rsize, rs.getSize());
                    ResultSetMetaData md = rs.getMetaData();
                    QueryTestCase.assertEquals(sql, 4, md.getColumnCount());
                    QueryTestCase.assertEquals(sql, 4, rs.getFieldMap().size());
                    QueryTestCase.assertEquals(sql, "d", rs.getColumn(1).getName());
                    QueryTestCase.assertEquals(sql, "seven", rs.getColumn(2).getName());
                    QueryTestCase.assertEquals(sql, "d_1", rs.getColumn(3).getName());
                    QueryTestCase.assertEquals(sql, "seven_1", rs.getColumn(4).getName());
                }
            }
        }

        @Test
        public void testContainerFilter() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            Container sub = getSubfolder();

            lists = DefaultSchema.get(user, c).getSchema("lists");
            if (1==1 || null == lists)
            {
                _tearDown();
                _setUp();
                lists = DefaultSchema.get(user, c).getSchema("lists");
            }

            {
            QueryDefinition q = QueryService.get().getQueryDef(user, JunitUtil.getTestContainer(), "lists", "QThisContainer");
            if (null != q)
                q.delete(user);
            }

            try
            {
                //
                // test default container filter with inherited query
                //
                SqlTest createQ = new SqlTest("QThisContainer", "SELECT Name, ID FROM core.Containers", null, 2, 1);
                createQ.validate(this, c);
                SqlTest selectQ = new SqlTest("SELECT * FROM QThisContainer");
                selectQ.validate(this, c);
                selectQ.validate(this, sub);

                try (ResultSet rs = resultset(selectQ._sql, c))
                {
                    boolean hasNext = rs.next();
                    assert hasNext;
                    assertEquals(rs.getInt(2), c.getRowId());
                }

                try (ResultSet rs = resultset(selectQ._sql, sub))
                {
                    boolean hasNext = rs.next();
                    assert hasNext;
                    assertEquals(rs.getInt(2), sub.getRowId());
                }

                //
                // can you think of more good tests
                //
            }
            finally
            {
                QueryDefinition q = QueryService.get().getQueryDef(user, JunitUtil.getTestContainer(), "lists", "QThisContainer");
                if (null != q)
                    q.delete(user);
            }

            GUID testGUID = new GUID("01234567-ABCD-ABCD-ABCD-012345679ABC");
            ContainerFilter custom = new ContainerFilter(null, null)
            {
                @Override
                public String getCacheKey()
                {
                    return " ~~CONTAINERFILTER~~ ";
                }

                @Override
                public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, boolean allowNulls)
                {
                    return new SQLFragment(" ~~CONTAINERFILTER~~ ");
                }

                @NotNull
                @Override
                public Collection<GUID> getIds()
                {
                    return Collections.singletonList(testGUID);
                }

                @NotNull
                @Override
                public Type getType()
                {
                    return Type.AllFolders;
                }
            };

            // TWO regression tests here
            //    lookup (QueryLookupWrapper) TODO need to find a DelegatingContainerFilter usage (Folder is a bad case)
            //    exists (subquery)
            //

            // Query.setContainerFilter()
            QueryDefinition q = QueryService.get().createQueryDef(user, c, "issues", "testquery");
            q.setContainerFilter(custom);
            q.setSql("SELECT DISTINCT label, container.name\n" +
                    "FROM (SELECT DISTINCT rowid, container, label FROM issuelistdef WHERE EXISTS (SELECT * FROM issuelistdef WHERE rowid=5)) x");
            ArrayList<QueryException> errors = new ArrayList<>();
            TableInfo t = q.getTable(errors, false);
            assertTrue(errors.isEmpty());
            SQLFragment sqlf = t.getFromSQL("$");
            assertNotNull(sqlf);
            String debugSql = sqlf.toDebugString();
            assertFalse(debugSql.contains(testGUID.toString()));
            assertTrue(debugSql.contains("CONTAINERFILTER"));
            assertFalse(debugSql.contains(c.getId()));
            assertEquals(2, StringUtils.countMatches(debugSql, "CONTAINERFILTER"));

            // TableInfo.setContainerFilter()
            q = QueryService.get().createQueryDef(user, c, "issues", "testquery");
            q.setSql("SELECT DISTINCT label, container.name\n" +
                    "FROM (SELECT DISTINCT rowid, container, label FROM issuelistdef WHERE EXISTS (SELECT * FROM issuelistdef WHERE rowid=5)) x");
            errors = new ArrayList<>();
            q.setContainerFilter(custom);
            t = q.getTable(errors, false);
            assertTrue(errors.isEmpty());
            sqlf = t.getFromSQL("$");
            assertNotNull(sqlf);
            debugSql = sqlf.toDebugString();
            assertFalse(debugSql.contains(testGUID.toString()));
            assertFalse(debugSql.contains(c.getId()));
            assertTrue(debugSql.contains("CONTAINERFILTER"));
            assertEquals(2, StringUtils.countMatches(debugSql, "CONTAINERFILTER"));
        }

        @Test
        public void testJSONDataLoader() throws Exception
        {
            // note getPrimarySchema() will return NULL if there are no lists yet
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();

            lists = DefaultSchema.get(user, c).getSchema("lists");
            if (1==1 || null == lists)
            {
                _tearDown();
                _setUp();
                lists = DefaultSchema.get(user, c).getSchema("lists");
            }

            assertNotNull(lists);
            assertNotNull(lists);
            TableInfo Rinfo = lists.getTable("R");
            assertNotNull(Rinfo);

            // mock request to selectRows
            ActionURL url = new ActionURL(QueryController.SelectRowsAction.class, c);
            url.addParameter(QueryParam.schemaName, "lists");
            url.addParameter(QueryParam.queryName, "R");

            MockHttpServletResponse resp = ViewServlet.GET(url, user, null);
            String content = resp.getContentAsString();

            // parse the response using JSONDataLoader and count the results
            InputStream stream = IOUtils.toInputStream(content);
            DataLoader loader = DataLoaderService.get().createLoader("selectRows.json", ApiJsonWriter.CONTENT_TYPE_JSON, stream, false, null, JSONDataLoader.FILE_TYPE);
            int count = 0;
            for (Map<String, Object> row : loader)
            {
                Assert.assertTrue(row.containsKey("rowid"));
                Assert.assertTrue("Expected rowid to be an Integer instance", row.get("rowid") instanceof Integer);

                Assert.assertTrue(row.containsKey(TestDataLoader.COLUMNS[0]));
//                Assert.assertTrue(
//                        "Expected '" + TestDataLoader.COLUMNS[0] + "' to be a '" + TestDataLoader.CLASSES[0] + "' instance, " + TestDataLoader.COLUMNS[0].getClass(),
//                        TestDataLoader.CLASSES[0] == row.get(TestDataLoader.COLUMNS[0]).getClass());

                count++;
            }
            Assert.assertEquals("Expected to find " + Rsize + " rows in lists.R table", Rsize, count);
        }


        static SqlTest[] containerTests = new SqlTest[]
        {
            new SqlTest("SELECT name FROM core.containers", 1, 1),
            new SqlTest("SELECT name FROM core.containers[ContainerFilter='Current']", 1, 1),
            new SqlTest("SELECT name FROM core.containers[ContainerFilter='CurrentAndFirstChildren']", 1, 2),

            // test caching of resolved tables, these two references to core.containers should not be shared
            new SqlTest("SELECT A.name FROM core.containers[ContainerFilter='CurrentAndFirstChildren'] A inner join core.containers B on A.entityId = B.entityId", 1, 1),
            new SqlTest("SELECT A.name FROM core.containers A inner join core.containers[ContainerFilter='CurrentAndFirstChildren'] B on A.entityId = B.entityId", 1, 1),
            new SqlTest("SELECT A.name FROM core.containers[ContainerFilter='AllInProject'] A inner join core.containers[ContainerFilter='CurrentAndFirstChildren'] B on A.entityId = B.entityId", 1, 2),
            new SqlTest("SELECT A.name FROM core.containers[ContainerFilter='CurrentAndFirstChildren'] A inner join core.containers[ContainerFilter='AllInProject'] B on A.entityId = B.entityId", 1, 2)
        };


        @Test
        public void testContainerAnnotation() throws Exception
        {
            // note getPrimarySchema() will return NULL if there are no lists yet
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();

            if (1==1 || null == lists)
            {
                _tearDown();
                _setUp();
                lists = DefaultSchema.get(user, c).getSchema("lists");
            }

            for (SqlTest test : containerTests)
            {
                test.validate(this, null);
            }
        }


        private void validateInvolvedColumns(String sql, @Nullable Container container, List<String> expectedInvolvedColumns)
        {
            QuerySchema schema = lists;
            if (null != container)
                schema = schema.getSchema("Folder").getSchema(container.getPath()).getSchema("lists");
            assert null != schema;

            try
            {
                mockSelect(schema, sql, null, true, expectedInvolvedColumns);
            }
            catch (QueryParseException x)
            {
                fail(x, sql);
            }
        }

        private void fail(QueryParseException qpe, String sql)
        {
            Exception ex = qpe;
            if (ex.getCause() instanceof Exception)
                ex = (Exception)ex.getCause();
            fail(ex.getMessage() + "\n" + sql);
        }

        private void mockSelect(@NotNull QuerySchema schema, String sql, @Nullable Map<String, TableInfo> tableMap,
                                  boolean strictColumnList, List<String> expectedColumns)
        {
            Query q = new Query(schema);
            q.setStrictColumnList(strictColumnList);
            q.setTableMap(tableMap);
            q.parse(sql);

            if (q.getParseErrors().size() > 0)
                throw q.getParseErrors().get(0);

            Map<String, QueryTable.TableColumn> involvedColumnMap = new CaseInsensitiveHashMap<>();
            for (QueryTable.TableColumn column : q.getInvolvedTableColumns())
                involvedColumnMap.put(column.getTable().getTableInfo().getName() + "/" + column.getFieldKey().toString(), column);

            for (String expectedColumn : expectedColumns)
            {
                if (!involvedColumnMap.containsKey(expectedColumn))
                    fail("Involved column '" + expectedColumn + "' not found for sql:\n" + sql);
            }
        }
    }
}
