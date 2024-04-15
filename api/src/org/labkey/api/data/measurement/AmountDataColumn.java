package org.labkey.api.data.measurement;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
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
import static org.labkey.api.data.measurement.UnitsDataColumn.getFieldKey;

public class AmountDataColumn extends DataColumn
{
    public static final String AMOUNT_FIELD_PROPERTY_NAME = "amountField";

    private final MultiValuedMap<String, String> _properties;
    private final FieldKey _unitsField;
    private final FieldKey _defaultUnitsField;
    private final FieldKey _amountField;

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
        FieldKey fieldKeyParent = getBoundColumn().getFieldKey().getParent();
        _unitsField = getFieldKey(fieldKeyParent, _properties, UNITS_FIELD_PROPERTY_NAME, "Units");
        _defaultUnitsField = getFieldKey(fieldKeyParent, _properties, DEFAULT_UNITS_FIELD_PROPERTY_NAME, null);
        _amountField = getFieldKey(fieldKeyParent, _properties, AMOUNT_FIELD_PROPERTY_NAME, null);
    }

    private Object convertToDisplayUnits(RenderContext ctx)
    {
        Double storedAmount;
        Object amountObj = ctx.get(_amountField);
        // Issue 48500: For the MultiValuedDisplayColumn this may be an empty string when the actual value is null
        if (amountObj instanceof String)
            if (StringUtils.isEmpty((String) amountObj))
                storedAmount = null;
            else
                storedAmount = Double.parseDouble((String) amountObj);
        else
            storedAmount = (Double) amountObj;
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

        if (storedAmount == null)
            return null;

        if (ctx.get(_unitsField) == null)
            return Precision.round(storedAmount, 6);

        try
        {
            Measurement.Unit storedUnit = Measurement.Unit.valueOf(ctx.get(_unitsField).toString());
            if (storedUnit.isCompatible(sampleTypeUnit))
                return storedUnit.convertAmountForDisplay(storedAmount, sampleTypeUnit);
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
        keys.add(_unitsField);
        if (_defaultUnitsField != null)
            keys.add(_defaultUnitsField);
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
