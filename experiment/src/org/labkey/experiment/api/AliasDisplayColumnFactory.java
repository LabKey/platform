package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MultiValuedDisplayColumn;
import org.labkey.api.data.RenderContext;

import java.util.List;

class AliasDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DataColumn dataColumn = new DataColumn(colInfo, false);
        dataColumn.setInputType("text");

        return new MultiValuedDisplayColumn(dataColumn)
        {
            @Override
            public Object getInputValue(RenderContext ctx)
            {
                Object value = super.getInputValue(ctx);
                if (value instanceof List)
                    return String.join(", ", (List) value);
                return "";
            }
        };
    }
}
