package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MethodInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.data.xml.ColumnType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QueryRelationWrapper<R extends QueryRelation> extends QueryRelation
{
    R _wrapped;

    QueryRelationWrapper(Query query)
    {
        super(query);
    }

    void setWrapped(R wrapped)
    {
        _wrapped = wrapped;
    }

    private class _RelationColumn extends RelationColumn
    {
        final RelationColumn _wrapped;

        _RelationColumn(RelationColumn wrapped)
        {
            _wrapped = wrapped;
        }

        @Override
        public FieldKey getFieldKey()
        {
            return _wrapped.getFieldKey();
        }

        @Override
        String getAlias()
        {
            return _wrapped.getAlias();
        }

        @Override
        QueryRelation getTable()
        {
            return QueryRelationWrapper.this;
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
        public @NotNull JdbcType getJdbcType()
        {
            return _wrapped.getJdbcType();
        }

        @Override
        public ForeignKey getFk()
        {
            return _wrapped.getFk();
        }

        @Override
        void copyColumnAttributesTo(@NotNull BaseColumnInfo to)
        {
            _wrapped.copyColumnAttributesTo(to);
        }

        @Override
        public int addRef(@NotNull Object refer)
        {
            _wrapped.addRef(refer);
            return super.addRef(refer);
        }
    }

    RelationColumn wrap(RelationColumn c)
    {
        if (null == c)
            return null;
        return new _RelationColumn(c);
    }

    RelationColumn unwrap(RelationColumn c)
    {
        if (null == c)
            return null;
        return ((_RelationColumn)c)._wrapped;
    }

    @Override
    public void setAlias(String alias)
    {
        super.setAlias(alias);
    }

    @Override
    public void setSavedName(String name)
    {
        _wrapped.setSavedName(name);
    }

    @Override
    public void setQuery(Query query)
    {
        _wrapped.setQuery(query);
    }

    @Override
    public QuerySchema getSchema()
    {
        return _wrapped.getSchema();
    }

    @Override
    public List<QueryException> getParseErrors()
    {
        return _wrapped.getParseErrors();
    }

    @Override
    public void reportWarning(String string, @Nullable QNode node)
    {
        _wrapped.reportWarning(string, node);
    }

    @Override
    public void declareFields()
    {
        _wrapped.declareFields();
    }

    @Override
    public void resolveFields()
    {
        _wrapped.resolveFields();
    }

    @Override
    public TableInfo getTableInfo()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, RelationColumn> getAllColumns()
    {
        var map = _wrapped.getAllColumns();
        var ret = new LinkedHashMap<String, RelationColumn>();
        for (var e : map.entrySet())
            ret.put(e.getKey(), wrap(e.getValue()));
        return ret;
    }

    @Override
    @Nullable
    public RelationColumn getColumn(@NotNull String name)
    {
        return wrap(_wrapped.getColumn(name));
    }

    @Override
    public Collection<String> getKeyColumns()
    {
        return _wrapped.getKeyColumns();
    }

    @Override
    public int getSelectedColumnCount()
    {
        return _wrapped.getSelectedColumnCount();
    }

    @Override
    @Nullable
    public RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name)
    {
        return wrap(_wrapped.getLookupColumn(unwrap(parent), name));
    }

    @Override
    @Nullable
    public RelationColumn getLookupColumn(@NotNull RelationColumn parent, ColumnType.@NotNull Fk fk, @NotNull String name)
    {
        return wrap(_wrapped.getLookupColumn(unwrap(parent), fk, name));
    }

    @Override
    public SQLFragment getSql()
    {
        return _wrapped.getSql();
    }

    @Override
    public SQLFragment getFromSql()
    {
        return _wrapped.getFromSql();
    }

    @Override
    public String getQueryText()
    {
        return _wrapped.getQueryText();
    }

    @Override
    public String getAlias()
    {
        return super.getAlias();
    }

    @Override
    public RelationColumn declareField(FieldKey key, QExpr location)
    {
        return wrap(_wrapped.declareField(key, location));
    }

    @Override
    public QField getField(FieldKey key, QNode expr, Object referant)
    {
        return _wrapped.getField(key, expr, referant);
    }

    @Override
    public MethodInfo getMethod(String name)
    {
        return _wrapped.getMethod(name);
    }

    @Override
    public int getNestingLevel()
    {
        return _wrapped.getNestingLevel();
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        _wrapped.setContainerFilter(containerFilter);
    }

    @Override
    public Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        var unwrapped = new LinkedHashSet<RelationColumn>();
        for (var sel : selected)
            unwrapped.add(((_RelationColumn)sel)._wrapped);

        var ret = new LinkedHashSet<RelationColumn>();
        for (RelationColumn c : _wrapped.getSuggestedColumns(unwrapped))
            ret.add(wrap(c));

        return ret;
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return _wrapped.getContainerFieldKey();
    }

    @Override
    public Set<SchemaKey> getResolvedTables()
    {
        return _wrapped.getResolvedTables();
    }

    @Override
    public CommonTableExpressions getCommonTableExpressions()
    {
        return _wrapped.getCommonTableExpressions();
    }

    @Override
    public void setCommonTableExpressions(CommonTableExpressions queryWith)
    {
        _wrapped.setCommonTableExpressions(queryWith);
    }

    @Override
    public String toStringDebug()
    {
        return _wrapped.toStringDebug();
    }
}
