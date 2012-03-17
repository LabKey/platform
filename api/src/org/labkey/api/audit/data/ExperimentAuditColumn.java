package org.labkey.api.audit.data;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.util.Pair;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 15, 2012
 */
public abstract class ExperimentAuditColumn extends DataColumn
{
    protected ColumnInfo _containerId;
    protected ColumnInfo _defaultName;

    public static final String KEY_SEPARATOR = "~~KEYSEP~~";

    public ExperimentAuditColumn(ColumnInfo col, ColumnInfo containerId, ColumnInfo defaultName)
    {
        super(col);
        _containerId = containerId;
        _defaultName = defaultName;
    }

    public String getName()
    {
        return getColumnInfo().getLabel();
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        if (_containerId != null)
            columns.add(_containerId);
        if (_defaultName != null)
            columns.add(_defaultName);
    }

    public boolean isFilterable()
    {
        return false;
    }

    protected static Pair<String, String> splitKey3(Object value)
    {
        if (value == null)
            return null;
        String[] parts = value.toString().split(KEY_SEPARATOR);
        if (parts == null || parts.length != 2)
            return null;
        return new Pair<String, String>(parts[0], parts[1].length() > 0 ? parts[1] : null);
    }
}
