package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.data.xml.ColumnType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class QValuesTable extends QTable
{
    QueryRelation relation;
    Query query;
    QValues values;
    int countOfColumns;

    QValuesTable(Query query, QValues values, QIdentifier alias)
    {
        super(values, null);
        this.query = query;
        this.values = values;
        setAlias(alias);
        var rows = values.childList();
        countOfColumns = 0==rows.size() ? 0 : rows.get(0).childList().size();
        relation = new _QueryRelation();
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append(" VALUES ");
        values.appendSource(builder,false);
        builder.append(" AS ");
        builder.append(_alias.getTokenText());
    }

    @Override
    public QueryRelation getQueryRelation()
    {
        return relation;
    }

    @Override
    public void setQueryRelation(QueryRelation queryRelation)
    {
        assert queryRelation == relation;
    }

    @Override
    public ContainerFilter.Type getContainerFilterType()
    {
        return ContainerFilter.EVERYTHING.getType();
    }

    class _QueryRelation extends QueryRelation
    {
        String generatedAlias;
        Map<String, RelationColumn> columns;

        _QueryRelation()
        {
            super(query);
            generatedAlias = QValuesTable.this._alias.getIdentifier();
            columns = initColumns();
        }

        @Override
        protected void setAlias(String alias)
        {
            generatedAlias = alias;
        }

        @Override
        public String getAlias()
        {
            return generatedAlias;
        }

        @Override
        protected void resolveFields()
        {
        }

        @Override
        protected void declareFields()
        {
        }

        TableInfo getTableInfo()
        {
            throw new UnsupportedOperationException();
        }

        String getQueryText()
        {
            throw new UnsupportedOperationException();
        }

        @Nullable RelationColumn getColumn(@NotNull String name)
        {
            return columns.get(name);
        }

        int getSelectedColumnCount()
        {
            return columns.size();
        }

        @Nullable RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name)
        {
            return null;
        }

        @Nullable RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull ColumnType.Fk fk, @NotNull String name)
        {
            return null;
        }

        @Override
        protected Map<String, RelationColumn> getAllColumns()
        {
            return columns;
        }

        protected Map<String, RelationColumn> initColumns()
        {
            if (countOfColumns == 0)
                return Map.of();
            Map<String,RelationColumn> map = new LinkedHashMap<>();
            var firstRow = values.childList().get(0).childList();
            for (int i=0 ; i<firstRow.size() ; i++)
            {
                final QExpr expr = (QExpr)firstRow.get(i);
                final int index = i;
                RelationColumn rc = new RelationColumn()
                {
                    @Override
                    public FieldKey getFieldKey()
                    {
                        return new FieldKey(null, getAlias());
                    }

                    @Override
                    String getAlias()
                    {
                        return "column" + (index+1);
                    }

                    @Override
                    QueryRelation getTable()
                    {
                        return _QueryRelation.this;
                    }

                    @Override
                    boolean isHidden()
                    {
                        return false;
                    }

                    @Override
                    String getPrincipalConceptCode()
                    {
                        return null;
                    }

                    @Override
                    String getConceptURI()
                    {
                        return null;
                    }

                    @Override
                    public @NotNull JdbcType getJdbcType()
                    {
                        return expr.getJdbcType();
                    }

                    @Override
                    void copyColumnAttributesTo(@NotNull BaseColumnInfo to)
                    {
                        to.setJdbcType(getJdbcType());
                    }
                };
                map.put(rc.getAlias(), rc);
            }
            return map;
        }

        @Override
        public SQLFragment getFromSql()
        {
            SqlBuilder ret = new SqlBuilder(query.getSchema().getDbSchema().getSqlDialect());
            ret.append("(");
            appendSql(ret);
            ret.append(") ");
            ret.append(getAlias());
            ret.append(" (");
            String comma = "";
            for (int i=1 ; i<= countOfColumns ; i++)
            {
                ret.append(comma).append("column" + i);
                comma = ",";
            }
            ret.append(")\n");
            return ret;
        }

        @Override
        public SQLFragment getSql()
        {
            return appendSql(new SqlBuilder(query.getSchema().getDbSchema().getSqlDialect()));
        }

        SqlBuilder appendSql(SqlBuilder sql)
        {
            sql.append(" VALUES ");
            values.appendSql(sql, query, false);
            return sql;
        }

        @Override
        public void setContainerFilter(ContainerFilter containerFilter)
        {
            /* pass */
        }

        @Override
        protected Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
        {
            return Set.of();
        }
    }
}
