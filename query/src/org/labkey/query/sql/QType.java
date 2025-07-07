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
        if ("NVARCHAR".equalsIgnoreCase(typeName) && null == length)
            len = 4000;
        if (null != length)
        {
            builder.append("(").appendValue(length);
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
