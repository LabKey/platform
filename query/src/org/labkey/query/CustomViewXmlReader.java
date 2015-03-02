/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.data.xml.queryCustomView.AggregateType;
import org.labkey.data.xml.queryCustomView.AggregatesType;
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
    private List<Pair<String,String>> _filters;
    private List<String> _sorts;
    private List<Aggregate> _aggregates;
    private String _customIconUrl;
    private ContainerFilter.Type _containerFilter;
    private boolean _showInDataViews;
    private String _category;

    protected String _name;
    private String _label;


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

    public List<Pair<String, String>> getFilters()
    {
        return _filters;
    }

    public List<Aggregate> getAggregates()
    {
        return _aggregates;
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

    // TODO: There should be a common util for filter/sort url handling.  Should use a proper URL class to do this, not create/encode the query string manually
    public String getFilterAndSortString()
    {
        String sort = getSortParamValue();

        if (null == getFilters() && null == sort && null == getAggregates())
            return null;

        StringBuilder ret = new StringBuilder("?");

        String sep = "";

        if (null != getFilters())
        {
            for (Pair<String, String> filter : getFilters())
            {
                ret.append(sep);
                ret.append(CustomViewInfo.FILTER_PARAM_PREFIX).append(".");
                ret.append(PageFlowUtil.encode(filter.first));
                ret.append("=");
                ret.append(PageFlowUtil.encode(filter.second));
                sep = "&";
            }
        }

        if (null != sort)
        {
            ret.append(sep);
            ret.append(CustomViewInfo.FILTER_PARAM_PREFIX).append(".sort=");
            ret.append(PageFlowUtil.encode(sort));
            sep = "&";
        }

        if (null != getAggregates())
        {
            for (Aggregate aggregate : getAggregates())
            {
                ret.append(sep);
                ret.append(CustomViewInfo.FILTER_PARAM_PREFIX).append(".").append(CustomViewInfo.AGGREGATE_PARAM_PREFIX).append(".");
                ret.append(PageFlowUtil.encode(aggregate.getFieldKey().toString()));
                ret.append("=");
                ret.append(PageFlowUtil.encode(aggregate.getValueForUrl()));
                sep = "&";
            }
        }

        if (null != getContainerFilter())
        {
            ret.append(sep);
            ret.append(CustomViewInfo.FILTER_PARAM_PREFIX).append(".").append(CustomViewInfo.CONTAINER_FILTER_NAME);
            ret.append(getContainerFilter());
        }

        return ret.toString();
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
            reader._customIconUrl = viewElement.isSetCustomIconUrl() ? viewElement.getCustomIconUrl() : "/reports/icon_query_view.png";
            reader._label = viewElement.getLabel();
            if (viewElement.isSetShowInDataViews())
                reader._showInDataViews = viewElement.getShowInDataViews();
            if (viewElement.isSetCategory())
                reader._category = viewElement.getCategory();

            //load the columns, filters, sorts, aggregates
            reader._colList = loadColumns(viewElement.getColumns());
            reader._filters = loadFilters(viewElement.getFilters());
            reader._sorts = loadSorts(viewElement.getSorts());
            reader._aggregates = loadAggregates(viewElement.getAggregates());
            reader._containerFilter = loadContainerFilter(viewElement.getContainerFilter());

            return reader;
        }
        catch (Exception e)
        {
            throw new UnexpectedException(e);
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

    protected static List<Aggregate> loadAggregates(AggregatesType aggregates)
    {
        if (null == aggregates)
            return null;

        List<Aggregate> ret = new ArrayList<>();
        for (AggregateType aggregate : aggregates.getAggregateArray())
        {
            String column = StringUtils.trimToNull(aggregate.getColumn());
            String type = StringUtils.trimToNull(aggregate.getType() != null ? aggregate.getType().toString() : null);
            if (column == null || type == null)
                continue;

            Aggregate map = new Aggregate(FieldKey.fromString(column), Aggregate.Type.valueOf(type));
            if(aggregate.getLabel() != null)
                map.setLabel(aggregate.getLabel());

            ret.add(map);
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
