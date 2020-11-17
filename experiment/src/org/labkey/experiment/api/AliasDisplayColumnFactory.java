package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MultiValuedDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;

import java.util.Collections;
import java.util.List;

class AliasDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DataColumn dataColumn = new DataColumn(colInfo);
        dataColumn.setInputType("text");

        return new MultiValuedDisplayColumn(dataColumn, true)
        {
            @Override
            public Object getInputValue(RenderContext ctx)
            {
                Object value = super.getInputValue(ctx);
                StringBuilder sb = new StringBuilder();
                if (value instanceof List)
                {
                    String delim = "";
                    for (Object item : (List) value)
                    {
                        if (item != null)
                        {
                            String name = new TableSelector(ExperimentService.get().getTinfoAlias(), Collections.singleton("Name")).getObject(item, String.class);

                            sb.append(delim);
                            sb.append(name);
                            delim = ",";
                        }
                    }
                }
                return sb.toString();
            }
        };
    }
}
