package org.labkey.api.query.column;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.WrappedColumnInfo;

public interface ConceptURIColumnInfoTransformer extends ColumnInfoTransformer, MutableColumnInfoTransformer
{
    @NotNull String getConceptURI();

    // ColumnInfoTransformer provides only for wrapping a ColumnInfo
    // In some cases it makes sense to allow mutating (AbstractTableInfo.afterConstruct())
    // so This method is also available
    MutableColumnInfo applyMutable(MutableColumnInfo m);

    @Override
    default ColumnInfo apply(ColumnInfo column)
    {
        return applyMutable(WrappedColumnInfo.wrap(column));
    }
}
