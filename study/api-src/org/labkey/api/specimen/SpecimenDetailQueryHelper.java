package org.labkey.api.specimen;

import org.labkey.api.data.SQLFragment;

import java.util.Map;

public class SpecimenDetailQueryHelper
{
    private final SQLFragment _viewSql;
    private final String _typeGroupingColumns;
    private final Map<String, SpecimenTypeBeanProperty> _aliasToTypePropertyMap;

    public SpecimenDetailQueryHelper(SQLFragment viewSql, String typeGroupingColumns, Map<String, SpecimenTypeBeanProperty> aliasToTypePropertyMap)
    {
        _viewSql = viewSql;
        _typeGroupingColumns = typeGroupingColumns;
        _aliasToTypePropertyMap = aliasToTypePropertyMap;
    }

    public SQLFragment getViewSql()
    {
        return _viewSql;
    }

    public String getTypeGroupingColumns()
    {
        return _typeGroupingColumns;
    }

    public Map<String, SpecimenTypeBeanProperty> getAliasToTypePropertyMap()
    {
        return _aliasToTypePropertyMap;
    }
}
