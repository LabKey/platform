/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;

public class QType extends QExpr
{
    final JdbcType jdbcType;
    final @Nullable Integer length;
    final @Nullable Integer scale;

    QType(@NotNull JdbcType jdbcType, @Nullable Integer length /* precision */, @Nullable Integer scale)
    {
        this.jdbcType = jdbcType;
        this.length = length;
        this.scale = scale;
        if (scale != null && length == null)
            throw new IllegalArgumentException("Length cannot be null");
    }

    QType(@NotNull ConvertType type, @Nullable Integer length /* precision */, @Nullable Integer scale)
    {
        this(type.jdbcType, length, scale);
    }

    QType(@NotNull ParameterType type, @Nullable Integer length /* precision */, @Nullable Integer scale)
    {
        this(type.type, length, scale);
    }

    @Override
    public @NotNull JdbcType getJdbcType()
    {
        return jdbcType;
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        String typeName = builder.getDialect().getSqlTypeName(jdbcType);
        builder.append(typeName);
        Integer len = length;

        // SQL Server silently truncates CAST(value AS NVARCHAR) to the first 30 characters.
        // If a length is not explicitly specified and this is NVARCHAR, then CAST(value AS NVARCHAR(4000))
        // to avoid premature truncation.
        if (null == len && "NVARCHAR".equalsIgnoreCase(typeName))
            len = 4000;

        if (null != len)
        {
            builder.append("(").appendValue(len);
            if (scale != null)
                builder.append(",").appendValue(scale);
            builder.append(")");
        }
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append(jdbcType.name());
        if (null != length)
        {
            builder.append("(").appendValue(length);
            if (scale != null)
                builder.append(",").appendValue(scale);
            builder.append(")");
        }
    }
}
