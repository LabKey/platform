package org.labkey.query.reports.getdata;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;

/**
 * User: jeckels
 * Date: 5/21/13
 */
public class FilterClauseBuilder
{
    private FieldKey _fieldKey;
    private CompareType _type;
    private Object _value;

    public void setFieldKey(String[] fieldKey)
    {
        _fieldKey = FieldKey.fromParts(fieldKey);
    }

    public void setType(String typeName)
    {
        // First check based on URL name
        for (CompareType compareType : CompareType.values())
        {
            if (compareType.getUrlKeys().contains(typeName))
            {
                _type = compareType;
                break;
            }
        }
        if (_type == null)
        {
            // If no match, check based on the enum name itself
            for (CompareType compareType : CompareType.values())
            {
                if (compareType.toString().equalsIgnoreCase(typeName))
                {
                    _type = compareType;
                    break;
                }
            }
        }
        if (_type == null)
        {
            // If still no match, check based on the verbose text
            for (CompareType compareType : CompareType.values())
            {
                if (compareType.getDisplayValue().equalsIgnoreCase(typeName))
                {
                    _type = compareType;
                    break;
                }
            }
        }
        if (_type == null)
        {
            throw new IllegalArgumentException("Could not resolve filter type: '" + typeName + "'");
        }
    }

    public void setValue(Object value)
    {
        _value = value;
    }

    public void append(SimpleFilter filter)
    {
        filter.addCondition(_fieldKey, _value, _type);
    }
}
