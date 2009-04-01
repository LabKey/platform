/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ColumnInfo;
//import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.util.MemTracker;
import static org.labkey.query.sql.SqlTokenTypes.*;
import org.labkey.data.xml.ColumnType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class QueryUnion extends QueryRelation
{
	QUnion _qunion;
	QOrder _qorderBy;

    List<QueryRelation> _termList = new ArrayList<QueryRelation>();
    Map<String, UnionColumn> _unionColumns = new HashMap<String, UnionColumn>();


	QueryUnion(Query query, QUnion qunion)
    {
        super(query);
		_qunion = qunion;
        collectUnionTerms(qunion);
		_qorderBy = _qunion.getChildOfType(QOrder.class);
        assert MemTracker.put(this);
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
                //noinspection ThrowableInstanceNeverThrown
                _query.getParseErrors().add(new QueryParseException("LIMIT not supported with UNION", null, n.getLine(), n.getColumn()));
			}
            else if (n instanceof QQuery)
            {
                QuerySelect select = new QuerySelect(_query, (QQuery)n);
                _termList.add(select);
            }
            else if (n instanceof QUnion && (_qunion.getTokenType() == UNION || n.getTokenType() == UNION_ALL))
			{
                collectUnionTerms((QUnion)n);
			}
			else if (n instanceof QUnion)
			{

				QueryUnion union = new QueryUnion(_query, (QUnion)n);
				_termList.add(union);
			}
        }
    }


    void declareFields()
    {
        for (QueryRelation term : _termList)
        {
            term.declareFields();
        }
    }


    void initColumns()
    {
        if (_unionColumns.isEmpty())
        {
            List<RelationColumn> all = _termList.get(0).getAllColumns();
            for (RelationColumn c : all)
            {
                _unionColumns.put(c.getName(), new UnionColumn(c.getName(), c));
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
        initColumns();
        if (_query.getParseErrors().size() > 0)
            return null;

		String unionOperator = "";
        SQLFragment unionSql = new SQLFragment();
        assert unionSql.appendComment("<QueryUnion@" + System.identityHashCode(this) + ">");

		for (QueryRelation term : _termList)
		{
			SQLFragment sql = term.getSql();
			unionSql.append(unionOperator);
			unionSql.append("(");
			unionSql.append(sql);
			unionSql.append(")");
			unionOperator = _qunion.getTokenType() == UNION_ALL ? "\nUNION ALL\n" : "\nUNION\n";
		}

		if (null != _qorderBy)
		{
			List<Map.Entry<QExpr,Boolean>> sort = _qorderBy.getSort();
			if (sort.size() > 0)
			{
				unionSql.append("\n\nORDER BY ");
				String comma = "";
				for (Map.Entry<QExpr, Boolean> entry : _qorderBy.getSort())
				{
					QExpr expr = resolveFields(entry.getKey());
					unionSql.append(comma);
					unionSql.append(expr.getSqlFragment(_schema.getDbSchema()));
					if (!entry.getValue().booleanValue())
						unionSql.append(" DESC");
					comma = ", ";
				}
			}
		}

        SQLTableInfo sti = new SQLTableInfo(_schema.getDbSchema());
        sti.setName("_union");
        sti.setFromSQL(unionSql);
        QueryTableInfo ret = new QueryTableInfo(this, sti, "_union");
        for (UnionColumn unioncol : _unionColumns.values())
        {
            ColumnInfo ucol = new RelationColumnInfo(ret, unioncol);
            ret.addColumn(ucol);
        }
        assert unionSql.appendComment("</QueryUnion@" + System.identityHashCode(this) + ">");
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
			return new QField(null, key.getName(), expr)
			{
				@Override
				public void appendSql(SqlBuilder builder)
				{
					builder.append(_name);
				}
			};
		}

		QExpr ret = (QExpr) expr.clone();
		for (QNode child : expr.children())
			ret.appendChild(resolveFields((QExpr)child));
		return ret;
	}


    RelationColumn getColumn(String name)
    {
        initColumns();
        return _unionColumns.get(name);
    }


    protected List<RelationColumn> getAllColumns()
    {
        initColumns();
        return new ArrayList<RelationColumn>(_unionColumns.values());
    }


    RelationColumn getLookupColumn(RelationColumn parent, String name)
    {
        return null;
    }


    RelationColumn getLookupColumn(RelationColumn parent, ColumnType.Fk fk, String name)
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
			unionOperator = _qunion.getTokenType() == UNION_ALL ? "\nUNION ALL\n" : "\nUNION\n";
		}

		return sb.toString();
    }


    class UnionColumn extends RelationColumn
    {
        String _name;
        RelationColumn _first;

        UnionColumn(String name, RelationColumn col)
        {
            _name = name;
            _first = col;
        }
        
        public String getName()
        {
            return _name;
        }

        String getAlias()
        {
            return _name;
        }

        QueryRelation getTable()
        {
            return QueryUnion.this;
        }

        public int getSqlTypeInt()
        {
            return _first.getSqlTypeInt();
        }

        ColumnInfo getColumnInfo()
        {
            ColumnInfo ci = new ColumnInfo(_name);
            copyColumnAttributesTo(ci);
            return ci;
        }

        @Override
        void copyColumnAttributesTo(ColumnInfo to)
        {
            _first.copyColumnAttributesTo(to);
            to.setFk(null);
        }
    }
}
