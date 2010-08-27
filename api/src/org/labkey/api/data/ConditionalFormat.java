package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.gwt.client.model.GWTConditionalFormat;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.ConditionalFormatFilterType;
import org.labkey.data.xml.ConditionalFormatFiltersType;
import org.labkey.data.xml.ConditionalFormatType;
import org.labkey.data.xml.ConditionalFormatsType;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Aug 20, 2010
 */
public class ConditionalFormat extends GWTConditionalFormat
{
    private static final Logger LOG = Logger.getLogger(ConditionalFormat.class);

    public ConditionalFormat(GWTConditionalFormat f)
    {
        super(f);
    }

    public ConditionalFormat()
    {
        super();
    }

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

    public static List<ConditionalFormat> convertFromXML(ConditionalFormatsType conditionalFormats)
    {
        List<ConditionalFormat> result = new ArrayList<ConditionalFormat>();
        if (conditionalFormats == null)
        {
            return result;
        }
        for (ConditionalFormatType xmlFormat : conditionalFormats.getConditionalFormatArray())
        {
            ConditionalFormat format = new ConditionalFormat();
            ConditionalFormatFiltersType filters = xmlFormat.getFilters();
            SimpleFilter simpleFilter = new SimpleFilter();
            for (ConditionalFormatFilterType filter : filters.getFilterArray())
            {
                CompareType compareType = CompareType.getByURLKey(filter.getOperator().toString());
                if (compareType != null)
                {
                    simpleFilter.addClause(compareType.createFilterClause(COLUMN_NAME, filter.getValue()));
                }
                else
                {
                    LOG.warn("Could not find CompareType for " + filter.getOperator() + ", ignoring");
                }
            }
            try
            {
                // Process it through a URL to get the query string equivalent
                URLHelper url = new URLHelper("/test");
                simpleFilter.applyToURL(url, DATA_REGION_NAME);
                format.setFilter(url.getQueryString());
            }
            catch (URISyntaxException e)
            {
                throw new UnexpectedException(e);
            }
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
            if (xmlFormat.isSetTextColor())
            {
                format.setTextColor(xmlFormat.getTextColor());
            }
            result.add(format);
        }
        return result;
    }

    public static void convertToXML(List<? extends GWTConditionalFormat> formats, ColumnType xmlColumn)
    {
        if (xmlColumn.isSetConditionalFormats())
        {
            // Clear out any existing conditional formats in the XML
            xmlColumn.unsetConditionalFormats();
        }
        if (!formats.isEmpty())
        {
            ConditionalFormatsType xmlFormats = xmlColumn.addNewConditionalFormats();
            addToXML(formats, xmlFormats);
        }
    }

    public static void convertToXML(List<? extends GWTConditionalFormat> formats, PropertyDescriptorType xmlProp)
    {
        if (xmlProp.isSetConditionalFormats())
        {
            // Clear out any existing conditional formats in the XML
            xmlProp.unsetConditionalFormats();
        }
        if (!formats.isEmpty())
        {
            ConditionalFormatsType xmlFormats = xmlProp.addNewConditionalFormats();
            addToXML(formats, xmlFormats);
        }
    }

    private static void addToXML(List<? extends GWTConditionalFormat> formats, ConditionalFormatsType xmlFormats)
    {
        for (GWTConditionalFormat baseFormat : formats)
        {
            ConditionalFormat format = new ConditionalFormat(baseFormat);
            ConditionalFormatType xmlFormat = xmlFormats.addNewConditionalFormat();
            xmlFormat.addNewFilters();
            SimpleFilter simpleFilter = format.getSimpleFilter();
            for (SimpleFilter.FilterClause filterClause : simpleFilter.getClauses())
            {
                ConditionalFormatFilterType xmlFilter = xmlFormat.getFilters().addNewFilter();
                if (!(filterClause instanceof CompareType.CompareClause))
                {
                    throw new IllegalArgumentException("Unable to serialize a FilterClause that is not a CompareClause");
                }
                CompareType.CompareClause compareClause = (CompareType.CompareClause)filterClause;
                xmlFilter.setOperator(compareClause.getComparison().getXmlType());
                Object[] paramValues = compareClause.getParamVals();
                if (paramValues != null && paramValues.length > 0 && paramValues[0] != null)
                {
                    xmlFilter.setValue(paramValues[0].toString());
                }
            }
            if (format.isBold())
            {
                xmlFormat.setBold(true);
            }
            if (format.isItalic())
            {
                xmlFormat.setItalics(true);
            }
            if (format.isStrikethrough())
            {
                xmlFormat.setStrikethrough(true);
            }
            if (format.getBackgroundColor() != null)
            {
                xmlFormat.setBackgroundColor(format.getBackgroundColor());
            }
            if (format.getTextColor() != null)
            {
                xmlFormat.setTextColor(format.getTextColor());
            }
        }
    }
}
