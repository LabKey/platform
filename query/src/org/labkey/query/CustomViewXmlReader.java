/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

package org.labkey.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.stats.SummaryStatisticRegistry;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.AnalyticsProviderItem;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.data.xml.queryCustomView.AggregateType;
import org.labkey.data.xml.queryCustomView.AggregatesType;
import org.labkey.data.xml.queryCustomView.AnalyticsProviderType;
import org.labkey.data.xml.queryCustomView.AnalyticsProvidersType;
import org.labkey.data.xml.queryCustomView.ColumnType;
import org.labkey.data.xml.queryCustomView.ColumnsType;
import org.labkey.data.xml.queryCustomView.ContainerFilterType;
import org.labkey.data.xml.queryCustomView.CustomViewDocument;
import org.labkey.data.xml.queryCustomView.CustomViewType;
import org.labkey.data.xml.queryCustomView.FilterType;
import org.labkey.data.xml.queryCustomView.FiltersType;
import org.labkey.data.xml.queryCustomView.PropertiesType;
import org.labkey.data.xml.queryCustomView.PropertyType;
import org.labkey.data.xml.queryCustomView.SortType;
import org.labkey.data.xml.queryCustomView.SortsType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * User: adam
 * Date: Jun 4, 2009
 * Time: 10:56:23 AM
 */

/*
    Base reader for de-serializing query custom view XML files (creating using queryCustomView.xsd) into a form that's
    compatible with CustomView. This class is used by folder import and simple modules.
 */
public class CustomViewXmlReader
{
    public static final String XML_FILE_EXTENSION = ".qview.xml";

    private String _schema;
    private String _query;
    private List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> _colList = new ArrayList<>();
    private boolean _hidden = false;
    private boolean _canInherit = false;
    private boolean _canOverride = false;
    private List<Pair<String,String>> _filters;
    private List<String> _sorts;
    private List<AnalyticsProviderItem> _analyticsProviders;
    private String _customIconUrl;
    private String _customIconCls;
    private ContainerFilter.Type _containerFilter;
    private boolean _showInDataViews;
    private String _category;

    protected String _name;
    private String _label;

    private List<String> _errors;

    private static final Logger LOG = Logger.getLogger(CustomViewXmlReader.class);

    private CustomViewXmlReader()
    {
    }

    public String getName()
    {
        return _name;
    }

    public String getSchema()
    {
        return _schema;
    }

    public String getQuery()
    {
        return _query;
    }

    public List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> getColList()
    {
        return _colList;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public boolean canInherit()
    {
        return _canInherit;
    }

    public boolean canOverride()
    {
        return _canOverride;
    }

    public List<Pair<String, String>> getFilters()
    {
        return _filters;
    }

    public List<AnalyticsProviderItem> getAnalyticsProviders()
    {
        return _analyticsProviders;
    }

    public ContainerFilter.Type getContainerFilter()
    {
        return _containerFilter;
    }

    public String getLabel()
    {
        return _label;
    }

    public boolean isShowInDataViews()
    {
        return _showInDataViews;
    }

    public String getCategory()
    {
        return _category;
    }

    public List<String> getErrors()
    {
        return _errors;
    }

    public void addError(String error)
    {
        if (_errors == null)
            _errors = new ArrayList<>();

        if (null != error)
            _errors.add(error);
    }

    // TODO: There should be a common util for filter/sort url handling.  Should use a proper URL class to do this, not create/encode the query string manually
    public String getFilterAndSortString()
    {
        String sort = getSortParamValue();

        if (null == getFilters() && null == sort && null == getAnalyticsProviders() && null == getContainerFilter())
            return null;

        StringBuilder ret = new StringBuilder("?");

        String sep = "";

        if (null != getFilters())
        {
            for (Pair<String, String> filter : getFilters())
            {
                String paramName = CustomViewInfo.FILTER_PARAM_PREFIX + "." + filter.first;
                ret.append(sep).append(getFilterAndSortParam(paramName, filter.second));
                sep = "&";
            }
        }

        if (null != sort)
        {
            String paramName = CustomViewInfo.FILTER_PARAM_PREFIX + ".sort";
            ret.append(sep).append(getFilterAndSortParam(paramName, sort));
            sep = "&";
        }

        if (null != getAnalyticsProviders() && !getAnalyticsProviders().isEmpty())
        {
            for (AnalyticsProviderItem analyticsProvider : getAnalyticsProviders())
            {
                String paramName = CustomViewInfo.getAnalyticsProviderParamKey(analyticsProvider.getFieldKey().toString());
                String paramVal = analyticsProvider.getValueForUrl();
                ret.append(sep).append(getFilterAndSortParam(paramName, paramVal));
                sep = "&";
            }
        }

        if (null != getContainerFilter())
        {
            String paramName = CustomViewInfo.FILTER_PARAM_PREFIX + "." + CustomViewInfo.CONTAINER_FILTER_NAME;
            ret.append(sep).append(getFilterAndSortParam(paramName, getContainerFilter().name()));
        }

        return ret.toString();
    }

    private String getFilterAndSortParam(String paramName, String paramVal)
    {
        return PageFlowUtil.encode(paramName) + "=" + PageFlowUtil.encode(paramVal);
    }

    public String getSortParamValue()
    {
        if (null == getSorts())
            return null;

        StringBuilder sortParam = new StringBuilder();
        String sep = "";

        for (String sort : getSorts())
        {
            sortParam.append(sep);
            sortParam.append(sort);
            sep = ",";
        }

        return sortParam.toString();
    }

    public List<String> getSorts()
    {
        return _sorts;
    }

    public String getCustomIconUrl()
    {
        return _customIconUrl;
    }

    public String getCustomIconCls()
    {
        return _customIconCls;
    }

    public static CustomViewXmlReader loadDefinition(Resource r)
    {
        try (InputStream is = r.getInputStream())
        {
            return loadDefinition(is, r.getPath().toString());
        }
        catch (IOException ioe)
        {
            throw new UnexpectedException(ioe);
        }
    }

    public static CustomViewXmlReader loadDefinition(InputStream is, String path)
    {
        try
        {
            CustomViewDocument doc = CustomViewDocument.Factory.parse(is, XmlBeansUtil.getDefaultParseOptions());
            XmlBeansUtil.validateXmlDocument(doc, path);
            CustomViewType viewElement = doc.getCustomView();

            CustomViewXmlReader reader = new CustomViewXmlReader();
            reader._name = viewElement.getName();
            reader._schema = viewElement.getSchema();
            reader._query = viewElement.getQuery();
            reader._hidden = viewElement.isSetHidden() && viewElement.getHidden();
            reader._canInherit = viewElement.isSetCanInherit() && viewElement.getCanInherit();
            reader._canOverride = viewElement.isSetCanOverride() && viewElement.getCanOverride();
            reader._customIconUrl = viewElement.isSetCustomIconUrl() ? viewElement.getCustomIconUrl() : "/reports/grid.gif";
            reader._customIconCls = viewElement.isSetCustomIconUrl() ? "" : "fa fa-table";  // cannot be changed, so pick a sane default
            reader._label = viewElement.getLabel();
            if (viewElement.isSetShowInDataViews())
                reader._showInDataViews = viewElement.getShowInDataViews();
            if (viewElement.isSetCategory())
                reader._category = viewElement.getCategory();

            //load the columns, filters, sorts, analyticsProviders
            reader._colList = loadColumns(viewElement.getColumns());
            reader._filters = loadFilters(viewElement.getFilters());
            reader._sorts = loadSorts(viewElement.getSorts());
            reader._analyticsProviders = loadAnalyticsProviders(viewElement);
            reader._containerFilter = loadContainerFilter(viewElement.getContainerFilter());

            return reader;
        }
        catch (XmlException | IOException | XmlValidationException e)
        {
            LOG.error("Failed to parse custom view file from custom module at location " + path + ", falling back on default view", e);
            CustomViewXmlReader reader = new CustomViewXmlReader();
            reader.addError(e.getMessage());
            return reader;
        }
    }

    protected static List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> loadColumns(ColumnsType columns)
    {
        List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> ret = new ArrayList<>();

        if(null == columns)
            return ret;

        for(ColumnType column : columns.getColumnArray())
        {
            FieldKey fieldKey = getFieldKey(column.getName());
            if(null == fieldKey)
                continue;

            //load any column properties that might be there
            Map<CustomView.ColumnProperty,String> props = new HashMap<>();

            PropertiesType propsList = column.getProperties();
            if(null != propsList)
            {
                for(PropertyType propDef : propsList.getPropertyArray())
                {
                    CustomView.ColumnProperty colProp = CustomViewInfo.ColumnProperty.getForXmlEnum(propDef.getName());

                    if(null == colProp)
                        continue;

                    props.put(colProp, propDef.getValue());
                }
            }

            ret.add(Pair.of(fieldKey, props));
        }

        return ret;
    }

    protected static List<Pair<String, String>> loadFilters(FiltersType filters)
    {
        if(null == filters)
            return null;

        List<Pair<String,String>> ret = new ArrayList<>();
        for(FilterType filter : filters.getFilterArray())
        {
            if(null == filter.getColumn() || null == filter.getOperator())
                continue;

            ret.add(new Pair<>(filter.getColumn() + "~" + filter.getOperator().toString(), filter.getValue()));
        }

        return ret;
    }

    protected static FieldKey getFieldKey(String name)
    {
        return null == name ? null : FieldKey.fromString(name);
    }

    protected static List<String> loadSorts(SortsType sorts)
    {
        if(null == sorts)
            return null;

        List<String> ret = new ArrayList<>();
        for(SortType sort : sorts.getSortArray())
        {
            if(null == sort.getColumn())
                continue;

            ret.add(sort.isSetDescending() && sort.getDescending() ? "-" + sort.getColumn() : sort.getColumn());
        }
        return ret;
    }

    protected static List<AnalyticsProviderItem> loadAnalyticsProviders(CustomViewType customViewType)
    {
        if (customViewType.getAggregates() == null && customViewType.getAnalyticsProviders() == null)
            return null;

        List<AnalyticsProviderItem> ret = new ArrayList<>();
        ret.addAll(loadAggregates(customViewType.getAggregates()));
        ret.addAll(loadAnalyticsProviders(customViewType.getAnalyticsProviders()));
        return ret;
    }

    protected static List<AnalyticsProviderItem> loadAggregates(AggregatesType aggregates)
    {
        if (null == aggregates)
            return Collections.emptyList();

        SummaryStatisticRegistry registry = ServiceRegistry.get().getService(SummaryStatisticRegistry.class);

        List<AnalyticsProviderItem> ret = new ArrayList<>();
        for (AggregateType aggregate : aggregates.getAggregateArray())
        {
            String column = StringUtils.trimToNull(aggregate.getColumn());
            String type = StringUtils.trimToNull(aggregate.getType());
            if (column == null || type == null)
                continue;

            Aggregate.Type aggType = registry != null ? registry.getByName(type) : null;
            if (aggType != null)
            {
                Aggregate agg = new Aggregate(FieldKey.fromString(column), aggType, aggregate.getLabel());
                ret.add(new AnalyticsProviderItem(agg));
            }
            else
                LOG.warn("Invalid summary statistic type: " + type);
        }
        return ret;
    }

    protected static List<AnalyticsProviderItem> loadAnalyticsProviders(AnalyticsProvidersType analyticsProviders)
    {
        if (null == analyticsProviders)
            return Collections.emptyList();

        AnalyticsProviderRegistry registry = ServiceRegistry.get().getService(AnalyticsProviderRegistry.class);

        List<AnalyticsProviderItem> ret = new ArrayList<>();
        for (AnalyticsProviderType analytic : analyticsProviders.getAnalyticsProviderArray())
        {
            String column = StringUtils.trimToNull(analytic.getColumn());
            String type = StringUtils.trimToNull(analytic.getType());
            if (column == null || type == null)
                continue;

            ColumnAnalyticsProvider analyticsProvider = registry != null ? registry.getColumnAnalyticsProvider(type) : null;
            if (analyticsProvider != null)
                ret.add(new AnalyticsProviderItem(FieldKey.fromString(column), type, analytic.getLabel()));
            else
                LOG.warn("Invalid analytics provider name: " + type);
        }

        return ret;
    }

    protected static ContainerFilter.Type loadContainerFilter(ContainerFilterType.Enum containerFilterEnum)
    {
        if (containerFilterEnum == null)
            return null;

        try
        {
            return ContainerFilter.Type.valueOf(containerFilterEnum.toString());
        }
        catch (IllegalArgumentException ex)
        {
            return null;
        }
    }
}
