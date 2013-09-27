/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.util.DateUtil;


/**
 * User: matthew
 * Date: 6/7/13
 * Time: 12:37 PM
 */
public class QDate extends QExpr implements IConstant
{
    java.sql.Date _value = null;

    public QDate(CommonTree n, java.sql.Date value)
    {
        super(false);
        from(n);
        _value = value;
    }

    public java.sql.Date getValue()
    {
        return _value;
    }

    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append("{d '").append(DateUtil.toISO(_value).substring(0, 10)).append("'}");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("{d ").append(getTokenText()).append("}");
    }

    public @NotNull JdbcType getSqlType()
    {
        return JdbcType.DATE;
    }

    public String getValueString()
    {
        return"{d " + QString.quote(getTokenText()) + "}";
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QDate && getValue().equals(((QDate) other).getValue());
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
