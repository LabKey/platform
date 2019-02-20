package org.labkey.mothership;

import org.apache.commons.collections4.MultiValuedMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;

import java.util.Collection;

public class MetricJSONDisplayColumnFactory implements DisplayColumnFactory
{
    Collection<String> _properties ;
    public MetricJSONDisplayColumnFactory(MultiValuedMap<String, String> properties)
    {
        _properties = properties.get("jsonPath");
    }


    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new MetricJSONDisplayColumn(colInfo, _properties);
    }
}
