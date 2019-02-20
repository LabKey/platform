package org.labkey.mothership;

import com.jayway.jsonpath.JsonPath;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;

public class MetricJSONDisplayColumn extends DataColumn
{
    private String _jsonProp;

    public MetricJSONDisplayColumn(ColumnInfo col, Collection<String> props)
    {
        super(col);
        for(String pr: props)
        {
            _jsonProp = pr;
        }
    }

    public String getOutput(RenderContext ctx)
    {
        if(ctx.get("jsonMetrics") != null && ctx.get("jsonMetrics") != "")
        {
            String json = (String) ctx.get("jsonMetrics");
            String path = "$.folderTypeCounts";
            Map<String, Integer> folderType = JsonPath.parse(json).read(path);

            if (folderType.containsKey(_jsonProp))
                return String.valueOf(folderType.get(_jsonProp));
        }
        return "";
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        out.write(getOutput(ctx));
    }

    @Override
    public String getValue(RenderContext ctx)
    {
        return getOutput(ctx);
    }

    @Override
    public Class getValueClass()
    {
        return String.class;
    }

    @Override
    public Class getDisplayValueClass()
    {
        return String.class;
    }


    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return getValue(ctx);
    }
}
