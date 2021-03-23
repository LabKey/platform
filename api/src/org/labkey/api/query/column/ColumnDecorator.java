package org.labkey.api.query.column;

import org.labkey.api.data.MutableColumnInfo;

public interface ColumnDecorator
{
    void apply(MutableColumnInfo col);
}
