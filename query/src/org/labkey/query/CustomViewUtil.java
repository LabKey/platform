/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
import org.labkey.api.data.Aggregate;
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

import static org.labkey.api.query.CustomViewInfo.AGGREGATE_PARAM_PREFIX;
import static org.labkey.api.query.CustomViewInfo.CONTAINER_FILTER_NAME;
import static org.labkey.api.query.CustomViewInfo.FILTER_PARAM_PREFIX;

// Helper class to serialize a CustomView to/from json
public class CustomViewUtil
{
    public static void update(CustomView view, JSONObject jsonView, boolean saveFilterAndSort)
    {
        List<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>> fields = new ArrayList<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>>();

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
                map = new EnumMap<CustomViewInfo.ColumnProperty,String>(CustomViewInfo.ColumnProperty.class);
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

        JSONArray jsonAggregates = jsonView.optJSONArray("aggregates");
        if (jsonAggregates != null && jsonAggregates.length() > 0)
        {
            for (Map<String, Object> aggInfo : jsonAggregates.toMapList())
            {
                String fieldKey = StringUtils.trimToNull((String)aggInfo.get("fieldKey"));
                String type = StringUtils.trimToNull((String)aggInfo.get("type"));

                String label = (String)aggInfo.get("label");
                label = StringUtils.trimToNull(label);

                if (fieldKey == null || type == null)
                    continue;

                StringBuilder ret = new StringBuilder();
                if(label != null)
                {
                    ret.append(PageFlowUtil.encode("label=" + label));
                    ret.append(PageFlowUtil.encode("&type=" + type));
                }
                else
                {
                    ret.append(PageFlowUtil.encode(type));
                }

                url.addParameter(FILTER_PARAM_PREFIX + "." + AGGREGATE_PARAM_PREFIX + "." + fieldKey, ret.toString());
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
        return toMap(view, user, includeFieldMeta, new HashMap<FieldKey, Map<String, Object>>());
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
        if (columns.size() == 0 && tinfo != null)
        {
            columns = new ArrayList<Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>>>();
            for (FieldKey key : tinfo.getDefaultVisibleColumns())
            {
                columns.add(Pair.of(key, Collections.<CustomViewInfo.ColumnProperty, String>emptyMap()));
            }
        }

        Set<FieldKey> allKeys = new LinkedHashSet<FieldKey>();
        List<Map<String, Object>> colInfos = new ArrayList<Map<String, Object>>();
        for (Map.Entry<FieldKey, Map<CustomViewInfo.ColumnProperty, String>> entry : columns)
        {
            allKeys.add(entry.getKey());

            Map<String, Object> colInfo = new LinkedHashMap<String, Object>();
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

        List<Map<String, Object>> filterInfos = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> sortInfos = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> aggInfos = new ArrayList<Map<String, Object>>();
        try
        {
            CustomViewInfo.FilterAndSort fas = CustomViewInfo.FilterAndSort.fromString(view.getFilterAndSort());
            for (FilterInfo filter : fas.getFilter())
            {
                Map<String, Object> filterInfo = new HashMap<String, Object>();
                filterInfo.put("fieldKey", filter.getField().toString());
                filterInfo.put("op", filter.getOp() != null ? filter.getOp().getPreferredUrlKey() : "");
                filterInfo.put("value", filter.getValue());
                allKeys.add(filter.getField());
                filterInfos.add(filterInfo);
            }

            for (Sort.SortField sf : fas.getSort())
            {
                Map<String, Object> sortInfo = new HashMap<String, Object>();
                sortInfo.put("fieldKey", sf.getFieldKey());
                sortInfo.put("dir", sf.getSortDirection().getDir());
                allKeys.add(FieldKey.fromString(sf.getFieldKey().toString()));
                sortInfos.add(sortInfo);
            }

            for (Aggregate agg : fas.getAggregates())
            {
                Map<String, Object> aggInfo = new HashMap<String, Object>();
                aggInfo.put("fieldKey", agg.getFieldKey());
                aggInfo.put("type", agg.getType().toString());
                aggInfo.put("label", agg.getLabel());
                allKeys.add(FieldKey.fromString(agg.getFieldKey().toString()));
                aggInfos.add(aggInfo);
            }

        }
        catch (URISyntaxException e)
        {
        }
        ret.put("filter", filterInfos);
        ret.put("sort", sortInfos);
        ret.put("aggregates", aggInfos);
        ret.put("containerFilter", view.getContainerFilterName());
        

        if (includeFieldMeta)
        {
            Set<FieldKey> uncachedFieldKeys = new HashSet<FieldKey>();
            for (FieldKey key : allKeys)
            {
                if (!columnMetadata.containsKey(key))
                {
                    uncachedFieldKeys.add(key);
                }
            }


            List<Map<String, Object>> allColMaps = new ArrayList<Map<String, Object>>(allKeys.size());
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
