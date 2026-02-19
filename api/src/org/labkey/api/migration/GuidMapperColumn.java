package org.labkey.api.migration;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.util.GUID;

// In this column, map any value that exactly matches a GUID to lowercase
public final class GuidMapperColumn extends WrappedColumn
{
    public GuidMapperColumn(ColumnInfo col)
    {
        super(col, col.getName());
    }

    @Override
    public SQLFragment getValueSql(String tableAlias)
    {
        SQLFragment columnAlias = super.getValueSql(tableAlias);
        //noinspection StringConcatenationInsideStringBufferAppend - SQLFragment flips out about unmatched quotes, so we're forced to use string concatenation
        return new SQLFragment("CASE WHEN ")
            .append(columnAlias)
            .append(" LIKE '" + GUID.SQL_LIKE_GUID_PATTERN + "'")
            .append(" THEN LOWER(")
            .append(columnAlias)
            .append(") ELSE ")
            .append(columnAlias)
            .append(" END");
    }
}
