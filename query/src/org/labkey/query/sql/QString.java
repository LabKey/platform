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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.sql.LabKeySql;


public class QString extends QExpr implements IConstant
{
    @NotNull
    @Override
    public JdbcType getJdbcType()
    {
        return JdbcType.VARCHAR;
    }

    public QString()
    {
		super(false);
    }

    public QString(String value)
    {
		this();
        setTokenText(LabKeySql.quoteString(value));
    }

    @Override
    public String getValue()
    {
        return LabKeySql.unquoteString(getTokenText());
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.appendStringLiteral(getValue());
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append(getTokenText());
    }

    @Override
    public String getValueString()
    {
        return getTokenText();
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QString && getValue().equals(((QString) other).getValue());
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
