package org.labkey.query.sql;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.data.xml.ColumnType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class QValuesTable extends QTable
{
    QuerySelect selectParent;
    Query query;
    QValues values;
    int countOfColumns;

    QueryRelation relation;

    QValuesTable(QuerySelect selectParent, QValues values, QIdentifier alias)
    {
        super(values, null);
        this.selectParent = selectParent;
        this.query = selectParent._query;
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
            values = (QValues)_resolveFields(values, null, null);
        }

        // simplified version of QuerySelect.resolveFields(), just resolve parameters and wrap method identifiers with QField
        QExpr _resolveFields(QExpr expr, @Nullable QNode parent, @Nullable Object referant)
        {
            LogManager.getLogger(QValuesTable.class).debug("QValuesTable.resolveFields()");
            if (expr instanceof QQuery || expr instanceof QUnion)
            {
                getParseErrors().add(new QueryParseException("Unexpected subquery expression", null, expr.getLine(), expr.getColumn()));
                return null;
            }
            if (expr instanceof QIfDefined)
                return new QNull();

            FieldKey key = expr.getFieldKey();
            if (key != null)
            {
                if (key.getParent() == null)
                {
                    QParameter param = selectParent.resolveParameter(key);
                    if (null != param)
                        return param;
                }
                getParseErrors().add(new QueryParseException("Unexpected identifier " + expr.getTokenText(), null, expr.getLine(), expr.getColumn()));
                return null;
            }

            if (expr.childList().isEmpty())
                return expr;

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
                if (child == methodName)
                {
                    FieldKey methodKey = ((QExpr) child).getFieldKey();
                    if (methodKey.getTable() != null)
                        getParseErrors().add(new QueryParseException("Unexpected identifier " + expr.getTokenText(), null, expr.getLine(), expr.getColumn()));
                    ret.appendChild(new QField(null, methodKey.getName(), expr));
                }
                else
                {
                    QExpr resolved = _resolveFields((QExpr) child, expr, referant);
                    if (null != resolved)
                        ret.appendChild(resolved);
                }
            }
            return ret;
        }


        @Override
        public void declareFields()
        {
        }

        public TableInfo getTableInfo()
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
            for (int i=0 ; i<countOfColumns ; i++)
            {
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
                        var firstRow = values.childList().get(0).childList();
                        final QExpr expr = (QExpr)firstRow.get(index);
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
            // generate names consistent with PostgreSQL default name for VALUES
            // https://www.postgresql.org/docs/12/queries-values.html
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
