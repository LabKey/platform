/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;
import java.util.stream.Collectors;

public class JavaScriptExportScriptModel extends ExportScriptModel
{
    public JavaScriptExportScriptModel(QueryView view)
    {
        super(view);
    }

    @Override
    public String getFilters()
    {
        return getFilters("null", "[", "", ",", "]");
    }

    /**
     * This is very close to getStandardJavaScriptParameters(), however,
     * getStandardJavaScriptParameters() may inject JavaScript _code_ e.g. Filter constructors etc.
     * This method returns a pure JSON representation of the view.
     *
     * @return
     * 
     * 
{
  "columns": [
    ["X"],
    [
      "CreatedBy",
      "DisplayName"
    ],
    [
      "ModifiedBy",
      "DisplayName"
    ],
    ["Modified"],
    ["Created"],
    [
      "container",
      "DisplayName"
    ]
  ],
  "requiredVersion": 17.1,
  "queryName": "asdf",
  "sort": "-X,container/DisplayName",
  "schemaName": "lists",
  "filterArray": [
    {
      "fieldKey": [
        "ModifiedBy",
        "DisplayName"
      ],
      "type": "CONTAINS",
      "value": "mat"
    },
    {
      "fieldKey": [
        "ModifiedBy",
        "DisplayName"
      ],
      "type": "CONTAINS",
      "value": "thew"
    }
  ]
}
     */
    public JSONObject getJSON(double requiredVersion)
    {
        var config = new JSONObject();
        config.put("requiredVersion", requiredVersion);
        config.put("schemaName", getSchemaName());
        config.put("queryName", getQueryName());
        if (null != getViewName())
            config.put("viewName", getViewName());
        if (hasContainerFilter())
            config.put("containerFilter", getContainerFilterTypeName());

        // list of fieldkeys represented as String[]
        // e.g. "columns": [ ["X"], [ "CreatedBy", "DisplayName" ] ]
        var columnArray = getJSONColumns();
        if (columnArray != null && !columnArray.isEmpty())
            config.put("columns", columnArray);

        // TODO, why does filterArray go in transforms[]????
        var filterArray = getJSONFilters();
        if (filterArray != null && !filterArray.isEmpty())
            config.put("filterArray", filterArray);

        // sort in URL format e.g. "sort": "-X,container/DisplayName"
        if (hasSort())
            config.put("sort", getSort());

        if (hasQueryParameters())
            config.put("parameters", getQueryParameters());

        return config;
    }

    public JSONArray getJSONFilters()
    {
        // Returns filters in a JSON format for use with the GetData API.
        // Most of this code was taken from ExportScriptModel.getFilterExpressions()
        JSONArray filters = new JSONArray();
        QueryView view = getQueryView();
        SimpleFilter filter = new SimpleFilter(view.getSettings().getSortFilterURL(), view.getDataRegionName());

        for (SimpleFilter.FilterClause filterClause : filter.getClauses())
        {
            if (!(filterClause instanceof CompareType.AbstractCompareClause clause))
                throw new UnsupportedOperationException("Filter clause '" + filterClause.getClass().getName() + "' not currently supported in export scripts");

            JSONObject filterObj = new JSONObject();
            List<String> fieldKey = clause.getFieldKey().getParts();
            CompareType operator = clause.getCompareType();
            var param = clause.toURLParam("q");
            var value = param == null ? null : param.getValue();

            filterObj.put("value", value);
            filterObj.put("type", operator.getPreferredUrlKey());
            filterObj.put("fieldKey", fieldKey);
            filters.put(filterObj);
        }

        return filters;
    }

    public JSONArray getJSONColumns()
    {
        // Returns columns in a JSON format for use with the GetData API.
        JSONArray jsonCols = new JSONArray();

        for (DisplayColumn dc : getQueryView().getDisplayColumns())
        {
            if (dc.isQueryColumn())
            {
                jsonCols.put(dc.getDisplayColumnInfo().getFieldKey().getParts());
            }
        }

        return jsonCols;
    }

    @Override
    protected String quote(String value)
    {
        return PageFlowUtil.jsString(value);
    }

    @Override
    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "LABKEY.Filter.create(" + quote(name) + ", " + quote(value) + ", LABKEY.Filter.Types." + operator.getScriptName() + ")";
    }

    // Produce javascript code block containing all the standard query parameters. Callers need to wrap this block in
    // curly braces (at a minimum) and modify/add parameters as appropriate.
    public String getStandardJavaScriptParameters(int indentSpaces, boolean includeStandardCallbacks)
    {
        String indent = StringUtils.repeat(" ", indentSpaces);
        StringBuilder params = new StringBuilder();
        params.append(indent).append("requiredVersion: 9.1,\n");
        params.append(indent).append("schemaName: ").append(quote(getSchemaName())).append(",\n");

        if (null != getViewName())
            params.append(indent).append("viewName: ").append(quote(getViewName())).append(",\n");

        params.append(indent).append("queryName: ").append(quote(getQueryName())).append(",\n");
        params.append(indent).append("columns: ").append(quote(getColumns())).append(",\n");
        params.append(indent).append("filterArray: ").append(getFilters());

        if (hasSort())
            params.append(",\n").append(indent).append("sort: ").append(quote(getSort()));

        if (hasContainerFilter())
            params.append(",\n").append(indent).append("containerFilter: ").append(quote(getContainerFilterTypeName()));

        if (hasQueryParameters())
        {
            params.append(",\n").append(indent).append("parameters: {")
                .append(getQueryParameters().entrySet().stream()
                    .map(e -> "'" + e.getKey() + "': '" + e.getValue() + "'")
                    .collect(Collectors.joining(", ")))
                .append("}");
        }

        if (includeStandardCallbacks)
        {
            params.append(",\n");
            params.append(indent).append("success: onSuccess,\n");
            params.append(indent).append("failure: onError");
        }

        return params.toString();
    }

    @Override
    public String getScriptExportText()
    {
        String indent = StringUtils.repeat(" ", 4);

        StringBuilder sb = new StringBuilder();

        sb.append("<script type=\"text/javascript\">").append("\n");
        sb.append("\n");
        sb.append("LABKEY.Query.selectRows({").append("\n");
        sb.append(getStandardJavaScriptParameters(4, true)).append("\n");
        sb.append("});").append("\n");
        sb.append("\n");
        sb.append("function onSuccess(results) {\n");
        sb.append(indent).append("var data = '';\n");
        sb.append(indent).append("var length = Math.min(10, results.rows.length);").append("\n");
        sb.append("\n");
        sb.append(indent).append("// Display first 10 rows in a popup dialog").append("\n");
        sb.append(indent).append("for (var idxRow = 0; idxRow < length; idxRow++) {").append("\n");
        sb.append(indent).append(indent).append("var row = results.rows[idxRow];").append("\n");
        sb.append("\n");
        sb.append(indent).append(indent).append("for (var col in row) {").append("\n");
        sb.append(indent).append(indent).append(indent).append("data = data + row[col].value + ' ';").append("\n");
        sb.append(indent).append(indent).append("}").append("\n");
        sb.append("\n");

        sb.append(indent).append(indent).append("data = data + '\\n';").append("\n");
        sb.append(indent).append("}").append("\n");
        sb.append("\n");
        sb.append(indent).append("alert(data);").append("\n");
        sb.append("}").append("\n");
        sb.append("\n");
        sb.append("function onError(errorInfo) {").append("\n");
        sb.append(indent).append("alert(errorInfo.exception);").append("\n");
        sb.append("}").append("\n");
        sb.append("\n");
        sb.append("</script>");


        sb.append("\n\n------\n\n");
        sb.append(getJSON(17.1).toString(2));

        return sb.toString();
    }
}
