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

package org.labkey.query.data;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.util.*;
import org.labkey.common.tools.DataLoader;
import org.labkey.query.design.*;
import org.labkey.query.sql.*;
import static org.labkey.query.sql.antlr.SqlBaseTokenTypes.ON;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.ResultSetMetaData;
import java.util.*;


public class Query
{
    private static Logger _log = Logger.getLogger(Query.class);
    private String _queryText = null;
    private List<QColumn> _columns;
    private QuerySchema _schema;

    private Query _parent;
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
    private List<QueryParseException> _parseErrors = new ArrayList<QueryParseException>();
    private AliasManager _subqueryAliasManager;
    private AliasManager _queryAliasManager;


    public Query(QuerySchema schema)
    {
        _schema = schema;
        _subqueryAliasManager = new AliasManager(schema.getDbSchema());
        _queryAliasManager = new AliasManager(schema.getDbSchema());
        assert MemTracker.put(this);
    }


    private Query(Query parent, QQuery query, boolean inFromClause)
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
            throw wrapRuntimeException(ex);
        }
        assert MemTracker.put(this);
    }


    public void parse(String queryText)
    {
        _queryText = queryText;
        _parseErrors = new ArrayList<QueryParseException>();

        try
        {
            _root = (new SqlParser()).parseStatement(queryText, _parseErrors);
            if (_parseErrors.isEmpty())
                parseTree();

            if (null != _root && _log.isDebugEnabled())
            {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                _root.dump(pw);
                pw.close();
                _log.debug(sw.toString());
            }
        }
        catch (RuntimeException ex)
        {
            throw wrapRuntimeException(ex);
        }
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
                Query query = new Query(this, (QQuery) qtable.getTable(), true);
                table = query.getTableInfo(tableName);
            }
            else
            {
                table = resolveTable(qtable.getTable(), qtable.getTableKey(), tableName);
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

    /**
     * When the user has chosen to create a new query based on a particular table, create a new QueryDef which
     * selects all of the non-hidden columns from that table.
     */
    public void setRootTable(FieldKey key)
    {
        SourceBuilder builder = new SourceBuilder();
        builder.append("SELECT ");
        builder.pushPrefix("");
        TableInfo table = resolveTable(null, key, key.getName());
        if (table == null)
        {
            builder.append("'Table not found' AS message");
        }
        else
        {
            for (FieldKey field : table.getDefaultVisibleColumns())
            {
                if (field.getParent() != null)
                    continue;
                List<String> parts = new ArrayList<String>();
                parts.add(key.getName());
                parts.addAll(field.getParts());
                QFieldKey qfield = QFieldKey.of(FieldKey.fromParts(parts));
                qfield.appendSource(builder);
                builder.nextPrefix(",");
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

    private void parseError(String message, QNode node)
    {
        int line = 0;
        int column = 0;
        if (node != null)
        {
            line = node.getLine();
            column = node.getColumn();
        }
		//noinspection ThrowableInstanceNeverThrown
		_parseErrors.add(new QueryParseException(message, null, line, column));
    }

    /**
     * Resolve a particular table name.  The table name may have schema names (folder.schema.table etc.) prepended to it.
     */
    private TableInfo resolveTable(QNode node, FieldKey key, String alias)
    {
        QuerySchema schema = _schema;
        List<String> parts = key.getParts();
        for (int i = 0; i < parts.size() - 1; i ++)
        {
            schema = schema.getSchema(parts.get(i));
            if (schema == null)
            {
                parseError("Table " + key + " not found.", node);
                return null;
            }
        }

        TableInfo ret = schema.getTable(key.getName(), alias);
        if (ret == null)
        {
            parseError("Table " + key + " not found.", node);
            return null;
        }

        return ret;
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public DbSchema getSchema()
    {
        return _schema.getDbSchema();
    }

    /**
     * Get the list of columns in the SELECT clause.
     * @return
     */
    public List<QColumn> getColumns()
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

    public QQuery getRoot()
    {
        return _root;
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
            if (_parent != null)
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
            Query subquery = new Query(this, (QQuery) expr, false);
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
            Query subquery = new Query(this, (QQuery) expr, false);
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


    public List<QNode> getFilter()
    {
        return _filter.childList();
    }


    public List<QueryParseException> getParseErrors()
    {
        return _parseErrors;
    }


    public boolean hasErrors()
    {
        return !_parseErrors.isEmpty();
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
            throw wrapRuntimeException(x);
        }
    }

    public QueryTableInfo _getTableInfo(String tableAlias)
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
        sql.insert(0, "(");
        sql.append(")");
        _subqueryTable.setFromSQL(sql);
        if (!_parseErrors.isEmpty())
            return null;

        assert MemTracker.put(ret);
        return ret;
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


    QueryInternalException wrapRuntimeException(RuntimeException ex)
    {
        if (ex instanceof QueryInternalException)
            return (QueryInternalException)ex;
        else
            return new QueryInternalException(ex);
    }

    
    public class QueryInternalException extends RuntimeException
    {
        QueryInternalException(RuntimeException cause)
        {
            super("Internal error while parsing \""+ _queryText + "\"", cause);
        }
    }



    public static class TestDataLoader extends DataLoader
    {
        static final String[] COLUMNS = new String[] {"d", "seven", "twelve", "day", "month", "date", "duration", "guid"};
        static final String[] TYPES = new String[] {"double", "int", "int", "string", "string", "date", "string", "string"};
        static final String[] days = new String[] {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        static final String[] months = new String[] {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

        String[][] data;
        ArrayListMap<String,String> templateRow = new ArrayListMap<String,String>();


		// UNDONE: need some NULLS in here
		TestDataLoader(String propertyPrefix, int len)
        {
            data = new String[len+1][];
            data[0] = COLUMNS;
            for (String c : data[0])
                templateRow.put(propertyPrefix + "#" + c, c);
            for (int i=1 ; i<=len ; i++)
            {
                String[] row = data[i] = new String[8];
                int c = 0;
                row[c++] = "" + Math.exp(i);
                row[c++] = "" + (i%7);
                row[c++] = "" + (i%12);
                row[c++] = days[i%7];
                row[c++] = months[i%12];
                row[c++] = DateUtil.toISO(DateUtil.parseDateTime("2000-01-01") + i*24*60*60*1000);
                row[c++] = DateUtil.formatDuration(i*1000);
                row[c] = GUID.makeGUID();
            }

//            for (String[] row : data) System.err.println(StringUtils.join(row,"\t")); System.err.flush();
        }

        public String[][] getFirstNLines(int n) throws IOException
        {
            return data;
        }

        int i=1;
        
        protected Iterator<?> iterator() throws IOException
        {
            return new _Iterator();
        }

        class _Iterator implements Iterator
        {
            public boolean hasNext()
            {
                return i < data.length;
            }

            public Map next()
            {
                return new ArrayListMap<String,String>(templateRow, data[i++]);
            }

            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        }
    }


    static class SqlTest
    {
        public String sql;
        public int countColumns = -1;
        public int countRows = -1;

        SqlTest(String sql)
        {
            this.sql = sql;
        }

        SqlTest(String sql, int cols, int rows)
        {
            this.sql = sql;
            countColumns = cols;
            countRows = rows;
        }
    }


	static int Rsize = 84;
	static int Ssize = 84;

    static SqlTest[] tests = new SqlTest[]
    {
        new SqlTest("SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid FROM R", 8, Rsize),
        new SqlTest("SELECT R.duration AS elapsed FROM R WHERE R.rowid=1", 1, 1),
		new SqlTest("SELECT R.rowid, R.seven, R.day FROM R WHERE R.day LIKE '%ues%'", 3, 12),
		new SqlTest("SELECT R.rowid, R.twelve, R.month FROM R WHERE R.month BETWEEN 'L' and 'O'", 3, 3*7), // March, May, Nov
        new SqlTest("SELECT R.rowid, R.twelve, (SELECT S.month FROM S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday'", 3, 12),
        new SqlTest("SELECT T.R, T.T, T.M FROM (SELECT R.rowid as R, R.twelve as T, (SELECT S.month FROM S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday') T", 3, 12)
    };
    

    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }

        public TestCase(String name)
        {
            super(name);
        }


        String hash = GUID.makeHash();
        QuerySchema lists;
        ListDefinition R;
        ListDefinition S;


        private void addProperties(ListDefinition l)
        {
            Domain d = l.getDomain();
            for (int i=0 ; i<TestDataLoader.COLUMNS.length ; i++)
            {
                DomainProperty p = d.addProperty();
                p.setPropertyURI(d.getName() + hash + "#" + TestDataLoader.COLUMNS[i]);
                p.setName(TestDataLoader.COLUMNS[i]);
                p.setRangeURI(TestDataLoader.TYPES[i]);
            }
        }


        @Override
        protected void setUp() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            ListService.Interface s = ListService.get();

            Map<String,ListDefinition> m = s.getLists(c);
            if (m.containsKey("R"))
                m.get("R").delete(user);
            if (m.containsKey("S"))
                m.get("S").delete(user);

            R = s.createList(c, "R");
            R.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
            R.setKeyName("rowid");
            addProperties(R);
            R.save(user);
            R.insertListItems(user, new TestDataLoader(R.getName() + hash, Rsize));
            
            S = s.createList(c, "S");
            S.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
            S.setKeyName("rowid");
            addProperties(S);
            S.save(user);
            S.insertListItems(user, new TestDataLoader(S.getName() + hash, Ssize));

            // note getSchema() will return NULL if there are no lists yet
            lists = DefaultSchema.get(user, c).getSchema(s.getSchemaName());
        }


        @Override
        protected void tearDown() throws Exception
        {
            User user = TestContext.get().getUser();
            S.delete(user);
            R.delete(user);
        }


        @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
        private CachedRowSetImpl resultset(String sql) throws Exception
        {
			CachedRowSetImpl rs = (CachedRowSetImpl)QueryService.get().select(lists, sql);
            assertNotNull(sql, rs);
            return rs;
        }


        private void validate(SqlTest test) throws Exception
        {
            CachedRowSetImpl rs = null;
            try
            {
                rs = resultset(test.sql);
                ResultSetMetaData md = rs.getMetaData();
                assertTrue(test.sql, test.countColumns == -1 || test.countColumns == md.getColumnCount());
                assertTrue(test.sql, test.countRows == -1 || test.countRows == rs.getSize());
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }


        public void test() throws Exception
        {
            assertNotNull(lists);
            TableInfo Rinfo = lists.getTable("R", "R");
            assertNotNull(Rinfo);
            TableInfo Sinfo = lists.getTable("S", "S");
            assertNotNull(Sinfo);

            // custom tests
            CachedRowSetImpl rs = resultset("SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration FROM R");
            ResultSetMetaData md = rs.getMetaData();
            assertTrue(0 < rs.findColumn("d"));
            assertTrue(0 < rs.findColumn("seven"));
            assertTrue(0 < rs.findColumn("twelve"));
            assertTrue(0 < rs.findColumn("day"));
            assertTrue(0 < rs.findColumn("month"));
            assertTrue(0 < rs.findColumn("date"));
            assertTrue(0 < rs.findColumn("duration"));
            assertEquals(7, md.getColumnCount());
            assertEquals(Rsize, rs.getSize());
			rs.next();
			for (int c=1; c<=md.getColumnCount() ; c++)
				assertNotNull(rs.getObject(c));
            rs.close();

            // simple tests
            for (SqlTest test : tests)
            {
                validate(test);
            }
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
