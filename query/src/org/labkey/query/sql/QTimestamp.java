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
import org.labkey.api.data.JdbcType;
import org.labkey.api.util.DateUtil;
import java.sql.Timestamp;

/**
 * User: matthew
 * Date: 6/7/13
 * Time: 12:37 PM
 */
public class QTimestamp extends QExpr implements IConstant
{
    Timestamp _value = null;

    public QTimestamp(CommonTree n, Timestamp value)
    {
        super(false);
        from(n);
        _value = value;
    }

    public Timestamp getValue()
    {
        return _value;
    }

    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append("{ts '" + DateUtil.toISO(_value) + "'}");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("{ts " + getTokenText() + "}");
    }

    public JdbcType getSqlType()
    {
        return JdbcType.TIMESTAMP;
    }

    public String getValueString()
    {
        return"{ts " + QString.quote(getTokenText()) + "}";
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QTimestamp && getValue().equals(((QNumber) other).getValue());
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
