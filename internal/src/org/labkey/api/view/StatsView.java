package org.labkey.api.view;

import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.common.tools.DoubleArray;
import org.labkey.api.util.PageFlowUtil;

import java.io.Writer;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.Map;
import java.util.HashMap;

/**
 * Copyright (C) 2004 Fred Hutchinson Cancer Research Center. All Rights Reserved.
 * User: migra
 * Date: Mar 2, 2006
 * Time: 12:24:35 PM
 */
public class StatsView extends GridView
{
    Map<String,DoubleArray> valueMap = new HashMap<String, DoubleArray>();
    String[] cols;

    public StatsView(DataRegion dataRegion, String statsString)
    {
        super(dataRegion);
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


        Map<String, Stats.DoubleStats> statMap = new HashMap<String, Stats.DoubleStats>();
        for (String col : cols)
            statMap.put(col, new Stats.DoubleStats(valueMap.get(col).toArray(null)));

        out.write("<table><tr><td class=\"normal\"></td>");
        for (String col : cols)
        {
            out.write("<td class=\"normal\" style=\"font-weight:bold\" >");
            out.write(PageFlowUtil.filter(col));
            out.write("</td>");
        }
        out.write("</td></tr>");

        out.write("<tr><td class=\"normal\" style=\"font-weight:bold\" >count</td>");
        for (String col : cols)
            out.write("<td class=\"normal\">" + fmt(statMap.get(col).getCount()) + "</td>");
        out.write("</tr>");


        out.write("<tr><td class=\"normal\" style=\"font-weight:bold\" >mean</td>");
        for (String col : cols)
            out.write("<td class=\"normal\">" + fmt(statMap.get(col).getMean()) + "</td>");
        out.write("</tr>");

        out.write("<tr><td class=\"normal\" style=\"font-weight:bold\" >min</td>");
        for (String col : cols)
            out.write("<td class=\"normal\">" + fmt(statMap.get(col).getMin()) + "</td>");
        out.write("</tr>");

        out.write("<tr><td class=\"normal\" style=\"font-weight:bold\" >max</td>");
        for (String col : cols)
            out.write("<td class=\"normal\">" + fmt(statMap.get(col).getMax()) + "</td>");
        out.write("</tr>");

        out.write("<tr><td class=\"normal\" style=\"font-weight:bold\" >stdDev</td>");
        for (String col : cols)
            out.write("<td class=\"normal\">" + fmt(statMap.get(col).getStdDev()) + "</td>");
        out.write("</tr>");

        out.write("</table>");
    }

    private String fmt(double d)
    {
        return Double.isNaN(d) ? "" : String.valueOf(((int) (d * 1000))/1000.0);
    }

}
