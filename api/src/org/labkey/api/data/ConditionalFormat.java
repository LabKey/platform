/*
 * Copyright (c) 2010-2018 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.gwt.client.model.GWTConditionalFormat;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.ConditionalFormatFilterType;
import org.labkey.data.xml.ConditionalFormatFiltersType;
import org.labkey.data.xml.ConditionalFormatType;
import org.labkey.data.xml.ConditionalFormatsType;

import java.awt.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conditional formats allow columns values to be styled based on their values. For example, values that fall below the
 * normal range might be rendered in red text or bolded.
 * User: jeckels
 * Date: Aug 20, 2010
 */
public class ConditionalFormat extends GWTConditionalFormat
{
    private static final Logger LOG = LogManager.getLogger(ConditionalFormat.class);

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
        catch (URISyntaxException ignored) {}
        return result;
    }

    /**
     * Checks if the conditional format applies to the value.
     * @throws RuntimeSQLException if the filter parameters are invalid.
     */
    private boolean _meetsCriteria(ColumnRenderProperties col, Object value)
    {
        for (FilterClause filterClause : getSimpleFilter().getClauses())
        {
            if (!filterClause.meetsCriteria(col, value))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the conditional format applies to the value.
     */
    public boolean meetsCriteria(ColumnInfo col, Object value)
    {
        try
        {
            return _meetsCriteria(col, value);
        }
        catch (RuntimeSQLException e)
        {
            // CompareType.convertParamValue will throw if the filter clause parameter(s) can't be converted
            if (e.getSQLException() instanceof SQLGenerationException)
            {
                // Error message should have already been reported by <code>validateFormat</code>
                return false;
            }
            else
            {
                throw e;
            }
        }
    }

    /**
     * Validates the conditional format filter parameter values.
     * @return An error message if the format is invalid, otherwise null
     */
    @Nullable
    public String validateFormat(@NotNull ColumnRenderProperties col)
    {
        try
        {
            _meetsCriteria(col, newValue(col));
            return null;
        }
        catch (RuntimeSQLException e)
        {
            // Deal with unparseable filter values - see issue 23321
            // CompareType.convertParamValue will throw if the filter clause parameter(s) can't be converted
            if (e.getSQLException() instanceof SQLGenerationException)
            {
                return "Invalid conditional format filter: " + e.getMessage();
            }
            else
            {
                throw e;
            }
        }
    }

    // best-effort to create new value instance for validating conditional format parameters
    private Object newValue(ColumnRenderProperties col)
    {
        try
        {
            return col.getJavaClass().getDeclaredConstructor().newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            // ok
            return null;
        }
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
            // Issue 38333: add !important so that the conditional format wins out when grid row click/hover style applied
            sb.append(" !important;");
        }

        return sb.toString();
    }

    /** Converts from the XMLBean representation to our standard class. Does not save to the database */
    @NotNull
    public static List<ConditionalFormat> convertFromXML(ConditionalFormatsType conditionalFormats)
    {
        if (conditionalFormats == null)
        {
            return Collections.emptyList();
        }
        List<ConditionalFormat> result = new ArrayList<>();
        for (ConditionalFormatType xmlFormat : conditionalFormats.getConditionalFormatArray())
        {
            ConditionalFormat format = new ConditionalFormat();
            SimpleFilter simpleFilter = new SimpleFilter();
            ConditionalFormatFiltersType filters = xmlFormat.getFilters();
            if (null != filters)
            {
                ConditionalFormatFilterType[] filterArray = filters.getFilterArray();
                if (filterArray != null)
                {
                    for (ConditionalFormatFilterType filter : filterArray)
                    {
                        CompareType compareType = CompareType.getByURLKey(filter.getOperator().toString());
                        if (compareType != null)
                        {
                            simpleFilter.addClause(compareType.createFilterClause(FieldKey.fromParts(COLUMN_NAME), filter.getValue()));
                        }
                        else
                        {
                            LOG.warn("Could not find CompareType for " + filter.getOperator() + ", ignoring");
                        }
                    }
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
            if (!addToXML(formats, xmlFormats))
            {
                LOG.warn("One or more invalid conditional formats were discovered on column \"" + xmlColumn.getColumnName() + "\"");
            }
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
            if (!addToXML(formats, xmlFormats))
            {
                LOG.warn("One or more invalid conditional formats were discovered on property \"" + xmlProp.getName() + "\"");
            }
        }
    }

    private Color getParsedColor(String colorString)
    {
        if (colorString == null || colorString.isEmpty() || !colorString.matches(COLOR_REGEX))
        {
            return null;
        }
        colorString = colorString.toUpperCase();

        String redString = colorString.substring(0, 2);
        String greenString = colorString.substring(2, 4);
        String blueString = colorString.substring(4, 6);

        return new Color(Integer.parseInt(redString, 16), Integer.parseInt(greenString, 16), Integer.parseInt(blueString, 16));
    }

    public Color getParsedTextColor()
    {
        return getParsedColor(getTextColor());
    }

    public Color getParsedBackgroundColor()
    {
        return getParsedColor(getBackgroundColor());
    }

    // Returns true if all formats were valid and successfully serialized to XML
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean addToXML(List<? extends GWTConditionalFormat> formats, ConditionalFormatsType xmlFormats)
    {
        boolean success = true;

        for (GWTConditionalFormat baseFormat : formats)
        {
            ConditionalFormat format = new ConditionalFormat(baseFormat);
            SimpleFilter simpleFilter = format.getSimpleFilter();
            // issue 20350 and subsequent failures - don't create a conditional format until we know the filter has clauses.
            if (null != simpleFilter.getClauses() && !simpleFilter.getClauses().isEmpty())
            {
                ConditionalFormatType xmlFormat = xmlFormats.addNewConditionalFormat();
                xmlFormat.addNewFilters();
                for (FilterClause filterClause : simpleFilter.getClauses())
                {
                    ConditionalFormatFilterType xmlFilter = xmlFormat.getFilters().addNewFilter();

                    if (filterClause instanceof CompareType.AbstractCompareClause)
                    {
                        CompareType.AbstractCompareClause compareClause = (CompareType.AbstractCompareClause)filterClause;
                        xmlFilter.setOperator(compareClause.getCompareType().getXmlType());
                        String value = compareClause.toURLParamValue();
                        if (value != null)
                            xmlFilter.setValue(value);
                    }
                    else
                    {
                        throw new IllegalArgumentException("Unsupported filter clause: " + filterClause);
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
            else
            {
                success = false;
            }
        }

        return success;
    }
}
