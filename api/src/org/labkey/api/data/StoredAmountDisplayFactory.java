package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.SampleMeasurementUnit;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HtmlString;

import java.util.Set;

public class StoredAmountDisplayFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new StoredAmountDataColumn(colInfo);
    }

    static class StoredAmountDataColumn extends DataColumn
    {
        public StoredAmountDataColumn(ColumnInfo col)
        {
            super(col, false);
        }

        private Object convertToDisplayUnits(RenderContext ctx)
        {
            Double storedAmount = (Double) ctx.get("StoredAmount");
            String sampleTypeUnitsStr = (String) ctx.get("SampleTypeUnits");
            SampleMeasurementUnit sampleTypeUnit = null;
            if (!StringUtils.isEmpty(sampleTypeUnitsStr))
            {
                try
                {
                    sampleTypeUnit = SampleMeasurementUnit.valueOf(sampleTypeUnitsStr);
                }
                catch (IllegalArgumentException e)
                {
                    // do nothing; leave unit as null;
                }
            }

            if (ctx.get("Units") == null)
                return storedAmount;

            try
            {
                SampleMeasurementUnit storedUnit = SampleMeasurementUnit.valueOf(ctx.get("Units").toString());
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
            keys.add(FieldKey.fromParts("Units"));
            keys.add(FieldKey.fromParts("SampleTypeUnits"));
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
}
