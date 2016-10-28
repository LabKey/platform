/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.util.MemTracker;
import org.labkey.data.xml.ColumnType;
import org.labkey.query.sql.antlr.SqlBaseParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QueryUnion extends QueryRelation
{
	QUnion _qunion;
	QOrder _qorderBy;
    QLimit _limit;


    List<QueryRelation> _termList = new ArrayList<>();
    Map<String, UnionColumn> _unionColumns = new LinkedHashMap<>();
    List<UnionColumn> _allColumns = new ArrayList<>();


	QueryUnion(Query query, QUnion qunion)
    {
        super(query);
		_qunion = qunion;
        collectUnionTerms(qunion);
		_qorderBy = _qunion.getChildOfType(QOrder.class);
        MemTracker.getInstance().put(this);
    }


    QueryUnion(QueryRelation parent, QUnion qunion, boolean inFromClause, String alias)
    {
        this(parent._query, qunion);
        assert inFromClause == (alias != null);
        this._query = parent._query;
        _parent = parent;
        _inFromClause = inFromClause;
        setAlias(alias);
    }


    @Override
    void setQuery(Query query)
    {
        super.setQuery(query);
        for (QueryRelation r : _termList)
            r.setQuery(query);
    }


    void collectUnionTerms(QUnion qunion)
    {
        for (QNode n : qunion.children())
        {
            assert n instanceof QQuery || n instanceof QUnion || n instanceof QOrder || n instanceof QLimit;

			if (n instanceof QLimit)
			{
                _limit = (QLimit)n;
			}
            else if (n instanceof QQuery)
            {
                // NOTE inFromClause==true because we want 'nested' behavior (especially wrt comments)
                QuerySelect select = new QuerySelect(_query, (QQuery)n, true);
                select._queryText = null; // see issue 23918, we don't want to repeat the source sql for each term in devMode
                select._parent = this;
                select.markAllSelected(qunion);
                _termList.add(select);
            }
            else if (n instanceof QUnion && canFlatten(_qunion.getTokenType(),n.getTokenType()))
			{
                collectUnionTerms((QUnion)n);
			}
			else if (n instanceof QUnion)
			{

				QueryUnion union = new QueryUnion(_query, (QUnion)n);
				_termList.add(union);
			}
			if (!getParseErrors().isEmpty())
			    break;
        }
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
    void declareFields()
    {
        for (QueryRelation term : _termList)
        {
            term.declareFields();
        }

        initColumns();
        
        if (null != _qorderBy)
        {
            for (Map.Entry<QExpr, Boolean> entry : _qorderBy.getSort())
            {
                resolveFields(entry.getKey());
            }
        }
    }


    @Override
    protected void resolveFields()
    {
        for (QueryRelation r : _termList)
            r.resolveFields();
    }


    void initColumns()
    {
        if (_unionColumns.isEmpty())
        {
            Map<String,RelationColumn> all = _termList.get(0).getAllColumns();
            for (Map.Entry<String,RelationColumn> e : all.entrySet())
            {
                _unionColumns.put(e.getKey(), new UnionColumn(e.getKey(), e.getValue()));
            }
        }

        if (_allColumns.isEmpty())
        {
            for (QueryRelation relation : _termList)
            {
                for (Map.Entry<String,RelationColumn> e  : relation.getAllColumns().entrySet())
                    _allColumns.add(new UnionColumn(e.getKey(), e.getValue()));
            }
        }

//        if (_tinfos.size() > 0)
//			return;
//		for (Object o : _termList)
//		{
//			if (o instanceof QuerySelect)
//				_tinfos.add(((QuerySelect)o).getTableInfo());
//			else
//				_tinfos.add(((QueryUnion)o).getTableInfo());
//        }
    }


	SQLFragment _unionSql = null;


    public QueryTableInfo getTableInfo()
    {
        SqlDialect dialect = _schema.getDbSchema().getSqlDialect();
        initColumns();
        if (_query.getParseErrors().size() > 0)
            return null;

		String unionOperator = "";
        SqlBuilder unionSql = new SqlBuilder(dialect);

        assert unionSql.appendComment("<QueryUnion>", dialect);
        if (null != _query._querySource)
            assert QuerySelect.appendLongComment(unionSql, _query._querySource);

		for (QueryRelation term : _termList)
		{
			SQLFragment sql = term.getSql();

            if (term.getSelectedColumnCount() != _unionColumns.size())
            {
                _query.getParseErrors().add(new QueryParseException("All subqueries in a UNION must have the same number of columns", null, _qunion.getLine(), _qunion.getColumn()));
                return null;
            }
            
            if (null == sql)
            {
                if (_query.getParseErrors().size() > 0)
                    return null;
                String src = "";
                int line = 0, col=0;
                if (term instanceof QuerySelect && null != ((QuerySelect)term)._root)
                {
                    src=((QuerySelect)term)._root.getSourceText();
                    line = ((QuerySelect)term)._root.getLine();
                    col  = ((QuerySelect)term)._root.getColumn();
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

        List<Map.Entry<QExpr,Boolean>> sort = null == _qorderBy ? null : _qorderBy.getSort();
        
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
			if (sort.size() > 0)
			{
				unionSql.append("\nORDER BY ");
				String comma = "";
				for (Map.Entry<QExpr, Boolean> entry : _qorderBy.getSort())
				{
					QExpr expr = resolveFields(entry.getKey());
					unionSql.append(comma);
					unionSql.append(expr.getSqlFragment(_schema.getDbSchema().getSqlDialect(), _query));
					if (!entry.getValue())
						unionSql.append(" DESC");
					comma = ", ";
				}
			}
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
            public boolean hasSort()
            {
                return _qorderBy != null && !_qorderBy.childList().isEmpty(); 
            }
        };

        for (UnionColumn unioncol : _unionColumns.values())
        {
            ColumnInfo ucol = new RelationColumnInfo(ret, unioncol);
            ret.addColumn(ucol);
        }
        for (UnionColumn unioncol : _allColumns)
            ret.addUnionColumn(new RelationColumnInfo(ret, unioncol));
        
        assert unionSql.appendComment("</QueryUnion>", _schema.getDbSchema().getSqlDialect());
		_unionSql = unionSql;
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
    int getSelectedColumnCount()
    {
        return _unionColumns.size();
    }
    

    RelationColumn getColumn(@NotNull String name)
    {
        initColumns();
        return _unionColumns.get(name);
    }


    protected Map<String,RelationColumn> getAllColumns()
    {
        initColumns();
        return new LinkedHashMap<String,RelationColumn>(_unionColumns);
    }


    RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name)
    {
        return null;
    }


    RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull ColumnType.Fk fk, @NotNull String name)
    {
        return null;
    }


    public SQLFragment getSql()
    {
        if (_unionSql == null)
            getTableInfo();
        return _unionSql;
    }


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
        for (QueryRelation queryRelation : _termList)
        {
            queryRelation.setContainerFilter(containerFilter);
        }
        // Uncache the SQL that was generated since it's likely changed
        _unionSql = null;
    }


    @Override
    protected Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        return Collections.emptySet();
    }


    class UnionColumn extends RelationColumn
    {
        FieldKey _name;
        RelationColumn _first;

        UnionColumn(String name, RelationColumn col)
        {
            _name = new FieldKey(null, name);
            _first = col;
        }
        
        @Override
        public FieldKey getFieldKey()
        {
            return _name;
        }

        String getAlias()
        {
            return _first.getAlias();
        }

        QueryRelation getTable()
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
        void copyColumnAttributesTo(ColumnInfo to)
        {
            _first.copyColumnAttributesTo(to);
            to.setFk(null);
            to.setKeyField(false);
        }
    }
}
