/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import javax.servlet.ServletException;
import java.net.URISyntaxException;
import java.util.*;

// Helper class to serialize a CustomView to/from json
public class CustomViewUtil
{
    protected static final String FILTER_PARAM_PREFIX = "filter";

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

        JSONArray jsonFilters = jsonView.optJSONArray("filters");
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

                String columnName = ((dir != null && dir.length() == 1) ? dir : "") + fieldKey;
                sort.insertSortColumn(columnName, true);
            }
            sort.applyToURL(url, FILTER_PARAM_PREFIX);
        }

        // UNDONE: container filter

        view.setFilterAndSortFromURL(url, FILTER_PARAM_PREFIX);
    }

    public static Map<String, Object> toMap(ViewContext context, UserSchema schema, String queryName, String viewName, boolean includeFieldMeta)
            throws ServletException
    {
        //build a query view.  XXX: is this necessary?  Old version used queryView.getDisplayColumns() to get cols in the default view
        QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, queryName, viewName);
        QueryView qview = schema.createView(context, settings, null);

        CustomView view = qview.getCustomView();
        if (view == null)
        {
            if (viewName == null)
                // create a new default view if it doesn't exist
                view = qview.getQueryDef().createCustomView(context.getUser(), viewName);
            else
                return Collections.emptyMap();
        }

        return toMap(view, includeFieldMeta);
    }

    // UNDONE: Move to API so DataRegion can use this
    public static Map<String, Object> propertyMap(CustomView view)
    {
        Map<String, Object> ret = new LinkedHashMap<String, Object>();
        ret.put("name", view.getName() == null ? "" : view.getName());
        ret.put("default", view.getName() == null);
        if (null != view.getOwner())
            ret.put("owner", view.getOwner().getDisplayName(null));
        ret.put("shared", view.isShared());
        ret.put("inherit", view.canInherit());
        ret.put("session", view.isSession());
        ret.put("editable", view.isEditable());
        ret.put("hidden", view.isHidden());
        ret.put("containerPath", view.getContainer().getPath());
        return ret;
    }

    public static Map<String, Object> toMap(CustomView view, boolean includeFieldMeta)
    {
        Map<String, Object> ret = propertyMap(view);

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
        if (columns.size() == 0)
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
        try
        {
            CustomViewInfo.FilterAndSort fas = CustomViewInfo.FilterAndSort.fromString(view.getFilterAndSort());
            for (FilterInfo filter : fas.getFilter())
            {
                Map<String, Object> filterInfo = new HashMap<String, Object>();
                filterInfo.put("fieldKey", filter.getField().toString());
                filterInfo.put("op", filter.getOp().getPreferredUrlKey());
                filterInfo.put("value", filter.getValue());
                allKeys.add(filter.getField());
                filterInfos.add(filterInfo);
            }

            for (Sort.SortField sf : fas.getSort())
            {
                Map<String, Object> sortInfo = new HashMap<String, Object>();
                sortInfo.put("fieldKey", sf.getColumnName());
                sortInfo.put("dir", sf.getSortDirection().getDir());
                allKeys.add(FieldKey.fromString(sf.getColumnName()));
                sortInfos.add(sortInfo);
            }

        }
        catch (URISyntaxException e)
        {
        }
        ret.put("filter", filterInfos);
        ret.put("sort", sortInfos);

        if (includeFieldMeta)
        {
            Map<FieldKey, ColumnInfo> allCols = QueryService.get().getColumns(tinfo, allKeys);
            List<Map<String, Object>> allColMaps = new ArrayList<Map<String, Object>>(allCols.size());
            for (FieldKey field : allKeys)
            {
                // Column may be in select list but not present in the actual table
                ColumnInfo col = allCols.get(field);
                if (col != null)
                {
                    DisplayColumn dc = col.getDisplayColumnFactory().createRenderer(col);
                    allColMaps.add(JsonWriter.getMetaData(dc, null, false, true));
                }
            }
            // property name "fields" matches LABKEY.Query.ExtendedSelectRowsResults (ie, metaData.fields)
            ret.put("fields", allColMaps);
        }

        return ret;
    }
}
