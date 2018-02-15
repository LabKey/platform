/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseExceptionUnresolvedField;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.MemTracker;
import org.labkey.data.xml.ColumnType;
import org.labkey.query.design.DgColumn;
import org.labkey.query.design.DgOrderByString;
import org.labkey.query.design.DgQuery;
import org.labkey.query.design.DgValue;
import org.labkey.query.design.QueryDocument;
import org.labkey.query.sql.antlr.SqlBaseParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class QuerySelect extends QueryRelation implements Cloneable
{
    private static final Logger _log = Logger.getLogger(QuerySelect.class);

    String _queryText = null;
    private Map<FieldKey, SelectColumn> _columns;

    // these three fields are accessed by QueryPivot
    QGroupBy _groupBy;
    QOrder _orderBy;
    QLimit _limit;
    boolean _forceAllowOrderBy = false; // when we know this will not be wrapped by another SELECT

    private QSelect _select;
    private QWhere _where;
    QWhere _having;
    QQuery _root;
    QDistinct _distinct;

    private Map<FieldKey, QTable> _parsedTables;
    private List<QJoinOrTable> _parsedJoins;
    private List<QExpr> _onExpressions;

    private Map<FieldKey, QueryRelation> _tables;
    // Don't forget to recurse passing container filter into subqueries.
    private List<QueryRelation> _subqueries = new ArrayList<>();
    private Map<FieldKey, RelationColumn> _declaredFields = new HashMap<>();

    // shim tableinfo used for creating expression columninfo
    private SQLTableInfo _sti;
    private AliasManager _aliasManager;

    /**
     * If this node has exactly one FROM table, it may be occasionally possible to skip
     * generating SQL for this node.
     */
    private Boolean _generateSelectSQL = true;

    private Set<FieldKey> parametersInScope = new HashSet<>();


    private QuerySelect(@NotNull Query query, @NotNull QuerySchema schema, String alias)
    {
        super(query, schema, StringUtils.defaultString(alias, "_select" + query.incrementAliasCounter()));
        _inFromClause = false;

        // subqueryTable is only for expr.createColumnInfo()
        // should refactor so tableinfo is not necessary, maybe expr.setColumnAttributes(target)
        _sti = new SQLTableInfo(_schema.getDbSchema(), alias) {
            @Override
            public UserSchema getUserSchema()
            {
                return (UserSchema)QuerySelect.this.getSchema();
            }
        };

        _aliasManager = new AliasManager(schema.getDbSchema());
        _queryText = query._querySource;
        _savedName = query._name;

        for (QParameter p : query._parameters)
            parametersInScope.add(new FieldKey(null, p.getName()));
        
        MemTracker.getInstance().put(this);
    }


	QuerySelect(@NotNull Query query, QQuery root, boolean inFromClause)
	{
		this(query, query.getSchema(), null);
        this._query = query;
        this._inFromClause = inFromClause;
		initializeFromQQuery(root);
		initializeSelect();
        MemTracker.getInstance().put(this);
	}


    // create a simple QuerySelect over a QRelation
    QuerySelect(QueryRelation from, QOrder order, QLimit limit)
    {
        this(from._query, from.getSchema(), null);

        String alias = StringUtils.defaultString(from.getAlias(), "T");
        from.setAlias(alias);

        _orderBy = order;
        _limit = limit;
        
        QTable t = new QTable(from, alias);
        _parsedJoins = new ArrayList<>();
        _parsedJoins.add(t);
        _onExpressions = Collections.EMPTY_LIST;
        _parsedTables = new LinkedHashMap<>();
        _parsedTables.put(t.getTableKey(), t);
        _select = new QSelect();
        _select.childList().add(new QRowStar());
        initializeSelect();
    }


    QueryRelation createSubquery(QQuery qquery, boolean inFromClause, String alias)
    {
        QueryRelation sub = Query.createQueryRelation(this._query, qquery, inFromClause);
        sub._parent = this;
        if (null != alias)
            sub.setAlias(alias);
        return sub;
    }


    @Override
    void setQuery(Query query)
    {
//        assert getParseErrors().size() == 0;
        super.setQuery(query);
        for (QueryRelation r : _tables.values())
            r.setQuery(query);

        for (QueryRelation r : _subqueries)
            r.setQuery(query);
    }


    /*
     * used by QueryPivot
     * find each group by field in the select list
     * add a new column if we can't find it
     */
    Map<String,SelectColumn> getGroupByColumns()
    {
        // CONSIDER: find selected group keys if they are already in the select list
        Map<String,SelectColumn> ret = new HashMap<>();
        int index = 0;
groupByLoop:        
        for (QNode gb : _groupBy.childList())
        {
            index++;
            for (SelectColumn c : _columns.values())
            {
                QNode n = c._field;
                if (n.equals(gb))
                {
                    ret.put(c.getName(), c);
                    continue groupByLoop;
                }
            }
            QExpr copy = ((QExpr)gb).copyTree();
            SelectColumn col = new SelectColumn(copy, "__gb_key__" + index);
            _columns.put(col.getFieldKey(), col);
            ret.put(col.getName(), col);
        }
        return ret;
    }

    private void initializeFromQQuery(QQuery root)
    {
        _root = root;
        if (root == null)
            return;
        _limit = root.getLimit();
        QSelect select = root.getSelect();

        QFrom from = root.getFrom();
        if (from == null)
        {
            _parsedTables = Collections.EMPTY_MAP;
            _parsedJoins = Collections.EMPTY_LIST;
            _onExpressions = Collections.EMPTY_LIST;
        }
        else
        {
            FromParser p = new FromParser(from);
            _parsedTables = p.getTables();
            _parsedJoins = p.getJoins();
            _onExpressions = p.getOns();
        }
        _select = root.getSelect();
        _where = root.getWhere();
        _having = root.getHaving();
        _groupBy = root.getGroupBy();
        _orderBy = root.getOrderBy();
    }


    private void initializeSelect()
    {
        _columns = new LinkedHashMap<>();
        _tables = new HashMap<>();

        for (QTable qtable : _parsedTables.values())
        {
            FieldKey key = qtable.getTableKey();
            String alias = qtable.getAlias().getName();
            QNode node = qtable.getTable();

            QueryRelation relation;
            if (null != qtable.getQueryRelation())
            {
                relation = qtable.getQueryRelation();
            }
            else if (node instanceof QQuery)
            {
                relation = createSubquery((QQuery) node, true, alias);
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
            if (!getParseErrors().isEmpty())
                break;
            if (relation == null)
                continue;
            qtable.setQueryRelation(relation);
            String relationAlias = makeRelationName(relation.getAlias());
            relation.setAlias(relationAlias);
            _tables.put(qtable.getAlias(), relation);
        }

        ArrayList<SelectColumn> columnList = new ArrayList<>();
        if (_select != null)
        {
            LinkedList<QNode> process = new LinkedList(_select.childList());
            while (!process.isEmpty())
            {
                QNode node = process.removeFirst();
                
                if (node instanceof QDistinct)
                {
                    if (!columnList.isEmpty())
                        parseError("DISTINCT not expected", node);
                    else
                        _distinct = (QDistinct)node;
                    continue;
                }

                if (node instanceof QRowStar)
                {
                    for (QTable t : _parsedTables.values())
                    {
                        QNode tableStar = QFieldKey.of(new FieldKey(t.getAlias(), "*"));
                        tableStar.setLineAndColumn(node);
                        process.addFirst(tableStar);
                    }
                    continue;
                }

                // look for table.*
                if (node instanceof QFieldKey || (node instanceof QAs && node.childList().size()==1 && node.getFirstChild() instanceof QFieldKey))
                {
                    FieldKey key = ((QFieldKey)(node instanceof QAs ? node.getFirstChild() : node)).getFieldKey();

                    if (null != key && key.getName().equals("*"))
                    {
                        FieldKey parent = key.getParent();
                        if (parent.getParent() != null)
                        {
                            parseError("Can't resolve column: " + node.getSourceText(), node);
                            continue;
                        }
                        QueryRelation r = getTable(parent);
                        if (null == r)
                        {
                            parseError("Can't resolve column: " + node.getSourceText(), node);
                            continue;
                        }
                        for (String name :  r.getAllColumns().keySet())
                        {
                            SelectColumn col = new SelectColumn(new FieldKey(parent,name));
                            // UNDONE: Remember columns expanded by "SELECT *" so we can copy the hiddenness of a column.
                            // UNDONE: Unfortunately, since .selectRows() and .executeSql() use QueryView, the default column list won't include these hidden columns.
                            // UNDONE: See issue 17316 and 17332.
                            //col._selectStarColumn = true;
                            columnList.add(col);
                        }
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

        // two passes to maintain ordering and avoid the odd name collision
        // create unique aliases where missing
        int expressionUniq = 0;
        Map<FieldKey,FieldKey> fieldKeys = new HashMap<>();
        for (SelectColumn column : columnList)
        {
            String name = column.getName();
            if (null == name)
                continue;
            if (null == column._key)
                column._key = new FieldKey(null,name);
            if (fieldKeys.containsKey(column._key))
                parseError("Duplicate column '" + column.getName() + "'", column.getField());
            fieldKeys.put(column._key, column._key);
        }
        for (SelectColumn column : columnList)
        {
            String name = column.getName();
            if (null != name)
                continue;
            while (fieldKeys.containsKey(new FieldKey(null,"Expression" + ++expressionUniq)))
                ;
            name = "Expression" + expressionUniq;
            reportWarning("Automatially creating alias for expression column: " + name, column._node);
            if (null == column._key)
                column._key = new FieldKey(null,name);
            fieldKeys.put(column._key, column._key);
        }
        for (SelectColumn column : columnList)
        {
            if (null == column.getAlias())
                column.setAlias(_aliasManager.decideAlias(column.getName()));
            _columns.put(column._key, column);
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
        QueryRelation qr = getTable(key);
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
        for (QJoinOrTable range : _parsedJoins)
        {
            range.appendSource(builder);
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
        return _parsedTables.keySet();
    }




    private class FromParser
    {
        final Map<FieldKey, QTable> _tables;
        final List<QJoinOrTable> _joins;        // root joins, these may be trees
        final List<QExpr> _ons;                 // on expressions for declareFields
        
        FromParser(QFrom from)
        {
            _tables = new LinkedHashMap<>();
            _joins = new ArrayList<>();
            _ons = new ArrayList<>();
            parseFrom(from);
        }


        Map<FieldKey, QTable> getTables()
        {
            return _tables;
        }


        List<QJoinOrTable> getJoins()
        {
            return _joins;
        }


        List<QExpr> getOns()
        {
            return _ons;
        }


        private void parseFrom(QNode from)
        {
            List<QNode> l = from.childList();
            if (l.isEmpty())
            {
                parseError("FROM clause is empty", from);
                return;
            }
            for (QNode r : from.childList())
            {
                QJoinOrTable q = parseNode(r);
                if (null != q)
                    _joins.add(q);
                else
                    assert !getParseErrors().isEmpty();
            }
        }

        private QJoinOrTable parseNode(QNode r)
        {
            if (r.getTokenType() == SqlBaseParser.RANGE)
                return parseRange(r);
            else if (r.getTokenType() == SqlBaseParser.JOIN)
                return parseJoin(r);
            else
            {
                parseError("Error in FROM clause", r);
                return null;
            }
        }


        private QTable parseRange(QNode node)
        {
            int countChildren = node.childList().size();
            if (countChildren  < 1 || 2 < countChildren || !(node.childList().get(0) instanceof QExpr))
            {
                parseError("Syntax error in JOIN clause", node);
                return null;
            }

            List<QNode> children = node.childList();
            QExpr expr = (QExpr) children.get(0);
            QIdentifier alias = null;
            if (children.size() > 1 && children.get(1) instanceof QIdentifier)
                alias = (QIdentifier) children.get(1);

            QTable table = new QTable(expr);
            table.setAlias(alias);
            FieldKey aliasKey = table.getAlias();
            if (null == aliasKey)
            {
                table.setAlias(new QIdentifier("_auto_alias_"+_tables.size() + "_"));
                aliasKey = table.getAlias();
                reportWarning("Subquery in FROM clause does not have an alias", expr);
            }
            if (_tables.containsKey(aliasKey))
            {
                parseError(aliasKey + " was specified more than once", table.getTable());
            }
            else
            {
                _tables.put(aliasKey, table);
            }
            return table;
        }


        private QJoin parseJoin(QNode join)
        {
            List<QNode> children = join.childList();
            if (children.size() < 3 || children.size() >  4)
            {
                parseError("Error in JOIN clause", join);
                return null;
            }

            int childIndex = 0;

            QNode leftNode = children.get(childIndex++);
            QJoinOrTable left = parseNode(leftNode);

            JoinType joinType = JoinType.inner;
            switch (children.get(childIndex++).getTokenType())
            {
                case SqlBaseParser.LEFT:
                    joinType = JoinType.left;
                    break;
                case SqlBaseParser.RIGHT:
                    joinType = JoinType.right;
                    break;
                case SqlBaseParser.INNER:
                    joinType = JoinType.inner;
                    break;
                case SqlBaseParser.OUTER:
                    joinType = JoinType.outer;
                    break;
                case SqlBaseParser.FULL:
                    joinType = JoinType.full;
                    break;
                case SqlBaseParser.CROSS:
                    joinType = JoinType.cross;
                    break;
                default:
                    childIndex--;
                    break;
            }

            QNode rightNode = children.get(childIndex++);
            QJoinOrTable right = parseNode(rightNode);

            QNode on = null;
            if (children.size() > childIndex)
            {
                on = children.get(childIndex);
                if (on.childList().size() != 1 || !(on.childList().get(0) instanceof QExpr))
                {
                    parseError("Error in ON expression", on);
                    return null;
                }
                _ons.add((QExpr)on.childList().get(0));
            }

            if (joinType == JoinType.cross && null != on)
                parseError("ON unexpected in a CROSS JOIN", on);
            else if (joinType != JoinType.cross && null == on)
                parseError("ON expected", rightNode);
            QJoin qjoin = new QJoin(left, right, joinType, null==on ? null : (QExpr)on.childList().get(0));
            return qjoin;
        }
    }


    private QueryRelation getTable(FieldKey key)
    {
        QueryRelation ret = _tables.get(key);
        return ret;
    }


    String makeRelationName(String name)
    {
        if (!AliasManager.isLegalName(name) || name.length() > 40)
            return AliasManager.makeLegalName(name, getSqlDialect()) + "_" + _query.incrementAliasCounter();
        else
            return name + "_" + _query.incrementAliasCounter();
    }


    /**
     * Resolve a particular field.
     * The FieldKey may refer to a particular ColumnInfo.
     * Also, if expr is a child of a QMethodCall, then the FieldKey may refer to a {@link org.labkey.query.sql.Method}
     * or a {@link org.labkey.api.data.TableInfo#getMethod}
     *
     * CONSIDER: mark method identifiers...
     */
    @Override
    protected QField getField(FieldKey key, QNode expr, Object referant)
    {
        return getFieldInternal(key, expr, referant, false);
    }


    private QField getFieldInternal(FieldKey key, QNode expr, Object referant, boolean methodName)
    {
        String debugMsg = "";
        boolean isDebugEnabled = _log.isDebugEnabled();

        try
        {
            if (isDebugEnabled)
            {
                debugMsg = "getField( " + this.toStringDebug() + ", " + key.toDisplayString() + " )\n        referant: " + referant;
                _log.debug(">>" + debugMsg);
            }

            if (!methodName)
            {
                RelationColumn column = _declaredFields.get(key);
                if (column != null)
                {
                    if (null != referant)
                        column.addRef(referant);
                    if (isDebugEnabled)
                        debugMsg += "\n        resolvedTo: " + column.getDebugString();
                    return new QField(column, expr);
                }
                if (isDebugEnabled)
                    debugMsg += "\n        resolvedTo: /NOT FOUND/";
            }

            if (key.getTable() == null)
            {
                return new QField(null, key.getName(), expr);
            }
            else
            {
                QueryRelation table = getTable(key.getTable());
                if (table != null)
                {
                    return new QField(table, key.getName(), expr);
                }
                // TODO make table method work on outer tables
                if (methodName)
                {
                    parseError("Method not found: " + key.toString(), expr);
                }
                return super.getField(key, expr, referant);
            }
        }
        finally
        {
            _log.debug("<<" + debugMsg);
        }
    }


    /**
     * Indicate that a particular ColumnInfo is used by this query.
     */
    @Override
    protected RelationColumn declareField(FieldKey declareKey, QExpr location)
    {
        RelationColumn colTry = _declaredFields.get(declareKey);
        if (colTry != null)
            return colTry;

        List<String> parts = declareKey.getParts();
        QueryRelation table = null;
        FieldKey tableKey = null;
        boolean qualified = true;

        if (parts.size() >= 2)
        {
            tableKey = new FieldKey(null, parts.get(0));
            table = getTable(tableKey);
            if (table != null)
                parts.remove(0);
        }

        if (null == table)
        {
            String name = parts.get(0);
            for (Map.Entry<FieldKey,QueryRelation> e : _tables.entrySet())
            {
                if (null != e.getValue().getColumn(name))
                {
                    if (null != table)
                    {
                        parseError("Ambiguous field: " + name, location);
                        return null;
                    }
                    table = e.getValue();
                    tableKey = e.getKey();
                }
            }
            if (null == table)
            {
                RelationColumn ret = super.declareField(declareKey, location);
                if (null == ret)
                {
                    if (location instanceof QIfDefined)
                        ((QIfDefined)location).isDefined = false;
                    else
                        parseError("Could not resolve column: " + declareKey, location);
                }
                return ret;
            }

            FieldKey fullKey = FieldKey.fromParts(tableKey, declareKey);
            colTry = _declaredFields.get(fullKey);
            if (colTry != null)
            {
                _declaredFields.put(declareKey, colTry);
                return colTry;
            }
            qualified = false;
        }

        FieldKey key = new FieldKey(tableKey, parts.get(0));
        RelationColumn colParent = _declaredFields.get(key);
        if (colParent == null)
        {
            RelationColumn relColumn = table.getColumn(key.getName());
            if (relColumn == null)
            {
                if (location instanceof QIfDefined)
                    ((QIfDefined)location).isDefined = false;
                else
                    parseErrorUnknownField(key, location);
                return null;
            }
            _declaredFields.put(key, relColumn);
            colParent = relColumn;
        }
        for (int i = 1; i < parts.size(); i ++)
        {
            key = new FieldKey(key, parts.get(i));
            RelationColumn nextColumn = _declaredFields.get(key);
            if (nextColumn == null)
            {
                nextColumn = table.getLookupColumn(colParent, key.getName());
                if (nextColumn == null)
                {
                    if (location instanceof QIfDefined)
                        ((QIfDefined)location).isDefined = false;
                    else
                        parseErrorUnknownField(key, location);
                    return null;
                }

                _declaredFields.put(key, nextColumn);
            }
            colParent = nextColumn;
        }

        // if table name was not specified, add the actual requested field as well
        if (!qualified)
            _declaredFields.put(declareKey, colParent);
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
            QueryRelation sub = ((QQuery)expr).getQuerySelect();
            if (sub == null)
            {
                sub = createSubquery((QQuery)expr, false, null);
                ((QQuery)expr)._select = sub;
                _subqueries.add(sub);
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
            if (null != resolveParameter(key))
                return;
            RelationColumn column = declareField(key, expr);
            assert null!=column || expr instanceof QIfDefined || getParseErrors().size() > 0;
            return;
        }
        for (QNode child : expr.children())
        {
            declareFields((QExpr)child);
        }
    }


    private boolean _declareCalled = false;

    void declareFields()
    {
        if (_declareCalled)
            return;
        _declareCalled = true;
        _log.debug("declareFields " + this.toStringDebug());

        for (Map.Entry<FieldKey,QueryRelation> entry : _tables.entrySet())
            entry.getValue().declareFields();

        Set selectAliases = Sets.newCaseInsensitiveHashSet();
        if (null != _columns)
        {
            for (SelectColumn column : _columns.values())
            {
                if (null != column.getAlias())
                    selectAliases.add(column.getAlias());
                declareFields(column.getField());
            }
        }
        for (QExpr on : _onExpressions)
        {
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
        if (null != _orderBy)
        {
            for (Map.Entry<QExpr, Boolean> entry : _orderBy.getSort())
            {
                QExpr expr = entry.getKey();
                if (expr instanceof QIdentifier && selectAliases.contains(expr.getTokenText()))
                    continue;
                declareFields(expr);
            }
        }

        // move validation step into Query interface
        validateAfterParse();
    }


    void validateAfterParse()
    {
        if (null != _groupBy)
        {
            for (QNode expr : _groupBy.children())
            {
                if (expr instanceof QExpr && ((QExpr)expr).isConstant())
                    parseError("Expression in Group By clause must not be a constant", expr);
            }
        }
    }

    
    /*
     * Return the result of replacing field names in the expression with QField objects.
     *
     * referant is used for reference counting, this is who is pointing at these fields
     * it might be a QJoin, QWhere, SelectColumn etc.
     */
    QExpr resolveFields(QExpr expr, @Nullable QNode parent, @Nullable Object referant)
    {
        if (expr instanceof QQuery || expr instanceof QUnion)
        {
            QueryRelation subquery;
            if (expr instanceof QQuery)
                subquery = ((QQuery)expr)._select;
            else
                subquery = ((QUnion)expr)._union;
            if (null == subquery)
            {
                if (getParseErrors().isEmpty())
                    getParseErrors().add(new QueryException("Unexpected error: sub query not resolved"));
            }
            else
            {
                subquery.resolveFields();
            }
            return expr;
        }
        if (expr instanceof QRowStar)
        {
            return expr;
        }
        if (expr instanceof QIfDefined && !((QIfDefined)expr).isDefined)
        {
            return new QNull();
        }

        FieldKey key = expr.getFieldKey();
        if (key != null)
        {
            if (key.getParent() == null)
            {
                QParameter param = resolveParameter(key);
                if (null != param)
                    return param;
            }
            QField ret = getFieldInternal(key, expr, referant, false);
            QueryParseException error = ret.fieldCheck(parent, getSqlDialect());
            if (error != null)
                getParseErrors().add(error);
            return ret;
        }

        // We need to recognize method call identifiers, so we handle
        // unqualified column names that are the same as a built-in method.
        // e.g. SELECT Floor, Floor(1.4) FROM ...
        QExpr methodName = null;
        if (expr instanceof QMethodCall)
        {
            methodName = (QExpr)expr.childList().get(0);
            if (null == methodName.getFieldKey())
                methodName = null;
        }

        QExpr ret = (QExpr) expr.clone();
        for (QNode child : expr.children())
        {
            //
            if (child == methodName)
                ret.appendChild(getFieldInternal(((QExpr) child).getFieldKey(), expr, referant, true));
            else
                ret.appendChild(resolveFields((QExpr)child, expr, referant));
        }

        QueryParseException error = ret.fieldCheck(parent, getSqlDialect());
        if (error != null)
            getParseErrors().add(error);
        return ret;
    }


    /* NOTE there is only one merged global list of parameters.
     * However, we still want to make sure the parameter is in scope
     * for this particular select
     *
     * declareField should catch the case of a parameter that exists in the global
     * list, but is not in scope for this select.
     */
    QParameter resolveParameter(FieldKey key)
    {
        if (!parametersInScope.contains(key))
            return null;
        return _query.resolveParameter(key);
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
        Set<RelationColumn> set = new LinkedHashSet<RelationColumn>(_columns.values());

        getOrderedSuggestedColumns(set);
        if (!getParseErrors().isEmpty())
            return null;

        // mark all top level columns selected, since we want to generate column info for all columns
        markAllSelected(_query);

        final SQLFragment sql = getSql();
        if (null == sql)
            return null;

        QueryTableInfo ret = new QueryTableInfo(this, getAlias())
        {
            // Hold a separate reference so we can null it out if the container filter changes
            private SQLFragment _sqlAllColumns = null;

            @NotNull
            @Override
            public SQLFragment getFromSQL(String alias)
            {
                SQLFragment f = new SQLFragment();
                if (_sqlAllColumns == null)
                {
                    markAllSelected(_query);
                    _sqlAllColumns = getSql();
                }
                f.append("(").append(_sqlAllColumns).append(") ").append(alias);
                return f;
            }

            @NotNull
            @Override
            public SQLFragment getFromSQL(String alias, Set<FieldKey> selectedFieldKeys)
            {
                if (null != selectedFieldKeys && !selectedFieldKeys.isEmpty())
                {
                    Set<String> names = selectedFieldKeys.stream()
                            .map((k) -> k.getRootName())
                            .collect(Collectors.toSet());
                    releaseAllSelected(_query);
                    markAllSelected(new CaseInsensitiveHashSet(names),_query);
                }
                else
                    markAllSelected(_query);
                SQLFragment s = getSql();
                SQLFragment f = new SQLFragment();
                f.append("(").append(s).append(") ").append(alias);
                return f;
            }

            @Override
            public boolean hasSort()
            {
                return _orderBy != null && !_orderBy.childList().isEmpty();
            }

            @Override
            public void setContainerFilter(@NotNull ContainerFilter containerFilter)
            {
                super.setContainerFilter(containerFilter);
                // This changes the SQL we'll need to generate, so clear out the cached version
                _sqlAllColumns = null;
            }

            @Override
            public String getPublicSchemaName()
            {
                // Use the QuerySchema name, not the DbSchema name
                return _relation.getSchema().getSchemaName();
            }

            @Override
            public String getTitleColumn()
            {
                SelectColumn sc = QuerySelect.this.getTitleColumn();
                if (null != sc)
                    return sc.getName();
                return super.getTitleColumn();
            }

        };

        Collection<String> keys = getKeyColumns();
        String key = null;
        if (keys.size() == 1)
            key = keys.iterator().next();

        for (SelectColumn col : _columns.values())
        {
            ColumnInfo aliasedColumn = new RelationColumnInfo(ret, col);
            ret.addColumn(aliasedColumn);

            if (StringUtils.equalsIgnoreCase(aliasedColumn.getName(),key))
                aliasedColumn.setKeyField(true);
        }
        MemTracker.getInstance().put(ret);
        return ret;
    }


    boolean _resolved = false;

    public void resolveFields()
    {
        if (_resolved)
            return;
        _resolved = true;

        if (getParseErrors().size() != 0)
            return;

        declareFields();
        if (getParseErrors().size() != 0)
            return;

//        SqlDialect dialect = getSqlDialect();

        CaseInsensitiveHashMap<SelectColumn> aliasSet = new CaseInsensitiveHashMap<>();

        for (SelectColumn col : _columns.values())
        {
            aliasSet.put(col.getAlias(), col);
            col.getResolvedField();
        }
        if (getParseErrors().size() != 0)
            return;

        for (QJoinOrTable qt : _parsedJoins)
            resolveFields(qt);

        if (_where != null)
            for (QNode expr : _where.children())
                resolveFields((QExpr)expr, null, _where);

        if (_groupBy != null)
        {
            for (QNode expr : _groupBy.children())
                resolveFields((QExpr)expr, _groupBy, _groupBy);
        }
        if (_having != null)
        {
            for (QNode expr : _having.children())
                resolveFields((QExpr)expr, null, _having);
        }
        if (_orderBy != null)
        {
            for (Map.Entry<QExpr, Boolean> entry : _orderBy.getSort())
            {
                QExpr expr = entry.getKey();
                if (expr instanceof QIdentifier && aliasSet.containsKey(expr.getTokenText()))
                    aliasSet.get(expr.getTokenText()).addRef(_orderBy);
                else
                    resolveFields(expr, _orderBy, _orderBy);
            }
        }
    }


    void resolveFields(QJoinOrTable qt)
    {
        if (qt instanceof QJoin)
        {
            if (null != ((QJoin)qt)._on)
                resolveFields(((QJoin)qt)._on, null, qt);
            resolveFields(((QJoin)qt)._left);
            resolveFields(((QJoin)qt)._right);
        }
        else if (qt instanceof QTable)
        {
            if (((QTable)qt).getQueryRelation() instanceof QuerySelect)
                ((QTable)qt).getQueryRelation().resolveFields();
        }
    }


    public void markAllSelected(Object referant)
    {
        for (SelectColumn c : _columns.values())
        {
            c._selected = true;
            c.addRef(referant);
        }
    }

    public void markAllSelected(Set<String> names, Object referant)
    {
        for (SelectColumn c : _columns.values())
        {
            if (names.contains(c.getName()))
            {
                c._selected = true;
                c.addRef(referant);
            }
        }
    }

    public void releaseAllSelected(Object referant)
    {
        for (SelectColumn column : _columns.values())
        {
            int count = column.releaseRef(referant);
            column._selected = (count > 0);
        }
    }

    @Override
    public SQLFragment getFromSql()
    {
        return _getSql(true);
    }


    public SQLFragment getSql()
    {
        return _getSql(false);
    }


    private SQLFragment _getSql(boolean asFromSql)
    {
        resolveFields();
        if (getParseErrors().size() != 0)
            return null;

        optimize();

        if (!_generateSelectSQL)
        {
            assert _parsedJoins.size()==1;
            assert _parsedTables.size()==1;
            assert _onExpressions.size()==0;
            assert _distinct == null;
            assert _groupBy == null;
            assert _having == null;
            assert _where == null;
            assert _orderBy == null;
            assert _limit == null;
            QueryRelation in = _tables.values().iterator().next();
            return asFromSql ? in.getFromSql() : in.getSql();
        }

        SqlDialect dialect = getSqlDialect();
        SqlBuilder sql = new SqlBuilder(getDbSchema());

        if (null != _distinct)
            markAllSelected(_distinct);

        int count = 0;
        boolean isDebugEnabled = _log.isDebugEnabled();
        if (isDebugEnabled)
            _log.debug("SELECT COLUMN LIST: " + this.toStringDebug());
        for (SelectColumn col : _columns.values())
        {
            if (isDebugEnabled)
                _log.debug("    " + col.getDebugString() + " ref=" + col.ref.count());
            // NOTE: container columns are sometimes used by lookups without being explicitly REF'd
            if (col.getJdbcType() == JdbcType.GUID)
                col.addRef(col);
            if (0 < col.ref.count())
                count++;
        }
        if (count == 0)
            markAllSelected(_query);


        // FROM first, call getSql() on children first
        SqlBuilder fromSql = new SqlBuilder(getDbSchema());
        fromSql.pushPrefix("\nFROM ");
        for (QJoinOrTable qt : _parsedJoins)
        {
            qt.appendSql(fromSql, this);
            fromSql.nextPrefix(",");
        }
        fromSql.popPrefix();


        // keep track of mapping from source alias to generated alias (used by ORDER BY)
        CaseInsensitiveHashMap<String> aliasMap = new CaseInsensitiveHashMap<>();

        if (null == _distinct)
            sql.pushPrefix("SELECT ");
        else
            sql.pushPrefix("SELECT DISTINCT ");

        for (SelectColumn col : _columns.values())
        {
            if (0 == col.ref.count())
                continue;
            String colAlias = col.getAlias();
            assert null != colAlias;
            if (colAlias == null)
            {
                parseError("Column requires alias", col.getField());
                return null;
            }
            sql.append(col.getInternalSql());
            sql.append(" AS ");
            String sqlAlias = dialect.makeLegalIdentifier(colAlias);
            sql.append(sqlAlias);
            aliasMap.put(colAlias,sqlAlias);
            sql.nextPrefix(",\n");
            count++;
        }
        sql.popPrefix();
        if (getParseErrors().size() != 0)
            return null;

        sql.append(fromSql);

        if (_where != null)
        {
            sql.pushPrefix("\nWHERE ");
            for (QNode expr : _where.children())
            {
                sql.append("(");
                QExpr term = resolveFields((QExpr)expr, null, _where);
                term.appendSql(sql, _query);
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
                QExpr gbExpr = resolveFields((QExpr)expr, _groupBy, _groupBy);
                // check here again for constants, after resolveFields()
                if (gbExpr.isConstant())
                    parseError("Expression in Group By clause must not be a constant", expr);
                gbExpr.appendSql(sql, _query);
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
                QExpr term = resolveFields((QExpr)expr, null, _having);
                term.appendSql(sql, _query);
                sql.append(")");
                sql.nextPrefix(" AND ");
            }
            sql.popPrefix();
        }
        if (_orderBy != null)
        {
            if (_limit == null & !_forceAllowOrderBy && !getSqlDialect().allowSortOnSubqueryWithoutLimit())
            {
                reportWarning("The underlying database does not supported nested ORDER BY unless LIMIT is also specified. Ignoring ORDER BY.", _orderBy);
            }
            else
            {
                sql.pushPrefix("\nORDER BY ");
                for (Map.Entry<QExpr, Boolean> entry : _orderBy.getSort())
                {
                    QExpr expr = entry.getKey();
                    if (expr instanceof QIdentifier && aliasMap.containsKey(expr.getTokenText()))
                    {
                        sql.append(aliasMap.get(expr.getTokenText()));
                    }
                    else
                    {
                        QExpr r = resolveFields(expr, _orderBy, _orderBy);
                        if (r instanceof QNull)
                            continue;
                        r.appendSql(sql, _query);
                    }
                    if (!entry.getValue().booleanValue())
                        sql.append(" DESC");
                    sql.nextPrefix(",");
                }
                sql.popPrefix();
            }
        }
        if (_limit != null)
        {
            getSqlDialect().limitRows(sql, _limit.getLimit());
        }
        if (!getParseErrors().isEmpty())
            return null;

        if (getParseErrors().size() != 0)
            return null;

        if (asFromSql)
        {
            // NOTE inserting the "(" earlier blows up limitRows()
            sql.insert(0,"(");
            sql.append(") ").append(getAlias());
        }

        if (!AppProps.getInstance().isDevMode() || _inFromClause || null == sql)
            return sql;

        // debug comments
        SqlBuilder ret = new SqlBuilder(_schema.getDbSchema());
        String comment = "<QuerySelect";
        if (!StringUtils.isEmpty(_savedName))
            comment += " name='" + StringUtils.trimToEmpty(_savedName) + "'";
        comment += ">";
        ret.appendComment(comment);
        if (null != _queryText)
        {
            appendLongComment(ret, _queryText);
        }
        ret.append(sql);
        ret.appendComment("</" + comment.substring(1));
        return ret;
    }


    public static boolean appendLongComment(SqlBuilder sb, String queryText)
    {
        if (!AppProps.getInstance().isDevMode())
            return true;
        boolean truncated = false;
        if (queryText.length() > 10_000)
        {
            queryText = queryText.substring(0,10_000);
            truncated = true;
        }
        // Handle any combination of line endings - \n (Unix), \r (Mac), \r\n (Windows), \n\r (nobody)
        for (String s : queryText.split("(\n\r?)|(\r\n?)"))
        {
            if (null != StringUtils.trimToNull(s))
                sb.appendComment("|         " + s);
        }
        if (truncated)
            sb.appendComment("|         . . .");
        return true;
    }

	private void parseError(String message, QNode node)
	{
		Query.parseError(getParseErrors(), message, node);
	}

    private void parseErrorUnknownField(FieldKey fk, QNode node)
    {
        int line = 0;
        int column = 0;
        if (node != null)
        {
            line = node.getLine();
            column = node.getColumn();
        }
        //noinspection ThrowableInstanceNeverThrown
        getParseErrors().add(new QueryParseExceptionUnresolvedField(fk, null, line, column));
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
        MemTracker.getInstance().put(ret);
        return ret;
    }


    public void update(DgQuery query, List<QueryException> errors)
    {
        _columns.clear();
        for (DgColumn dgColumn : query.getSelect().getColumnArray())
        {
            QExpr field = null;
            DgValue value = dgColumn.getValue();
            if (value.isSetField())
            {
                field = QIdentifier.of(FieldKey.fromString(value.getField().getStringValue()));
            }
            else if (value.isSetSql())
            {
                field = (new SqlParser()).parseExpr(value.getSql(), errors);
            }
            if (null == field)
                continue;
            SelectColumn column = new SelectColumn(field);
            if (dgColumn.isSetAlias() && dgColumn.getAlias().trim().length() > 0)
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


    int getSelectedColumnCount()
    {
        return _columns.size();
    }


    SelectColumn getColumn(String name)
    {
        FieldKey key = new FieldKey(null,name);
        SelectColumn col = _columns.get(key);
        if (col != null)
            col._selected = true;
        return col;
    }


    protected Map<String,RelationColumn> getAllColumns()
    {
        LinkedHashMap<String,RelationColumn> ret = new LinkedHashMap<>(_columns.size()*2);
        for (Map.Entry<FieldKey,SelectColumn> e : _columns.entrySet())
        {
            if (null == e.getKey().getParent())
                ret.put(e.getKey().getName(), e.getValue());
        }
        return ret;
    }


    SelectColumn getTitleColumn()
    {
        if (_tables.size() != 1)
            return null;
        QueryRelation in = _tables.values().iterator().next();
        if (!(in instanceof QueryTable))
            return null;
        String title = in.getTableInfo().getTitleColumn();
        if (null == title)
            return null;
        SelectColumn sc = findColumnInSelectList(in, title);
        return sc;
    }


    @Override
    Collection<String> getKeyColumns()
    {
        if (!this._resolved)
            throw new IllegalStateException();
        // TODO handle multi column primary keys
        // TODO handle group by/distinct
        if (_tables.size() != 1 || null != _distinct || null != _groupBy || this.isAggregate())
            return Collections.emptyList();
        // get the single table
        QueryRelation in = _tables.values().iterator().next();
        Collection<String> keys = in.getKeyColumns();
        if (keys.size() != 1)
            return Collections.emptyList();
        String pkName = keys.iterator().next();
        SelectColumn sc = findColumnInSelectList(in, pkName);
        // OK find this column in the output and mark it as a key
        if (null == sc)
            return Collections.emptyList();
        return Collections.singletonList(sc.getName());
    }


    private SelectColumn findColumnInSelectList(QueryRelation in, String colName)
    {
        RelationColumn find = in.getColumn(colName);
        if (null==find)
            return null;

        for (SelectColumn sc : _columns.values())
        {
            QExpr expr = sc.getResolvedField();
            if (expr instanceof QField)
            {
                QField f = (QField) expr;
                if (f._column == find)
                    return sc;
            }
        }
        return null;
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


    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        _sti.setContainerFilter(containerFilter);

        for (QueryRelation queryRelation : _tables.values())
        {
            queryRelation.setContainerFilter(containerFilter);
        }

        for (QueryRelation queryRelation : _subqueries)
        {
            queryRelation.setContainerFilter(containerFilter);
        }
    }


    @Override
    protected Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        resolveFields();

        if (!getParseErrors().isEmpty())
            return Collections.emptySet();

        if (this.isAggregate() || null != this._distinct)
            return Collections.emptySet();
        if (this._parent instanceof QueryUnion || this._parent instanceof QueryPivot)
            return Collections.emptySet();

        MultiValuedMap<QueryRelation, RelationColumn> maps = new ArrayListValuedHashMap<>();
        Set<RelationColumn> ret = new HashSet<>();

        for (SelectColumn sc : _columns.values())
        {
            if (null == sc._field)
                continue;
            QExpr expr = sc.getResolvedField();
            if (!(expr instanceof QField))
                continue;
            QField field = (QField)expr;
            if (null == field.getTable() || null == field.getRelationColumn())
                continue;
            maps.put(field.getTable(), field.getRelationColumn());
        }

        for (Map.Entry<QueryRelation, Collection<RelationColumn>> e : maps.asMap().entrySet())
        {
            IdentityHashMap<RelationColumn,RelationColumn> h = new IdentityHashMap<>();
            for (RelationColumn rc : e.getValue())
                h.put(rc, rc);
            Set<RelationColumn> suggestedColumns = e.getKey().getOrderedSuggestedColumns(h.keySet());
            if (null == suggestedColumns) suggestedColumns = Collections.emptySet();
            for (RelationColumn s : suggestedColumns)
            {
                QField field = new QField(s, null);
                SelectColumn selectColumn = new SelectColumn(field, true);
                selectColumn._suggestedColumn = true;
                selectColumn._selected = true;
                this._columns.put(selectColumn.getFieldKey(), selectColumn);
                ret.add(selectColumn);
            }
        }
        return ret;
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

        QField parentField = (QField)qexpr;
        RelationColumn fromParentColumn = parentField.getRelationColumn();
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
        boolean _selectStarColumn = false;
        QNode _node;
        QExpr _field;
        QExpr _resolved;
        ColumnInfo _colinfo = null;
        Map<String,Object> _annotations = null;

        QIdentifier _aliasId;
        String _alias;

        private SelectColumn()
        {
        }

        public SelectColumn(FieldKey fk)
        {
            _field = QFieldKey.of(fk);
            _key = new FieldKey(null, fk.getName());
            _alias = _aliasManager.decideAlias(getName());
        }

        public SelectColumn(QExpr expr, String aliasPrefix)
        {
            _node = expr;
            _field = expr;
            _alias = _aliasManager.decideAlias(aliasPrefix);
            _key = new FieldKey(null,_alias);
        }

        public SelectColumn(QNode node)
        {
            if (node instanceof SupportsAnnotations)
                _annotations = ((SupportsAnnotations)node).getAnnotations();

            _node = node;
            if (node instanceof QAs && node.childList().size() > 1)
            {
                _field = ((QAs) node).getExpression();
                FieldKey key = _field.getFieldKey();
                if (null != key && key.getName().equals("*"))
                    parseError("* expression can not be aliased", node);
                _aliasId = ((QAs) node).getAlias();
            }
            else
            {
                if (node instanceof QAs)
                    node = node.getFirstChild();
                _field = (QExpr) node;
                FieldKey fk = _field.getFieldKey();
                if (null != fk && !fk.getName().startsWith("@@"))
                    _key = new FieldKey(null, fk.getName());
            }
            String name = getName();
            if (null != name)
                _alias = _aliasManager.decideAlias(name);
        }

        public SelectColumn(QExpr expr)
        {
            _field = expr;
            FieldKey key = _field.getFieldKey();
            if (null != key)
                _key = new FieldKey(null, key.getName());
            _alias = getName() == null ? null : _aliasManager.decideAlias(getName());
        }

        public SelectColumn(QField field)
        {
            this(field, false);
        }

        public SelectColumn(QField field, boolean generateName)
        {
            _field = field;
            _resolved = field;
            if (generateName)
            {
                _alias = _aliasManager.decideAlias(field.getName());
                _key = new FieldKey(null, _alias);
            }
            else
            {
                _key = new FieldKey(null, field.getName());
                _alias = _aliasManager.decideAlias(getName());
            }
        }

        SQLFragment getInternalSql()
        {
            SqlBuilder b = new SqlBuilder(getDbSchema());
            QExpr expr = getResolvedField();

            // NOTE SqlServer does not like predicates (A=B) in select list, try to help out
            if (expr instanceof QMethodCall && expr.getSqlType() == JdbcType.BOOLEAN && b.getDialect().isSqlServer())
            {
                b.append("CASE WHEN (");
                expr.appendSql(b, _query);
                b.append(") THEN 1 ELSE 0 END");
                return b;
            }

            // avoid org.postgresql.util.PSQLException: ERROR: failed to find conversion function from unknown to text
            if (expr instanceof QString && b.getDialect().isPostgreSQL())
            {
                int len = ((QString)expr).getValue().length();
                b.append("CAST(");
                expr.appendSql(b, _query);
                b.append(" AS VARCHAR");
                if (len > 0)
                    b.append("(").append(len).append(")");
                b.append(")");
                return b;
            }

            expr.appendSql(b, _query);
            return b;
        }

        public String getName()
        {
            if (null != _aliasId)
                return _aliasId.getIdentifier();
            else if (null != _key)
                return _key.getName();
            else
                return null;
        }

        // QuerySelect always returns one part field key, QueryLookupWrapper handles lookups
        @Override public FieldKey getFieldKey()
        {
            return new FieldKey(null, getName());    
        }

        public String getAlias()
        {
            return _alias;
        }

        QueryRelation getTable()
        {
            return QuerySelect.this;
        }

        public JdbcType getJdbcType()
        {
            return null != _resolved ? _resolved.getSqlType() : JdbcType.NULL;
        }


        @Override
        public ForeignKey getFk()
        {
            QExpr expr = getResolvedField();
            if (expr instanceof QField)
            {
                RelationColumn relationColumn = ((QField) expr).getRelationColumn();
                if (relationColumn != null)
                    return relationColumn.getFk();
            }

//                return ((QField)expr).getRelationColumn().getFk();
            return null;
        }


        @Override
        boolean isHidden()
        {
            QExpr expr = getResolvedField();
            if (expr instanceof QField)
            {
                return ((QField)expr).getRelationColumn().isHidden();
            }
            return false;
        }


        @Override
        void copyColumnAttributesTo(ColumnInfo to)
        {
            QExpr expr = getResolvedField();
            if (expr instanceof QField)
            {
                RelationColumn rc = ((QField)expr).getRelationColumn();
                if (null == rc)
                {
                    // all columns should have been resolved by now
                    parseError("Can't resolve column: " + expr.getSourceText(), expr);
                    return;
                }
                rc.copyColumnAttributesTo(to);
                if (_selectStarColumn)
                    to.setHidden(rc.isHidden());
            }
            else
            {
                if (_colinfo == null)
                {
                    _colinfo = expr.createColumnInfo(_sti, _aliasManager.decideAlias(getAlias()), _query);
                }
                to.copyAttributesFrom(_colinfo);
                if (_selectStarColumn)
                    to.setHidden(_colinfo.isHidden());
            }

            String label=chooseLabel();
            if (label != null)
                to.setLabel(label);

            boolean hidden = null != _annotations && _annotations.containsKey("hidden");
            if (hidden)
                to.setHidden(hidden);

            // does not remove the FK, just changes display behaviour
            boolean nolookup = null != _annotations && _annotations.containsKey("nolookup");
            if (nolookup)
                to.setDisplayColumnFactory(ColumnInfo.NOLOOKUP_FACTORY);

            // copy URL if possible
            FieldKey fk = null != _resolved ? _resolved.getFieldKey() : null;
            if (null != fk)
            {
                to.copyURLFromStrict(_colinfo, Collections.singletonMap(fk,to.getFieldKey()));
            }

            if (to.getURL() instanceof DetailsURL && to.getURL() != AbstractTableInfo.LINK_DISABLER)
            {
                FieldKey containerFieldKey = getContainerFieldKey();
                if (containerFieldKey != null)
                    ((DetailsURL)to.getURL()).setContainerContext(new ContainerContext.FieldKeyContext(containerFieldKey), false);
            }

            if (_parsedTables.size() != 1)
                to.setKeyField(false);
        }

        private String chooseLabel()
        {
            if (null != _annotations && _annotations.containsKey("preservetitle"))
                return null;
            Object lbl = null==_annotations?null:_annotations.get("title");
            if (lbl instanceof String && null != (lbl = StringUtils.trimToNull((String)lbl)))
                return (String)lbl;
            if (_aliasId != null)
                return ColumnInfo.labelFromName(getName());
            return null;
        }

        public void appendSource(SourceBuilder builder)
        {
            _field.appendSource(builder);
            if (_aliasId != null)
            {
                builder.append(" AS ");
                _aliasId.appendSource(builder);
            }
        }

        public void setAlias(String alias)
        {
            _alias = StringUtils.trimToNull(alias);
            assert null != _alias;
            _aliasId = new QIdentifier(_alias);
        }

        public QExpr getField()
        {
            return _field;
        }

        public QExpr getResolvedField()
        {
            if (null == _resolved)
            {
                _resolved = resolveFields(getField(), null, null);
                if (0 < ref.count())
                    _resolved.addFieldRefs(this);
            }
            return _resolved;
        }

        @Override
        public SQLFragment getValueSql()
        {
            assert null != _generateSelectSQL;
            if (!QuerySelect.this._generateSelectSQL)
            {
                QExpr expr = getResolvedField();
                assert expr instanceof QField;
                return ((QField)expr)._column.getValueSql();
            }
            return super.getValueSql();
        }

        @Override
        public String toString()
        {
            return super.toString() + " alias: " + this.getAlias();
        }

        @Override
        public int addRef(@NotNull Object refer)
        {
            if (0 == ref.count())
            {
                if (null != _resolved)
                    _resolved.addFieldRefs(this);
            }
            return super.addRef(refer);
        }

        @Override
        public int releaseRef(@NotNull Object refer)
        {
            if (0 == ref.count())
                return 0;
            int count = super.releaseRef(refer);
            if (0 == count)
            {
                if (null != _resolved)
                    _resolved.releaseFieldRefs(this);
            }
            return count;
        }
    }


    // optimization specific member variables
    boolean _optOrderByAbove = false;
    // used by QueryPivot.getPivotValues() which doesn't want to mess with
    // the main query
    boolean _allowStructuralOptimization = true;

    void optimize()
    {
        // precompute select child
        QueryRelation r = _tables.size() == 1 ? _tables.values().iterator().next() : null;
        if (r instanceof QueryLookupWrapper)
        {
            if (((QueryLookupWrapper)r)._hasLookups)
                r = null;
            else
                r = ((QueryLookupWrapper)r)._source;
        }
        if (!(r instanceof QuerySelect) && !(r instanceof QueryTable))
            r = null;
        QueryRelation childSelectOrTable = r;

        r = _parent;
        if (r instanceof QueryLookupWrapper)
            r = ((QueryLookupWrapper)r)._parent;
        if (!(r instanceof QuerySelect))
            r = null;
        QuerySelect selectParent = (QuerySelect)r;

        if (_allowStructuralOptimization)
        {
            optimizeOrderBy(selectParent, childSelectOrTable);
        }

        // doesn't mess with references or structure
        mergeWithChildSelect(selectParent, childSelectOrTable);
    }


    boolean optimizeOrderBy(@Nullable QuerySelect selectParent, @Nullable QueryRelation childRelation)
    {
        if (!_inFromClause || null == selectParent)
            return false;
        if (selectParent._optOrderByAbove || null != selectParent._orderBy || null != selectParent._distinct)
            _optOrderByAbove = true;
        if (!_optOrderByAbove)
            return false;
        if (null == _orderBy || null != _limit)
            return false;
        releaseAllSelected(_orderBy);
        _orderBy = null;
        return true;
    }


    boolean mergeWithChildSelect(@Nullable QuerySelect selectParent, @Nullable QueryRelation childRelation)
    {
        _generateSelectSQL = true;

        if (!_inFromClause || null == selectParent || null == childRelation)
            return false;
        if (_distinct != null)
            return false;
        if (_groupBy != null)
            return false;
        if (_having != null)
            return false;
        if (_where != null)
            return false;
        if (_orderBy != null)
            return false;
        if (_limit != null)
            return false;

        // don't remove if immediate parent is union, exact column set and order are important
        // null == selectParent check should be sufficient also
        if (_parent instanceof QueryUnion)
            return false;

        for (SelectColumn c : _columns.values())
        {
            QExpr expr = c.getResolvedField();
            if (!(expr instanceof QField))
                return false;
        }

/*
        // I think we could get distinct to work
        // however, we should write a bunch of specific tests first
        // also need to be wary of destructive optimization
        // QueryPivot re-uses part of the query tree for getPivotValues()
        if (null != selectChild._limit)
            return false;
        if (null != _distinct)
        {
            Map<FieldKey,SelectColumn> map = new HashMap<FieldKey, SelectColumn>();
            for (SelectColumn col : from._columns.values())
                if (col.ref.count() > 0)
                    map.put(col.getFieldKey(), col);
            for (SelectColumn c : _columns.values())
            {
                RelationColumn selectCol = ((QField)c.getResolvedField()).getRelationColumn();
                if (null == selectCol)
                    continue;
                assert selectCol.getTable() == from;
                map.remove(selectCol.getFieldKey());
            }
            // ORDER BY can cause a select reference, that's one reason we might bail here
            if (!map.isEmpty())
                return false;

            if (null != from._distinct)
                from._distinct = _distinct;
            _distinct = null;
        } */

        _generateSelectSQL = false;

        return true;
    }


    @Override
    protected QuerySelect clone()
    {
        try
        {
            return (QuerySelect)super.clone();
        }
        catch (CloneNotSupportedException x)
        {
            throw new RuntimeException(x);
        }
    }


    QuerySelect shallowClone()
    {
        QuerySelect clone = this.clone();
        return clone;
    }
}
