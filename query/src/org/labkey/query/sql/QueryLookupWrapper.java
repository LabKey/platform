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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CrosstabMeasure;
import org.labkey.api.data.CrosstabMember;
import org.labkey.api.data.CrosstabSettings;
import org.labkey.api.data.CrosstabTableInfo;
import org.labkey.api.data.Filter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseWarning;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.MemTracker;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * User: matthewb
 * Date: Mar 9, 2009
 * Time: 1:56:39 PM
 *
 *
 * If the metadata editor has been used to set additional lookup properties
 * on a query result, we can use this wrapper to handle those additional joins.
 *
 * This lets us avoid having to handle lookups in every QueryRelation implementation.
 *
 * QueryLookupWrapper is actually very similar to QueryTable, the difference being that
 * the input to QueryLookupWrapper is a QueryRelation rather than a TableInfo
 */
public class QueryLookupWrapper extends AbstractQueryRelation implements QueryRelation.ColumnResolvingRelation
{
    private static final Logger _log = LogManager.getLogger(QueryLookupWrapper.class);

    final AliasManager _aliasManager;
    AbstractQueryRelation _source;
    boolean _hasLookups = false;

    Map<String, ColumnType> _columnMetaDataMap = new CaseInsensitiveHashMap<>();
    Map<String, ColumnType.Fk> _fkMap = new CaseInsensitiveHashMap<>();
    ArrayListMap<FieldKey, QLWColumn> _selectedColumns = new ArrayListMap<>();

    // shim for creating lookup columns w/o a real TableInfo
    SQLTableInfo _sti = null;


    QueryLookupWrapper(Query query, AbstractQueryRelation relation, @Nullable TableType md)
    {
        super(query);
        _aliasManager = new AliasManager(query.getSchema().getDbSchema());
        _alias = "qlw" + relation.getAlias();
        _source = relation;
        _inFromClause = relation._inFromClause;
        relation._parent = this;

        for (var col : relation.getAllColumns().values())
        {
            PassThroughColumn pt = new PassThroughColumn(col.getFieldKey(), col);
            _selectedColumns.put(pt.getFieldKey(), pt);
        }

        // add ref to first column, this may be required for expressions such as WHERE IN (SELECT ...)
        // we use _query as the referer to make sure this ref does not go away
        if (!_selectedColumns.isEmpty())
            _selectedColumns.get(0).addRef(_query);

        org.labkey.data.xml.TableType.Columns cols = null==md ? null : md.getColumns();
        if (null != cols)
        {
            for (ColumnType col : cols.getColumnArray())
            {
                _columnMetaDataMap.put(col.getColumnName(), col);
                ColumnType.Fk colFK = col.getFk();
                if (null == colFK || null == colFK.getFkTable() || null == colFK.getFkColumnName())
                    continue;
                _fkMap.put(col.getColumnName(), colFK);
            }
        }
    }


    @Override
    public void setQuery(Query query)
    {
        super.setQuery(query);
        _source.setQuery(query);
    }


    @Override
    public void setAlias(String alias)
    {
        super.setAlias(alias);
        if (_hasLookups)
            _source.setAlias(getAlias() + "Wrapped");
        else
            _source.setAlias(alias);
    }


    protected void setHasLookup()
    {
        _hasLookups = true;
        _source.setAlias(getAlias() + "Wrapped");
    }

    
    @Override
    public void declareFields()
    {
        _log.debug("declareFields " + this.toStringDebug());

        _source.declareFields();
    }


    @Override
    public void resolveFields()
    {
        _source.resolveFields();
    }


    @Override
    public TableInfo getTableInfo()
    {
        Set<RelationColumn> set = new LinkedHashSet<>(_selectedColumns.values());

        getOrderedSuggestedColumns(set);
        if (!getParseErrors().isEmpty())
            return null;

        resolveFields();
        if (!getParseErrors().isEmpty())
            return null;

        Collection<String> keys = getKeyColumns();
        FieldKey key = null;
        if (keys.size() == 1)
            key = new FieldKey(null, keys.iterator().next());

        var ret = new QLWTableInfo();
        for (var col : _selectedColumns.values())
        {
            RelationColumnInfo ci = new RelationColumnInfo(ret, col);
            ColumnLogging columnLogging = col.getColumnLogging();
            if (null == columnLogging.getException())
                columnLogging = new ColumnLogging(getSchema().getName(), ret.getName(), ci.getFieldKey(),
                        columnLogging.shouldLogName(), columnLogging.getDataLoggingColumns(), columnLogging.getLoggingComments(), columnLogging.getSelectQueryAuditProvider());
            ci.setColumnLogging(columnLogging);
            ci.setKeyField(ci.getFieldKey().equals(key));
            ret.addColumn(ci);
        }

        ret.afterInitializeColumns();
        MemTracker.getInstance().put(ret);
        return ret;
    }


    private class QLWTableInfo extends QueryTableInfo implements CrosstabTableInfo
    {
        CrosstabTableInfo _crosstabTableInfo;

        QLWTableInfo()
        {
            super(QueryLookupWrapper.this, QueryLookupWrapper.this.getAlias());
            if (_source instanceof QueryPivot)
                _crosstabTableInfo = (CrosstabTableInfo)_source.getTableInfo();
        }

        @Override
        protected void afterInitializeColumns()
        {
            remapSelectFieldKeys(true);
        }

        @Override
        public String getTitleColumn()
        {
            RelationColumn titleColumn = null;
            if (_source instanceof QuerySelect select)
                titleColumn = select.getTitleColumn();
            if (null != titleColumn && titleColumn.getFieldKey().size() == 1)
                return titleColumn.getFieldKey().getName();
            return super.getTitleColumn();
        }

        // CrosstabTableInfo


        @Override
        public boolean isCrosstab()
        {
            return null != _crosstabTableInfo && _crosstabTableInfo.isCrosstab();
        }

        @Override
        public CrosstabSettings getSettings()
        {
            return _crosstabTableInfo.getSettings();
        }

        @Override
        public List<CrosstabMember> getColMembers()
        {
            return _crosstabTableInfo.getColMembers();
        }

        @Override
        public CrosstabMeasure getMeasureFromKey(String fieldKey)
        {
            return _crosstabTableInfo.getMeasureFromKey(fieldKey);
        }

        @Override
        public void setAggregateFilter(Filter filter)
        {
            _crosstabTableInfo.setAggregateFilter(filter);
        }

        @Override
        public Sort getDefaultSort()
        {
            return _crosstabTableInfo.getDefaultSort();
        }



        @Override
        public @NotNull SQLFragment getFromSQL(String alias)
        {
            for (var lkCol : _selectedColumns)
                lkCol.addRef(this);
            return super.getFromSQL(alias);
        }

        @Override
        public @NotNull SQLFragment getFromSQLExpanded(String alias, Set<FieldKey> fieldKeys)
        {
            if (null == fieldKeys)
                getFromSQL(alias);
            for (var lkCol : _selectedColumns)
                lkCol.releaseRef(this);
            for (var lkCol : _selectedColumns)
            {
                if (fieldKeys.contains(lkCol.getFieldKey()))
                    lkCol.addRef(this);
            }
            // column 0 should still have a reference because it has a reference from _query (see constructor)
            assert _selectedColumns.get(0).ref.count() > 0;
            return super.getFromSQL(alias);
        }
    }


    @Override
    public List<Sort.SortField> getSortFields()
    {
        return _source.getSortFields();
    }


    @Override
    public int getSelectedColumnCount()
    {
        return _selectedColumns.size();
    }
    

    @Override
    public QLWColumn getColumn(@NotNull String name)
    {
        FieldKey k = new FieldKey(null, name);
        var ret = _selectedColumns.get(k);
        if (null != ret)
        {
            ret.addRef(this);
            return ret;
        }
        RelationColumn c = _source.getColumn(name);
        if (null == c)
            return null;
        ret = new PassThroughColumn(k, c);
        _selectedColumns.put(k,ret);
        ret.addRef(this);
        return ret;
    }

    @Override
    public @Nullable AbstractQueryRelation.RelationColumn getFirstColumn()
    {
        return _selectedColumns.get(0);
    }

    @Override
    public Map<String,RelationColumn> getAllColumns()
    {
        Map<String,RelationColumn> all = _source.getAllColumns();
        Map<String,RelationColumn> ret = new LinkedHashMap<>(2*all.size());
        for (Map.Entry<String,RelationColumn> e : all.entrySet())
        {
            RelationColumn out = getColumn(e.getKey());
            assert null != out;
            ret.put(e.getKey(), out);
        }
        return ret;
    }


    @Override
    public RelationColumn getLookupColumn(@NotNull RelationColumn parentRelCol, @NotNull String name)
    {
        assert parentRelCol instanceof QLWColumn;
        assert parentRelCol.getTable() == this;

        QLWColumn parent = (QLWColumn)parentRelCol;
        FieldKey k = new FieldKey(parent._key, name);
        RelationColumn ret = _selectedColumns.get(k);
        if (null != ret)
            return ret;

        // parent is one part passthrough with extended FK
        // parent is one part passthrough with underlying FK
        // parent is a lookup (extended FK)
        // parent is a lookup (underlying FK)

        ColumnType.Fk fk = parent instanceof PassThroughColumn ? ((PassThroughColumn)parent)._columnFK : null;
        return getLookupColumn(parent, fk, name);
    }


    @Override
    public RelationColumn getLookupColumn(@NotNull RelationColumn parentRelCol, ColumnType.Fk fk, @NotNull String name)
    {
        QLWColumn parent = (QLWColumn)parentRelCol;
        FieldKey k = new FieldKey(parent._key, name);

        if (parent instanceof PassThroughColumn)
        {
            // push down to input relation if we can, otherwise wrap it and do it ourselves
            RelationColumn sourceCol;
            if (null != fk)
                sourceCol = _source.getLookupColumn(((PassThroughColumn)parent)._wrapped, fk, name);
            else
                sourceCol = _source.getLookupColumn(((PassThroughColumn)parent)._wrapped, name);
            if (null != sourceCol)
            {
                PassThroughColumn pt = new PassThroughColumn(k, sourceCol);
                _selectedColumns.put(k,pt);
                return pt;
            }
            // fall through
        }

        var parentFk = parentRelCol.getFk();
        if (parentFk == null)
            return null;

        QueryLookupColumn lc = createQueryLookupColumn(k, parentRelCol, parentFk);
        if (null == lc)
            return null;

        setHasLookup();
        _selectedColumns.put(k,lc);
        return lc;
    }


    @Override
    public Collection<String> getKeyColumns()
    {
        return _source.getKeyColumns();
    }


    @Override
    public SQLFragment getFromSql()
    {
        if (!_hasLookups)
            return _source.getFromSql();
        else
            return super.getFromSql();
    }


    @Override
    public SQLFragment getSql()
    {
        if (!_hasLookups)
            return _source.getSql();

        SQLFragment sourceFromSql = _source.getFromSql();
        if (null == sourceFromSql || !_query.getParseErrors().isEmpty())
            return null;

        Map<String, SQLFragment> joins = new LinkedHashMap<>();
        SqlBuilder sql = new SqlBuilder(getSchema().getDbSchema());
        assert sql.appendComment("<QueryLookupWrapper>");

        sql.append("SELECT ");
        String comma = "";
        for (RelationColumn col : _selectedColumns.values())
        {
            if (0 == col.ref.count())
                continue;
            sql.append(comma);
            if (col instanceof QueryLookupColumn)
                col.declareJoins(_source.getAlias(), joins);
            SQLFragment f = col.getInternalSql();
            if (f.getSQL().length() > 40)
                sql.append("\n").append(f).append("\n");
            else
                sql.append(f);
            sql.append(" AS ");
            sql.append(col.getAlias());
            comma = ", ";
        }
        sql.append("\nFROM ");
        sql.append(sourceFromSql);

        for (SQLFragment j : joins.values())
            sql.append(j);

        assert sql.appendComment("</QueryLookupWrapper>");
        return sql;
    }


    @Override
    public String getQueryText()
    {
        return _source.getQueryText();
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        getSqlTableInfo().setContainerFilter(containerFilter);
        _source.setContainerFilter(containerFilter);
    }


    @Override
    public Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        // TODO handle lookup columns
        HashSet<RelationColumn> unwrapped = new HashSet<>();
        for (RelationColumn rc : selected)
        {
            if (rc instanceof PassThroughColumn)
                unwrapped.add(((PassThroughColumn)rc)._wrapped);
        }
        var ret = new LinkedHashSet<RelationColumn>();
        _source.getOrderedSuggestedColumns(unwrapped).stream()
                .filter(sc -> sc.getFieldKey().getParent() == null)
                .map(sc -> getColumn(sc.getFieldKey().getName())).filter(Objects::nonNull)
                .peek(sc -> sc._suggestedColumn = true)
                .peek(sc -> _selectedColumns.put(sc.getFieldKey(), sc))
                .forEach(ret::add);
        return ret;
    }

    @Override
    public void setCommonTableExpressions(CommonTableExpressions queryWith)
    {
        // Defer to source relation; that's where we'll look for the With
        _source.setCommonTableExpressions(queryWith);
    }


    /* These are the lookup columns that are resolved by this QueryLookupWrapper.  QLW is responsible for joining in these columns. */
    private abstract class QLWColumn extends RelationColumn
    {
        final AbstractQueryRelation _table;
        final FieldKey _key;
        final String _alias;
        ColumnLogging _columnLogging = null;
        PHI _phi;
        
        QLWColumn(AbstractQueryRelation table, FieldKey key, String alias)
        {
            _table = table;
            _key = key;
            _alias = alias;
        }

        @Override
        public FieldKey getFieldKey()
        {
            return _key;
        }

        @Override
        AbstractQueryRelation getTable()
        {
            return _table;
        }

        @Override
        String getAlias()
        {
            return _alias;
        }

        @Override
        ColumnLogging getColumnLogging()
        {
            computeColumnLoggingsAndPHI();
            return _columnLogging;
        }

        @Override
        PHI getPHI()
        {
            computeColumnLoggingsAndPHI();
            return _phi;
        }
    }


    /* These are the columns that are generated from the _source relation, usually QuerySelect */
    class PassThroughColumn extends QLWColumn
    {
        AbstractQueryRelation.RelationColumn _wrapped;
        ColumnType.Fk _columnFK;
        ForeignKey _fk;

        PassThroughColumn(FieldKey key, RelationColumn c)
        {
            super(QueryLookupWrapper.this, key, _aliasManager.decideAlias(c.getAlias()));
            _wrapped = c;
            _query.addUniqueRelationColumn(this);

            // extra metadata only for selected columns
            if (key.getParent() != null)
                return;
            _columnFK = _fkMap.get(key.getName());
        }


        @Override
        public Collection<RelationColumn> gatherInvolvedSelectColumns(Collection<RelationColumn> collect)
        {
            // NOTE we can't pass through some columns and not others, so that makes us responsible for
            // creating new ColumnLogging objects
            collect.add(this);
            return collect;
        }


        @Override
        public ForeignKey getFk()
        {
            if (null != _fk)
            {
                return _fk;
            }
            if (null != _columnFK)
            {
                _fk = AbstractTableInfo.makeForeignKey(_schema, _query.getContainerFilter(), _columnFK);
                if (_fk == null)
                {
                    //noinspection ThrowableInstanceNeverThrown
                    _table._query.getParseErrors().add(new QueryParseException("Could not resolve ForeignKey on column '" + getFieldKey().getName() + "'", null, 0, 0));
                }
                return _fk;
            }
            return _wrapped.getFk();
        }

        @Override
        @NotNull
        public JdbcType getJdbcType()
        {
            return _wrapped.getJdbcType();
        }

        @Override
        boolean isHidden()
        {
            return _wrapped.isHidden();
        }

        @Override
        String getPrincipalConceptCode()
        {
            return _wrapped.getPrincipalConceptCode();
        }

        @Override
        String getConceptURI()
        {
            return _wrapped.getConceptURI();
        }

        @Override
        void copyColumnAttributesTo(@NotNull BaseColumnInfo to)
        {
            computeColumnLoggingsAndPHI();

            _wrapped.copyColumnAttributesTo(to);
            to.setColumnLogging(null);

            // Because QLW implements ColumnResolvingRelation (and because we apply xml metadata),
            // we have to do remapFieldKeys() here.
            //
            // This code started off in QueryTableInfo.remapSelectFieldKeys()
            // We may be able to remove that similar/duplicate code if the root relation of QueryTableInfo always
            // implements ColumnResolvingRelation.

            AbstractQueryRelation.RelationColumn sourceColumn = _query._mapUniqueNamesToRelationColumn.get(_wrapped.getUniqueName());
            QueryRelation sourceRelation = null == sourceColumn ? null : sourceColumn.getTable();
            Map<FieldKey, FieldKey> remap = sourceRelation instanceof QueryRelation.ColumnResolvingRelation crr ? crr.getRemapMap(_mapSourceUniqueNameToFieldKey) : null;

            if (null != remap)
            {
                var warnings = new CaseInsensitiveHashSet();
                to.remapFieldKeys(null, remap, warnings, true);
                for (String w : warnings)
                    _query.getParseWarnings().add(new QueryParseWarning(w, null, 0, 0));
            }

            to.setColumnLogging(_columnLogging);
            to.setPHI(_phi);

            if (to.getFieldKey().getParent() == null)
            {
                ColumnType columnMetaData = _columnMetaDataMap.get(to.getName());
                if (null != columnMetaData)
                    to.loadFromXml(columnMetaData, true);
            }
        }

        @Override
        public SQLFragment getValueSql()
        {
            if (!_hasLookups)
                return _wrapped.getValueSql();
            else
                return super.getValueSql();
        }

        @Override
        SQLFragment getInternalSql()
        {
            return _wrapped.getValueSql();
        }

        @Override
        public int addRef(@NotNull Object refer)
        {
            if (0 == ref.count())
                _wrapped.addRef(this);
            return super.addRef(refer);
        }

        @Override
        public int releaseRef(@NotNull Object refer)
        {
            if (0 == ref.count())
                return 0;
            int count = super.releaseRef(refer);
            if (count == 0)
                _wrapped.releaseRef(this);
            return count;
        }
    }


    private SQLTableInfo getSqlTableInfo()
    {
        if (null == _sti)
            _sti = new SQLTableInfo(getSchema().getDbSchema(), getAlias());
        return _sti;
    }


    public QueryLookupColumn createQueryLookupColumn(FieldKey key, RelationColumn parent, @NotNull ForeignKey fk)
    {
        ColumnInfo fkCol;
        if (!(parent instanceof QueryLookupColumn))
        {
            fkCol = new RelationColumnInfo(getSqlTableInfo(), parent);
        }
        else
        {
            fkCol = ((QueryLookupColumn)parent)._lkCol;
        }
        var lkCol = (BaseColumnInfo)fk.createLookupColumn(fkCol, key.getName());
        if (null == lkCol)
            return null;
        return new QueryLookupColumn(key, parent, fk, lkCol);
    }


    // almost the same as TableColumn
    class QueryLookupColumn extends QLWColumn
    {
        final RelationColumn _foreignKey;
        final ForeignKey _fk;
        final ColumnInfo _lkCol;

        protected QueryLookupColumn(FieldKey key, RelationColumn parent, @NotNull ForeignKey fk, BaseColumnInfo lkCol)
        {
            super(parent.getTable(), key, _aliasManager.decideAlias((parent.getAlias() + "$" + key.getName()).toLowerCase()));
            lkCol.setAlias(getAlias());
            _foreignKey = parent;
            _fk = fk;
            _lkCol = lkCol;

            _query.addUniqueRelationColumn(this);
        }

        @Override
        public Collection<RelationColumn> gatherInvolvedSelectColumns(Collection<RelationColumn> collect)
        {
            collect.add(this);
            return collect;
        }

        @Override
        public ForeignKey getFk()
        {
            return _lkCol.getFk();
        }

        @Override
        @NotNull
        public JdbcType getJdbcType()
        {
            return _lkCol.getJdbcType();
        }

        @Override
        boolean isHidden()
        {
            return _lkCol.isHidden();
        }

        @Override
        String getPrincipalConceptCode()
        {
            return _lkCol.getPrincipalConceptCode();
        }

        @Override
        String getConceptURI()
        {
            return _lkCol.getConceptURI();
        }

        @Override
        void copyColumnAttributesTo(BaseColumnInfo to)
        {
            to.copyAttributesFrom(_lkCol);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            _lkCol.declareJoins(parentAlias, map);
        }

        @Override
        SQLFragment getInternalSql()
        {
            return _lkCol.getValueSql(_source.getAlias());
        }

        @Override
        public int addRef(@NotNull Object refer)
        {
            _foreignKey.addRef(this);
            return super.addRef(refer);
        }

        @Override
        public int releaseRef(@NotNull Object refer)
        {
            if (0 == ref.count())
                return 0;
            int count = super.releaseRef(refer);
            if (count == 0)
                _foreignKey.releaseRef(this);
            return count;
        }

        @Override
        PHI getPHI()
        {
            return _lkCol.getPHI();
        }

        @Override
        ColumnLogging getColumnLogging()
        {
            return _lkCol.getColumnLogging();
        }
    }


    @Override
    public Map<FieldKey, FieldKey> getRemapMap(Map<String, FieldKey> outerMap)
    {
        Map<FieldKey,String> innerMap = new HashMap<>();
        _selectedColumns.forEach((key, value) -> {
            assert key.equals(value.getFieldKey());
            innerMap.put(key, value.getUniqueName());
        });
        return QueryRelation.generateRemapMap(innerMap , outerMap);
    }


    /* NOTE:
     * The fact that QueryLookupWrapper needs to implements ColumnResolvingRelation suggests
     * that it should participate in remapping of FieldKeys in other places besides ColumnLogging objects.
     * There are may be some subtle (not new) bugs with StringExpression lurking to be fixed.
     */

    boolean computeColumnLoggingsCalled = false;
    Map<String,FieldKey> _mapSourceUniqueNameToFieldKey = null;


    void computeColumnLoggingsAndPHI()
    {
        if (computeColumnLoggingsCalled)
            return;
        computeColumnLoggingsCalled = true;

        _mapSourceUniqueNameToFieldKey = new HashMap<>();
        _selectedColumns.values().stream().filter(sc -> sc instanceof PassThroughColumn).map(sc -> (PassThroughColumn)sc)
                .forEach(ptc -> _mapSourceUniqueNameToFieldKey.put(ptc._wrapped.getUniqueName(), ptc.getFieldKey()));

        CaseInsensitiveHashSet warnings = new CaseInsensitiveHashSet();
        QueryTableInfo fakeTableInfo = new QueryTableInfo(this, "QLW")
        {
            @Override
            @NotNull
            public SQLFragment getFromSQL() {throw new UnsupportedOperationException();}

            @Override
            public @Nullable UserSchema getUserSchema()
            {
                return (UserSchema)QueryLookupWrapper.this.getSchema();
            }
        };

        for (var qlwColumn : _selectedColumns.values())
        {
            ColumnLogging cl;
            PHI phi = PHI.NotPHI;
            if (qlwColumn instanceof PassThroughColumn ptColumn)
            {
                var columnsUsed = ptColumn._wrapped.gatherInvolvedSelectColumns(new ArrayList<>());
                QueryColumnLogging qcl = QueryColumnLogging.create(fakeTableInfo, qlwColumn.getFieldKey(), columnsUsed);
                cl = qcl.remapQueryFieldKeys(fakeTableInfo, qlwColumn.getFieldKey(), _mapSourceUniqueNameToFieldKey);
                for (var columnUsed : columnsUsed)
                    phi = PHI.max(phi, columnUsed.getPHI());
            }
            else if (qlwColumn instanceof QueryLookupColumn qlColumn)
            {
                cl = qlColumn._lkCol.getColumnLogging();
                cl = cl.remapFieldKeys(qlColumn._key.getParent(), null, warnings);
                phi = qlColumn._lkCol.getPHI();
            }
            else
                throw new IllegalStateException();

            qlwColumn._columnLogging = cl;
            qlwColumn._phi = phi;
        }

        for (String w : warnings)
            _query.getParseWarnings().add(new QueryParseWarning(w, null, 0, 0));
    }

    public QueryRelation getSource()
    {
        return _source;
    }
}
