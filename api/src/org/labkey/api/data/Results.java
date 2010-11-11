package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.FieldKey;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.Map;

public class Results
{
    public final ResultSet _rs;
    public final Map<FieldKey, ColumnInfo> _fieldMap;

    public Results(ResultSet rs, Map<FieldKey, ColumnInfo> fieldMap)
    {
        _rs = rs;
        _fieldMap = null == fieldMap ? Collections.<FieldKey, ColumnInfo>emptyMap() : fieldMap;
    }

    public Results(RenderContext ctx)
    {
        _rs = ctx.getResultSet();
        _fieldMap = ctx.getFieldMap();
    }

    @NotNull
    public Map<FieldKey, ColumnInfo> getFieldMap()
    {
        return _fieldMap;
    }

    @Nullable
    public ResultSet getResultSet()
    {
        return _rs;
    }
}
