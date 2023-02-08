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

public class StoredUnitsDisplayFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new StoredUnitDataColumn(colInfo);
    }

    static class StoredUnitDataColumn extends DataColumn
    {
        StoredUnitDataColumn(ColumnInfo colInfo)
        {
            super(colInfo, false);
        }

        // Display the units of the converted value
        private Object getDisplayUnits(RenderContext ctx)
        {
            String sampleTypeUnitsStr = (String) ctx.get("SampleTypeUnits");
            SampleMeasurementUnit sampleTypeUnit = StringUtils.isEmpty(sampleTypeUnitsStr) ? null : SampleMeasurementUnit.valueOf(sampleTypeUnitsStr);

            String displayUnitStr = null;
            Object units = ctx.get("Units");
            if (units == null)
                displayUnitStr = sampleTypeUnitsStr;
            else
            {
                try
                {
                    // if item.units is compatible with sample type unit, use sample type unit. otherwise, use item.unit
                    SampleMeasurementUnit storedUnit = SampleMeasurementUnit.valueOf(units.toString());
                    if (!storedUnit.isCompatible(sampleTypeUnit))
                        return units;
                    else
                        displayUnitStr = sampleTypeUnitsStr;
                }
                catch (IllegalArgumentException e)
                {
                    displayUnitStr = units.toString();
                }
            }

            // if neither sample type unit or item.unit is available, use aliquots' rollup unit as sample unit
            if (StringUtils.isEmpty(displayUnitStr))
            {
                String aliquotUnitStr = (String) ctx.get("AliquotUnit");

                if (!StringUtils.isEmpty(aliquotUnitStr))
                    displayUnitStr = aliquotUnitStr;
            }

            return displayUnitStr;

        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(FieldKey.fromParts("SampleTypeUnits"));
            keys.add(FieldKey.fromParts("AliquotUnit"));
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
}
