/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.AggregateColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CrosstabDimension;
import org.labkey.api.data.CrosstabMeasure;
import org.labkey.api.data.CrosstabMember;
import org.labkey.api.data.CrosstabSettings;
import org.labkey.api.data.CrosstabTableInfo;
import org.labkey.api.data.Filter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.data.xml.ColumnType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: Jan 12, 2011
 * Time: 11:47:53 AM
 */
public class QueryPivot extends QueryRelation
{
    final QuerySelect _from;
    final AliasManager _manager;
    final Map<FieldKey,String> pivotColumnAliases = new HashMap<>();

    // all columns in the select except the pivot column, in original order
    LinkedHashMap<String,RelationColumn> _select = new LinkedHashMap<>();

    // the grouping keys (except the pivot column) (aka. row axis CrosstabDimension)
    final HashMap<String,RelationColumn> _grouping = new HashMap<>();

    // pivoted aggregate columms (aka. CrosstabMeasure)
    final Map<String,QAggregate.Type> _aggregates = new HashMap<>();

    // the pivot column (aka. column axis CrosstabDimension)
    RelationColumn _pivotColumn;
    Map<String,IConstant> _pivotValues;

    QueryRelation _inQuery;


    public QueryPivot(Query query, QuerySelect from, QQuery root)
    {
        super(query);

        // We may need to modify the QuerySelect a little
        // make sure all group by columns are selected
        // grab orderby and limit
        _from = from;
        _manager = new AliasManager(query.getSchema().getDbSchema());

        QPivot pivotClause = root.getChildOfType(QPivot.class);
        QNode aggsList = pivotClause.childList().get(0);
        QIdentifier byId = (QIdentifier)pivotClause.childList().get(1);
        QNode inList = pivotClause.childList().size() > 2 ? pivotClause.childList().get(2) : null;

        // this column is not selected, its values become columns
        _pivotColumn = _from.getColumn(byId.getIdentifier());
        if (null == _pivotColumn)
        {
            parseError("Can not find pivot column: " + byId.getIdentifier(), byId);
            return;
        }

        // get all the columns, but delete the pivot column
        Map<String,RelationColumn> allFromColumns = _from.getAllColumns();
        for (RelationColumn r : allFromColumns.values())
            _manager.claimAlias(r.getAlias(), null);
        _select = new LinkedHashMap<>();
        _select.putAll(allFromColumns);
        _select.remove(_pivotColumn.getFieldKey().getName());

        // modify QuerySelect _from (after we copy the original select list)
        prepareQuerySelect();
        if (!getParseErrors().isEmpty())
            return;

        // inspect the agg columns
        for (QNode node : aggsList.childList())
        {
            QIdentifier id = (QIdentifier)node;
            QuerySelect.SelectColumn col = _from.getColumn(id.getIdentifier());
            if (null == col)
            {
                parseError("Can not find column in select list: " + id.getIdentifier(), id);
                continue;
            }
            QNode source = col._node;
            if (source instanceof QAs)
                source = source.childList().get(0);
            QAggregate.Type rollupType = null;
            if (source instanceof QAggregate)
            {
                switch (((QAggregate) source).getType())
                {
                    case COUNT:
                    case SUM:
                        rollupType = QAggregate.Type.SUM;
                        break;
                    case MIN:
                        rollupType = QAggregate.Type.MIN;
                        break;
                    case MAX:
                        rollupType = QAggregate.Type.MAX;
                        break;
                    case AVG:
                    case STDDEV:
                    case STDERR:
                    case GROUP_CONCAT:
                        // translate AVG() -> SUM(sum())/SUM(count())
                        // etc.
                        break;
                }
            }
            _aggregates.put(col.getFieldKey().getName(), rollupType);
        }

        if (inList instanceof QQuery)
        {
            _inQuery = createSubquery((QQuery)inList, "_pivotValues");
            QuerySelect qs = _inQuery instanceof QuerySelect ?
                    (QuerySelect)_inQuery :
                    _inQuery instanceof QueryLookupWrapper ?
                    (QuerySelect)((QueryLookupWrapper)_inQuery)._source :
                    null;
            if (null != qs)
                qs._forceAllowOrderBy = true;
            // call getSql() here to generate errors (quick fail)
            _inQuery.getSql();
        }
        else if (inList instanceof QSelect)
        {
            // simple in list
            CaseInsensitiveMapWrapper<IConstant> pivotValues = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<String,IConstant>());
            for (QNode node : inList.childList())
            {
                IConstant constant;
                String name = null;

                if (node instanceof IConstant)
                    constant = (IConstant) node;
                else
                {
                    assert node instanceof QAs;
                    constant = (IConstant)node.childList().get(0);
                    if (node.childList().size() > 1)
                        name = ((QIdentifier)node.childList().get(1)).getIdentifier();
                }
                if (null == name)
                    name = toName(constant);

                // TODO check duplicate values as well as duplicate names
                if (pivotValues.containsKey(name))
                    parseError("Duplicate pivot column name: " + name + ".\nColumn names are case-insensitve, you may need to use lower() or upper() in your query to work around this.", node);
                else
                    pivotValues.put(name, constant);
            }
            _pivotValues = pivotValues;
        }
    }


    QueryRelation createSubquery(QQuery qquery, String alias)
    {
        QueryRelation sub = Query.createQueryRelation(this._query, qquery, true);
        sub._parent = this;
        if (null != alias)
            sub.setAlias(alias);
        return sub;
    }


    void prepareQuerySelect()
    {
        // undone copy order by
        // undone copy limit
        _from._limit = null;
        _from._orderBy = null;

        // make sure all group by columns are selected
        Map<String, QuerySelect.SelectColumn> map = _from.getGroupByColumns();
        for (QuerySelect.SelectColumn c : map.values())
            c.addRef(this);

        boolean pivotFound = false;
        for (Map.Entry<String, QuerySelect.SelectColumn> entry : map.entrySet())
        {
            if (entry.getValue() == _pivotColumn)
            {
                pivotFound = true;
                continue;
            }
            _grouping.put(entry.getKey(), entry.getValue());
            // find/verify
        }
        if (!pivotFound)
            parseError("Could not find pivot column in group by list, expression must match exactly: " + _pivotColumn.getAlias(), null);
    }



    Map<String,IConstant> getPivotValues() throws SQLException
    {
        if (null != _pivotValues)
            return _pivotValues;

        _pivotValues = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<String,IConstant>());
        SQLFragment sqlPivotValues = null;

        if (null != _inQuery)
        {
            // explicit subquery case
            sqlPivotValues = _inQuery.getSql();
        }
        else
        {
            SQLFragment fromSql;
            if (1==1)   // optimized version
            {
                // if our optimizations get more clever, we may need to implement deepClone()
                QuerySelect fromForPivotValues = _from.shallowClone();
                fromForPivotValues.releaseAllSelected(this);
                if (null != fromForPivotValues._distinct)
                {
                    fromForPivotValues.releaseAllSelected(this);
                    fromForPivotValues._distinct = null;
                }
                _pivotColumn.addRef(this);
                if (null == fromForPivotValues._having)
                {
                    fromForPivotValues._groupBy.releaseFieldRefs(fromForPivotValues._groupBy);
                    fromForPivotValues._groupBy = null;
                }
                fromForPivotValues._allowStructuralOptimization = false;
                fromSql = fromForPivotValues.getFromSql();
                // the fields and columns are shared, to be safe fix up reference counts
                _from._groupBy.addFieldRefs(_from._groupBy);
                if (null != _from._distinct)
                    _from.markAllSelected(_from._distinct);
                _from.markAllSelected(this);
            }
            else
            {
                fromSql = _from.getFromSql();
            }
            if (null != fromSql)
            {
                sqlPivotValues = new SQLFragment();
                sqlPivotValues.append("SELECT DISTINCT ").append(_pivotColumn.getValueSql());
                sqlPivotValues.append("\nFROM ").append(fromSql);
                sqlPivotValues.append("\nORDER BY 1 ASC");
            }
        }
        if (null == sqlPivotValues)
        {
            // If there are errors, it will get handled later
            assert !getParseErrors().isEmpty();
            return _pivotValues;
        }

        // We've directly generated SQL instead of using QueryService, so be sure that the listeners
        // get the context they need before trying to run it
        QueryProfiler.getInstance().ensureListenerEnvironment();

        try (ResultSet rs = new SqlSelector(getSchema().getDbSchema(), sqlPivotValues).getResultSet())
        {
            JdbcType type = JdbcType.valueOf(rs.getMetaData().getColumnType(1));
            int columnCount = rs.getMetaData().getColumnCount();
            while (rs.next())
            {
                Object value = rs.getObject(1);
                IConstant wrap = wrapConstant(value, type, rs.wasNull());
                String name = columnCount > 1 ? toName(rs.getString(2)) : toName(wrap);
                // CONSIDER: error on name collision
                _pivotValues.put(name, wrap);
            }
        }
        catch (QueryService.NamedParameterNotProvided npnp)
        {
            parseError("When used with parameterized query, PIVOT requires an explicit values list", null);
            parseError(npnp.getMessage(), null);
        }
        catch (UnauthorizedException e)
        {
            parseError("Pivot query unauthorized.", null);
        }

        return _pivotValues;
    }


    String toName(IConstant c)
    {
        if (c instanceof QNull)
            return "NULL";
        Object v = c.getValue();
        if (v instanceof String)
            return (String)v;
        return ConvertUtils.convert(v);
    }

    String toName(String s)
    {
        return StringUtils.defaultString(s, "NULL");
    }


    IConstant wrapConstant(Object v, JdbcType t, boolean wasNull)
    {
        if (wasNull)
            return new QNull();
        if (t == JdbcType.BOOLEAN)
        {
            assert v instanceof Boolean;
            return new QBoolean(v==Boolean.TRUE);
        }
        if (v instanceof Number)
        {
            return new QNumber((Number)v);
        }
        if (!(v instanceof String))
            v = ConvertUtils.convert(v);
        return new QString((String)v);
    }


    @Override
    void declareFields()
    {
        _from.declareFields();
    }


    @Override
    protected void resolveFields()
    {
        _inQuery.resolveFields();
    }


    @Override
    TableInfo getTableInfo()
    {
        QueryTableInfo qti = new PivotTableInfo();
        if (!getParseErrors().isEmpty())
            return null;

        try
        {
            getPivotValues();
        }
        catch (QueryService.NamedParameterNotProvided x)
        {
            parseError("When used with parameterized query, PIVOT requires an explicit values list", null);
            parseError(x.getMessage(), null);
            return null;
        }
        catch (SQLException x)
        {
            parseError("Could not compute pivot column list", null);
            return null;
        }
        return qti;
    }


    class PivotColumn extends RelationColumn
    {
        RelationColumn _s;

        PivotColumn(RelationColumn s)
        {
            _s = s;
        }

        @Override
        SQLFragment getInternalSql()
        {
            SQLFragment ret = _s.getInternalSql();
            return ret;
        }

        // wrapper methods

        @Override
        public FieldKey getFieldKey()
        {
            return _s.getFieldKey();
        }

        @Override
        String getAlias()
        {
            return _s.getAlias();
        }

        @Override
        QueryRelation getTable()
        {
            return QueryPivot.this;
        }

        @NotNull
        @Override
        public JdbcType getJdbcType()
        {
            return _s.getJdbcType();
        }

        @Override
        public ForeignKey getFk()
        {
            return _s.getFk();
        }

        @Override
        boolean isHidden()
        {
            return _s.isHidden();
        }

        @Override
        void copyColumnAttributesTo(ColumnInfo to)
        {
            _s.copyColumnAttributesTo(to);
            if (_aggregates.containsKey(_s.getFieldKey().getName()))
            {
                to.setHidden(true);
            }
        }
    }


    Map<String,RelationColumn> _columns = null;

    @Override
    protected Map<String, RelationColumn> getAllColumns()
    {
        if (null == _columns)
        {
            Map<String, IConstant> pivotValues;
            try
            {
                // UNDONE: _from.getSql() has side-effect that seems to be important for declareFields()
                _from.markAllSelected(this);
                _from.getSql();

                pivotValues = getPivotValues();
            }
            catch (SQLException x)
            {
                assert(!getParseErrors().isEmpty());
                if (getParseErrors().isEmpty())
                    getParseErrors().add(new QueryException(getSqlDialect().sanitizeException(x), x));
                pivotValues = Collections.EMPTY_MAP;
            }

            _columns = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<String, RelationColumn>(_select.size()*2));
            List<Map.Entry<String,RelationColumn>> aggs = new ArrayList<>(_aggregates.size());
            for (Map.Entry<String,RelationColumn> entry : _select.entrySet())
            {
                String name = entry.getKey();
                RelationColumn s = entry.getValue();
                RelationColumn p = new PivotColumn(s);
                _columns.put(name, p);

                if (_aggregates.containsKey(name))
                {
                    aggs.add(entry);
                }
            }

            // Add the pivoted aggregate columns grouped by pivot value
            if (!aggs.isEmpty())
            {
                for (String pivotValue : pivotValues.keySet())
                {
                    for (Map.Entry<String, RelationColumn> entry : aggs)
                    {
                        String name = entry.getKey();
                        RelationColumn s = entry.getValue();

                        String pivotName = makePivotAggName(name, pivotValue);
                        RelationColumn pvt = _makePivotedAggColumn(s, new FieldKey(null, pivotName), pivotValue);
                        _columns.put(pivotName, pvt);
                    }
                }
            }
        }
        return _columns;
    }


    final static String PIVOT_SEPARATOR = "::";

    // NOTE: See AggregateColumnInfo.getColumnName(pivotValue, aggName) for magic column names used by CrosstabView.
    String makePivotAggName(String aggName, String pivotValue)
    {
        return pivotValue + PIVOT_SEPARATOR + aggName;
    }
    

    @Override
    RelationColumn getColumn(@NotNull String name)
    {
        return getAllColumns().get(name);
    }

    @Override
    int getSelectedColumnCount()
    {
        return _select.size();
    }

    @Override
    RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull final String name)
    {
        if (!(parent instanceof PivotColumn))
            return null;
        RelationColumn agg = ((PivotColumn)parent)._s;
        return _makePivotedAggColumn(agg, new FieldKey(parent.getFieldKey(), name), name);
    }


    private RelationColumn _makePivotedAggColumn(@NotNull final RelationColumn agg, final FieldKey key, @NotNull final String name)
    {
        if (!_aggregates.containsKey(agg.getFieldKey().getName()))
            return null;

        Map<String, IConstant> pivotValues;
        try
        {
            pivotValues = getPivotValues();
        }
        catch (SQLException x)
        {
            return null;
        }

        if (null == pivotValues)
        {
            assert(!getParseErrors().isEmpty());
            return null;
        }

        final IConstant c = pivotValues.get(name);
        final String alias = makePivotColumnAlias(agg.getAlias(), name);

        final CrosstabMember member = createCrosstabMember(c.getValue(), name);

        return new RelationColumn()
        {
            @Override
            public FieldKey getFieldKey()
            {
                return key;
            }

            @Override
            String getAlias()
            {
                return alias;
            }

            @Override
            boolean isHidden()
            {
                return agg.isHidden();
            }

            @Override
            QueryRelation getTable()
            {
                return QueryPivot.this;
            }

            @NotNull
            @Override
            public JdbcType getJdbcType()
            {
                return agg.getJdbcType();
            }

            @Override
            void copyColumnAttributesTo(ColumnInfo to)
            {
                agg.copyColumnAttributesTo(to);

                to.setCrosstabColumnDimension(agg.getFieldKey());
                to.setCrosstabColumnMember(member);

                if (_aggregates.size() == 1)
                    to.setLabel(name);
                else
                {
                    String aggLabel = to.getLabel();
                    to.setLabel(name + " " + aggLabel);
                }
            }

            @Override
            public SQLFragment getValueSql()
            {
                if (null == c)
                    return new SQLFragment(NullColumnInfo.nullValue(getSqlDialect().sqlTypeNameFromJdbcType(getJdbcType())));
                else
                    return new SQLFragment(getTable().getAlias() + "." + alias);
            }
        };
    }



    @Override
    RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull ColumnType.Fk fk, @NotNull String name)
    {
        return null;
    }

    
    @Override
    public SQLFragment getSql()
    {
        String tableAlias = "_t";
        SqlBuilder sql = new SqlBuilder(getSqlDialect());
        sql.appendComment("<QueryPivot>", getSqlDialect());
        sql.append("SELECT ");
        String comma = "";

        for (RelationColumn col : _select.values())
        {
            String name = col.getFieldKey().getName();
            boolean isAgg = _aggregates.containsKey(name);
            boolean isKey = _grouping.containsKey(name);

            if (isKey)
            {
                sql.append(comma).append(col.getValueSql()).append(" AS ").append(col.getAlias());
                comma = ",\n";
                continue;
            }

            // Add aggregate around summary columns -- the non-key and non-pivoted-aggregates
            if (!isAgg)
            {
                String wrappedAgg = "MAX";
                if (col instanceof QuerySelect.SelectColumn && ((QuerySelect.SelectColumn)col)._field instanceof QAggregate)
                {
                    QAggregate aggregate = (QAggregate)((QuerySelect.SelectColumn)col)._field;
                    switch (aggregate.getType())
                    {
                        case COUNT:
                            wrappedAgg = "SUM";
                            if (aggregate.isDistinct())
                            {
                                _query.reportError("Can't use 'COUNT(DISTINCT)' in summary column '" + ((QuerySelect.SelectColumn) col).getName() + "'.  " +
                                        "Either remove this column from the select list; change the aggregate to SUM, MIN, MAX; or add this column to the PIVOT BY column set.");
                            }
                            break;
                        case SUM:
                            wrappedAgg = "SUM";
                            break;
                        case MIN:
                            wrappedAgg = "MIN";
                            break;
                        case MAX:
                            wrappedAgg = "MAX";
                            break;
                        case AVG:
                        case STDDEV:
                        case STDERR:
                        case GROUP_CONCAT:
                        default:
                            // illegal
                            _query.reportError("Can't use '" + aggregate.getType().name() + "' in summary column '" + ((QuerySelect.SelectColumn) col).getName() + "'.  " +
                                    "Either remove this column from the select list; change the aggregate to SUM, MIN, MAX; or add this column to the PIVOT BY column set.");
                            break;
                    }
                }

                sql.append(comma).append(wrappedAgg).append("(").append(col.getValueSql()).append(") AS ").append(col.getAlias());
                comma = ",\n";
                continue;
            }

            QAggregate.Type type = _aggregates.get(name);
            if (null == type)
            {
                sql.append(comma).append("NULL AS ").append(col.getAlias());
                comma = ",\n";
            }
            else
            {
                String agg = type.name();
                sql.append(comma).append(agg).append("(").append(col.getValueSql()).append(") AS ").append(col.getAlias());
                comma = ",\n";
            }

            // getSql() does not return
            Map<String,IConstant> pivotValues;
            try
            {
                pivotValues = getPivotValues();
            }
            catch (QueryService.NamedParameterNotProvided x)
            {
                QueryParseException qpe = new QueryParseException("When used with parameterized query, PIVOT requires an explicit values list", null);
                _query.decorateException(qpe);
                throw qpe;
            }
            catch (SQLException x)
            {
                QueryParseException qpe = new QueryParseException("Could not compute pivot column list", null);
                _query.decorateException(qpe);
                throw qpe;
            }
            
            // add aggregate expressions
            for (Map.Entry<String,IConstant> pivotValue : pivotValues.entrySet())
            {
                QNode value = (QNode)pivotValue.getValue();
                String alias = makePivotColumnAlias(col.getAlias(), pivotValue.getKey());
                sql.append(comma).append("MAX(CASE WHEN (").append(_pivotColumn.getValueSql());
                if (value instanceof QNull)
                    sql.append(" IS NULL");
                else
                    sql.append("=").append(value.getSourceText());
                sql.append(") THEN (").append(col.getValueSql()).append(") ELSE NULL END) AS ").append(alias);
                comma = ",\n";
            }
        }

        _from.markAllSelected(this);
        SQLFragment fromSql = _from.getFromSql();
        if (null == fromSql)
        {
            assert !getParseErrors().isEmpty();
            return null;
        }
        sql.append("\n FROM ").append(fromSql).append("\n");

        // UNDONE: separate grouping columns from extra 'fact' columns
        sql.pushPrefix("GROUP BY ");
        for (RelationColumn col : _grouping.values())
        {
            if (_aggregates.containsKey(col.getFieldKey().getName()))
                continue;
            sql.append(col.getValueSql());
            sql.nextPrefix(",");
        }
        sql.appendComment("</QueryPivot>", getSqlDialect());
        return sql;
    }


    @Override
    String getQueryText()
    {
        return null;
    }


    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        _from.setContainerFilter(containerFilter);
    }


    @Override
    protected Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        return Collections.emptySet();
    }


    class PivotTableInfo extends QueryTableInfo implements CrosstabTableInfo
    {
        PivotTableInfo()
        {
            super(QueryPivot.this, "_pivot");

            for (RelationColumn col : getAllColumns().values())
            {
                String name = col.getFieldKey().getName();
                ColumnInfo columnInfo = new RelationColumnInfo(this, col);
                addColumn(columnInfo);
            }
        }

        SQLFragment _sqlPivot = null;

        @NotNull
        @Override
        public SQLFragment getFromSQL(String pivotTableAlias)
        {
            if (null != _sqlPivot)
                return _sqlPivot;
            SQLFragment f = new SQLFragment();
            SQLFragment fromSql = getSql();
            if (null == fromSql)
            {
                if (!getParseErrors().isEmpty())
                    throw getParseErrors().get(0);
                QueryParseException qpe = new QueryParseException("Error compiling query" + (null!=_query._name?": " + _query._name:""), null, 0, 0);
                _query.decorateException(qpe);
                throw qpe;
            }
            f.append("(").append(fromSql).append(") ").append(pivotTableAlias);
            _sqlPivot = f;
            return _sqlPivot;
        }


        @Override
        public List<FieldKey> getDefaultVisibleColumns()
        {
            ArrayList<FieldKey> list = new ArrayList<>();

            for (ColumnInfo col : getColumns())
            {
                if (_aggregates.containsKey(col.getFieldKey().getName()))
                    continue;
                if (col.isHidden())
                    continue;
                list.add(col.getFieldKey());
            }
            return list;
        }

        //
        // CrosstabTableInfo
        //

        // XXX: make immutable ?
        CrosstabSettings _settings;
        List<CrosstabMember> _members;

        @Override
        public CrosstabSettings getSettings()
        {
            if (_settings == null)
            {
                // XXX: Should source table be the from clause or the PivotTable?
                TableInfo fromTable = _from.getTableInfo();
                _settings = new CrosstabSettings(fromTable);

                // row axis dimensions are the group by columns
                for (RelationColumn col : _grouping.values())
                {
                    _settings.getRowAxis().addDimension(col.getFieldKey());
                }
                //_settings.getRowAxis().setCaption("grouping cols description");

                // column axis dimension is the pivot column
                // XXX: Is it safe to get the pivot ColumnInfo here so we can copy label and URL metadata to CrosstabDimension?
                ColumnInfo pivotColumnInfo = fromTable.getColumn(_pivotColumn.getFieldKey());
                String pivotURL = null;
                String pivotLabel = _pivotColumn.getAlias();
                if (pivotColumnInfo != null)
                {
                    if (pivotColumnInfo.getURL() != null)
                        pivotURL = pivotColumnInfo.getURL().toString();
                    if (pivotColumnInfo.getLabel() != null)
                        pivotLabel = pivotColumnInfo.getLabel();
                }
                CrosstabDimension dim = _settings.getColumnAxis().addDimension(_pivotColumn.getFieldKey());
                if (pivotURL != null)
                    dim.setUrl(pivotURL);
                _settings.getColumnAxis().setCaption(pivotLabel);

                // aggregates are the crosstab measure
                for (Map.Entry<String, QAggregate.Type> agg : _aggregates.entrySet())
                {
                    String name = agg.getKey();
                    CrosstabMeasure.AggregateFunction aggFn = toAggFn(agg.getValue());
                    _settings.addMeasure(FieldKey.fromParts(name), aggFn, name);
                }
            }
            return _settings;
        }

        @Override
        public List<CrosstabMember> getColMembers()
        {
            if (_members == null)
            {
                Map<String, IConstant> pivotValues;
                try
                {
                    pivotValues = getPivotValues();
                }
                catch (SQLException x)
                {
                    // UNDONE: need better error handling -- see other usages of getPivotValues()
                    throw new RuntimeSQLException(x);
                }

                _members = new ArrayList<>(pivotValues.size());
                for (Map.Entry<String, IConstant> pivotValue : pivotValues.entrySet())
                {
                    Object value = pivotValue.getValue().getValue();
                    String caption = pivotValue.getKey();
                    CrosstabMember member = createCrosstabMember(value, caption);
                    _members.add(member);
                }
            }
            return _members;
        }

        @Override
        public CrosstabMeasure getMeasureFromKey(String fieldKey)
        {
            for (CrosstabMeasure measure : getSettings().getMeasures())
            {
                if (AggregateColumnInfo.getColumnName(null, measure).equals(fieldKey))
                    return measure;
            }
            return null;
        }

        @Override
        public void setAggregateFilter(Filter filter)
        {
            // UNDONE
        }

        @Override
        public Sort getDefaultSort()
        {
            return new Sort();
        }

    }

    private CrosstabMeasure.AggregateFunction toAggFn(QAggregate.Type aggType)
    {
        if (aggType == null)
            return null;

        switch (aggType)
        {
            case AVG:           return CrosstabMeasure.AggregateFunction.AVG;
            case COUNT:         return CrosstabMeasure.AggregateFunction.COUNT;
            case GROUP_CONCAT:  return CrosstabMeasure.AggregateFunction.GROUP_CONCAT;
            case MAX:           return CrosstabMeasure.AggregateFunction.MAX;
            case MIN:           return CrosstabMeasure.AggregateFunction.MIN;
            case STDDEV:        return CrosstabMeasure.AggregateFunction.STDDEV;
            case STDERR:        return CrosstabMeasure.AggregateFunction.STDERR;
            case SUM:           return CrosstabMeasure.AggregateFunction.SUM;
            default:
                throw new IllegalArgumentException(aggType.toString());
        }
    }

    private CrosstabMember createCrosstabMember(Object value, String caption)
    {
        return new CrosstabMember(value, _pivotColumn.getFieldKey(), caption);
    }


    SqlDialect _dialect;
    
    SqlDialect getSqlDialect()
    {
        if (null == _dialect)
            _dialect = _query.getSchema().getDbSchema().getSqlDialect();
        return _dialect;
    }


    
    // We could use aliasManager and remember these
    // but its easier if we can generate names we expect to be unique
    String makePivotColumnAlias(String aggAlias, String pivotValueName)
    {
        FieldKey key = FieldKey.fromParts(aggAlias, pivotValueName.toLowerCase());
        String alias =  pivotColumnAliases.get(key);
        if (null != alias)
            return alias;
        alias =  _manager.decideAlias("__pvt_" + _query.incrementAliasCounter() + "_" + key.toString());
        pivotColumnAliases.put(key,alias);
        return alias;
    }


    // marker interface
    // ForeignKey whose lookup columns are carried along with the fk
    interface ExpandoForeignKey {}


    class PivotForeignKey implements ForeignKey, ExpandoForeignKey
    {
        RelationColumn _agg;

        PivotForeignKey(RelationColumn agg)
        {
            _agg = agg;
        }
        
        @Override
        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            if (null == displayField)
                return null;
            RelationColumn lk = _makePivotedAggColumn(_agg, new FieldKey(parent.getFieldKey(), displayField), displayField);
            ColumnInfo col = new RelationColumnInfo(parent.getParentTable(), lk);
            return col;
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            AbstractTableInfo t = new AbstractTableInfo(_query.getSchema().getDbSchema(), null)
            {
                @Override
                protected SQLFragment getFromSQL()
                {
                    return null;
                }

                @Override
                public UserSchema getUserSchema()
                {
                    return null;
                }
            };
            Map<String, IConstant> pivotValues;
            try
            {
                pivotValues = getPivotValues();
            }
            catch (SQLException x)
            {
                return null;
            }
            if (null == pivotValues)
            {
                assert(!getParseErrors().isEmpty());
                return null;
            }
            for (String displayField : pivotValues.keySet())
            {
                ColumnInfo c = new RelationColumnInfo(t, _agg);
                c.setName(displayField);
                c.setLabel(displayField);
                t.addColumn(c);
            }
            return t;
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        @Override
        public NamedObjectList getSelectList(RenderContext ctx)
        {
            return null;
        }

        @Override
        public Container getLookupContainer()
        {
            return null;
        }

        @Override
        public String getLookupTableName()
        {
            return null;
        }

        @Override
        public String getLookupSchemaName()
        {
            return null;
        }

        @Override
        public String getLookupColumnName()
        {
            return null;
        }

        @Override
        public String getLookupDisplayName()
        {
            return null;
        }

        @Override
        public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
        {
            return this;
        }

        @Override
        public Set<FieldKey> getSuggestedColumns()
        {
            return null;
        }
    }

    private void parseError(String message, QNode node)
    {
        Query.parseError(getParseErrors(), message, node);
    }
}


/***  TODO
  case-sensitive grouping in Postgres
  non-string pivot column (allow?)
  QuerySelect.getGroupByColumns() after resolveFields() would give better expression matching
  NOTE bugs with postgres < 8.3.7?
***/
