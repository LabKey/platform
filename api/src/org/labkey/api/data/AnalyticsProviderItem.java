/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.BaseAggregatesAnalyticsProvider;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.stats.SummaryStatisticRegistry;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AnalyticsProviderItem
{
    private static final Logger LOG = Logger.getLogger(AnalyticsProviderItem.class);

    private FieldKey _fieldKey;
    private String _name;
    private @Nullable String _label;
    private boolean _isSummaryStatistic;

    public AnalyticsProviderItem(FieldKey fieldKey, String name, @Nullable String label)
    {
        _fieldKey = fieldKey;
        _name = name;
        _label = label;
    }

    public AnalyticsProviderItem(FieldKey fieldKey, ColumnAnalyticsProvider analyticsProvider, @Nullable String label)
    {
        this(fieldKey, analyticsProvider.getName(), label);
        _isSummaryStatistic = analyticsProvider instanceof BaseAggregatesAnalyticsProvider;
    }

    public AnalyticsProviderItem(Aggregate aggregate)
    {
        this(aggregate.getFieldKey(), aggregate.getName(), aggregate.getLabel());
        _isSummaryStatistic = true;
    }

    public FieldKey getFieldKey()
    {
        return _fieldKey;
    }

    public void setFieldKey(FieldKey fieldKey)
    {
        _fieldKey = fieldKey;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public boolean isSummaryStatistic()
    {
        return _isSummaryStatistic;
    }

    public void setSummaryStatistic(boolean summaryStatistic)
    {
        _isSummaryStatistic = summaryStatistic;
    }

    @NotNull
    public static List<AnalyticsProviderItem> fromURL(URLHelper urlHelper, String regionName)
    {
        List<Pair<String, Object>> paramPairs = new ArrayList<>();
        for (Pair<String, String> stringPair : urlHelper.getParameters())
            paramPairs.add(new Pair<>(stringPair.getKey(), stringPair.getValue()));

        return fromURL(paramPairs, regionName);
    }

    @NotNull
    public static List<AnalyticsProviderItem> fromURL(PropertyValues pvs, String regionName)
    {
        List<Pair<String, Object>> paramPairs = new ArrayList<>();
        for (PropertyValue val : pvs.getPropertyValues())
            paramPairs.add(new Pair<>(val.getName(), val.getValue()));

        return fromURL(paramPairs, regionName);
    }

    @NotNull
    private static List<AnalyticsProviderItem> fromURL(List<Pair<String, Object>> paramPairs, String regionName)
    {
        String aggPrefix = regionName + "." + CustomViewInfo.AGGREGATE_PARAM_PREFIX + ".";
        String apPrefix = regionName + "." + CustomViewInfo.ANALYTICSPROVIDER_PARAM_PREFIX + ".";

        List<AnalyticsProviderItem> analyticsProviderItems = new LinkedList<>();

        for (Pair<String, Object> val : paramPairs)
        {
            boolean isAggregate = val.getKey().startsWith(aggPrefix);
            boolean isAnalyticsProvider = val.getKey().startsWith(apPrefix);

            if (isAggregate || isAnalyticsProvider)
            {
                FieldKey fieldKey = isAggregate
                    ? FieldKey.fromString(val.getKey().substring(aggPrefix.length()))
                    : FieldKey.fromString(val.getKey().substring(apPrefix.length()));

                List<String> values = new ArrayList<>();
                if (val.getValue() instanceof String)
                    values.add((String) val.getValue());
                else
                    Collections.addAll(values, (String[]) val.getValue());

                for (String s : values)
                {
                    Map<String, String> properties = decodeUrlProperties(s);
                    String label = properties.containsKey("label") ? properties.get("label") : null;
                    ColumnAnalyticsProvider analyticsProvider = getAnalyticsProviderFromType(properties.get("type"));

                    if (analyticsProvider != null)
                        analyticsProviderItems.add(new AnalyticsProviderItem(fieldKey, analyticsProvider, label));
                }
            }
        }

        return analyticsProviderItems;
    }

    /**
     * Allow analytics provider types either in the basic form, ie. filter.analytics.columnName=AGG_MAX,
     * or more complex, ie: filter.analytics.columnName=label=xyz&type%3BAGG_MAX
     * @param value value to decode type from
     * @return map of properties
     */
    private static Map<String, String> decodeUrlProperties(String value)
    {
        Map<String, String> properties = new HashMap<>();

        value = PageFlowUtil.decode(value);
        if (!value.contains("="))
        {
            properties.put("type", value);
        }
        else
        {
            for (Pair<String, String> entry : PageFlowUtil.fromQueryString(PageFlowUtil.decode(value)))
                properties.put(entry.getKey().toLowerCase(), entry.getValue());
        }

        return properties;
    }

    /**
     * Try to find a ColumnAnalyticsProvider for the type/name.
     * @param type String value to attempt to find a matching ColumnAnalyticsProvider for
     * @return ColumnAnalyticsProvider that match the given type String
     */
    private static @Nullable ColumnAnalyticsProvider getAnalyticsProviderFromType(String type)
    {
        if (type == null)
            return null;

        SummaryStatisticRegistry ssRegistry = ServiceRegistry.get().getService(SummaryStatisticRegistry.class);
        AnalyticsProviderRegistry apRegistry = ServiceRegistry.get().getService(AnalyticsProviderRegistry.class);

        // backwards compatibility for AVG summary stat to map to MEAN
        if ("AVG".equalsIgnoreCase(type))
            type = "MEAN";
        else if ((BaseAggregatesAnalyticsProvider.PREFIX + "AVG").equalsIgnoreCase(type))
            type = BaseAggregatesAnalyticsProvider.PREFIX + "MEAN";

        // backwards compatibility for aggregates to map to AGG_<aggregate name>
        if (ssRegistry != null && ssRegistry.getByName(type) != null)
            type = BaseAggregatesAnalyticsProvider.PREFIX + type;

        ColumnAnalyticsProvider analyticsProvider = apRegistry != null ? apRegistry.getColumnAnalyticsProvider(type) : null;

        if (analyticsProvider == null)
        {
            //throw new IllegalArgumentException("Invalid analytic provider name: '" + type + "'.");
            LOG.warn("Invalid analytic provider name: '" + type + "'.");
        }

        return analyticsProvider;
    }

    public String getValueForUrl()
    {
        if (getLabel() == null)
            return getName();

        StringBuilder ret = new StringBuilder();
        ret.append(PageFlowUtil.encode("label=" + getLabel()));
        ret.append(PageFlowUtil.encode("&type=" + getName()));
        return ret.toString();
    }

    /**
     * Add the analytics provider parameter on the url
     * @param url The url to be modified.
     * @param regionName The dataRegion used to scope the sort.
     * @param fieldKey The fieldKey to use in the url parameter
     */
    public void applyToURL(URLHelper url, String regionName, FieldKey fieldKey)
    {
        url.addParameter(CustomViewInfo.getAnalyticsProviderParamKey(regionName, fieldKey.toString()), getValueForUrl());
    }

    @NotNull
    public List<Aggregate> createAggregates()
    {
        if (isSummaryStatistic())
        {
            AnalyticsProviderRegistry apRegistry = ServiceRegistry.get().getService(AnalyticsProviderRegistry.class);
            ColumnAnalyticsProvider analyticsProvider = apRegistry != null ? apRegistry.getColumnAnalyticsProvider(getName()) : null;
            if (analyticsProvider != null && analyticsProvider instanceof BaseAggregatesAnalyticsProvider)
            {
                BaseAggregatesAnalyticsProvider baseAggProvider = (BaseAggregatesAnalyticsProvider) analyticsProvider;
                return createAggregates(baseAggProvider, getFieldKey(), getLabel());
            }
        }

        return Collections.emptyList();
    }

    public static List<Aggregate> createAggregates(BaseAggregatesAnalyticsProvider baseAggProvider, FieldKey fieldKey, @Nullable String label)
    {
        List<Aggregate> aggs = new ArrayList<>();
        aggs.add(new Aggregate(fieldKey, baseAggProvider.getAggregateType(), label));

        if (baseAggProvider.getAdditionalAggregateTypes() != null)
        {
            for (Aggregate.Type addType : baseAggProvider.getAdditionalAggregateTypes())
                aggs.add(new Aggregate(fieldKey, addType, label));
        }

        return aggs;
    }
}
