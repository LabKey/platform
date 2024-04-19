/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;

/**
 * User: matthewb
 * Date: 2012-10-17
 * Time: 1:31 PM
 */
public class QIfDefined extends QExpr
{
    protected QuerySelect select = null;
    protected boolean isDefined = true;

    QIfDefined(CommonTree node)
    {
        super(QFieldKey.class);
        from(node);
    }

    void setQuerySelect(QuerySelect select)
    {
        this.select = select;
    }

    @Override
    public FieldKey getFieldKey()
    {
        assert null != select;
        return ((QExpr)getFirstChild()).getFieldKey();
    }

    @Override
    public boolean isConstant()
    {
        return false;
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append("IFDEFINED(");
        getFirstChild().appendSource(builder);
        builder.append(")");
    }

    @Override
    protected boolean isValidChild(QNode n)
    {
        return n instanceof QField || n instanceof QIdentifier || n instanceof QDot;
    }

    @NotNull
    @Override
    public JdbcType getJdbcType()
    {
        if (isDefined)
            return ((QExpr)getFirstChild()).getJdbcType();
        else
            return JdbcType.OTHER;
    }

    @Override
    public boolean isAggregate()
    {
        return false;
    }


    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        throw new IllegalStateException();
    }

    @Override
    public SQLFragment getSqlFragment(SqlDialect dialect, Query query)
    {
        throw new IllegalStateException();
    }

    @Override
    public String getValueString()
    {
        throw new IllegalStateException();
    }

    @Override
    public BaseColumnInfo createColumnInfo(TableInfo table, String name, Query query)
    {
        throw new IllegalStateException();
    }

    @Override
    public QueryParseException fieldCheck(QNode parent, SqlDialect d)
    {
        return null;
    }

    @Override
    protected JdbcType getChildrenSqlType()
    {
        throw new IllegalStateException();
    }
}
