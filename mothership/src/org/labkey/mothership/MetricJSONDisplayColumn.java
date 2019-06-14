/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.mothership;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

public class MetricJSONDisplayColumn extends DataColumn
{
    private static final Logger _log = Logger.getLogger(MetricJSONDisplayColumn.class);

    private String _jsonProp;

    public MetricJSONDisplayColumn(ColumnInfo col, Collection<String> props)
    {
        super(col);
        if(props.size() > 1)
        {
            _log.warn("Multiple properties specified for a column, only the last one will be used.");
        }
        for(String pr: props)
        {
            _jsonProp = pr;
        }
    }

    public String getOutput(RenderContext ctx)
    {
        String json = (String) getColumnInfo().getValue(ctx);

        DocumentContext dc = JsonPath.parse(json);

        StringBuilder path = new StringBuilder();

        try
        {
            Object val = dc.read(path.append("$.").append(_jsonProp).toString());
            return val == null ? "" : val.toString();
        }
        catch (PathNotFoundException ex)
        {
            //no value found
           return "";
        }
        catch (JsonPathException ex)
        {
            return "Invalid Json Path Exception - " + path.toString();
        }

    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        out.write(PageFlowUtil.filter(getOutput(ctx)));
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

    @Override
    public boolean isSortable()
    {
        return false;
    }
}
