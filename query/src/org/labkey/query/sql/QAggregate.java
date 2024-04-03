/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.antlr.runtime.tree.CommonTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedDisplayColumn;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class QAggregate extends QExpr
{
    public static final String COUNT = "count";
    public static final String GROUP_CONCAT = "group_concat";

    public enum Type
    {
        COUNT(false, false),
        SUM(true, true),
        MIN(true, true),
        MAX(true, true),
        AVG(true, true),
        GROUP_CONCAT(false, true),
        STDDEV(true, false)
            {
                @Override
                String getFunction(SqlDialect d)
                {
                    return d.getStdDevFunction();
                }
            },
        STDERR(false, false)
            {
                @Override
                String getFunction(SqlDialect d)
                {
                    return null;
                }
            },
        BOOL_AND(false, true)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        BOOL_OR(false, true)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        BIT_AND(false, true)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        BIT_OR(false, true)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        CORR(false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        COVAR_POP(false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        COVAR_SAMP(false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        REGR_AVGX(false, true)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        REGR_AVGY(false, true)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        REGR_COUNT(false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        REGR_INTERCEPT(false, false)

            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        REGR_SLOPE( false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        REGR_SXX( false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        REGR_R2( false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        REGR_SXY( false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        REGR_SYY( false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        EVERY( false, true)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        MEDIAN(false, true)               // Only Postgres so far
            {
                @Override
                String getFunction(SqlDialect d)
                {
                    return d.getMedianFunction();
                }
            },
        MODE(false, true)                // Only Postgres so far
            {
                @Override
                boolean dialectSupports(SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        STDDEV_POP(false, false)
            {
                @Override
                String getFunction(SqlDialect d)
                {
                    return d.getStdDevPopFunction();
                }
            },
        STDDEV_SAMP(false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            },
        VARIANCE(false, false)
            {
                @Override
                String getFunction(SqlDialect d)
                {
                    return d.getVarianceFunction();
                }
            },
        VAR_POP(false, false)
            {
                @Override
                String getFunction(SqlDialect d)
                {
                    return d.getVarPopFunction();
                }
            },
        VAR_SAMP(false, false)
            {
                @Override
                boolean dialectSupports(@Nullable SqlDialect d)
                {
                    return null != d && d.isPostgreSQL();
                }
            }
        ;

        private final boolean _propagateAttributes;
        private final boolean _propagateColumnLogging;

//        Type()
//        {
//            this(false, true);
//        }
//
//        Type(boolean propagateAttributes)
//        {
//            this(propagateAttributes, propagateAttributes);
//        }


        /**
         * @param propagateAttributes This indicates whether column properties should be inherited by the aggregate result.
         *     This includes formats but not label, url, mvcolumnname, displaycolumnfactory and foreign key.
         *     Setting this to true is probably  most useful for simple aggregates (SUM, MIN, MAX)
         *
         * @param propagateColumnLogging Should this column inherit the columnLogging property of the source column.
         *      This should be set to TRUE for any column that __can__ return an unmodified value from the source column.
         *      Consider the case where the input has only one row for instance.
         * <p>
         *      MIN, MAX, SUM should specify propagateColumnLogging=TRUE,
         *      STDDEV, COUNT can specifiy propagateColumnLogging=FALSE
         */
        Type(boolean propagateAttributes, boolean propagateColumnLogging)
        {
            _propagateAttributes = propagateAttributes;
            _propagateColumnLogging = propagateColumnLogging;
        }

        String getFunction(SqlDialect d)
        {
            return name();
        }

        boolean dialectSupports(@Nullable SqlDialect d)
        {
            return true;
        }
    }

    private Type _type;
    private boolean _distinct;

    public QAggregate()
    {
        super(QNode.class);
    }


    public Type getType()
    {
        if (null == _type)
        {
            String function = getTokenText();
            _type = Type.valueOf(function.toUpperCase());
        }
        return _type;
    }

    @Override
    protected void from(CommonTree n)
    {
        super.from(n);
        if (Type.MEDIAN.equals(getType()))
            setHasTransformableAggregate(true);
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        Type type = getType();

        if (type == Type.GROUP_CONCAT)
        {
            SqlBuilder nestedBuilder = new SqlBuilder(builder.getDialect());
            Iterator<QNode> iter = children().iterator();
            ((QExpr)iter.next()).appendSql(nestedBuilder, query);

            SQLFragment gcSql;

            // Don't blow up if database doesn't support GROUP_CONCAT, #15554
            if (builder.getDialect().supportsGroupConcat())
            {
                if (iter.hasNext())
                {
                    SqlBuilder delimiter = new SqlBuilder(builder.getDialect());
                    ((QExpr)iter.next()).appendSql(delimiter, query);
                    gcSql = builder.getDialect().getGroupConcat(nestedBuilder, _distinct, true, delimiter);
                }
                else
                {
                    gcSql = builder.getDialect().getGroupConcat(nestedBuilder, _distinct, true);
                }
            }
            else
            {
                gcSql = new SQLFragment("'<GROUP_CONCAT function not supported on this database>'");
            }

            builder.append(gcSql);
        }
        else if (type == Type.STDERR)
        {
            assert !_distinct;
            // verify that NULL/0 is NULL not #DIV0
            // postgres[ok]
            // sqlserver[?]
            builder.append(" (").append(Type.STDDEV.getFunction(builder.getDialect())).append("(");
            for (QNode child : children())
                ((QExpr)child).appendSql(builder, query);
            builder.append(")/SQRT(COUNT(");
            for (QNode child : children())
                ((QExpr)child).appendSql(builder, query);
            builder.append(")))");
        }
        else if (type == Type.MEDIAN)
        {
            assert !_distinct;
            QNode partitionBy = builder.getDialect().isSqlServer() ? getLastChild() : null;

            builder.append(" (").append(type.getFunction(builder.getDialect())).append("(0.5) WITHIN GROUP (ORDER BY (");
            for (QNode child : children())
            {
                if (partitionBy != child)
                    ((QExpr) child).appendSql(builder, query);
            }
            builder.append(")) ");
            if (builder.getDialect().isSqlServer() && partitionBy instanceof QPartitionBy)
            {
                ((QPartitionBy)partitionBy).appendSql(builder, query);
            }
            builder.append(")");
        }
        else if (type == Type.MODE)
        {
            assert !_distinct;
            builder.append(" (").append(type.getFunction(builder.getDialect())).append("() WITHIN GROUP (ORDER BY (");
            for (QNode child : children())
            {
                ((QExpr)child).appendSql(builder, query);
            }
            builder.append(")))");
        }
        else
        {
            String function = type.getFunction(builder.getDialect());
            builder.append(" ").append(function).append("(");
            if (_distinct)
            {
                builder.append("DISTINCT ");
            }
            String sep = "";
            for (QNode child : children())
            {
                builder.append(sep);
                ((QExpr)child).appendSql(builder, query);
                sep = ", ";
            }
            builder.append(")");
        }
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append(" ").append(getTokenText()).append("(");
        if (_distinct)
        {
            builder.append("DISTINCT ");
        }
        for (QNode child : children())
        {
            child.appendSource(builder);
        }
        builder.append(")");
    }

    @Override @NotNull
    public JdbcType getJdbcType()
    {
        if (getType() == Type.COUNT)
        {
            return JdbcType.INTEGER;
        }
        if (getType() == Type.GROUP_CONCAT)
        {
            return JdbcType.VARCHAR;
        }
		if (getFirstChild() != null)
			return ((QExpr)getFirstChild()).getJdbcType();
        return JdbcType.OTHER;
    }

    @Override
    public boolean isAggregate()
    {
        return true;
    }

    @Override
    public ColumnInfo createColumnInfo(TableInfo table, String alias, Query query)
    {
        var ret = (MutableColumnInfo)super.createColumnInfo(table, alias, query);
        List<QNode> children = childList();
        if (children.size() == 1 && children.get(0) instanceof QField field)
        {
            if (getType()._propagateAttributes)
            {
                field.getRelationColumn().copyColumnAttributesTo((BaseColumnInfo)ret);
                // but not these attributes, maybe I should have an include list instead of an exclude list
                ret.setLabel(null);
                ret.setURL(null);
                ret.setMvColumnName(null);
                ret.setDisplayColumnFactory(ColumnInfo.DEFAULT_FACTORY);
                ret.clearFk();
            }
        }

        if (getType()._propagateColumnLogging)
        {
            QueryColumnLogging qcl = QueryColumnLogging.create(table, ret.getFieldKey(), gatherInvolvedSelectColumns(new HashSet<>()));
            ret.setColumnLogging(qcl);
            ret.setPHI(qcl.getPHI());
        }
        else
        {
            ret.setColumnLogging(ColumnLogging.defaultLogging(ret));
        }

        if (getType() == Type.GROUP_CONCAT)
        {
            final DisplayColumnFactory originalFactory = ret.getDisplayColumnFactory();
            ret.setDisplayColumnFactory(colInfo -> new MultiValuedDisplayColumn(originalFactory.createRenderer(colInfo)));
        }
        return ret;
    }

    @Override
    public Collection<AbstractQueryRelation.RelationColumn> gatherInvolvedSelectColumns(Collection<AbstractQueryRelation.RelationColumn> collect)
    {
        if (getType()._propagateColumnLogging)
            super.gatherInvolvedSelectColumns(collect);
        return collect;
    }

    public void setDistinct(boolean distinct)
    {
        _distinct = distinct;
    }

    public boolean isDistinct()
    {
        return _distinct;
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QAggregate &&
                ((QAggregate) other).getType() == getType() &&
                _distinct == ((QAggregate)other)._distinct;
    }

    @Override
    public boolean isConstant()
    {
        return false;
    }
}
