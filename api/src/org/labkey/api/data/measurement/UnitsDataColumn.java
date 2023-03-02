package org.labkey.api.data.measurement;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HtmlString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class UnitsDataColumn extends DataColumn
{
    public static final String UNITS_FIELD_PROPERTY_NAME = "unitsField";
    public static final String DEFAULT_UNITS_FIELD_PROPERTY_NAME = "defaultUnitsField";
    public static final String ALTERNATE_UNITS_FIELD_PROPERTY_NAME = "alternateUnitsField";

    private final MultiValuedMap<String, String> _properties;
    private final FieldKey _unitsField;
    private final FieldKey _defaultUnitsField;
    private final FieldKey _alternateUnitsField;

    public static class Factory implements DisplayColumnFactory
    {
        private final MultiValuedMap _properties;             // metadata XML column properties

        // factory for metadata XML loading
        public Factory(MultiValuedMap properties)
        {
            _properties = properties;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo col)
        {
            return new UnitsDataColumn(col, _properties);
        }
    }

    UnitsDataColumn(ColumnInfo colInfo, MultiValuedMap properties)
    {
        super(colInfo, false);
        _properties = properties;

        FieldKey fieldKeyParent = getBoundColumn().getFieldKey().getParent();
        _unitsField = getFieldKey(fieldKeyParent, _properties, UNITS_FIELD_PROPERTY_NAME, "Units");
        _defaultUnitsField = getFieldKey(fieldKeyParent, _properties, DEFAULT_UNITS_FIELD_PROPERTY_NAME, null);
        _alternateUnitsField = getFieldKey(fieldKeyParent, _properties, ALTERNATE_UNITS_FIELD_PROPERTY_NAME, null);
    }

    static FieldKey getFieldKey(FieldKey fieldKeyParent, MultiValuedMap<String, String> properties, String propertyName, String defaultValue)
    {
        List<String> keyParts = new ArrayList<>();
        if (fieldKeyParent != null)
            keyParts.addAll(fieldKeyParent.getParts());
        int parentPartsSize = keyParts.size();
        if (properties != null)
        {
            String fieldName = properties.get(propertyName).stream().findFirst().orElse(defaultValue);
            if (fieldName != null)
                keyParts.addAll(Arrays.asList(fieldName.split("/")));
        }
        else if (defaultValue != null)
            keyParts.add(defaultValue);
        return parentPartsSize == keyParts.size() ? null : FieldKey.fromParts(keyParts);
    }

    // Display the units of the converted value
    private Object getDisplayUnits(RenderContext ctx)
    {
        String defaultUnitsStr = _defaultUnitsField != null ? (String) ctx.get(_defaultUnitsField) : null;
        Measurement.Unit unit = StringUtils.isEmpty(defaultUnitsStr) ? null : Measurement.Unit.valueOf(defaultUnitsStr);

        String displayUnitStr;
        Object units = ctx.get(_unitsField);
        if (units == null)
            displayUnitStr = defaultUnitsStr;
        else
        {
            String unitsStr = units.toString().trim();
            try
            {
                // if units is compatible with default unit, use default units otherwise, use unit
                Measurement.Unit storedUnit = Measurement.Unit.valueOf(unitsStr);
                if (!storedUnit.isCompatible(unit))
                    return units;
                else
                    displayUnitStr = defaultUnitsStr;
            }
            catch (IllegalArgumentException e)
            {
                displayUnitStr = unitsStr;
            }
        }

        // if neither default unit nor item unit is available, use alternate unit for display
        if (StringUtils.isEmpty(displayUnitStr) && _alternateUnitsField != null)
        {
            String alternateUnitStr = (String) ctx.get(_alternateUnitsField);

            if (!StringUtils.isEmpty(alternateUnitStr))
                displayUnitStr = alternateUnitStr;
        }

        return displayUnitStr;

    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        if (_defaultUnitsField != null)
            keys.add(_defaultUnitsField);
        if (_alternateUnitsField != null)
            keys.add(_alternateUnitsField);
    }

    @Override
    public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
    {
        Object displayUnits = getDisplayUnits(ctx);
        return HtmlString.unsafe(displayUnits == null ? null : displayUnits.toString());
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return getDisplayUnits(ctx);
    }

}
