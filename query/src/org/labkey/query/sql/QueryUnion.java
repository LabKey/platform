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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SelectQueryAuditProvider;
import org.labkey.api.data.Sort;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.MemTracker;
import org.labkey.data.xml.ColumnType;
import org.labkey.query.sql.QueryRelation.ColumnResolvingRelation;
import org.labkey.query.sql.antlr.SqlBaseParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class QueryUnion extends AbstractQueryRelation implements ColumnResolvingRelation
{
	QUnion _qunion;
	QOrder _qorderBy;
    QLimit _limit;

    // if this union is a direct child of a recurisive CTE, set that here so we know
    CommonTableExpressions.QueryTableWith _queryTableWith;

    /* NOTE: because of how UNION works, I need to reference columns by ordinal position as well as by name.
     * That is why we use ArrayListMap all over this class.
     */

    // For the column logging computation (and other uses) we track the matrix of source columns using
    // UnionSourceColumn and UnionTerm.
    static class UnionSourceColumn
    {
        UnionSourceColumn(RelationColumn src, int ordinal)
        {
            this.source = src;
            this.ordinal = ordinal;
        }
        final RelationColumn source;
        final int ordinal;
        ColumnLogging columnLogging;
    }
    record UnionTerm(QueryRelation relation, ArrayListMap<String, UnionSourceColumn> columns, Map<String,FieldKey> uniqueNameMap)
    {
    };

    List<UnionTerm> _termList = new ArrayList<>();
    ArrayListMap<String, UnionColumn> _unionColumns = new ArrayListMap<>();

    final HashMap<FieldKey, String> _uniqueNameToAliasMap = new HashMap<>();
    final HashMap<String, FieldKey> _aliasToUniqueNameMap = new HashMap<>();



    // This constructor is used by CommonTableExpressions so that the QueryUnion object
    // can be registered in the global namespace before it is fully constructed
    private QueryUnion(Query query)
    {
        super(query);
        MemTracker.getInstance().put(this);
    }

    QueryUnion(Query query, CommonTableExpressions.QueryTableWith tableWith)
    {
        this(query);
        _queryTableWith = tableWith;
    }

    QueryUnion(Query query, QUnion qunion)
    {
        this(query);
        setQUnion(qunion);
    }

    QueryUnion(AbstractQueryRelation parent, QUnion qunion, boolean inFromClause, String alias)
    {
        this(parent._query, qunion);
        assert inFromClause == (alias != null);
        _query = parent._query;
        _parent = parent;
        _inFromClause = inFromClause;
        setAlias(alias);
    }

    protected void setQUnion(QUnion qunion)
    {
        _qunion = qunion;
        collectUnionTerms(qunion);
        _qorderBy = _qunion.getChildOfType(QOrder.class);
        MemTracker.getInstance().put(this);
    }

    @Override
    public void setQuery(Query query)
    {
        // UNION within a CTE can be recursive, need to guard against that.
        if (query != _query)
        {
            super.setQuery(query);
            for (UnionTerm t : _termList)
                t.relation.setQuery(query);
        }
    }


    private void addTerm(QueryRelation term)
    {

        ArrayListMap<String, UnionSourceColumn> sourceColumns = new ArrayListMap<>();
        for (RelationColumn col : term.getAllColumns().values())
        {
            UnionSourceColumn sourceColumn = new UnionSourceColumn(col, sourceColumns.size());
            sourceColumns.put(col.getAlias(), sourceColumn);
        }

        /* NOTE can't init the uniqueNameMap here.  Doing this before declareFields()/resolveFields() doesn't work for QuerySelect */
        _termList.add(new UnionTerm(term, sourceColumns, new HashMap<>()));
    }


    void collectUnionTerms(QUnion qunion)
    {
        for (QNode n : qunion.children())
        {
            QueryRelation termToAdd = null;

            assert n instanceof QQuery || n instanceof QUnion || n instanceof QOrder || n instanceof QLimit;

			if (n instanceof QLimit)
			{
                _limit = (QLimit)n;
			}
            else if (n instanceof QQuery)
            {
                // NOTE inFromClause==true because we want 'nested' behavior (especially wrt comments)
                QuerySelect select = new QuerySelect(_query, (QQuery)n, this, true);
                select._queryText = null; // see issue 23918, we don't want to repeat the source sql for each term in devMode
                select.markAllSelected(qunion);
                termToAdd = select;
            }
            else if (n instanceof QUnion && canFlatten(_qunion.getTokenType(),n.getTokenType()))
			{
                collectUnionTerms((QUnion)n);
			}
			else if (n instanceof QUnion)
			{
				QueryUnion union = new QueryUnion(_query, (QUnion)n);
                termToAdd = union;
			}
			if (!getParseErrors().isEmpty())
			    break;

            // To handle CTE recursion we want to getAllColumns() to be available immediately after parsing the first union term.
            // The first term should not be the recursive one.
            if (_unionColumns.isEmpty())
            {
                assert null != termToAdd;
                Map<String,RelationColumn> all = termToAdd.getAllColumns();
                // If all is empty, it is probably because the first term has illegal recursion.
                if (all.isEmpty() && null != _queryTableWith && _queryTableWith.isSeenRecursiveReference())
                {
                    // postgres would give this error:" recursive reference to query "CTE" must not appear within its non-recursive term
                    _query.getParseErrors().add(new QueryParseException("Recursive reference to query \"" + _queryTableWith.getCteName() + "\" must not appear within its non-recursive term.", null, n.getLine(), n.getColumn()));
                    return;
                }
                for (Map.Entry<String,RelationColumn> e : all.entrySet())
                {
                    String name = e.getKey();
                    RelationColumn col = e.getValue();
                    UnionColumn unionCol = new UnionColumn(name, col, _unionColumns.size());
                    _unionColumns.put(e.getKey(), unionCol);
                    _uniqueNameToAliasMap.put(unionCol.getFieldKey(), unionCol.getUniqueName());
                    _aliasToUniqueNameMap.put(unionCol.getUniqueName(), unionCol.getFieldKey());
                    _query.addUniqueRelationColumn(unionCol);
                }
            }

            // _unionColumns.isEmpty() check before adding first term, so that we can reference those names in addTerm()
            if (null != termToAdd)
                addTerm(termToAdd);
        }
    }


    @Override
    public Map<FieldKey, FieldKey> getRemapMap(Map<String, FieldKey> outerMap)
    {
        return QueryRelation.generateRemapMap(_uniqueNameToAliasMap, outerMap);
    }


    private boolean canFlatten(int parent, int child)
    {
        // INTERSECT,INTERSECT ok
        // UNION,UNION ok
        // UNION,UNION_ALL ok
        // UNION_ALL,UNION_ALL ok
        // don't flatten other combinations
        // UNION_ALL,UNION NOT OK
        if (parent == SqlBaseParser.UNION && child == SqlBaseParser.UNION_ALL)
            return true;
        return parent == child && parent != SqlBaseParser.EXCEPT;
    }


    @Override
    public void declareFields()
    {
        for (UnionTerm term : _termList)
        {
            term.relation.declareFields();
        }

        initColumns();

        if (null != _qorderBy)
        {
            for (var entry : _qorderBy.getSort())
            {
                resolveFields(entry.expr());
            }
        }
    }


    @Override
    public void resolveFields()
    {
        for (UnionTerm term : _termList)
            term.relation.resolveFields();
    }


    boolean initColumnCalled = false;
    boolean computeColumnLoggingCalled = false;

    void initColumns()
    {
        if (initColumnCalled)
            return;
        initColumnCalled = true;

        // attach all sourceColumns to the corresponding UnionColumn
        for (UnionColumn col : _unionColumns)
            for (UnionTerm term : _termList)
                col.addSourceColumn(term.columns.get( col._ordinal));
    }


    /* Hopefully we can evantually avoid doing all this work when column logging is not enabled, so do this as late as possible.
     * copyAttributes() calls getColumnLogging(), so it's not trivial to avoid calling this.
     */
    void computeColumnLogging()
    {
        if (computeColumnLoggingCalled)
            return;
        computeColumnLoggingCalled = true;

        // Initialize uniqueNameMap for each term.  This is a composed map from src.uniqueName -> src.fieldkey -> unionColumn.fieldKey.
        // This is the "outerMap" used by getRemapMap() to translate ColumnLogging objects into the namespace
        // of the UNION query.
        for (var term : _termList)
        {
            for (var sourceColumn : term.columns)
            {
                var unionColumnName = _unionColumns.get(sourceColumn.ordinal).getFieldKey();
                term.uniqueNameMap.put(sourceColumn.source.getUniqueName(), unionColumnName);
            }
        }


        // generate a ColumnLogging object for each UnionColumn
        // step 1) For each source column create a ColumnLogging object that would
        //  make sense in the context of the UNION query.
        // step 2) for each output column derive a ColumnLogging from all its source columns

        QueryTableInfo fakeTableInfo = new QueryTableInfo(this, "UNION")
        {
            @Override
            @NotNull
            public SQLFragment getFromSQL() {throw new UnsupportedOperationException();}

            @Override
            public @Nullable UserSchema getUserSchema()
            {
                return (UserSchema)QueryUnion.this.getSchema();
            }
        };

        for (var term : _termList)
        {
            for (var col : term.columns)
            {
                List<RelationColumn> involved = new ArrayList<>();
                col.source.gatherInvolvedSelectColumns(involved);
                QueryColumnLogging qcl = QueryColumnLogging.create(fakeTableInfo, col.source.getFieldKey(), involved);
                col.columnLogging = qcl.remapQueryFieldKeys(fakeTableInfo, col.source.getFieldKey(), term.uniqueNameMap);
            }
        }

        for (var col : _unionColumns)
        {
            boolean shouldLog = false;
            SelectQueryAuditProvider sqap = null;
            Exception ex = null;
            Set<FieldKey> dataLoggingColumns = new HashSet<>();
            for (UnionSourceColumn source : col._sourceColumns)
            {
                shouldLog |= source.columnLogging.shouldLogName();
                sqap = null != sqap ? sqap : source.columnLogging.getSelectQueryAuditProvider();
                ex = null != ex ? ex : source.columnLogging.getException();
                if (null == ex)
                    dataLoggingColumns.addAll(source.columnLogging.getDataLoggingColumns());
            }
            if (null != ex)
                col._columnLogging = ColumnLogging.error(shouldLog, sqap, ex.getMessage());
            else
                col._columnLogging = new ColumnLogging(this.getSchema().getSchemaName(), "UNION", col.getFieldKey(), shouldLog, dataLoggingColumns, "TODO", sqap);
        }
    }


	SQLFragment _unionSql = null;


    @Override
    public QueryTableInfo getTableInfo()
    {
        SqlDialect dialect = _schema.getDbSchema().getSqlDialect();
        initColumns();
        if (_query.getParseErrors().size() > 0)
            return null;

		String unionOperator = "";
        SqlBuilder unionSql = new SqlBuilder(dialect);

        assert unionSql.appendComment("<QueryUnion>", dialect);
        assert null == _query._querySource || QuerySelect.appendLongComment(unionSql, _query._querySource);

        List<JdbcType> columnTypes = null;
		for (UnionTerm term : _termList)
		{
			SQLFragment sql = term.relation.getSql();

            if (term.relation.getSelectedColumnCount() != _unionColumns.size())
            {
                _query.getParseErrors().add(new QueryParseException("All subqueries in a UNION must have the same number of columns", null, _qunion.getLine(), _qunion.getColumn()));
                return null;
            }

            if (dialect.isPostgreSQL() || dialect.isSqlServer())
            {
                if (null == columnTypes)
                {
                    // First Union clause; remember types. Columns are ordered
                    columnTypes = new ArrayList<>();
                    for (RelationColumn termColumn : term.relation.getAllColumns().values())
                    {
                        columnTypes.add(termColumn.getJdbcType());
                    }
                }
                else
                {
                    // Subsequent clauses; check types. We use promote, which is lenient as long as there is a possible conversion.
                    // Postgres is stricter so we'll allow some cases that will yield errors when run.
                    // Also, this allows VARCHAR to INT, which will fail on SQL Server if the VARCHAR cannot be converted (and always, of course, on Postgres)
                    int i = 0;
                    for (UnionSourceColumn unionColumn : term.columns)
                    {
                        JdbcType calculatedUnionType = columnTypes.get(i);
                        JdbcType type = unionColumn.source.getJdbcType();

                        if (!JdbcType.NULL.equals(type) && JdbcType.NULL.equals(calculatedUnionType))
                        {
                            columnTypes.set(i, type);   // Once we see non-NULL for this position, remember that
                        }
                        else if (!JdbcType.NULL.equals(type) && !JdbcType.OTHER.equals(type) && !JdbcType.OTHER.equals(calculatedUnionType) &&
                                !type.equals(calculatedUnionType) && JdbcType.OTHER.equals(JdbcType.promote(type, calculatedUnionType)))
                        {
                            Query.parseError(_query.getParseErrors(), _query._debugName + ": Mismatched types in UNION (" +
                                    type.name() + ", " + calculatedUnionType.name() + ") for column position " + (i + 1), _qunion);
                        }
                        i += 1;
                    }
                }
            }

            if (null == sql)
            {
                if (_query.getParseErrors().size() > 0)
                    return null;
                String src = "";
                int line = 0, col=0;
                if (term.relation instanceof QuerySelect qselect && null != qselect._root)
                {
                    src  = qselect._root.getSourceText();
                    line = qselect._root.getLine();
                    col  = qselect._root.getColumn();
                }
                String message = "Unexpected error parsing union term: " + src;
                _query.getParseErrors().add(new QueryParseException(message, null, line, col));
                unionSql.append("#ERROR: ").append(message).append("#");
                return null;
            }
			unionSql.append(unionOperator);
			unionSql.append("(");
			unionSql.append(sql);
			unionSql.append(")");
			unionOperator = "\n" + SqlParser.tokenName(_qunion.getTokenType()) + "\n";
		}

        List<QOrder.SortEntry> sort = null == _qorderBy ? null : _qorderBy.getSort();

        if (null != sort && sort.size() > 0 || null != _limit)
        {
            SqlBuilder wrap = new SqlBuilder(dialect);
            wrap.append("SELECT * FROM (");
            wrap.append(unionSql);
            wrap.append(") u").append(unionSql.hashCode() & 0x7fffffff);
            unionSql = wrap;
        }

		if (null != sort && sort.size() > 0)
		{
            unionSql.append("\nORDER BY ");
            String comma = "";
            for (var entry : sort)
            {
                QExpr expr = resolveFields(entry.expr());
                unionSql.append(comma);
                unionSql.append(expr.getSqlFragment(_schema.getDbSchema().getSqlDialect(), _query));
                if (!entry.direction())
                    unionSql.append(" DESC");
                comma = ", ";
            }
            if (null == _limit)
                dialect.appendSortOnSubqueryWithoutLimitQualifier(unionSql);
		}
        if (null != _limit)
        {
            dialect.limitRows(unionSql, _limit.getLimit());
        }

        UnionTableInfoImpl ret = new UnionTableInfoImpl(this, "_union")
        {
            @NotNull
            @Override
            public SQLFragment getFromSQL(String alias)
            {
                SQLFragment f = new SQLFragment();
                f.append("(").append(getSql()).append(") ").append(alias);
                return f;
            }

            @Override
            protected void afterInitializeColumns()
            {
                remapSelectFieldKeys(true);
            }
        };

        for (UnionColumn unioncol : _unionColumns)
            ret.addColumn(new UnionColumnInfo(ret, unioncol));

        // Not sure how getUnionColumns() relates to getAllInvolvedColumns()
        // comment???
        for (UnionTerm term : _termList)
            for (UnionSourceColumn sourceCol : term.columns)
                ret.addUnionColumn(new RelationColumnInfo(ret, sourceCol.source));

        ret.afterInitializeColumns();

        assert unionSql.appendComment("</QueryUnion>", _schema.getDbSchema().getSqlDialect());
		_unionSql = unionSql;
        return ret;
    }


    private class UnionColumnInfo extends RelationColumnInfo
    {
        UnionColumnInfo(UnionTableInfoImpl table, UnionColumn unionColumn)
        {
            super(table, unionColumn);
            // unionColumn.getColumnLogging() is created using a fake table, we're just renaming it here to look like it belongs to this QueryTableInfo

            ColumnLogging columnLogging = unionColumn.getColumnLogging();
            if (null == columnLogging.getException())
                columnLogging = new ColumnLogging(getSchema().getName(), getParentTable().getName(), getFieldKey(),
                        columnLogging.shouldLogName(), columnLogging.getDataLoggingColumns(), columnLogging.getLoggingComment(), columnLogging.getSelectQueryAuditProvider());
            setColumnLogging(columnLogging);
        }

        @Override
        public ColumnLogging getColumnLogging()
        {
            return super.getColumnLogging();
        }
    }


    @Override
    public List<Sort.SortField> getSortFields()
    {
        if (_qorderBy == null || _qorderBy.childList().isEmpty())
            return List.of();

        List<Sort.SortField> ret = new ArrayList<>();
        for (var entry : _qorderBy.getSort())
        {
            QExpr expr = resolveFields(entry.expr());
            if (expr instanceof QIdentifier)
            {
                ret.add(new Sort.SortField(new FieldKey(null,expr.getTokenText()), entry.direction() ? Sort.SortDirection.ASC : Sort.SortDirection.DESC));
            }
            else if (expr instanceof QNumber qNum)
            {
                double d = qNum.getValue().doubleValue();
                int position = qNum.getValue().intValue();
                if (d == (double)position && position >= 1 && position <= _unionColumns.size())
                {
                    String alias = _unionColumns.get(position-1).getAlias();
                    ret.add(new Sort.SortField(new FieldKey(null,alias), entry.direction() ? Sort.SortDirection.ASC : Sort.SortDirection.DESC));
                }
            }
            else
            {
                return List.of();
            }
        }

        return ret;
    }


    // simplified version of resolve Field
	private QExpr resolveFields(QExpr expr)
	{
		if (expr instanceof QQuery)
		{
            //noinspection ThrowableInstanceNeverThrown
            _query.getParseErrors().add(new QueryParseException("Subquery not allowed in UNION's ORDER BY", null, expr.getLine(), expr.getColumn()));
			return expr;
		}

		FieldKey key = expr.getFieldKey();
		if (key != null)
		{
            final UnionColumn uc = _unionColumns.get(key.getName());
            if (null == uc)
            {
                _query.getParseErrors().add(new QueryParseException("Can't find column: " + key.getName(), null, expr.getLine(), expr.getColumn()));
                return null;
            }
			return new QField(null, key.getName(), expr)
			{
				@Override
				public void appendSql(SqlBuilder builder, Query query)
				{
                    builder.append(uc.getAlias());
				}
			};
		}

		QExpr ret = (QExpr) expr.clone();
		for (QNode child : expr.children())
			ret.appendChild(resolveFields((QExpr)child));
		return ret;
	}


    @Override
    public int getSelectedColumnCount()
    {
        return _unionColumns.size();
    }


    @Override
    public RelationColumn getColumn(@NotNull String name)
    {
        initColumns();
        return _unionColumns.get(name);
    }

    @Override
    public @Nullable AbstractQueryRelation.RelationColumn getFirstColumn()
    {
        if (_unionColumns.isEmpty())
            return null;
        return _unionColumns.values().iterator().next();
    }

    @Override
    public LinkedHashMap<String,RelationColumn> getAllColumns()
    {
        initColumns();
        return new LinkedHashMap<>(_unionColumns);
    }


    @Override
    public RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name)
    {
        return null;
    }


    @Override
    public RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull ColumnType.Fk fk, @NotNull String name)
    {
        return null;
    }


    @Override
    public SQLFragment getSql()
    {
        if (_unionSql == null)
            getTableInfo();
        return _unionSql;
    }


    @Override
    public String getQueryText()
    {
		StringBuilder sb = new StringBuilder();
		String unionOperator = "";
		for (Object term : _termList)
		{
			String sql;
			if (term instanceof QuerySelect)
				sql = ((QuerySelect) term).getQueryText();
			else
				sql = ((QueryUnion) term).getQueryText();
			sb.append(unionOperator);
			sb.append("(");
			sb.append(sql);
			sb.append(")");
			unionOperator = "\n" + SqlParser.tokenName(_qunion.getTokenType()) + "\n";
		}

		return sb.toString();
    }


    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        for (UnionTerm term : _termList)
        {
            term.relation.setContainerFilter(containerFilter);
        }
        // Uncache the SQL that was generated since it's likely changed
        _unionSql = null;
    }


    @Override
    public Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        return Collections.emptySet();
    }


    class UnionColumn extends RelationColumn
    {
        final FieldKey _name;
        final RelationColumn _first;
        final int _ordinal;
        final ArrayList<UnionSourceColumn> _sourceColumns = new ArrayList<>();

        ColumnLogging _columnLogging;

        UnionColumn(String name, RelationColumn col, int ordinal)
        {
            _name = new FieldKey(null, name);
            _first = col;
            _ordinal = ordinal;
        }

        @Override
        public String getUniqueName()
        {
            return super._defaultUniqueName(QueryUnion.this);
        }

        private void addSourceColumn(UnionSourceColumn col)
        {
            _sourceColumns.add(col);
        }

        @Override
        public Collection<RelationColumn> gatherInvolvedSelectColumns(Collection<RelationColumn> collect)
        {
            // QueryUnion implements ColumnResolvingRelation, so we do not recurse through union to the union terms.
            collect.add(this);
            return collect;
        }

        @Override
        ColumnLogging getColumnLogging()
        {
            computeColumnLogging();
            return _columnLogging;
        }

        @Override
        public FieldKey getFieldKey()
        {
            return _name;
        }

        @Override
        String getAlias()
        {
            return _first.getAlias();
        }

        @Override
        AbstractQueryRelation getTable()
        {
            return QueryUnion.this;
        }

        @Override @NotNull
        public JdbcType getJdbcType()
        {
            return _first.getJdbcType();
        }

        @Override
        boolean isHidden()
        {
            return _first.isHidden();
        }

        @Override
        String getPrincipalConceptCode()
        {
            return null; // CONSIDER: check that all match
        }

        @Override
        String getConceptURI()
        {
            return null; // CONSIDER: check that all match
        }

        @Override
        void copyColumnAttributesTo(@NotNull BaseColumnInfo to)
        {
            _first.copyColumnAttributesTo(to);
            to.clearFk();
            to.setKeyField(false);
        }
    }
}
