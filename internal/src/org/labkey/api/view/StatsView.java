/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.arrays.DoubleArray;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;

import java.io.Writer;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.Map;
import java.util.HashMap;

/**
 * User: migra
 * Date: Mar 2, 2006
 * Time: 12:24:35 PM
 */
public class StatsView extends GridView
{
    Map<String,DoubleArray> valueMap = new HashMap<>();
    String[] cols;

    public StatsView(DataRegion dataRegion, String statsString)
    {
        super(dataRegion, (BindException)null);
        cols = statsString.split(";");
        for (String col : cols)
            valueMap.put(col, new DoubleArray());
    }

    @Override
    protected void _renderDataRegion(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        ResultSet rs = getDataRegion().getResultSet(ctx);
        while (rs.next())
        {
            for (String col : cols)
            {
                Double val = rs.getDouble(col);
                if (null != val)
                    valueMap.get(col).add(val);
            }
        }


        Map<String, Stats.DoubleStats> statMap = new HashMap<>();
        for (String col : cols)
            statMap.put(col, new Stats.DoubleStats(valueMap.get(col).toArray(null)));

        out.write("<table><tr><td></td>");
        for (String col : cols)
        {
            out.write("<td style=\"font-weight:bold\" >");
            out.write(PageFlowUtil.filter(col));
            out.write("</td>");
        }
        out.write("</td></tr>");

        out.write("<tr><td style=\"font-weight:bold\" >count</td>");
        for (String col : cols)
            out.write("<td>" + fmt(statMap.get(col).getCount()) + "</td>");
        out.write("</tr>");


        out.write("<tr><td style=\"font-weight:bold\" >mean</td>");
        for (String col : cols)
            out.write("<td>" + fmt(statMap.get(col).getMean()) + "</td>");
        out.write("</tr>");

        out.write("<tr><td style=\"font-weight:bold\" >min</td>");
        for (String col : cols)
            out.write("<td>" + fmt(statMap.get(col).getMin()) + "</td>");
        out.write("</tr>");

        out.write("<tr><td style=\"font-weight:bold\" >max</td>");
        for (String col : cols)
            out.write("<td>" + fmt(statMap.get(col).getMax()) + "</td>");
        out.write("</tr>");

        out.write("<tr><td style=\"font-weight:bold\" >stdDev</td>");
        for (String col : cols)
            out.write("<td>" + fmt(statMap.get(col).getStdDev()) + "</td>");
        out.write("</tr>");

        out.write("</table>");
    }

    private String fmt(double d)
    {
        return Double.isNaN(d) ? "" : String.valueOf(((int) (d * 1000))/1000.0);
    }

}
