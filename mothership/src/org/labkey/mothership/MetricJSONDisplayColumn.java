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

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if(ctx.get("jsonMetrics") != null && ctx.get("jsonMetrics") != "")
        {
            String json = (String) ctx.get("jsonMetrics");
            String path = "$.folderTypeCounts";
            Map<String, Integer> folderType = JsonPath.parse(json).read(path);

            if (folderType.containsKey(_jsonProp))
                out.write(String.valueOf(folderType.get(_jsonProp)));
        }
    }
}
