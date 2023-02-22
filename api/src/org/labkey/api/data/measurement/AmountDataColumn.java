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

import static org.labkey.api.data.measurement.UnitsDataColumn.DEFAULT_UNITS_FIELD_PROPERTY_NAME;
import static org.labkey.api.data.measurement.UnitsDataColumn.UNITS_FIELD_PROPERTY_NAME;

public class AmountDataColumn extends DataColumn
{
    public static final String AMOUNT_FIELD_PROPERTY_NAME = "amountField";

    private final MultiValuedMap<String, String> _properties;
    private final String _unitsField;
    private final String _defaultUnitsField;
    private final String _amountField;

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
            if (_properties != null)
                return new AmountDataColumn(col, _properties);
            else
                throw new IllegalArgumentException("Cannot create a renderer from the specified configuration properties");
        }
    }

    public AmountDataColumn(ColumnInfo col, MultiValuedMap properties)
    {
        super(col, false);
        _properties = properties;
        _unitsField = _properties.get(UNITS_FIELD_PROPERTY_NAME).stream().findFirst().orElse("Units");
        _defaultUnitsField = _properties.get(DEFAULT_UNITS_FIELD_PROPERTY_NAME).stream().findFirst().orElse(null);
        _amountField = _properties.get(AMOUNT_FIELD_PROPERTY_NAME).stream().findFirst().orElse(null);
    }

    private Object convertToDisplayUnits(RenderContext ctx)
    {
        Double storedAmount = (Double) ctx.get(_amountField);
        String sampleTypeUnitsStr = (String) ctx.get(_defaultUnitsField);
        Measurement.Unit sampleTypeUnit = null;
        if (!StringUtils.isEmpty(sampleTypeUnitsStr))
        {
            try
            {
                sampleTypeUnit = Measurement.Unit.valueOf(sampleTypeUnitsStr);
            }
            catch (IllegalArgumentException e)
            {
                // do nothing, return unconverted amount
            }
        }

        if (ctx.get(_unitsField) == null)
            return storedAmount;

        try
        {
            Measurement.Unit storedUnit = Measurement.Unit.valueOf(ctx.get(_unitsField).toString());
            if (storedUnit.isCompatible(sampleTypeUnit))
                return storedUnit.convertAmount(storedAmount, sampleTypeUnit);
            else
                return storedAmount;
        }
        catch (IllegalArgumentException e) // units provided are not supported.
        {
            return storedAmount;
        }
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.add(FieldKey.fromParts(_unitsField));
        keys.add(FieldKey.fromParts(_defaultUnitsField.split("/")));
    }

    @Override
    public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
    {
        Object convertedValue = convertToDisplayUnits(ctx);
        return HtmlString.unsafe(convertedValue == null ? null : convertedValue.toString());
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return convertToDisplayUnits(ctx);
    }
}
