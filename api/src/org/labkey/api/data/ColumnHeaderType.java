package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.query.FieldKey;

import java.util.Map;

/**
 * User: kevink
 * Date: 2/19/15
 *
 * Column headers used in exporting grids to tsv or excel.
 */
public enum ColumnHeaderType
{
    None("None") {
        @Override
        public String getText(DisplayColumn dc)
        {
            throw new UnsupportedOperationException();
        }
    },

    Caption("Caption (grid captions)") {
        @Override
        public String getText(DisplayColumn dc)
        {
            return dc.getCaption(null, false);
        }
    },

    // The DisplayColumn name mangles special characters and removes spaces
    // making it almost worthless for export and import.
    Name("Name (property name; Deprecated)") {
        @Override
        public String getText(DisplayColumn dc)
        {
            return dc.getName();
        }
    },

    // Use ColumnInfo name -- usually just the FieldKey -- and use "." for lookup separators without FieldKey escaping.
    DisplayFieldKey("Display (friendly field name)") {
        @Override
        public String getText(DisplayColumn dc)
        {
            ColumnInfo columnInfo = dc.getColumnInfo();
            String name;
            if (columnInfo != null)
            {
                name = columnInfo.getName();
                org.labkey.api.query.FieldKey fieldKey = org.labkey.api.query.FieldKey.fromString(name);

                fieldKey = fixMissingValueIndicator(columnInfo, fieldKey);
                name = fieldKey.toDisplayString();
            }
            else
            {
                name = dc.getName();
            }
            return name;
        }
    },

    // Use the ColumnInfo's FieldKey with FieldKey escaping. Useful for import/export round-tripping, but can lead to ugly names.
    FieldKey("Field Key (canonical field identifier)") {
        @Override
        public String getText(DisplayColumn dc)
        {
            ColumnInfo columnInfo = dc.getColumnInfo();
            String name;
            if (columnInfo != null)
            {
                org.labkey.api.query.FieldKey fieldKey = columnInfo.getFieldKey();
                if (fieldKey == null)
                    fieldKey = org.labkey.api.query.FieldKey.fromString(columnInfo.getName());

                fieldKey = fixMissingValueIndicator(columnInfo, fieldKey);
                name = fieldKey.toString();
            }
            else
            {
                name = dc.getName();
            }
            return name;
        }
    };

    private String _optionText;

    ColumnHeaderType(String optionText)
    {
        _optionText = optionText;
    }

    protected FieldKey fixMissingValueIndicator(ColumnInfo columnInfo, FieldKey fieldKey)
    {
        // Issue 16476: Importers don't expect "_MVIndicator"
        if (columnInfo.isMvIndicatorColumn() && StringUtils.endsWithIgnoreCase(fieldKey.getName(), "_" + MvColumn.MV_INDICATOR_SUFFIX))
        {
            String name = fieldKey.getName();
            name = name.substring(0, name.length() - MvColumn.MV_INDICATOR_SUFFIX.length() - 1) + MvColumn.MV_INDICATOR_SUFFIX;
            fieldKey = new FieldKey(fieldKey.getParent(), name);
        }
        return fieldKey;
    }

    public String getOptionText()
    {
        return _optionText;
    }

    public abstract String getText(DisplayColumn dc);

    private static Map<String, ColumnHeaderType> _map;
    static
    {
        _map = new CaseInsensitiveHashMap<>();
        for (ColumnHeaderType type : ColumnHeaderType.values())
            _map.put(type.name(), type);

        // TSVWriter backwards compatibility
        _map.put("propertyName", Name);
        _map.put("queryPropertyName", DisplayFieldKey);

        // Excel backwards compatibility
        _map.put("Label", Caption);
    }

    @NotNull
    public static ColumnHeaderType fromString(@NotNull String value)
    {
        ColumnHeaderType ret;
        try
        {
            ret = ColumnHeaderType.valueOf(value);
        }
        catch (IllegalArgumentException e)
        {
            ret = _map.get(value);
        }

        if (ret == null)
            throw new ConversionException(value);

        return ret;
    }

    public static class Converter implements org.apache.commons.beanutils.Converter
    {
        @Override
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;

            return fromString(value.toString());
        }
    }
}
