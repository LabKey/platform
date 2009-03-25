/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.util.*;
import org.labkey.api.settings.AppProps;
import org.labkey.query.design.*;
import static org.labkey.query.sql.antlr.SqlBaseTokenTypes.ON;
import org.labkey.data.xml.ColumnType;
import org.apache.commons.lang.StringUtils;
import static org.apache.commons.lang.StringUtils.defaultString;

import java.util.*;


public class QuerySelect extends QueryRelation
{
    private String _queryText = null;
    private Map<FieldKey, SelectColumn> _columns;

    private QGroupBy _groupBy;
    private QOrder _orderBy;
    private QWhere _where;
    private QWhere _having;
    private QQuery _root;
    private QLimit _limit;
    private QDistinct _distinct;
    private Map<FieldKey, QTable> _from;
    private Map<FieldKey, QueryRelation> _tables;
    private Map<FieldKey, RelationColumn> _declaredFields = new HashMap<FieldKey, RelationColumn>();
    private SQLTableInfo _subqueryTable;
    private AliasManager _subqueryAliasManager;

    private QuerySelect(Query query, QuerySchema schema, String alias)
    {
        super(query, schema, alias == null ? "_select" + query.incrementAliasCounter() : alias);
        _subqueryTable = new SQLTableInfo(_schema.getDbSchema());
        _subqueryAliasManager = new AliasManager(schema.getDbSchema());
        _queryText = query == null ? null : query._querySource;
        assert MemTracker.put(this);
    }


	QuerySelect(Query query, QQuery root)
	{
		this(query, query.getSchema(), null);
        this._query = query;
		this._root = root;
		initializeSelect();
        assert MemTracker.put(this);
	}
	

    private QuerySelect(QuerySelect parent, QQuery query, boolean inFromClause, String alias)
    {
        this(parent._query, parent._schema, alias);
        
        assert inFromClause == (alias != null);
        this._query = parent._query;
        _parent = parent;
        _root = query;
        _inFromClause = inFromClause;
        try
        {
            initializeSelect();
        }
        catch (RuntimeException ex)
        {
            throw Query.wrapRuntimeException(ex, _queryText);
        }
    }


    @Override
    void setQuery(Query query)
    {
        assert getParseErrors().size() == 0;
        super.setQuery(query);
        for (QueryRelation r : _tables.values())
            r.setQuery(query);
    }


    private void initializeSelect()
    {
        _columns = new LinkedHashMap<FieldKey,SelectColumn>();
        _from = new LinkedHashMap<FieldKey, QTable>();
        if (_root == null)
            return;
        _limit = _root.getLimit();
        QSelect select = _root.getSelect();

        _tables = new HashMap<FieldKey, QueryRelation>();
        QFrom from = _root.getFrom();
        if (from == null)
        {
            _from = Collections.EMPTY_MAP;
        }
        else
        {
            _from = new FromParser().parseTables(from);
        }
        _where = _root.getWhere();
        _having = _root.getHaving();
        _groupBy = _root.getGroupBy();
        _orderBy = _root.getOrderBy();
        for (QTable qtable : _from.values())
        {
            FieldKey key = qtable.getTableKey();
            String alias = qtable.getAlias().getName();
            QNode node = qtable.getTable();

            QueryRelation relation;
            if (node instanceof QQuery)
            {
                relation = new QuerySelect(this, (QQuery) node, true, alias);
            }
            else if (node instanceof  QUnion)
            {
                relation = new QueryUnion(this, (QUnion) node, true, alias);
            }
            else
            {
                relation = _query.resolveTable(_schema, node, key, alias);
                assert relation == null || alias.equals(relation.getAlias());
                if (null != relation)
                    relation._parent = this;
            }
            assert relation != null || !getParseErrors().isEmpty();
            if (relation == null)
                continue;
            qtable.setQueryRelation(relation);
            if (!AliasManager.isLegalName(relation.getAlias()))
                relation.setAlias(AliasManager.makeLegalName(relation.getAlias(), getSqlDialect()) + "$" + _query.incrementAliasCounter());
            else
                relation.setAlias(relation.getAlias() + "$" + _query.incrementAliasCounter());
            _tables.put(qtable.getAlias(), relation);
        }

        ArrayList<SelectColumn> columnList = new ArrayList<SelectColumn>();
        if (select != null)
        {
            for (QNode node : _root.getSelect().children())
            {
                if (node instanceof QDistinct)
                {
                    if (!columnList.isEmpty())
                        parseError("DISTINCT not expected", node);
                    else
                        _distinct = (QDistinct)node;
                    continue;
                }

                // look for table.*
                if (node instanceof QFieldKey)
                {
                    FieldKey key = ((QDot)node).getFieldKey();

                    if (null != key && key.getName().equals("*"))
                    {
                        FieldKey parent = key.getParent();
                        if (null == parent)
                        {
                            parseError("SELECT * is not supported", node);
                            continue;
                        }
                        if (parent.getParent() != null)
                        {
                            parseError("Can't resolve column: " + node.getSourceText(), node);
                            continue;
                        }
                        QueryRelation r = _tables.get(parent);
                        if (null == r)
                        {
                            parseError("Can't resolve column: " + node.getSourceText(), node);
                            continue;
                        }
                        for (RelationColumn tableCol :  r.getAllColumns())
                            columnList.add(new SelectColumn(new FieldKey(parent,tableCol.getName())));
                        continue;
                    }
                }

                columnList.add(new SelectColumn(node));
            }
        }
        if (_parent != null && !_inFromClause && columnList.size() != 1)
        {
            parseError("Subquery can have only one column.", _root);
            return;
        }
        for (SelectColumn column : columnList)
        {
            FieldKey key = column._key;
            String alias = column.getAlias();
//            assert key == null || alias == null || key.getName().equals(alias);

            if (alias == null)
            {
                if (_parent != null && !_inFromClause)
                {
                    alias = "~~value~~";
                    key = new FieldKey(null,alias);
                }
                else
                {
                    parseError("Expression column requires an alias", column.getField());
                    continue;
                }
            }
            if (_columns.containsKey(key))
            {
                parseError("Duplicate column '" + key.getName() + "'", column.getField());
                continue;
            }
            _columns.put(key, column);
        }
    }



    private SqlDialect getSqlDialect()
    {
        return getDbSchema().getSqlDialect();
    }


    private DbSchema getDbSchema()
    {
        return _schema.getDbSchema();
    }


    public TableInfo getFromTable(FieldKey key)
    {
        QueryRelation qr = _tables.get(key);
        if (qr != null)
            return qr.getTableInfo();
        return null;
    }


    public String getQueryText()
    {
        if (!getParseErrors().isEmpty() && _columns == null)
        {
            return "ERROR";
        }
        SourceBuilder builder = new SourceBuilder();
        if (null == _distinct)
            builder.pushPrefix("SELECT ");
        else
            builder.pushPrefix("SELECT DISTINCT ");
        for (SelectColumn column : _columns.values())
        {
            column.appendSource(builder);
            builder.nextPrefix(",\n");
        }
        builder.popPrefix();
        builder.pushPrefix("\nFROM ");
        QTable[] tables = _from.values().toArray(new QTable[0]);
        for (int i = 0; i < tables.length; i ++)
        {
            QTable table = tables[i];
            table.appendSource(builder, i == 0);
            builder.nextPrefix("\n");
        }
        builder.popPrefix();
        if (null != _where)
        {
            _where.appendSource(builder);
        }
        if (null != _groupBy)
        {
            _groupBy.appendSource(builder);
        }
        if (null != _having)
        {
            assert null != _groupBy;
            _having.appendSource(builder);
        }
        if (_orderBy != null)
        {
            _orderBy.appendSource(builder);
        }
        if (_limit != null)
        {
            _limit.appendSource(builder);
        }
        return builder.getText();
    }
    

    public Set<FieldKey> getFromTables()
    {
        return _from.keySet();
    }

    private class FromParser
    {
        Map<FieldKey, QTable> _tables;

        private void parseTable(QNode parent)
        {
			List<QNode> l = parent.childList();
			QNode[] children = l.toArray(new QNode[l.size()]);
			int inode = 0;
            QNode node = children[inode];
			
            JoinType joinType = JoinType.root;

            if (parent.getTokenType() == SqlTokenTypes.JOIN)
            {
                joinType = JoinType.inner;
loop:
                for ( ; inode < children.length ; inode++)
                {
					node = children[inode];
                    switch (node.getTokenType())
                    {
                        case SqlTokenTypes.LEFT:
                            joinType = JoinType.left;
                            break;
                        case SqlTokenTypes.RIGHT:
                            joinType = JoinType.right;
                            break;
                        case SqlTokenTypes.INNER:
                            joinType = JoinType.inner;
                            break;
                        case SqlTokenTypes.OUTER:
                            joinType = JoinType.outer;
                            break;
                        case SqlTokenTypes.FULL:
                            joinType = JoinType.full;
                            break;
                        default:
                            break loop;
                    }
                }
            }
            QExpr expr = (QExpr) node;
            QExpr with = null;
            QIdentifier alias = null;
			QNode next = inode+1<children.length ? children[inode+1] : null;
            if (next instanceof QIdentifier)
            {
                alias = (QIdentifier) next;
				inode++;
            }
			next = inode+1<children.length ? children[inode+1] : null;
            if (null != next && next.getTokenType() == ON)
            {
                with = (QExpr) next.getFirstChild();
				inode++;
            }
            QTable table = new QTable(expr, joinType);
            table.setAlias(alias);
            table.setOn(with);
            FieldKey aliasKey = table.getAlias();
            if (_tables.containsKey(aliasKey))
            {
                parseError(aliasKey + " was specified more than once", table.getTable());
            }
            else
            {
                _tables.put(aliasKey, table);
            }
        }

        Map<FieldKey, QTable> parseTables(QFrom from)
        {
            _tables = new LinkedHashMap<FieldKey, QTable>();

            for (QNode node : from.children())
            {
                if (_tables.size() > 0 && node.getTokenType() == SqlTokenTypes.RANGE)
                {
                    parseError("Tables in the parse clause separated by commas are not yet supported.", node);
                    break;
                }
                parseTable(node);
            }
            return _tables;
        }
    }


    private QueryRelation getTable(FieldKey key)
    {
        QueryRelation ret = _tables.get(key);
        return ret;
    }

	
    /**
     * Resolve a particular field.
     * The FieldKey may refer to a particular ColumnInfo.
     * Also, if expr is a child of a QMethodCall, then the FieldKey may refer to a {@link org.labkey.query.sql.Method}
     * or a {@link org.labkey.api.data.TableInfo#getMethod}
     *
     */
    @Override
    protected QField getField(FieldKey key, QNode expr)
    {
        QField ret;
        if (key.getTable() == null)
        {
            ret = new QField(null, key.getName(), expr);
        }
        else
        {
            RelationColumn column = _declaredFields.get(key);
            if (column != null)
            {
                return new QField(column, expr);
            }
            else
            {
                QueryRelation table = getTable(key.getTable());
                if (table != null)
                {
                    return new QField(table, key.getName(), expr);
                }
                return super.getField(key,expr);
            }
        }
        return ret;
    }

	
    /**
     * Indicate that a particular ColumnInfo is used by this query.
     */
    @Override
    protected RelationColumn declareField(FieldKey key)
    {
        RelationColumn colTry = _declaredFields.get(key);
        if (colTry != null)
            return colTry;

        List<String> parts = key.getParts();
        if (parts.size() < 2)
            return null;
        QueryRelation table = getTable(new FieldKey(null, parts.get(0)));
        if (table == null)
        {
            return super.declareField(key);
        }
        key = FieldKey.fromParts(parts.get(0), parts.get(1));
        RelationColumn colParent = _declaredFields.get(key);
        if (colParent == null)
        {
            RelationColumn relColumn = table.getColumn(key.getName());
            if (relColumn == null)
                return null;
            _declaredFields.put(key, relColumn);
            colParent = relColumn;
        }
        for (int i = 2; i < parts.size(); i ++)
        {
            key = new FieldKey(key, parts.get(i));
            RelationColumn nextColumn = _declaredFields.get(key);
            if (nextColumn == null)
            {
                nextColumn = table.getLookupColumn(colParent, key.getName());
                if (nextColumn == null)
                    return null;

                _declaredFields.put(key, nextColumn);
            }
            colParent = nextColumn;
        }
        return colParent;
    }


    private void declareFields(QExpr expr)
    {
        if (expr instanceof QMethodCall)
        {
			assert expr.childList().size() == 1 || expr.childList().size() == 2;
			if (expr.childList().size() == 2)
            	declareFields((QExpr)expr.getLastChild());
            return;
        }
        if (expr instanceof QQuery)
        {
            QuerySelect sub = ((QQuery)expr).getQuerySelect();
            if (sub == null)
            {
                sub = new QuerySelect(this, (QQuery)expr, false, null);
                ((QQuery)expr)._select = sub;
            }
            sub.declareFields();
            return;
        }
        if (expr instanceof QUnion)
        {
            QueryUnion sub = ((QUnion)expr).getQueryUnion();
            if (sub == null)
            {
                sub = new QueryUnion(this, (QUnion)expr, false, null);
                ((QUnion)expr)._union= sub;
            }
            sub.declareFields();
            return;
        }
        if (expr instanceof QRowStar)
        {
            return;
        }
        
        FieldKey key = expr.getFieldKey();
        if (key != null)
        {
            RelationColumn column = declareField(key);
            if (column == null)
            {
                parseError("Unknown field " + key.getDisplayString(), expr);
            }
            return;
        }
        for (QNode child : expr.children())
        {
            declareFields((QExpr)child);
        }
    }


    void declareFields()
    {
        if (null != _columns)
            for (SelectColumn column : _columns.values())
            {
                declareFields(column.getField());
            }
        for (QTable table : _from.values())
        {
            QExpr on = table.getOn();
            if (on != null)
                declareFields(on);
        }
        if (null != _where)
            for (QNode expr : _where.children())
            {
                declareFields((QExpr)expr);
            }
        if (_groupBy != null)
        {
            for (QNode expr : _groupBy.children())
            {
                declareFields((QExpr)expr);
            }
        }
        if (null != _having)
            for (QNode expr : _having.children())
            {
                declareFields((QExpr)expr);
            }
    }


    /*
     * Return the result of replacing field names in the expression with QField objects.
     */
    private QExpr resolveFields(QExpr expr, QNode parent)
    {
        if (expr instanceof QQuery)
        {
            QuerySelect subquery = new QuerySelect(this, (QQuery) expr, false, null);
            return new QQuery(subquery);
        }
        if (expr instanceof QRowStar)
        {
            return expr;
        }

        FieldKey key = expr.getFieldKey();
        if (key != null)
        {
            QField ret = getField(key, expr);
            QueryParseException error = ret.fieldCheck(parent);
            if (error != null)
                getParseErrors().add(error);
            return ret;
        }

        QExpr ret = (QExpr) expr.clone();
		for (QNode child : expr.children())
            ret.appendChild(resolveFields((QExpr)child, expr));

        QueryParseException error = ret.fieldCheck(parent);
        if (error != null)
            getParseErrors().add(error);
        return ret;
    }


    public List<QueryParseException> getParseErrors()
    {
        return _query.getParseErrors();
    }


    public boolean isAggregate()
    {
        if (_groupBy != null)
        {
            return true;
        }
        if (null != _columns)
        {
            for (SelectColumn column : _columns.values())
            {
                if (column.getField().isAggregate())
                    return true;
            }
        }
        return false;
    }


    public boolean hasSubSelect()
    {
        if (_columns == null)
            return false;

        for (SelectColumn column : _columns.values())
        {
            QNode field = column.getField();
            if (field instanceof QQuery)
                return true;
        }
        return false;
    }


    /**
     * Construct a table info which represents this query.
     *
     */
    public QueryTableInfo getTableInfo()
    {
        SQLFragment sql = _getSql(true);
        if (null == sql)
            return null;

        QueryTableInfo ret = new QueryTableInfo(_subqueryTable, "_select");
        for (SelectColumn col : _columns.values())
        {
            ColumnInfo aliasedColumn = new RelationColumnInfo(ret, col.getAlias(), col);
            if (col.getAlias() != null)
            {
                aliasedColumn.setCaption(ColumnInfo.captionFromName(col.getAlias()));
            }

            aliasedColumn.setName(col.getAlias());
            ret.addColumn(aliasedColumn);
        }
        _subqueryTable.setFromSQL(sql);
        assert MemTracker.put(ret);
        return ret;
    }


    public SQLFragment getSql()
    {
        return _getSql(false);
    }


    public SQLFragment _getSql(boolean selectAll)
    {
        if (getParseErrors().size() != 0)
            return null;
        
        _subqueryTable.setName("_select");
        declareFields();
        if (getParseErrors().size() != 0)
            return null;

        SqlBuilder sql = new SqlBuilder(_schema.getDbSchema());

        if (null == _distinct)
        {
            sql.pushPrefix("SELECT ");
            selectAll = true;
        }
        else
        {
            sql.pushPrefix("SELECT DISTINCT ");
        }

        int count = 0;
        for (SelectColumn col : _columns.values())
            if (col._selected)
                count++;
        if (count == 0)
            selectAll = true;

        SqlDialect dialect = getSqlDialect();
        for (SelectColumn col : _columns.values())
        {
            if (!selectAll && !col._selected)
                continue;
            String alias = col.getAlias();
            assert null != alias;
            if (alias == null)
            {
                FieldKey key = col.getField().getFieldKey();
                if (key == null)
                {
                    parseError("Column requires alias", col.getField());
                    return null;
                }
                alias = key.getName();
            }
            sql.append(col.getInternalSql());
            sql.append(" AS ");
            sql.append(dialect.getColumnSelectName(alias));
            sql.nextPrefix(",\n");
            count++;
        }
        if (getParseErrors().size() != 0)
            return null;

        sql.popPrefix();
        sql.pushPrefix("\nFROM ");
        for (QTable qt : _from.values())
        {
            switch (qt.getJoinType())
            {
                case inner:
                    sql.append("\nINNER JOIN ");
                    break;
                case outer:
                    sql.append("\nOUTER JOIN ");
                    break;
                case left:
                    sql.append("\nLEFT JOIN ");
                    break;
                case right:
                    sql.append("\nRIGHT JOIN ");
                    break;
                case full:
                    sql.append("\nFULL JOIN ");
                    break;
            }
            SQLFragment sqlRelation = qt.getQueryRelation().getSql();
            assert sqlRelation != null || getParseErrors().size() > 0;
            if (sqlRelation == null)
                return null;
            sql.append("(").append(sqlRelation).append(") ");
            sql.append(qt.getQueryRelation().getAlias());

            if (qt.getJoinType() != JoinType.root)
            {
                sql.append(" ON ");
                if (qt.getOn() == null)
                {
                    sql.append("1 = 1");
                }
                else
                {
                    resolveFields(qt.getOn(), null).appendSql(sql);
                }
            }
        }
        if (_where != null)
        {
            sql.pushPrefix("\nWHERE ");
            for (QNode expr : _where.children())
            {
                sql.append("(");
                QExpr term = resolveFields((QExpr)expr, null);
                term.appendSql(sql);
                sql.append(")");
                sql.nextPrefix(" AND ");
            }
            sql.popPrefix();
        }
        if (_groupBy != null)
        {
            sql.pushPrefix("\nGROUP BY ");
            for (QNode expr : _groupBy.children())
            {
                sql.append("(");
                resolveFields((QExpr)expr, _groupBy).appendSql(sql);
                sql.append(")");
                sql.nextPrefix(",");
            }
            sql.popPrefix();
        }
        if (_having != null)
        {
            sql.pushPrefix("\nHAVING ");
            for (QNode expr : _having.children())
            {
                sql.append("(");
                QExpr term = resolveFields((QExpr)expr, null);
                term.appendSql(sql);
                sql.append(")");
                sql.nextPrefix(" AND ");
            }
            sql.popPrefix();
        }
        if (_orderBy != null)
        {
            if (_limit == null && !getSqlDialect().allowSortOnSubqueryWithoutLimit())
            {
                parseError("ORDER BY in a LabKey SQL query in this database is not supported unless LIMIT is also specified.  You can set the sort using a custom grid view.", _orderBy);
                return null;
            }
            sql.pushPrefix("\nORDER BY ");
            for (Map.Entry<QExpr, Boolean> entry : _orderBy.getSort())
            {
                QExpr expr = resolveFields(entry.getKey(), _orderBy);
                expr.appendSql(sql);
                if (!entry.getValue().booleanValue())
                {
                    sql.append(" DESC");
                }
                sql.nextPrefix(",");
            }
            sql.popPrefix();
        }
        if (_limit != null)
        {
            getSqlDialect().limitRows(sql, _limit.getLimit());
        }
        if (!getParseErrors().isEmpty())
            return null;

        SQLFragment ret = sql;

        if (AppProps.getInstance().isDevMode())
        {
            ret = new SQLFragment();
            String comment = "<QuerySelect@" + System.identityHashCode(this);
            if (!StringUtils.isEmpty(_savedName))
                comment += " name='" + StringUtils.trimToEmpty(_savedName) + "'";
            comment += ">";
            ret.appendComment(comment);
            if (null != _queryText)
            {
                for (String s : _queryText.split("\n"))
                    if (null != StringUtils.trimToNull(s))
                        ret.append("--|         ").append(s).append("\n");
            }
            ret.append(sql);
            ret.appendComment("</QuerySelect@" + System.identityHashCode(this) + ">");
        }
        return ret;
    }


	private void parseError(String message, QNode node)
	{
		Query.parseError(getParseErrors(), message, node);
	}

	

    public QueryDocument getDesignDocument()
    {
        QueryDocument ret = QueryDocument.Factory.newInstance();
        ret.addNewQuery();
        DgQuery.Select select = ret.getQuery().addNewSelect();
        DgQuery.Where dgWhere = ret.getQuery().addNewWhere();
        DgQuery.OrderBy dgOrderBy = ret.getQuery().addNewOrderBy();
        if (_columns == null)
        {
            return null;
        }
        for (SelectColumn column : _columns.values())
        {
            DgColumn dgColumn = select.addNewColumn();
            if (column.getAlias() != null)
            {
                dgColumn.setAlias(column.getAlias());
            }
            DgValue dgValue = dgColumn.addNewValue();
            QExpr value = column.getField();
            FieldKey key = value.getFieldKey();
            if (key != null)
            {
                dgValue.addNewField().setStringValue(key.toString());
            }
            else
            {
                dgValue.setSql(value.getSourceText());
            }
        }
        if (dgWhere != null)
        {
            if (_where == null)
            {
                _where = new QWhere();
            }
            _where.fillWhere(dgWhere);
        }
        if (_orderBy != null)
        {
            for (Map.Entry<QExpr, Boolean> entry : _orderBy.getSort())
            {
                DgOrderByString dgClause;
                FieldKey field = entry.getKey().getFieldKey();
                if (field != null)
                {
                    dgClause = dgOrderBy.addNewField();
                    dgClause.setStringValue(field.toString());
                }
                else
                {
                    dgClause = dgOrderBy.addNewSql();
                    dgClause.setStringValue(entry.getKey().getSourceText());
                }
                if (entry.getValue().booleanValue())
                {
                    dgClause.setDir("ASC");
                }
                else
                {
                    dgClause.setDir("DESC");
                }
            }
        }
        assert MemTracker.put(ret);
        return ret;
    }


    public void update(DgQuery query, List<QueryException> errors)
    {
        _columns.clear();
        for (DgColumn dgColumn : query.getSelect().getColumnArray())
        {
            QExpr field;
            DgValue value = dgColumn.getValue();
            if (value.isSetField())
            {
                field = QIdentifier.of(FieldKey.fromString(value.getField().getStringValue()));
            }
            else if (value.isSetSql())
            {
                field = (new SqlParser()).parseExpr(value.getSql(), errors);
            }
            else
            {
                continue;
            }
            SelectColumn column = new SelectColumn(field);
            if (dgColumn.isSetAlias())
            {
                column.setAlias(dgColumn.getAlias());
            }
            _columns.put(new FieldKey(null, column.getAlias()), column);
        }
        if (query.getWhere() != null)
        {
            if (_where == null)
            {
                _where = new QWhere();
            }
            _where.updateWhere(query.getWhere(), errors);
        }
        if (query.getOrderBy() != null)
        {
            if (_orderBy == null)
            {
                _orderBy = new QOrder();
            }
            _orderBy.removeChildren();
            for (DgOrderByString field : query.getOrderBy().getFieldArray())
            {
                _orderBy.addOrderByClause(QIdentifier.of(FieldKey.fromString(field.getStringValue())), !"DESC".equals(field.getDir()));
            }
            for (DgOrderByString expr : query.getOrderBy().getSqlArray())
            {
                _orderBy.addOrderByClause((new SqlParser()).parseExpr(expr.getStringValue(), errors), !"DESC".equals(expr.getDir()));
            }
        }
    }


    SelectColumn getColumn(String name)
    {
        FieldKey key = new FieldKey(null,name);
        SelectColumn col = _columns.get(key);
        if (col != null)
            col._selected = true;
        return col;
    }


    protected List<RelationColumn> getAllColumns()
    {
        return new ArrayList<RelationColumn>(_columns.values());
    }


    SelectColumn getLookupColumn(RelationColumn parentRelCol, String name)
    {
        assert parentRelCol instanceof SelectColumn;
        assert parentRelCol.getTable() == QuerySelect.this;

        if (null != QuerySelect.this._distinct || QuerySelect.this.isAggregate())
            return null;

        SelectColumn parent = (SelectColumn)parentRelCol;

        FieldKey key = new FieldKey(parent._key, name);
        SelectColumn c = _columns.get(key);
        if (null != c)
            return c;
        
        QExpr qexpr = parent.getResolvedField();
        if (!(qexpr instanceof QField))
            return null;

        RelationColumn fromParentColumn = ((QField)qexpr).getRelationColumn();
        if (fromParentColumn == null)
            return null;
        RelationColumn fromLookupColumn = fromParentColumn.getTable().getLookupColumn(fromParentColumn, name);
        if (fromLookupColumn == null)
            return null;
        SelectColumn sc = new SelectColumn(new QField(fromLookupColumn, null));
        sc._key = key;
        sc._selected = true;
        _columns.put(key, sc);
        return sc;
    }


    RelationColumn getLookupColumn(RelationColumn parentRelCol, ColumnType.Fk fk, String name)
    {
        assert parentRelCol instanceof SelectColumn;
        assert parentRelCol.getTable() == QuerySelect.this;

        if (null != QuerySelect.this._distinct || QuerySelect.this.isAggregate())
            return null;

        SelectColumn parent = (SelectColumn)parentRelCol;

        FieldKey key = new FieldKey(parent._key, name);
        SelectColumn c = _columns.get(key);
        if (null != c)
            return c;

        QExpr qexpr = parent.getResolvedField();
        if (!(qexpr instanceof QField))
            return null;

        RelationColumn fromParentColumn = ((QField)qexpr).getRelationColumn();
        if (fromParentColumn == null)
            return null;
        RelationColumn fromLookupColumn = fromParentColumn.getTable().getLookupColumn(fromParentColumn, fk, name);
        if (fromLookupColumn == null)
            return null;
        SelectColumn col = new SelectColumn(new QField(fromLookupColumn, null));
        col._key = key;
        col._selected = true;
        _columns.put(key, col);
        return col;
    }


    public class SelectColumn extends RelationColumn
    {
        FieldKey _key;
        boolean _selected = false;
        QNode _node;
        QExpr _field;
        QExpr _resolved;
        QIdentifier _alias;
        ColumnType _metadata;

        ColumnInfo _colinfo = null;


        public SelectColumn(FieldKey fk)
        {
            _field = QFieldKey.of(fk);
            _alias = new QIdentifier(fk.getName());
            _key = fk;
        }
        
        public SelectColumn(QNode node)
        {
            _node = node;
            if (node instanceof QAs)
            {
                _field = ((QAs) node).getExpression();
                FieldKey fk = _field.getFieldKey();
                if (null != fk && fk.getName().equals("*"))
                    parseError("* expression can not be aliased", node);
                _alias = ((QAs) node).getAlias();
            }
            else
            {
                _field = (QExpr) node;
                FieldKey fk = _field.getFieldKey();
                if (null != fk)
                    _alias = new QIdentifier(fk.getName());
            }
            if (null != _alias)
                _key = new FieldKey(null, _alias.getValueString());
        }

        public SelectColumn(QExpr expr)
        {
            _field = expr;
            _key = _field.getFieldKey();
            if (null != _key)
                _alias = new QIdentifier(_key.getName());
        }

        public SelectColumn(QField field)
        {
            _field = field;
            _resolved = field;
            _alias = new QIdentifier(field.getName());
        }

        SQLFragment getInternalSql()
        {
            SqlBuilder b = new SqlBuilder(getDbSchema());
            QExpr expr = getResolvedField();
            expr.appendSql(b);
            return b;
        }

        public String getName()
        {
            return _alias.getIdentifier();
        }

        QueryRelation getTable()
        {
            return QuerySelect.this;
        }

        public int getSqlTypeInt()
        {
            return 0;
        }

        ColumnType getColumnType()
        {
            if (null != _metadata)
                return _metadata;
            String alias = getAlias();
            // UNDONE: make ForeignKeys work
            return null;
        }

        ColumnInfo getColumnInfo()
        {
            if (_colinfo == null)
            {
                QExpr expr = getResolvedField();
                _colinfo = expr.createColumnInfo(_subqueryTable, _subqueryAliasManager.decideAlias(getAlias()));
            }
            return _colinfo;
        }

        void copyColumnAttributesTo(ColumnInfo to)
        {
            to.copyAttributesFrom(getColumnInfo());
        }

        public String getAlias()
        {
            if (_alias == null)
                return null;
            return _alias.getIdentifier();
        }

        public void appendSource(SourceBuilder builder)
        {
            _field.appendSource(builder);
            if (_alias != null)
            {
                builder.append(" AS ");
                _alias.appendSource(builder);
            }
        }

       public void setAlias(String alias)
        {
            alias = StringUtils.trimToNull(alias);
            if (alias == null)
            {
                _alias = null;
            }
            else
            {
                _alias = new QIdentifier(alias);
            }
        }

        public QExpr getField()
        {
            return _field;
        }

        public QExpr getResolvedField()
        {
            if (null == _resolved)
                _resolved = resolveFields(getField(), null);
            return _resolved;
        }
    }
}
