package org.labkey.experiment.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.data.xml.queryCustomView.OperatorType;

import java.util.Collection;
import java.util.Set;

/**
 * <code>
 * Filter.create('lsid', '{json:["urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522",1,"sourceKey"]}', Filter.Types.EXP_LINEAGE_OF)
 * Filter.create('lsid', ["urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522",1,"sourceKey"], Filter.Types.EXP_LINEAGE_OF)
 * </code>
 */
public class LineageCompareType extends CompareType
{
    public static final String SEPARATOR = ",";

    public LineageCompareType()
    {
        super("In The Lineage Of", "exp:lineageof", "EXP_LINEAGE_OF", true, " in the lineage of", OperatorType.EXP_LINEAGEOF);
    }

    @Override
    public SimpleFilter.FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        Object[] values;
        Object collection;
        if (value instanceof Collection valueCollection)
        {
            collection = value;
            values = valueCollection.toArray();
        }
        else
        {
            Set<String> params = parseParams(value, getValueSeparator());
            collection = params;
            values = params.toArray();
        }

        String lsid = (String) values[0];
        int depth = 0;
        if (values.length > 1)
        {
            Object depthObj = values[1];
            if (depthObj instanceof String depthObjStr)
                depth = Integer.parseInt(depthObjStr);
            else if (depthObj instanceof Integer depthObjInt)
                depth = depthObjInt;
        }

        String sourceKey = null;
        if (values.length > 2)
        {
            Object sourceKeyObj = values[2];
            if (sourceKeyObj instanceof String sourceKeyStr)
                sourceKey = sourceKeyStr;
        }

        return new LineageClause(fieldKey, collection, lsid, depth, sourceKey);
    }

    @Override
    public String getValueSeparator()
    {
        return SEPARATOR;
    }

    @Override
    public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
    {
        throw new UnsupportedOperationException("Conditional formatting not yet supported for EXP_LINEAGE_OF");
    }
}
