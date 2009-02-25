package org.labkey.query.sql;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import static org.labkey.query.sql.SqlTokenTypes.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

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


public class QueryUnion
{
    QuerySchema _schema;
    Query _query;
	QUnion _qunion;
	QOrder _qorderBy;
	
    List<Object> _termList = new ArrayList<Object>();
    List<QueryTableInfo> _tinfos = new ArrayList<QueryTableInfo>();

	QueryUnion(Query query, QUnion qunion)
    {
        _query = query;
		_qunion = qunion;
        _schema = query.getSchema();

        collectUnionTerms(qunion);
		_qorderBy = _qunion.getChildOfType(QOrder.class);
    }


    void collectUnionTerms(QUnion qunion)
    {
        for (QNode n : qunion.children())
        {
            assert n instanceof QQuery || n instanceof QUnion || n instanceof QOrder || n instanceof QLimit;

			if (n instanceof QLimit)
			{
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


    void computeTableInfos()
    {
        if (_tinfos.size() > 0)
			return;
		for (Object o : _termList)
		{
			if (o instanceof QuerySelect)
				_tinfos.add(((QuerySelect)o).getTableInfo("U"));
			else
				_tinfos.add(((QueryUnion)o).getTableInfo("U"));
        }
    }


	SQLFragment _unionSql = null;


    public QueryTableInfo getTableInfo(String alias)
    {
        computeTableInfos();
        if (_query.getParseErrors().size() > 0)
            return null;

		String unionOperator = "";
        SQLFragment unionSql = new SQLFragment();

		for (Object term : _termList)
		{
			SQLFragment sql;
			if (term instanceof QuerySelect)
				sql = ((QuerySelect) term).getSelectSql();
			else
				sql = ((QueryUnion) term).getUnionSql();

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
        sti.setName("UNION");
        sti.setAlias(alias);
        sti.setFromSQL(unionSql);
        QueryTableInfo ret = new QueryTableInfo(sti, "UNION", alias);
        for (ColumnInfo col : _tinfos.get(0).getColumns())
        {
            ColumnInfo ucol = new AliasedColumn(ret, col.getName(), col);
            ret.addColumn(ucol);
        }
		_unionSql = unionSql;
        return ret;
    }


	// simplified version of resolve Field
	private QExpr resolveFields(QExpr expr)
	{
		if (expr instanceof QQuery)
		{
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


	SQLFragment getUnionSql()
    {
        if (_unionSql == null)
            getTableInfo("SQL");
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
}
