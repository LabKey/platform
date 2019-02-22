package org.labkey.mothership;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import net.minidev.json.JSONArray;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

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

            StringBuilder checkPath = new StringBuilder();
            StringBuilder path = new StringBuilder();
            String[] paths = _jsonProp.split("\\.");

            checkPath.append("$.");
            try
            {
                if (paths.length <= 1)
                {
                    JSONArray jsonArray = JsonPath.parse(json).read(checkPath.append("[?(@.['").append(_jsonProp).append("'])]").toString());
                    if (jsonArray.size() > 0)
                    {
                        int count = JsonPath.parse(json).read(path.append("$.").append(_jsonProp).toString());
                        return String.valueOf(count);
                    }

                }
                else
                {
                    checkPath.append(paths[0]).append("[?(@.");
                    path.append("$.");
                    for (int i = 1; i < paths.length; i++)
                    {
                        checkPath.append("['").append(paths[i]).append("']");
                    }
                    checkPath.append(")]");

                    for (String str : paths)
                    {
                        path.append("['").append(str).append("']");
                    }

                    JSONArray jsonArray = JsonPath.parse(json).read(checkPath.toString());
                    if (jsonArray.size() > 0)
                    {
                        int folderTypeCount = JsonPath.parse(json).read(path.toString());
                        return String.valueOf(folderTypeCount);
                    }
                }
            }
            catch (JsonPathException ex)
            {
                return "Invalid Json Path Exception";
            }
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

    @Override
    public boolean isFilterable()
    {
        return false;
    }
}
