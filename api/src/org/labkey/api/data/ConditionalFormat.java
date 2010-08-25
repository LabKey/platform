package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.gwt.client.model.GWTConditionalFormat;
import org.labkey.api.util.URLHelper;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.ConditionalFormatType;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Aug 20, 2010
 */
public class ConditionalFormat extends GWTConditionalFormat
{

    public SimpleFilter getSimpleFilter()
    {
        SimpleFilter result = new SimpleFilter();
        try
        {
            URLHelper url = new URLHelper("/?" + getFilter());
            result.addUrlFilters(url, DATA_REGION_NAME);
        }
        catch (URISyntaxException e) {}
        return result;
    }

    public boolean meetsCriteria(Object value)
    {
        for (SimpleFilter.FilterClause filterClause : getSimpleFilter().getClauses())
        {
            if (!filterClause.meetsCriteria(value))
            {
                return false;
            }
        }
        return true;
    }

    @NotNull
    public String getCssStyle()
    {
        StringBuilder sb = new StringBuilder();
        if (isStrikethrough())
        {
            sb.append("text-decoration: line-through;");
        }

        if (isBold())
        {
            sb.append("font-weight: bold;");
        }

        if (isItalic())
        {
            sb.append("font-style: italic;");
        }

        if (getTextColor() != null)
        {
            sb.append("color: #");
            sb.append(getTextColor());
            sb.append(";");
        }

        if (getBackgroundColor() != null)
        {
            sb.append("background-color: #");
            sb.append(getBackgroundColor());
            sb.append(";");
        }

        return sb.toString();
    }

    public static List<ConditionalFormat> convertFromXml(ColumnType.ConditionalFormats conditionalFormats)
    {
        List<ConditionalFormat> result = new ArrayList<ConditionalFormat>();
        for (ConditionalFormatType xmlFormat : conditionalFormats.getConditionalFormatArray())
        {
            ConditionalFormat format = new ConditionalFormat();
            format.setFilter(xmlFormat.getFilter());
            if (xmlFormat.isSetBold() && xmlFormat.getBold())
            {
                format.setBold(true);
            }
            if (xmlFormat.isSetItalics() && xmlFormat.getItalics())
            {
                format.setItalic(true);
            }
            if (xmlFormat.isSetStrikethrough() && xmlFormat.getStrikethrough())
            {
                format.setStrikethrough(true);
            }
            if (xmlFormat.isSetBackgroundColor())
            {
                format.setBackgroundColor(xmlFormat.getBackgroundColor());
            }
            if (xmlFormat.isSetColor())
            {
                format.setTextColor(xmlFormat.getColor());
            }
            result.add(format);
        }
        return result;
    }
}
