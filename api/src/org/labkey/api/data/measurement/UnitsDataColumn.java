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

import java.util.Set;

public class UnitsDataColumn extends DataColumn
{
    public static final String UNITS_FIELD_PROPERTY_NAME = "unitsField";
    public static final String DEFAULT_UNITS_FIELD_PROPERTY_NAME = "defaultUnitsField";
    public static final String ALTERNATE_UNITS_FIELD_PROPERTY_NAME = "alternateUnitsField";

    private final MultiValuedMap<String, String> _properties;
    private final String _unitsField;
    private final String _defaultUnitsField;
    private final String _alternateUnitsField;

    public static class Factory implements DisplayColumnFactory
    {
        private MultiValuedMap _properties;             // metadata XML column properties

        // factory for metadata XML loading
        public Factory(MultiValuedMap properties)
        {
            if (properties != null)
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

        if (_properties != null)
        {
            _unitsField = _properties.get(UNITS_FIELD_PROPERTY_NAME).stream().findFirst().orElse("Units");
            _defaultUnitsField = _properties.get(DEFAULT_UNITS_FIELD_PROPERTY_NAME).stream().findFirst().orElse(null);
            _alternateUnitsField = _properties.get(ALTERNATE_UNITS_FIELD_PROPERTY_NAME).stream().findFirst().orElse(null);
        }
        else
        {
            _unitsField = "Units";
            _defaultUnitsField = null;
            _alternateUnitsField = null;
        }
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
            try
            {
                // if units is compatible with default unit, use default units otherwise, use unit
                Measurement.Unit storedUnit = Measurement.Unit.valueOf(units.toString());
                if (!storedUnit.isCompatible(unit))
                    return units;
                else
                    displayUnitStr = defaultUnitsStr;
            }
            catch (IllegalArgumentException e)
            {
                displayUnitStr = units.toString();
            }
        }

        // if neither defaut unit nor item unit is available, use alternate unit for display
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
            keys.add(FieldKey.fromParts(_defaultUnitsField));
        if (_alternateUnitsField != null)
            keys.add(FieldKey.fromParts(_alternateUnitsField));
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
