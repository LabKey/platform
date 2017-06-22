/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.stats.BaseAggregatesAnalyticsProvider;
import org.labkey.api.data.AnalyticsProviderItem;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import javax.servlet.ServletException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.query.CustomViewInfo.CONTAINER_FILTER_NAME;
import static org.labkey.api.query.CustomViewInfo.FILTER_PARAM_PREFIX;

// Helper class to serialize a CustomView to/from json
public class CustomViewUtil
{
    public static void update(CustomView view, JSONObject jsonView, boolean saveFilterAndSort)
    {
        List<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>> fields = new ArrayList<>();

        JSONArray jsonColumns = jsonView.optJSONArray("columns");
        if (jsonColumns == null || jsonColumns.length() == 0)
            throw new IllegalArgumentException("You must select at least one field to display in the grid.");

        for (Map<String, Object> column : jsonColumns.toMapList())
        {
            FieldKey key = FieldKey.fromString((String)column.get("fieldKey"));
            String title = column.containsKey("title") ? StringUtils.trimToNull((String)column.get("title")) : null;
            Map<CustomViewInfo.ColumnProperty, String> map = Collections.emptyMap();
            if (title != null)
            {
                map = new EnumMap<>(CustomViewInfo.ColumnProperty.class);
                map.put(CustomViewInfo.ColumnProperty.columnTitle, title);
            }
            fields.add(Pair.of(key, map));
        }

        view.setColumnProperties(fields);
        if (!saveFilterAndSort)
            return;

        ActionURL url = new ActionURL();

        JSONArray jsonFilters = jsonView.optJSONArray("filter");
        if (jsonFilters != null && jsonFilters.length() > 0)
        {
            for (Map<String, Object> filterInfo : jsonFilters.toMapList())
            {
                String fieldKey = (String)filterInfo.get("fieldKey");
                String op = (String)filterInfo.get("op");
                if (op == null)
                    op = "";

                String value = (String)filterInfo.get("value");
                if (value == null)
                    value = "";

                url.addParameter(FILTER_PARAM_PREFIX + "." + fieldKey + "~" + op, value);
            }
        }

        JSONArray jsonSorts = jsonView.optJSONArray("sort");
        if (jsonSorts != null && jsonSorts.length() > 0)
        {
            Sort sort = new Sort();
            for (Map<String, Object> sortInfo : jsonSorts.toMapList())
            {
                String fieldKey = (String)sortInfo.get("fieldKey");
                String dir = (String)sortInfo.get("dir");

                Sort.SortDirection sortDir = Sort.SortDirection.fromString(dir);
                sort.appendSortColumn(FieldKey.fromString(fieldKey), sortDir, true);
            }
            sort.applyToURL(url, FILTER_PARAM_PREFIX, false);
        }

        // aggregates have been deprecated in favor of analyticsProviders,
        // so map any saved "aggregates" to use the analytics provider param key
        JSONArray jsonAggregates = jsonView.optJSONArray("aggregates");
        if (jsonAggregates != null && jsonAggregates.length() > 0)
        {
            for (Map<String, Object> aggInfo : jsonAggregates.toMapList())
            {
                String fieldKey = StringUtils.trimToNull((String)aggInfo.get("fieldKey"));
                String type = StringUtils.trimToNull((String)aggInfo.get("type"));

                if (fieldKey == null || type == null)
                    continue;

                type = BaseAggregatesAnalyticsProvider.PREFIX + PageFlowUtil.encode(type);
                url.addParameter(CustomViewInfo.getAnalyticsProviderParamKey(fieldKey), type);
            }
        }

        JSONArray jsonAnalyticsProviders = jsonView.optJSONArray("analyticsProviders");
        if (jsonAnalyticsProviders != null && jsonAnalyticsProviders.length() > 0)
        {
            for (Map<String, Object> apInfo : jsonAnalyticsProviders.toMapList())
            {
                String fieldKey = StringUtils.trimToNull((String)apInfo.get("fieldKey"));
                String name = StringUtils.trimToNull((String)apInfo.get("name"));
                if (fieldKey == null || name == null)
                    continue;

                url.addParameter(CustomViewInfo.getAnalyticsProviderParamKey(fieldKey), name);
            }
        }

        String containerFilter = StringUtils.trimToNull(jsonView.optString("containerFilter"));
        if (containerFilter != null)
            url.addParameter(FILTER_PARAM_PREFIX + "." + CONTAINER_FILTER_NAME, containerFilter);

        view.setFilterAndSortFromURL(url, FILTER_PARAM_PREFIX);
    }

    public static Map<String, Object> toMap(ViewContext context, UserSchema schema, String queryName, String viewName, boolean includeFieldMeta, boolean initializeMissingView, Map<FieldKey, Map<String, Object>> columnMetadata)
            throws ServletException
    {
        //build a query view.  XXX: is this necessary?  Old version used queryView.getDisplayColumns() to get cols in the default view
        QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, queryName, viewName);
        QueryView qview = schema.createView(context, settings, null);

        boolean newView = false;
        CustomView view = qview.getCustomView();
        if (view == null)
        {
            if (viewName == null || initializeMissingView)
            {
                // create a new default view if it doesn't exist
                // Note that the view is not saved to the database yet.
                view = qview.getQueryDef().createCustomView(context.getUser(), viewName);
                newView = true;
            }
            else
            {
                return Collections.emptyMap();
            }
        }

        Map<String, Object> ret = toMap(view, context.getUser(), includeFieldMeta, columnMetadata);
        if (newView)
            ret.put("doesNotExist", true);

        return ret;
    }

    public static Map<String, Object> toMap(CustomView view, @NotNull User user, boolean includeFieldMeta)
    {
        assert user != null;
        return toMap(view, user, includeFieldMeta, new HashMap<>());
    }

    public static Map<String, Object> toMap(CustomView view, @NotNull User user, boolean includeFieldMeta, Map<FieldKey, Map<String, Object>> columnMetadata)
    {
        assert user != null;
        Map<String, Object> ret = QueryService.get().getCustomViewProperties(view, user);

        ActionURL gridURL = view.getQueryDefinition().urlFor(QueryAction.executeQuery);
        if (gridURL != null)
        {
            if (view.getName() != null)
                gridURL.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.viewName.name(), view.getName());
            ret.put("viewDataUrl", gridURL);
        }

        QueryDefinition queryDef = view.getQueryDefinition();
        TableInfo tinfo = queryDef.getTable(null, true);

        List<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>> columns = view.getColumnProperties();
        if (columns.isEmpty())
        {
            // Defer to the list of columns from the default view if possible
            if (view.getName() != null)
            {
                CustomView defaultView = queryDef.getCustomView(user, null, null);
                if (defaultView != null)
                {
                    columns = defaultView.getColumnProperties();
                }
            }
        }
        if (columns.isEmpty() && tinfo != null)
        {
            columns = new ArrayList<>();
            for (FieldKey key : tinfo.getDefaultVisibleColumns())
            {
                columns.add(Pair.of(key, Collections.emptyMap()));
            }
        }

        Set<FieldKey> allKeys = new LinkedHashSet<>();
        List<Map<String, Object>> colInfos = new ArrayList<>();
        for (Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>> entry : columns)
        {
            allKeys.add(entry.getKey());

            Map<String, Object> colInfo = new LinkedHashMap<>();
            colInfo.put("name", entry.getKey().getName());
            colInfo.put("key", entry.getKey().toString());
            colInfo.put("fieldKey", entry.getKey().toString());
            if (!entry.getValue().isEmpty())
            {
                String columnTitle = entry.getValue().get(CustomViewInfo.ColumnProperty.columnTitle);
                if (columnTitle != null)
                    colInfo.put("title", columnTitle);
            }
            colInfos.add(colInfo);
        }
        ret.put("columns", colInfos);

        List<Map<String, Object>> filterInfos = new ArrayList<>();
        List<Map<String, Object>> sortInfos = new ArrayList<>();
        List<Map<String, Object>> analyticsProvidersInfos = new ArrayList<>();
        try
        {
            CustomViewInfo.FilterAndSort fas = CustomViewInfo.FilterAndSort.fromString(view.getFilterAndSort());
            for (FilterInfo filter : fas.getFilter())
            {
                Map<String, Object> filterInfo = new HashMap<>();
                filterInfo.put("fieldKey", filter.getField().toString());
                filterInfo.put("op", filter.getOp() != null ? filter.getOp().getPreferredUrlKey() : "");
                filterInfo.put("value", filter.getValue());
                allKeys.add(filter.getField());
                filterInfos.add(filterInfo);
            }

            for (Sort.SortField sf : fas.getSort())
            {
                Map<String, Object> sortInfo = new HashMap<>();
                sortInfo.put("fieldKey", sf.getFieldKey());
                sortInfo.put("dir", sf.getSortDirection().getDir());
                allKeys.add(FieldKey.fromString(sf.getFieldKey().toString()));
                sortInfos.add(sortInfo);
            }

            for (AnalyticsProviderItem analyticsProvider : fas.getAnalyticsProviders())
            {
                Map<String, Object> apInfo = new HashMap<>();
                apInfo.put("fieldKey", analyticsProvider.getFieldKey());
                apInfo.put("name", analyticsProvider.getName());
                apInfo.put("label", analyticsProvider.getLabel());
                apInfo.put("isSummaryStatistic", analyticsProvider.isSummaryStatistic());
                allKeys.add(FieldKey.fromString(analyticsProvider.getFieldKey().toString()));
                analyticsProvidersInfos.add(apInfo);
            }
        }
        catch (URISyntaxException e)
        {
        }

        ret.put("filter", filterInfos);
        ret.put("sort", sortInfos);
        ret.put("analyticsProviders", analyticsProvidersInfos);
        ret.put("containerFilter", view.getContainerFilterName());

        if (includeFieldMeta)
        {
            Set<FieldKey> uncachedFieldKeys = new HashSet<>();
            for (FieldKey key : allKeys)
            {
                if (!columnMetadata.containsKey(key))
                {
                    uncachedFieldKeys.add(key);
                }
            }


            List<Map<String, Object>> allColMaps = new ArrayList<>(allKeys.size());
            if (tinfo != null)
            {
                Map<FieldKey, ColumnInfo> allCols = QueryService.get().getColumns(tinfo, uncachedFieldKeys);
                for (FieldKey field : allKeys)
                {
                    Map<String, Object> metadata = columnMetadata.get(field);
                    if (metadata == null)
                    {
                        // Column may be in select list but not present in the actual table
                        ColumnInfo col = allCols.get(field);
                        if (col != null)
                        {
                            DisplayColumn dc = col.getDisplayColumnFactory().createRenderer(col);
                            metadata = JsonWriter.getMetaData(dc, null, false, true, false);
                            columnMetadata.put(field, metadata);
                        }
                    }
                    if (metadata != null)
                        allColMaps.add(metadata);
                }
            }
            // property name "fields" matches LABKEY.Query.ExtendedSelectRowsResults (ie, metaData.fields)
            ret.put("fields", allColMaps);
        }

        return ret;
    }
}
