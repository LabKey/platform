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
import org.labkey.query.design.*;
import static org.labkey.query.sql.antlr.SqlBaseTokenTypes.ON;

import java.util.*;


public class QuerySelect
{
    private String _queryText = null;
    private List<QColumn> _columns;
    private QuerySchema _schema;

    private QuerySelect _parent;
    private boolean _inFromClause;
    private QGroupBy _groupBy;
    private QOrder _orderBy;
    private QWhere _filter;
    private QQuery _root;
    private QLimit _limit;
    private QDistinct _distinct;
    private Map<FieldKey, QTable> _from;
    private Map<FieldKey, TableInfo> _tables;
    private Map<FieldKey, ColumnInfo> _declaredFields = new HashMap<FieldKey, ColumnInfo>();
    private SQLTableInfo _subqueryTable;
    private List<QueryParseException> _parseErrors;
    private AliasManager _subqueryAliasManager;
    private AliasManager _queryAliasManager;


    private QuerySelect(QuerySchema schema)
    {
        _schema = schema;
        _subqueryAliasManager = new AliasManager(schema.getDbSchema());
        _queryAliasManager = new AliasManager(schema.getDbSchema());
        assert MemTracker.put(this);
    }


	QuerySelect(Query query, QQuery root)
	{
		this(query.getSchema());
		this._root = root;
		this._parseErrors = query.getParseErrors();
		parseTree();
	}
	

    private QuerySelect(QuerySelect parent, QQuery query, boolean inFromClause)
    {
        this(parent._schema);
        _queryText = query == null ? null : query.getQuery() == null ? null : query.getQuery()._queryText;
        _parent = parent;
        _root = query;
        _inFromClause = inFromClause;
        _parseErrors = parent.getParseErrors();
        try
        {
            parseTree();
        }
        catch (RuntimeException ex)
        {
            throw Query.wrapRuntimeException(ex, _queryText);
        }
        assert MemTracker.put(this);
    }


    private int getNestingLevel()
    {
        if (_parent == null)
            return 0;
        return _parent.getNestingLevel() + 1;
    }

    
    private void parseTree()
    {
        _columns = new ArrayList<QColumn>();
        _from = new LinkedHashMap<FieldKey, QTable>();
        _filter = new QWhere();
        if (_root == null)
            return;
        _limit = _root.getLimit();
        QSelect select = _root.getSelect();
        if (select != null)
        {
            boolean first = true;
            for (QNode node : _root.getSelect().children())
            {
                if (node instanceof QDistinct)
                {
                    if (!first)
                        parseError("DISTINCT not expected", node);
                    else
                        _distinct = (QDistinct)node;
                    continue;
                }
                _columns.add(new QColumn(node));
                first = false;
            }
        }
        _tables = new HashMap<FieldKey, TableInfo>();
        QFrom from = _root.getFrom();
        if (from == null)
        {
            _from = Collections.EMPTY_MAP;
        }
        else
        {
            _from = new FromParser().parseTables(from);
        }
        if (_root.getWhere() != null)
        {
            _filter = _root.getWhere();
        }
        _groupBy = _root.getGroupBy();
        _orderBy = _root.getOrderBy();
        for (QTable qtable : _from.values())
        {
            TableInfo table;
            int nestingLevel = getNestingLevel();
            String tableName;
            if (nestingLevel == 0)
            {
                tableName = "Table" + _tables.size();
            }
            else
            {
                tableName = "Table" + nestingLevel + "_" + _tables.size();
            }
            if (qtable.getTable() instanceof QQuery)
            {
                QuerySelect query = new QuerySelect(this, (QQuery) qtable.getTable(), true);
                table = query.getTableInfo(tableName);
            }
            else
            {
                table = Query.resolveTable(_schema, _parseErrors ,qtable.getTable(), qtable.getTableKey(), tableName);
            }
            qtable.setTableObject(table);
            _tables.put(qtable.getAlias(), table);
        }

        Map<String, QColumn> columnMap = new CaseInsensitiveHashMap<QColumn>();
        if (_parent != null && !_inFromClause)
        {
            if (_columns.size() != 1)
            {
                parseError("Subquery can have only one column.", _root);
            }
        }
        for (QColumn column : _columns)
        {
            String alias = column.getAlias();
            if (alias == null)
            {
                FieldKey key = column.getField().getFieldKey();
                if (key == null)
                {
                    if (_parent != null && !_inFromClause)
                    {
                        alias = "~~value~~";
                    }
                    else
                    {
                        parseError("Expression column requires an alias", column.getField());
                        continue;
                    }
                }
                else
                {
                    alias = key.getName();
                }
            }
            if (columnMap.containsKey(alias))
            {
                parseError("Duplicate column '" + alias + "'", column.getField());
                continue;
            }
            columnMap.put(alias, column);
        }
    }




    private SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    private DbSchema getSchema()
    {
        return _schema.getDbSchema();
    }

    /**
     * Get the list of columns in the SELECT clause.
     * @return
     */
    private List<QColumn> getColumns()
    {
        return Collections.unmodifiableList(_columns);
    }

    public TableInfo getFromTable(FieldKey key)
    {
        return _tables.get(key);
    }


    public String getQueryText()
    {
        if (!_parseErrors.isEmpty() && _columns == null)
        {
            return "ERROR";
        }
        SourceBuilder builder = new SourceBuilder();
        if (null == _distinct)
            builder.pushPrefix("SELECT ");
        else
            builder.pushPrefix("SELECT DISTINCT ");
        for (QColumn column : _columns)
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
        _filter.appendSource(builder);
        if (_groupBy != null)
        {
            _groupBy.appendSource(builder);
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

    private TableInfo getTable(FieldKey key)
    {
        TableInfo ret = _tables.get(key);
        if (ret != null)
            return ret;
        return null;
    }

	
    /**
     * Resolve a particular field.
     * The FieldKey may refer to a particular ColumnInfo.
     * Also, if expr is a child of a QMethodCall, then the FieldKey may refer to a {@link org.labkey.query.sql.Method}
     * or a {@link org.labkey.api.data.TableInfo#getMethod}
     *
     */
    private QField getField(FieldKey key, QNode expr)
    {
        QField ret;
        if (key.getTable() == null)
        {
            ret = new QField(null, key.getName(), expr);
        }
        else
        {
            ColumnInfo column;
            column = _declaredFields.get(key);
            if (column != null)
            {
                return new QField(column, expr);
            }
            else
            {
                TableInfo table = getTable(key.getTable());
                if (table != null)
                {
                    return new QField(table, key.getName(), expr);
                }
                if (_parent != null)
                {
                    return _parent.getField(key, expr);
                }
                return new QField(null, key.getName(), expr);
            }
        }
        return ret;
    }

	
    /**
     * Indicate that a particular ColumnInfo is used by this query.
     */
    private ColumnInfo declareField(FieldKey key)
    {
        ColumnInfo colTry = _declaredFields.get(key);
        if (colTry != null)
            return colTry;

        List<String> parts = key.getParts();
        if (parts.size() < 2)
            return null;
        TableInfo table = getTable(new FieldKey(null, parts.get(0)));
        if (table == null)
        {
            if (_parent != null && !_inFromClause)
                return _parent.declareField(key);
            return null;
        }
        key = FieldKey.fromParts(parts.get(0), parts.get(1));
        ColumnInfo colParent = _declaredFields.get(key);
        if (colParent == null)
        {
            ColumnInfo realColumn = table.getColumn(key.getName());
            if (realColumn == null)
                return null;
            colParent = new ExprColumn(realColumn.getParentTable(), key.toString(), realColumn.getValueSql(), realColumn.getSqlTypeInt());
            colParent.copyAttributesFrom(realColumn);
            String alias = _subqueryAliasManager.decideAlias(key.toString());
            AliasedColumn aliasedColumn = new AliasedColumn(_subqueryTable, colParent.getName(), colParent);
            aliasedColumn.setAlias(alias);
            _subqueryTable.addColumn(aliasedColumn);
            colParent.setAlias(aliasedColumn.getAlias());
            if (_from.size() == 1 && key.getTable().equals(_from.keySet().iterator().next()))
            {
                if (realColumn.isKeyField())
                {
                    colParent.setKeyField(true);
                }
            }
            _declaredFields.put(key, colParent);
        }
        for (int i = 2; i < parts.size(); i ++)
        {
            key = new FieldKey(key, parts.get(i));
            ColumnInfo nextColumn = _declaredFields.get(key);
            if (nextColumn == null)
            {
                ForeignKey fk = colParent.getFk();
                if (fk == null)
                    return null;

                nextColumn = fk.createLookupColumn(colParent, key.getName());
                if (nextColumn == null)
                    return null;
                _declaredFields.put(key, nextColumn);
                String alias = _subqueryAliasManager.decideAlias(key.toString());
                AliasedColumn aliasedColumn = new AliasedColumn(_subqueryTable, key.toString(), nextColumn);
                aliasedColumn.setAlias(alias);
                _subqueryTable.addColumn(aliasedColumn);
                nextColumn.setAlias(aliasedColumn.getAlias());
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
            SqlBuilder discard = new SqlBuilder(getSchema());
            QuerySelect subquery = new QuerySelect(this, (QQuery) expr, false);
            new QQuery(subquery).appendSql(discard);
            return;
        }
        FieldKey key = expr.getFieldKey();
        if (key != null)
        {
            ColumnInfo column = declareField(key);
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


    private void declareFields()
    {
        if (null != _columns)
            for (QColumn column : _columns)
            {
                declareFields(column.getField());
            }
        for (QTable table : _from.values())
        {
            QExpr on = table.getOn();
            if (on != null)
                declareFields(on);
        }
        for (QNode expr : _filter.children())
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
    }


    /**
     * Return the result of replacing field names in the expression with QField objects.
     */
    private QExpr resolveFields(QExpr expr, QNode parent)
    {
        if (expr instanceof QQuery)
        {
            QuerySelect subquery = new QuerySelect(this, (QQuery) expr, false);
            return new QQuery(subquery);
        }
        FieldKey key = expr.getFieldKey();
        if (key != null)
        {
            QField ret = getField(key, expr);
            QueryParseException error = ret.fieldCheck(parent);
            if (error != null)
            {
                _parseErrors.add(error);
            }
            return ret;
        }

        QExpr ret = (QExpr) expr.clone();
		for (QNode child : expr.children())
            ret.appendChild(resolveFields((QExpr)child, expr));

        QueryParseException error = ret.fieldCheck(parent);
        if (error != null)
            _parseErrors.add(error);
        return ret;
    }


    public List<QueryParseException> getParseErrors()
    {
        return _parseErrors;
    }


    public boolean isAggregate()
    {
        if (_groupBy != null)
        {
            return true;
        }
        if (null != _columns)
        {
            for (QColumn column : _columns)
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

        for (QColumn column : _columns)
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
    public QueryTableInfo getTableInfo(String tableAlias)
    {
        try
        {
            return _getTableInfo(tableAlias);
        }
        catch (RuntimeException x)
        {
            throw Query.wrapRuntimeException(x, _queryText);
        }
    }


    private QueryTableInfo _getTableInfo(String tableAlias)
    {
        if (_parseErrors.size() != 0)
            return null;
        _subqueryTable = new SQLTableInfo(_schema.getDbSchema());
        _subqueryTable.setName(tableAlias);
        _subqueryTable.setAlias(tableAlias);
        declareFields();
        if (_parseErrors.size() != 0)
            return null;
        Map<TableInfo, Map<String, SQLFragment>> joins = new HashMap<TableInfo, Map<String, SQLFragment>>();

        for (ColumnInfo col : _declaredFields.values())
        {
            Map<String, SQLFragment> map = joins.get(col.getParentTable());
            if (map == null)
            {
                map = new LinkedHashMap<String, SQLFragment>();
                joins.put(col.getParentTable(), map);
            }
            col.declareJoins(map);
        }

        SqlBuilder sql = new SqlBuilder(_schema.getDbSchema());
        if (null == _distinct)
            sql.pushPrefix("SELECT ");
        else
            sql.pushPrefix("SELECT DISTINCT ");

        QueryTableInfo ret = new QueryTableInfo(_subqueryTable, tableAlias, tableAlias);
        for (QColumn col : _columns)
        {
            String name = col.getAlias();
            boolean isKeyColumn = false;
            if (name == null)
            {
                FieldKey key = col.getField().getFieldKey();
                if (key == null)
                {
                    parseError("Column requires alias", col.getField());
                    return null;
                }
                name = key.getName();
            }

            QExpr expr = resolveFields(col.getField(), null);
            if (expr instanceof QField)
            {
                if (getFromTables().size() == 1)
                {
                    if (((QField) expr).getColumnInfo().isKeyField())
                    {
                        isKeyColumn = true;
                    }
                }
            }
            ColumnInfo subqueryColumn = expr.createColumnInfo(_subqueryTable, _subqueryAliasManager.decideAlias(name));
            ColumnInfo aliasedColumn = new AliasedColumn(ret, name, subqueryColumn);
            if (col.getAlias() != null)
            {
                aliasedColumn.setCaption(ColumnInfo.captionFromName(col.getAlias()));
            }
            else
            {
                aliasedColumn.setCaption(subqueryColumn.getCaption());
            }
            if (isKeyColumn)
            {
                // If there is only one table in the select list, and the column was a key column, then make this column
                // a key column.  This is to enable someone writing their own query for a built in table, and having it
                // behave the same as the original table.

                // I'm not sure if anyone uses this functionality.
                aliasedColumn.setKeyField(true);
            }

            String alias = _queryAliasManager.decideAlias(name);
            aliasedColumn.setAlias(alias);
            ret.addColumn(aliasedColumn);
            sql.append(subqueryColumn.getSelectSql());
            sql.nextPrefix(",\n");
        }


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
            sql.append(qt.getTableObject().getFromSQL());
            Map<String, SQLFragment> map = joins.get(qt.getTableObject());
            if (map != null)
            {
                for (SQLFragment join : map.values())
                {
                    sql.append("\n");
                    sql.append(join);
                }
            }
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
        if (_filter != null)
        {
            sql.pushPrefix("\nWHERE ");
            for (QNode expr : _filter.children())
            {
                sql.append("(");
                resolveFields((QExpr)expr, null).appendSql(sql);
                sql.append(")");
                sql.nextPrefix("\nAND ");
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
        _subqueryTable.setFromSQL(sql);
        if (!_parseErrors.isEmpty())
            return null;

        assert MemTracker.put(ret);
        _selectSql = sql;
        return ret;
    }


    SQLFragment _selectSql = null;
    
    SQLFragment getSelectSql()
    {
        if (_selectSql == null)
            getTableInfo("SQL");
        return _selectSql;
    }
    

	private void parseError(String message, QNode node)
	{
		Query.parseError(_parseErrors, message, node);
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
        for (QColumn column : getColumns())
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
            if (_filter == null)
            {
                _filter = new QWhere();
            }
            _filter.fillWhere(dgWhere);
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
            QColumn column = new QColumn(field);
            if (dgColumn.isSetAlias())
            {
                column.setAlias(dgColumn.getAlias());
            }
            _columns.add(column);
        }
        if (query.getWhere() != null)
        {
            if (_filter == null)
            {
                _filter = new QWhere();
            }
            _filter.updateWhere(query.getWhere(), errors);
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

}
