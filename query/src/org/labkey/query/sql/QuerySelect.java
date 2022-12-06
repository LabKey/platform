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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


public class QuerySelect extends QueryRelation implements Cloneable
{
    private static final Logger _log = LogManager.getLogger(QuerySelect.class);

    String _queryText;
    private Map<FieldKey, SelectColumn> _columns;

    // these three fields are accessed by QueryPivot
    QGroupBy _groupBy;
    QOrder _orderBy;
    List<QOrder.SortEntry> _sortEntries;
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
    private Map<FieldKey, QueryRelation> _qualifiedTables;
    // Don't forget to recurse passing container filter into subqueries.
    private final List<QueryRelation> _subqueries = new ArrayList<>();
    private final Map<FieldKey, RelationColumn> _declaredFields = new HashMap<>();

    // shim tableinfo used for creating expression columninfo
    private final SQLTableInfo _sti;
    private final AliasManager _aliasManager;
//    private List<SelectColumn> _medianColumns = new ArrayList<>();                  // Possible way to support SQL Server Median

    // This is set by initializeSelect(), it will remain false ONLY when there is a recursive union.
    // In that case initializeSelect() will have to be called again in a 2nd pass see QueryWith constructor
    private boolean initialized = false;

    private boolean  skipSuggestedColumns = false;  // set to skip normal getSuggestedColumns() code

    /**
     * If this node has exactly one FROM table, it may be occasionally possible to skip
     * generating SQL for this node.
     */
    private Boolean _generateSelectSQL = true;

    private final Set<FieldKey> parametersInScope = new HashSet<>();


    private QuerySelect(@NotNull Query query, @NotNull QuerySchema schema, String alias)
    {
        super(query, schema, StringUtils.defaultString(alias, "_select" + query.incrementAliasCounter()));
        _inFromClause = false;

        // subqueryTable is only for expr.createColumnInfo()
        // should refactor so tableinfo is not necessary, maybe expr.setColumnAttributes(target)
        _sti = new SQLTableInfo(_schema.getDbSchema(), alias)
        {
            @Override
            public UserSchema getUserSchema()
            {
                return (UserSchema) QuerySelect.this.getSchema();
            }
        };

        _aliasManager = new AliasManager(schema.getDbSchema());
        _queryText = query._querySource;
        _savedName = query._debugName;

        for (QParameter p : query._parameters)
            parametersInScope.add(new FieldKey(null, p.getName()));

        MemTracker.getInstance().put(this);
    }


    QuerySelect(@NotNull Query query, QQuery root, QueryRelation parent, boolean inFromClause)
    {
        this(query, query.getSchema(), null);
        _parent = parent;
        _query = query;
        _inFromClause = inFromClause;
        initializeFromQQuery(root);
        initializeSelect();
        MemTracker.getInstance().put(this);
    }


    QuerySelect(@NotNull Query query, QQuery root, boolean inFromClause)
    {
        this(query, root, null, inFromClause);
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
        _onExpressions = Collections.emptyList();
        _parsedTables = new LinkedHashMap<>();
        _parsedTables.put(t.getTableKey(), t);
        _select = new QSelect();
        _select.childList().add(new QRowStar());
        initializeSelect();
    }


    QueryRelation createSubquery(QQuery qquery, boolean inFromClause, String alias)
    {
        QueryRelation sub = Query.createQueryRelation(_query, qquery, inFromClause);
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
    Map<String, SelectColumn> getGroupByColumns()
    {
        // CONSIDER: find selected group keys if they are already in the select list
        Map<String, SelectColumn> ret = new HashMap<>();
        int index = 0;

        if (null != _groupBy)
        {
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
                QExpr copy = ((QExpr) gb).copyTree();
                SelectColumn col = new SelectColumn(copy, "__gb_key__" + index);
                _columns.put(col.getFieldKey(), col);
                ret.put(col.getName(), col);
            }
        }
        return ret;
    }

    private void initializeFromQQuery(QQuery root)
    {
        _root = root;
        if (root == null)
            return;
        _limit = root.getLimit();

        QFrom from = root.getFrom();
        if (from == null)
        {
            _parsedTables = Collections.emptyMap();
            _parsedJoins = Collections.emptyList();
            _onExpressions = Collections.emptyList();
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
        _qualifiedTables = new HashMap<>();

        for (QTable qtable : _parsedTables.values())
        {
            FieldKey key = qtable.getTableKey();
            String alias = qtable.getAlias().getName();
            QNode node = qtable.getTable();
            ContainerFilter.Type containerFilter = qtable.getContainerFilterType();

            QueryRelation relation = null;
            if (null != qtable.getQueryRelation())
            {
                relation = qtable.getQueryRelation();
            }
            else if (node instanceof QIdentifier && null != key)
            {
                // Check WITH
                relation = _query.lookupCteTable(CommonTableExpressions.getLegalName(getSqlDialect(), key.getName()));

                if (relation instanceof CommonTableExpressions.QueryTableWith queryTableWith)
                {
                    if (queryTableWith.isParsingWith())
                    {
                        if (queryTableWith.isSeenRecursiveReference())
                        {
                            parseError("Cannot reference query in WITH recursively more than once: " + ((QIdentifier) node).getIdentifier(), node);
                        }
                        queryTableWith.setSeenRecursiveReference(true);
                        _query.setHasRecursiveWith(true);
                    }

                    if (getParseErrors().isEmpty() && null == queryTableWith._wrapped)
                    {
                        if (!getSqlDialect().isRecursiveLabKeyWithSupported())
                            parseError("Recursive WITH not supported for " + getSqlDialect().getProductName(), node);
                        else
                            parseError("Reference not found for " + key.getName(), node);
                        return;
                    }

                    // wrap this 'global' relation to capture the FROM alias (setAlias() is called below)
                    relation = new QueryWithWrapper(queryTableWith, alias);
                }
            }

            if (null == relation)
            {
                if (node instanceof QQuery)
                {
                    relation = createSubquery((QQuery) node, true, alias);
                }
                else if (node instanceof QUnion)
                {
                    relation = new QueryUnion(this, (QUnion) node, true, alias);
                }
                else
                {
                    relation = _query.resolveTable(_schema, node, key, alias, containerFilter);
                    assert relation == null || alias.equals(relation.getAlias());
                    if (null != relation)
                        relation._parent = this;
                }
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

            // Remember schema-qualified key to resolve schema-qualified tables in declareFields()
            if (null != key && null == qtable._alias && key.size() > 1)
                _qualifiedTables.put(key, relation);
        }

        ArrayList<SelectColumn> columnList = new ArrayList<>();
        if (_select != null)
        {
            LinkedList<QNode> process = new LinkedList<>(_select.childList());
            while (!process.isEmpty())
            {
                QNode node = process.removeFirst();

                if (node instanceof QDistinct)
                {
                    if (!columnList.isEmpty())
                        parseError("DISTINCT not expected", node);
                    else
                        _distinct = (QDistinct) node;
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
                if (node instanceof QFieldKey || (node instanceof QAs && node.childList().size() == 1 && node.getFirstChild() instanceof QFieldKey))
                {
                    FieldKey key = ((QFieldKey) (node instanceof QAs ? node.getFirstChild() : node)).getFieldKey();

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
                        for (String name : r.getAllColumns().keySet())
                        {
                            SelectColumn col = new SelectColumn(new FieldKey(parent, name));
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
        Map<FieldKey, FieldKey> fieldKeys = new HashMap<>();
        for (SelectColumn column : columnList)
        {
            String name = column.getName();
            if (null == name)
                continue;
            if (null == column._key)
                column._key = new FieldKey(null, name);
            if (fieldKeys.containsKey(column._key))
            {
                if (_query.isAllowDuplicateColumns())
                {
                    // Fabricate a unique name for this duplicate column
                    int i = 1;
                    FieldKey parent = column._key.getParent();
                    String n = column._key.getName();
                    FieldKey uniqueKey;
                    do
                    {
                        uniqueKey = new FieldKey(parent, n + "_" + i++);
                    }
                    while (fieldKeys.containsKey(uniqueKey));
                    column._key = uniqueKey;
                    reportWarning("Automatically creating alias for duplicate column: " + name, column._node);
                }
                else
                {
                    parseError("Duplicate column '" + column.getName() + "'", column.getField());
                }
            }
            fieldKeys.put(column._key, column._key);
        }
        for (SelectColumn column : columnList)
        {
            String name = column.getName();
            if (null != name)
                continue;
            while (fieldKeys.containsKey(new FieldKey(null, "Expression" + ++expressionUniq)))
                ;
            name = "Expression" + expressionUniq;
            reportWarning("Automatically creating alias for expression column: " + name, column._node);
            if (null == column._key)
                column._key = new FieldKey(null, name);
            fieldKeys.put(column._key, column._key);
        }
        for (SelectColumn column : columnList)
        {
            if (null == column.getAlias())
                column.setAlias(_aliasManager.decideAlias(column.getName()));
            _columns.put(column._key, column);
        }

        // fix up ORDER BY position and associate each entry with an alias in the columnList
        if (null != _orderBy)
        {
            _sortEntries = _orderBy.getSort();
            for (int i=_sortEntries.size()-1 ; i>=0 ; i--)
            {
                var entry = _sortEntries.get(i);
                if (entry.expr() instanceof QIdentifier qId)
                {
                    _sortEntries.set(i, new QOrder.SortEntry(entry.expr(), entry.direction(), entry.expr().getTokenText()));
                }
                else if (entry.expr() instanceof QNumber qNum)
                {
                    double d = qNum.getValue().doubleValue();
                    int position = qNum.getValue().intValue();
                    if (d == (double)position && position >= 1 && position <= columnList.size())
                    {
                        String alias = columnList.get(position-1).getAlias();
                        QIdentifier qid = new QIdentifier(alias);
                        _sortEntries.set(i, new QOrder.SortEntry(qid, entry.direction(), alias));
                    }
                    else
                    {
                        _sortEntries.remove(i);
                    }
                }
                else if (entry.expr().isConstant())
                {
                    _sortEntries.remove(i);
                }
            }
        }

        initialized = true;
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


    @Override
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

    /* ** Possible way to support SQL Server Median
    public List<SelectColumn> getMedianColumns()
    {
        return _medianColumns;
    }

    public void addMedianColumn(SelectColumn col)
    {
        _medianColumns.add(col);
    }
    */


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
            {
                if (r.getFirstChild().getTokenType() == SqlBaseParser.VALUES)
                    return parseValues(r);
                return parseRange(r);
            }
            else if (r.getTokenType() == SqlBaseParser.JOIN)
                return parseJoin(r);
            else
            {
                parseError("Error in FROM clause", r);
                return null;
            }
        }

        private QTable parseValues(QNode node)
        {
            int countChildren = node.childList().size();
            if (2 < countChildren || !(node.childList().get(0) instanceof QValues))
            {
                parseError("Syntax error in JOIN clause", node);
                return null;
            }
            if (countChildren < 2)
            {
                parseError("VALUES expression requires an alias", node);
                return null;
            }

            List<QNode> children = node.childList();
            QValues values = (QValues) children.get(0);
            QIdentifier alias = (QIdentifier) children.get(1);
            QValuesTable valuesTable = new QValuesTable(QuerySelect.this, values, alias);

            FieldKey aliasKey = valuesTable.getAlias();
            if (_tables.containsKey(aliasKey))
            {
                parseError(aliasKey + " was specified more than once", alias);
            }
            else
            {
                _tables.put(aliasKey, valuesTable);
            }
            return valuesTable;
        }

        private QTable parseRange(QNode node)
        {
            int countChildren = node.childList().size();
            if (countChildren < 1 || 2 < countChildren || !(node.childList().get(0) instanceof QExpr))
            {
                parseError("Syntax error in JOIN clause", node);
                return null;
            }

            List<QNode> children = node.childList();
            QExpr expr = (QExpr) children.get(0);
            QIdentifier alias = null;
            if (children.size() > 1 && children.get(1) instanceof QIdentifier)
                alias = (QIdentifier) children.get(1);

            ContainerFilter.Type cfType = null;
            Map<String, Object> annotations = ((QUnknownNode) node).getAnnotations();
            if (null != annotations)
            {
                for (var entry : annotations.entrySet())
                {
                    var value = entry.getValue();
                    switch (entry.getKey().toLowerCase())
                    {
                        case "containerfilter":
                            if (!(value instanceof String))
                            {
                                _query.getParseErrors().add(new QueryParseException("ContainerFilter annotation requires a string value", null, node.getLine(), node.getColumn()));
                                continue;
                            }
                            cfType = ContainerFilter.getType((String) value);
                            if (null == cfType)
                                _query.getParseErrors().add(new QueryParseException("Unrecognized container filter type: " + value, null, node.getLine(), node.getColumn()));
                            break;
                        default:
                            _query.getParseErrors().add(new QueryParseException("Unknown annotation: " + entry.getKey(), null, node.getLine(), node.getColumn()));
                    }
                }
            }

            QTable table = new QTable(expr, cfType);
            table.setAlias(alias);
            FieldKey aliasKey = table.getAlias();
            if (null == aliasKey)
            {
                table.setAlias(new QIdentifier("_auto_alias_" + _tables.size() + "_"));
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
            if (children.size() < 3 || children.size() > 4)
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
                _ons.add((QExpr) on.childList().get(0));
            }

            if (joinType == JoinType.cross && null != on)
                parseError("ON unexpected in a CROSS JOIN", on);
            else if (joinType != JoinType.cross && null == on)
                parseError("ON expected", rightNode);
            QJoin qjoin = new QJoin(left, right, joinType, null == on ? null : (QExpr) on.childList().get(0));
            return qjoin;
        }
    }


    QueryRelation getTable(FieldKey key)
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

        // Handle possible table or schema-table prefix, #36273
        if (parts.size() >= 2)
        {
            // Attempt to resolve a simple table prefix
            tableKey = new FieldKey(null, parts.get(0));
            table = getTable(tableKey);

            if (table == null)
            {
                // Loop through schema-qualified table keys, attempting to resolve them
                for (FieldKey key : _qualifiedTables.keySet())
                {
                    if (declareKey.startsWith(key))
                    {
                        table = _qualifiedTables.get(key);
                        tableKey = key;
                        break;
                    }
                }
            }

            if (table != null)
                parts.subList(0, tableKey.size()).clear();
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
                else
                {
                    // If I don't 'own' the column, just addRef() to avoid having to try to find this reference later.
                    ret.addRef(this);
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
        if (expr instanceof QIfDefined)
        {
            ((QIfDefined)expr).setQuerySelect(this);
        }

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

        if (!(expr instanceof QResolveTableColumn))  // treat as terminal node even though it has children
        {
            for (QNode child : expr.children())
            {
                declareFields((QExpr) child);
            }
        }
    }


    private boolean _declareCalled = false;

    @Override
    public void declareFields()
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
        if (null != _sortEntries)
        {
            for (var entry : _sortEntries)
            {
                if (entry.expr() instanceof QIdentifier && selectAliases.contains(entry.expr().getTokenText()))
                    continue;
                declareFields(entry.expr());
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

        if (expr.childList().isEmpty())
            return expr;

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
    @Override
    public QueryTableInfo getTableInfo()
    {
        Set<RelationColumn> set = new LinkedHashSet<>(_columns.values());

        getOrderedSuggestedColumns(set);
        if (!getParseErrors().isEmpty())
            return null;

        resolveFields();
        if (!getParseErrors().isEmpty())
            return null;

        // mark all top level columns selected, since we want to generate column info for all columns
        markAllSelected(_query);

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
                        .map(FieldKey::getRootName)
                        .collect(Collectors.toSet());
                    releaseAllSelected(_query);
                    markAllSelected(new CaseInsensitiveHashSet(names),_query);
                }
                else
                    markAllSelected(_query);
                SQLFragment s = getSql();
                if (!getParseErrors().isEmpty())
                    throw getParseErrors().get(0);
                SQLFragment f = new SQLFragment();
                f.append("(").append(s).append(") ").append(alias);

                return f;
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
            var aliasedColumn = new RelationColumnInfo(ret, col);
            ret.addColumn(aliasedColumn);

            if (StringUtils.equalsIgnoreCase(aliasedColumn.getName(),key))
                aliasedColumn.setKeyField(true);
        }
        MemTracker.getInstance().put(ret);
        return ret;
    }


    @Override
    public List<Sort.SortField> getSortFields()
    {
        if (null == _sortEntries || _sortEntries.isEmpty())
            return List.of();

        Set<String> selectAliases = new CaseInsensitiveHashSet();
        for (SelectColumn col : _columns.values())
            if (null != col.getAlias())
                selectAliases.add(col.getAlias());

        List<Sort.SortField> ret = new ArrayList<>();
        for (var entry : _sortEntries)
        {
            if (entry.expr() instanceof QIdentifier qid && selectAliases.contains(qid.getIdentifier()))
            {
                ret.add(new Sort.SortField(new FieldKey(null, qid.getIdentifier()), entry.direction() ? Sort.SortDirection.ASC : Sort.SortDirection.DESC));
            }
            else
            {
                // fail for non-trivial expression
                QExpr r = resolveFields(entry.expr(), _orderBy, _orderBy);
                if (r instanceof QNull)
                    continue;
                return List.of();
            }
        }
        return ret;
    }


    boolean _resolved = false;

    @Override
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
        if (_sortEntries != null)
        {
            for (var entry : _sortEntries)
            {
                if (entry.expr() instanceof QIdentifier && aliasSet.containsKey(entry.expr().getTokenText()))
                    aliasSet.get(entry.expr().getTokenText()).addRef(_orderBy);
                else
                    resolveFields(entry.expr(), _orderBy, _orderBy);
            }
        }
    }


    void resolveFields(QJoinOrTable qt)
    {
        if (qt instanceof QJoin qJoin)
        {
            if (null != qJoin._on)
                resolveFields(qJoin._on, null, qt);
            resolveFields(qJoin._left);
            resolveFields(qJoin._right);
        }
        else if (qt instanceof QValuesTable qValuesTable)
        {
            qValuesTable.getQueryRelation().resolveFields();
        }
        else if (qt instanceof QTable qTable)
        {
            if (qTable.getQueryRelation() instanceof QuerySelect qSelect)
                qSelect.resolveFields();
            else if (qTable.getQueryRelation() instanceof QueryWithWrapper qWith)
                qWith.resolveFields();
        }
        else
        {
            assert false : "need top handle all the cases here";
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


    @Override
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
        if (_parsedJoins.isEmpty() && dialect.isOracle())
        {
            fromSql.append(" sys.dual ");
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
            if (0 == col.ref.count())                        // TODO: may need change for possible way to support SQL Server Median
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

        /* ** Possible way to support SQL Server Median
        String wrapAlias = null;
        if (getMedianColumns().size() > 0)
        {
            wrapAlias = this._aliasManager.decideAlias("_MedianWrap");
            SQLFragment wrapSelect = new SQLFragment("SELECT ");
            String sep = "";
            for (SelectColumn selectColumn : _columns.values())
            {
                String sqlAlias = aliasMap.get(selectColumn.getAlias());
                if (null != sqlAlias)
                {
                    boolean isMedian = getMedianColumns().contains(selectColumn);
                    wrapSelect.append(sep)
                            .append(isMedian ? "MAX(" : "")
                            .append(wrapAlias).append(".").append(sqlAlias)
                            .append(isMedian ? ")" : "")
                            .append(" AS ").append(sqlAlias);
                    sep = ", ";
                }
            }
            wrapSelect.append("\nFROM (\n");
            sql.prepend(wrapSelect);
            sql.append("\n) ").append(wrapAlias).append("\n");
        }
        */

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

                /* ** Possible way to support SQL Server Median
                if (null != wrapAlias)
                {
                    if (gbExpr instanceof QField)
                        sql.append(wrapAlias).append(".").append(getSqlDialect().makeLegalIdentifier(((QField) gbExpr).getName()));
                    else
                        parseError("Cannot generate SQL for Median", expr);
                }
                else
                */

                {
                    gbExpr.appendSql(sql, _query);
                }
                sql.append(")");
                sql.nextPrefix(",");
            }
            sql.popPrefix();
        }
        if (_having != null)
        {
            if (!isAggregate())
                parseError("HAVING requires an aggregate in the SELECT list", _having);

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
        if (_sortEntries != null && !_sortEntries.isEmpty())
        {
            if (_limit == null && !_forceAllowOrderBy && !getSqlDialect().allowSortOnSubqueryWithoutLimit())
            {
                reportWarning("The underlying database does not supported nested ORDER BY unless LIMIT is also specified. Ignoring ORDER BY.", _orderBy);
            }
            else
            {
                sql.pushPrefix("\nORDER BY ");
                for (var entry : _sortEntries)
                {
                    if (entry.expr() instanceof QIdentifier && aliasMap.containsKey(entry.expr().getTokenText()))
                    {
                        sql.append(aliasMap.get(entry.expr().getTokenText()));
                    }
                    else
                    {
                        QExpr r = resolveFields(entry.expr(), _orderBy, _orderBy);
                        if (r instanceof QNull)
                            continue;
                        r.appendSql(sql, _query);
                    }
                    if (!entry.direction())
                        sql.append(" DESC");
                    sql.nextPrefix(",");
                }
                sql.popPrefix();
                if (null == _limit)
                    getSqlDialect().appendSortOnSubqueryWithoutLimitQualifier(sql);
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

	void parseError(String message, QNode node)
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


    @Override
    int getSelectedColumnCount()
    {
        return _columns.size();
    }


    @Override
    SelectColumn getColumn(@NotNull String name)
    {
        FieldKey key = new FieldKey(null,name);
        SelectColumn col = _columns.get(key);
        if (col != null)
            col._selected = true;
        return col;
    }


    @Override
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
        return findColumnInSelectList(in, title);
    }


    @Override
    Collection<String> getKeyColumns()
    {
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


    @Override
    SelectColumn getLookupColumn(@NotNull RelationColumn parentRelCol, @NotNull String name)
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


    public void setSkipSuggestedColumns(boolean skipSuggestedColumns)
    {
        this.skipSuggestedColumns = skipSuggestedColumns;
    }


    @Override
    protected Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        if (skipSuggestedColumns)
            return Collections.emptySet();

        resolveFields();

        if (!getParseErrors().isEmpty())
            return Collections.emptySet();

        if (this.isAggregate() || null != _distinct)
            return Collections.emptySet();
        if (_parent instanceof QueryUnion || _parent instanceof QueryPivot)
            return Collections.emptySet();

        // Using a LinkedHashMap to preserve order of the Query Relations
        Map<QueryRelation, List<RelationColumn>> maps = new LinkedHashMap<>();
        List<RelationColumn> rcs;
        for (SelectColumn sc : _columns.values())
        {
            if (null == sc._field || sc._suggestedColumn)
                continue;
            QExpr expr = sc.getResolvedField();
            if (!(expr instanceof QField))
                continue;
            QField field = (QField)expr;
            if (null == field.getTable() || null == field.getRelationColumn())
                continue;

            rcs = maps.get(field.getTable());
            if (rcs == null)
            {
                rcs = new ArrayList<>();
                rcs.add(field.getRelationColumn());
                maps.put(field.getTable(), rcs);
            }
            else
            {
                rcs.add(field.getRelationColumn());
            }
        }

        Set<RelationColumn> ret = new HashSet<>();
        for (QueryRelation qr : maps.keySet())
        {
            IdentityHashMap<RelationColumn,RelationColumn> h = new IdentityHashMap<>();
            for (RelationColumn rc : maps.get(qr))
                h.put(rc, rc);
            Set<RelationColumn> suggestedColumns = qr.getOrderedSuggestedColumns(h.keySet());
            if (null == suggestedColumns) suggestedColumns = Collections.emptySet();
            for (RelationColumn s : suggestedColumns)
            {
                QField field = new QField(s, null);
                SelectColumn selectColumn = new SelectColumn(field, true);
                selectColumn._suggestedColumn = true;
                selectColumn._selected = true;
                _columns.put(selectColumn.getFieldKey(), selectColumn);
                ret.add(selectColumn);
            }
        }
        return ret;
    }


    @Override
    RelationColumn getLookupColumn(@NotNull RelationColumn parentRelCol, @NotNull ColumnType.Fk fk, @NotNull String name)
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
            {
                _annotations = new CaseInsensitiveHashMap<>(((SupportsAnnotations) node).getAnnotations());
                // concepturi annotation is not supported, if that is considered, make it consistent with BaseColumnInfo.loadFromXML().
                _annotations.remove("concepturi");
            }

            _node = node;
            if (node instanceof QAs && node.childList().size() > 1)
            {
                _field = ((QAs) node).getExpression();
                if (_field instanceof QIfDefined)
                    ((QIfDefined)_field).setQuerySelect(QuerySelect.this);
                FieldKey key = _field.getFieldKey();
                if (null != key && key.getName().equals("*"))
                    parseError("* expression can not be aliased", node);
                _aliasId = ((QAs) node).getAlias();
            }
            else
            {
                if (node instanceof QAs)
                    node = node.getFirstChild();
                if (node instanceof QIfDefined)
                    ((QIfDefined)node).setQuerySelect(QuerySelect.this);
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

        @Override
        SQLFragment getInternalSql()
        {
            SqlBuilder b = new SqlBuilder(getDbSchema());
            QExpr expr = getResolvedField();

            // NOTE SqlServer does not like predicates (A=B) in select list, try to help out
            if (expr instanceof QMethodCall && expr.getJdbcType() == JdbcType.BOOLEAN && b.getDialect().isSqlServer())
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

            expr.appendSql(b, _query); //, QuerySelect.this, this);  // Possible way to support SQL Server Median
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

        @Override
        public String getAlias()
        {
            return _alias;
        }

        @Override
        QueryRelation getTable()
        {
            return QuerySelect.this;
        }

        @Override
        @NotNull
        public JdbcType getJdbcType()
        {
            var resolved = getResolvedField();
            return null != resolved ? resolved.getJdbcType() : JdbcType.NULL;
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
        String getPrincipalConceptCode()
        {
            QExpr expr = getResolvedField();
            if (expr instanceof QField)
            {
                return ((QField)expr).getRelationColumn().getPrincipalConceptCode();
            }
            return null;
        }

        @Override
        String getConceptURI()
        {
            QExpr expr = getResolvedField();
            if (expr instanceof QField)
            {
                return ((QField)expr).getRelationColumn().getConceptURI();
            }
            return null;
        }

        @Override
        void copyColumnAttributesTo(@NotNull BaseColumnInfo to)
        {
            Objects.requireNonNull(to);
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
                    // NOTE If we're here then this is not a top-level output column. The name doesn't matter.
                    _colinfo = expr.createColumnInfo(_sti, "/*NOT AN OUTPUT COLUMN*/", _query);
                }
                to.copyAttributesFrom(_colinfo);
                if (_selectStarColumn)
                    to.setHidden(_colinfo.isHidden());
            }

            String label=chooseLabel();
            if (label != null)
                to.setLabel(label);

            if (null == _annotations)
                _annotations = Collections.emptyMap();

            if (_annotations.containsKey("concept"))
            {
                Object c = _annotations.get("concept");
                to.setPrincipalConceptCode(null==c ? null : StringUtils.trimToNull(c.toString()));
            }

            boolean hidden = _annotations.containsKey("hidden");
            if (hidden)
                to.setHidden(hidden);

            // does not remove the FK, just changes display behaviour
            boolean nolookup = _annotations.containsKey("nolookup");
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
        _sortEntries = null;
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
        if (_sortEntries != null)
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
            // annotations don't affect the generated SQL, but we don't want to lose them
            if (null != c._annotations && !c._annotations.isEmpty())
                return false;
        }

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


    // A CTE can be used more than once in FROM, but we can't have two FROM entries point to same QueryRelation, because
    // unlike TableInfo we expect the QueryRelation to know it's own Alias (mistake?)
    private static class QueryWithWrapper extends QueryLookupWrapper
    {
        String cteAlias;

        QueryWithWrapper(CommonTableExpressions.QueryTableWith wrapped, String alias)
        {
            super(wrapped._query, wrapped, null);
            _alias = alias;
            cteAlias = alias;
        }

        @Override
        protected void setAlias(String alias)
        {
            cteAlias = alias;
        }

        @Override
        public SQLFragment getFromSql()
        {
            // Ideally we wouldn't change the alias of the shared QueryTableWith, but let's at least restore it
            // We lie and say that we have lookups, to force QLW to wrap the inner SQL.  See comment above,
            // This is awkward because we have QueryRelation objects remember their own aliases.
            String savedSourceAlias = _source.getAlias();
            setHasLookup();
            try
            {
                super.setAlias(cteAlias);
                return super.getFromSql();
            }
            finally
            {
                _source.setAlias(savedSourceAlias);
            }
        }

        @Override
        protected void resolveFields()
        {
            super.resolveFields();
        }
    }
}
