/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
import org.labkey.api.data.JdbcType;
import org.labkey.api.sql.LabKeySql;
import org.labkey.api.util.DateUtil;

import java.sql.Timestamp;

/**
 * User: matthew
 * Date: 6/7/13
 * Time: 12:37 PM
 */
public class QTimestamp extends QExpr implements IConstant
{
    final private Timestamp _value;

    public QTimestamp(CommonTree n, Timestamp value)
    {
        super(false);
        from(n);
        _value = value;
    }

    @Override
    public Timestamp getValue()
    {
        return _value;
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append("{ts '").append(DateUtil.toISO(_value)).append("'}");
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append("{ts ").append(getTokenText()).append("}");
    }

    @NotNull
    @Override
    public JdbcType getJdbcType()
    {
        return JdbcType.TIMESTAMP;
    }

    @Override
    public String getValueString()
    {
        return "{ts " + LabKeySql.quoteString(getTokenText()) + "}";
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QTimestamp && getValue().equals(((QTimestamp) other).getValue());
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
